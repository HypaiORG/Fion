package com.hypai.fion.core

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarkProtocolTest {
    private val key16 = "1234567890123456"

    private val sms = EventKind.Sms.defaultProfile

    private fun message(title: String, body: String) = Message("id-1", EventKind.Sms, SimKey.subscription(1), SimTag(0), title, body, receivedAtMs = 0)

    private fun pushed(profile: PushProfile, language: Language, kind: EventKind = EventKind.Sms) =
        Json.parseToJsonElement(
            buildPushBody(BarkEndpoint("api.day.app", "k", cipher = null), profile, language, message("t", "b").copy(kind = kind), { error("no iv expected") }),
        ).jsonObject

    @Test
    fun `cbc output matches the openssl vector from bark docs`() {
        val aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, key16)

        val ciphertext = aesEncrypt("""{"body": "test", "sound": "birdsong"}""", aes, iv = key16)

        assertEquals("+aPt5cwN9GbTLLSFri60l3h1X00u/9j1FENfWiTxhNHVLGU+XoJ15JJG5W/d/yf0", ciphertext)
    }

    @Test
    fun `gcm output is ciphertext followed by tag`() {
        val aes = AesConfig(AesKeySize.Aes256, AesMode.Gcm, key16 + key16)
        val iv = "abcdefghijkl"

        val encrypted = Base64.getDecoder().decode(aesEncrypt("飞鸿", aes, iv))

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes.key.toByteArray(), "AES"), GCMParameterSpec(128, iv.toByteArray()))
        assertEquals("飞鸿", String(cipher.doFinal(encrypted)))
        assertEquals("飞鸿".toByteArray().size + 16, encrypted.size)
    }

    @Test
    fun `ecb round trips with a 24 byte key`() {
        val aes = AesConfig(AesKeySize.Aes192, AesMode.Ecb, key16 + "12345678")

        val encrypted = Base64.getDecoder().decode(aesEncrypt("hello", aes, iv = ""))

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes.key.toByteArray(), "AES"))
        assertEquals("hello", String(cipher.doFinal(encrypted)))
    }

    @Test
    fun `plain body carries every parameter`() {
        val bark = BarkEndpoint("api.day.app", "k", cipher = null)
        val profile = sms.copy(sound = "glass", level = BarkLevel.TimeSensitive, archive = false, group = GroupChoice.Custom("飞鸿"))

        val body = Json.parseToJsonElement(buildPushBody(bark, profile, Language.Zh, message("t", "b"), { error("no iv expected") })).jsonObject

        assertEquals(
            mapOf(
                "device_key" to "k", "id" to "id-1", "title" to "t", "body" to "b", "group" to "飞鸿",
                "sound" to "glass", "level" to "timeSensitive", "isArchive" to "0",
            ),
            body.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `an empty group is left out of the push`() {
        assertFalse("group" in pushed(sms.copy(group = GroupChoice.Custom("")), Language.Zh))
    }

    @Test
    fun `the default group follows the language at push time and a custom group is sent as typed`() {
        val call = EventKind.MissedCall.defaultProfile
        fun group(profile: PushProfile, language: Language, kind: EventKind = EventKind.Sms) =
            pushed(profile, language, kind).getValue("group").jsonPrimitive.content

        assertEquals(listOf("短信", "SMS"), listOf(Language.Zh, Language.En).map { group(sms, it) })
        assertEquals(listOf("未接来电", "Missed calls"), listOf(Language.Zh, Language.En).map { group(call, it, EventKind.MissedCall) })
        assertEquals(listOf("验证码", "验证码"), listOf(Language.Zh, Language.En).map { group(sms.copy(group = GroupChoice.Custom("验证码")), it) })
    }

    @Test
    fun `encrypted body hides every parameter except the device key`() {
        val aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, key16)
        val bark = BarkEndpoint("api.day.app", "k", aes)

        val body = Json.parseToJsonElement(buildPushBody(bark, sms, Language.Zh, message("t", "secret"), { key16.take(it) })).jsonObject

        assertEquals(setOf("device_key", "id", "ciphertext", "iv"), body.keys)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key16.toByteArray(), "AES"), IvParameterSpec(body.getValue("iv").jsonPrimitive.content.toByteArray()))
        val decrypted = String(cipher.doFinal(Base64.getDecoder().decode(body.getValue("ciphertext").jsonPrimitive.content)))
        assertEquals(
            mapOf("id" to "id-1", "title" to "t", "body" to "secret", "group" to "短信", "sound" to "bell", "level" to "active", "isArchive" to "1"),
            Json.parseToJsonElement(decrypted).jsonObject.mapValues { it.value.jsonPrimitive.content },
        )
    }

    @Test
    fun `the endpoint encrypts only when a key is filled in`() {
        val plain = BarkSettings(deviceKey = "k", aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, key = ""))
        val aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, key16)

        assertEquals(BarkEndpoint("api.day.app", "k", cipher = null), plain.endpoint())
        assertEquals(BarkEndpoint("api.day.app", "k", aes), plain.copy(aes = aes).endpoint())
    }

    @Test
    fun `an empty device key or any invalid field leaves no endpoint`() {
        assertNull(BarkSettings().endpoint())
        assertNull(BarkSettings(deviceKey = "k", aes = AesConfig(AesKeySize.Aes256, AesMode.Cbc, key16)).endpoint())
        assertNull(BarkSettings(server = "http://api.day.app", deviceKey = "k").endpoint())
        assertNull(BarkSettings(deviceKey = "body?group=test").endpoint())
    }

    @Test
    fun `ecb request omits iv`() {
        val bark = BarkEndpoint("api.day.app", "k", AesConfig(AesKeySize.Aes128, AesMode.Ecb, key16))

        val body = Json.parseToJsonElement(buildPushBody(bark, sms, Language.Zh, message("", "b"), { "" })).jsonObject

        assertEquals(setOf("device_key", "id", "ciphertext"), body.keys)
    }

    @Test
    fun `delivery is derived from status and code`() {
        assertEquals(Delivery.Delivered, parseDelivery(ok))
        assertEquals(Delivery.Rejected("failed to get device token"), parseDelivery(badKey))
        assertEquals(Delivery.Rejected("HTTP 404"), parseDelivery(HttpResult.Response(404, "not found")))
        assertEquals(Delivery.Rejected("HTTP 200"), parseDelivery(HttpResult.Response(200, "<html>portal</html>")))
        assertEquals(Delivery.Unreachable, parseDelivery(HttpResult.Response(500, """{"code":500,"message":"push failed"}""")))
        assertEquals(Delivery.Unreachable, parseDelivery(HttpResult.Response(429, "")))
        assertEquals(Delivery.Unreachable, parseDelivery(HttpResult.Failure))
    }

    @Test
    fun `normalizing extracts the key from a pasted push url`() {
        val draft = BarkSettings(server = " https://bark.example.com/ ", deviceKey = "https://api.day.app/AbCd123/")

        val endpoint = draft.endpoint()!!

        assertEquals("bark.example.com", endpoint.server)
        assertEquals("AbCd123", endpoint.deviceKey)
        assertEquals("fixture-key", BarkSettings(deviceKey = "https://api.day.app/fixture-key/title/body?group=test").endpoint()?.deviceKey)
        assertEquals("k9", BarkSettings(server = "example.com/bark", deviceKey = "https://example.com/bark/k9/hello").endpoint()?.deviceKey)
        assertEquals("plainKey", BarkSettings(deviceKey = " plainKey/ ").endpoint()?.deviceKey)
        assertEquals("https://bark.example.com/push", pushUrl(endpoint))
    }

    @Test
    fun `form errors cover server key and encryption key`() {
        assertTrue(BarkSettings(deviceKey = "k").formErrors().isValid)
        assertTrue(BarkSettings().formErrors().isValid)
        assertTrue(BarkSettings(server = "bark.example.com:8443/base", deviceKey = "k").formErrors().isValid)
        listOf("", "http://api.day.app", "a b.com", "user@host", "host?x=1", "api.day.app:99999", "api.day.app:0").forEach {
            assertFalse(it, BarkSettings(server = it, deviceKey = "k").formErrors().isValid)
        }
        assertFalse(BarkSettings(deviceKey = "body?group=test").formErrors().isValid)
        val short = BarkSettings(deviceKey = "k", aes = AesConfig(AesKeySize.Aes256, AesMode.Cbc, key16))
        assertEquals(KeyLengthMismatch(expected = 32, actual = 16), short.formErrors().key)
        assertNull(short.copy(aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, key16)).formErrors().key)
        assertNull(short.copy(aes = AesConfig(AesKeySize.Aes256, AesMode.Cbc, "")).formErrors().key)
    }
}
