package com.hypai.fion.bark

import com.hypai.fion.forward.Delivery
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.resolve
import com.hypai.fion.store.Message
import com.hypai.fion.store.PushProfile
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

const val DEFAULT_BARK_SERVER = "api.day.app"

val BARK_SOUNDS = listOf("telegraph", "healthnotification", "glass", "bell")

@Serializable
enum class BarkLevel(val wire: String) {
    Passive("passive"),
    Active("active"),
    TimeSensitive("timeSensitive"),
    Critical("critical"),
}

@Serializable
data class BarkSettings(
    val server: String = DEFAULT_BARK_SERVER,
    val deviceKey: String = "",
    val aes: AesConfig = AesConfig(),
)

data class BarkEndpoint(val server: String, val deviceKey: String, val cipher: AesConfig?) {
    val host: String get() = server.substringBefore('/')
}

sealed interface HttpResult {
    data class Response(val status: Int, val body: String) : HttpResult
    data object Failure : HttpResult
}

interface HttpTransport {
    /** Returns [HttpResult.Failure] once [timeoutMs] elapses; callers set no other deadline. */
    suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult
}

fun pushUrl(endpoint: BarkEndpoint): String = "https://${endpoint.server}/push"

private fun JsonObjectBuilder.putContent(profile: PushProfile, language: Language, message: Message) {
    val group = profile.group.resolve(message.kind, language)
    put("id", message.id)
    if (message.title.isNotEmpty()) put("title", message.title)
    put("body", message.body)
    if (group.isNotEmpty()) put("group", group)
    put("sound", profile.sound)
    put("level", profile.level.wire)
    put("isArchive", if (profile.archive) "1" else "0")
}

fun buildPushBody(
    endpoint: BarkEndpoint,
    profile: PushProfile,
    language: Language,
    message: Message,
    newIv: (length: Int) -> String,
): String {
    val aes = endpoint.cipher ?: return buildJsonObject {
        put("device_key", endpoint.deviceKey)
        putContent(profile, language, message)
    }.toString()
    val plaintext = buildJsonObject { putContent(profile, language, message) }.toString()
    val iv = newIv(aes.mode.nonceBytes)
    return buildJsonObject {
        put("device_key", endpoint.deviceKey)
        put("id", message.id)
        put("ciphertext", aesEncrypt(plaintext, aes, iv))
        if (iv.isNotEmpty()) put("iv", iv)
    }.toString()
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun parseJsonObject(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (_: SerializationException) {
    null
}

private fun isClientRejection(code: Int): Boolean = code in 400..499 && code != 408 && code != 429

fun parseDelivery(result: HttpResult): Delivery = when (result) {
    HttpResult.Failure -> Delivery.Unreachable()
    is HttpResult.Response -> {
        val json = parseJsonObject(result.body)
        val code = (json?.get("code") as? JsonPrimitive)?.intOrNull
        when {
            result.status == 200 && code == 200 -> Delivery.Delivered
            result.status == 200 && code != null && isClientRejection(code) ->
                Delivery.Rejected(json.string("message") ?: "HTTP $code")
            result.status == 200 && code != null && code >= 500 -> Delivery.Unreachable(serverError = true)
            result.status == 200 || result.status in 300..399 -> Delivery.Unreachable()
            result.status == 408 || result.status == 429 -> Delivery.Unreachable()
            result.status == 500 -> Delivery.Unreachable(serverError = true)
            result.status >= 500 -> Delivery.Unreachable()
            else -> Delivery.Rejected(json?.string("message") ?: "HTTP ${result.status}")
        }
    }
}

class BarkClient(private val transport: HttpTransport, private val random: SecureRandom = SecureRandom()) {
    suspend fun push(
        endpoint: BarkEndpoint,
        profile: PushProfile,
        language: Language,
        message: Message,
        timeoutMs: Long,
    ): Delivery {
        val payload = buildPushBody(endpoint, profile, language, message) { generateNonce(it, random) }
        return parseDelivery(transport.postJson(pushUrl(endpoint), payload, timeoutMs))
    }
}
