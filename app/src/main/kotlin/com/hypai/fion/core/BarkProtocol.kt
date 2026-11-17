package com.hypai.fion.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
enum class BarkLevel(val wire: String) {
    Passive("passive"),
    Active("active"),
    TimeSensitive("timeSensitive"),
    Critical("critical"),
}

val BARK_SOUNDS = listOf("telegraph", "healthnotification", "glass", "bell")

@Serializable
enum class AesKeySize(val bytes: Int) {
    Aes128(16), Aes192(24), Aes256(32);

    val label: String get() = "AES-${bytes * 8}"
}

@Serializable
enum class AesMode(val ivLength: Int) {
    Cbc(16), Ecb(0), Gcm(12);

    val label: String get() = name.uppercase()
}

@Serializable
data class AesConfig(
    val keySize: AesKeySize = AesKeySize.Aes128,
    val mode: AesMode = AesMode.Cbc,
    val key: String = "",
)

@Serializable
data class BarkSettings(
    val server: String = "api.day.app",
    val deviceKey: String = "",
    val aes: AesConfig = AesConfig(),
)

data class BarkEndpoint(val server: String, val deviceKey: String, val cipher: AesConfig?)

sealed interface HttpResult {
    data class Response(val status: Int, val body: String) : HttpResult
    data object Failure : HttpResult
}

interface HttpTransport {
    suspend fun postJson(url: String, body: String): HttpResult
}

private const val IV_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
private const val GCM_TAG_BITS = 128

fun randomIv(length: Int, random: SecureRandom): String =
    String(CharArray(length) { IV_ALPHABET[random.nextInt(IV_ALPHABET.length)] })

fun aesEncrypt(plaintext: String, aes: AesConfig, iv: String): String {
    val key = SecretKeySpec(aes.key.toByteArray(), "AES")
    val cipher = when (aes.mode) {
        AesMode.Cbc -> Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv.toByteArray()))
        }
        AesMode.Ecb -> Cipher.getInstance("AES/ECB/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        AesMode.Gcm -> Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv.toByteArray()))
        }
    }
    return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray()))
}

fun pushUrl(endpoint: BarkEndpoint): String = "https://${endpoint.server}/push"

fun buildPushBody(endpoint: BarkEndpoint, profile: PushProfile, language: Language, message: Message, newIv: (length: Int) -> String): String {
    val group = profile.group.resolve(message.kind, language)
    val content = buildJsonObject {
        put("id", message.id)
        if (message.title.isNotEmpty()) put("title", message.title)
        put("body", message.body)
        if (group.isNotEmpty()) put("group", group)
        put("sound", profile.sound)
        put("level", profile.level.wire)
        put("isArchive", if (profile.archive) "1" else "0")
    }
    val aes = endpoint.cipher
    val request = if (aes == null) {
        buildJsonObject {
            put("device_key", endpoint.deviceKey)
            content.forEach { (key, value) -> put(key, value) }
        }
    } else {
        val iv = newIv(aes.mode.ivLength)
        buildJsonObject {
            put("device_key", endpoint.deviceKey)
            put("id", message.id)
            put("ciphertext", aesEncrypt(content.toString(), aes, iv))
            if (iv.isNotEmpty()) put("iv", iv)
        }
    }
    return request.toString()
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun parseJsonObject(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
    null
}

fun parseDelivery(result: HttpResult): Delivery = when (result) {
    HttpResult.Failure -> Delivery.Unreachable
    is HttpResult.Response -> {
        val json = parseJsonObject(result.body)
        val code = (json?.get("code") as? JsonPrimitive)?.intOrNull
        when {
            result.status == 200 && code == 200 -> Delivery.Delivered
            result.status == 408 || result.status == 429 || result.status >= 500 -> Delivery.Unreachable
            else -> Delivery.Rejected(json?.string("message") ?: "HTTP ${result.status}")
        }
    }
}

class BarkClient(private val transport: HttpTransport, private val random: SecureRandom = SecureRandom()) {
    suspend fun push(endpoint: BarkEndpoint, profile: PushProfile, language: Language, message: Message): Delivery {
        val payload = buildPushBody(endpoint, profile, language, message) { randomIv(it, random) }
        return parseDelivery(transport.postJson(pushUrl(endpoint), payload))
    }
}

data class KeyLengthMismatch(val expected: Int, val actual: Int)

data class BarkFormErrors(
    val invalidServer: Boolean = false,
    val invalidDeviceKey: Boolean = false,
    val key: KeyLengthMismatch? = null,
) {
    val isValid: Boolean get() = !invalidServer && !invalidDeviceKey && key == null
}

private fun extractDeviceKey(input: String, server: String): String {
    val text = input.trim()
    if ("://" !in text) return text.trim('/')
    val path = text.substringAfter("://").substringBefore('?').substringBefore('#').substringAfter('/', "")
    val prefix = server.substringAfter('/', "")
    val afterPrefix = if (prefix.isNotEmpty() && path.startsWith("$prefix/")) path.removePrefix("$prefix/") else path
    return afterPrefix.split('/').firstOrNull { it.isNotEmpty() }.orEmpty()
}

private fun BarkSettings.normalized(): BarkSettings {
    val host = server.trim().removePrefix("https://").trimEnd('/')
    return copy(server = host, deviceKey = extractDeviceKey(deviceKey, host))
}

private val serverPattern = Regex("""[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:(\d{1,5}))?(/[A-Za-z0-9._~/-]*)?""")
private val deviceKeyPattern = Regex("""[A-Za-z0-9_-]+""")

private fun isValidServer(server: String): Boolean {
    val port = serverPattern.matchEntire(server)?.groupValues?.get(3) ?: return false
    return port.isEmpty() || port.toInt() in 1..65535
}

private fun BarkSettings.normalizedErrors(): BarkFormErrors = BarkFormErrors(
    invalidServer = !isValidServer(server),
    invalidDeviceKey = deviceKey.isNotEmpty() && !deviceKeyPattern.matches(deviceKey),
    key = aes.keyError(),
)

fun BarkSettings.formErrors(): BarkFormErrors = normalized().normalizedErrors()

fun BarkSettings.endpoint(): BarkEndpoint? {
    val input = normalized()
    if (input.deviceKey.isEmpty() || !input.normalizedErrors().isValid) return null
    return BarkEndpoint(input.server, input.deviceKey, input.aes.takeIf { it.key.isNotEmpty() })
}

fun AesConfig.keyError(): KeyLengthMismatch? {
    val actual = key.toByteArray().size
    return KeyLengthMismatch(keySize.bytes, actual).takeUnless { actual == 0 || actual == keySize.bytes }
}
