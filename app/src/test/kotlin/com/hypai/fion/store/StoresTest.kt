package com.hypai.fion.store

import androidx.datastore.core.CorruptionException
import com.hypai.fion.bark.AesConfig
import com.hypai.fion.bark.AesKeySize
import com.hypai.fion.bark.AesMode
import com.hypai.fion.bark.BarkSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

private class TestAesGcmCipher(
    key: ByteArray = ByteArray(32) { (it + 1).toByte() },
    associatedData: String = "settings",
) : ByteCipher {
    private val secret = SecretKeySpec(key, "AES")
    private val aad = associatedData.encodeToByteArray()
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
            updateAAD(aad)
        }
        val body = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + body.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, 1)
            body.copyInto(this, 1 + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val ivLen = ciphertext[0].toInt() and 0xFF
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, ciphertext, 1, ivLen))
            updateAAD(aad)
        }
        return cipher.doFinal(ciphertext, 1 + ivLen, ciphertext.size - 1 - ivLen)
    }
}

class StoresTest {
    @Test
    fun `json serializer ignores unknown fields from newer schema versions`() = runTest {
        val serializer = JsonSerializer(Settings.serializer(), Settings())
        val payload = """{"forwardingEnabled":false,"futureField":123,"bark":{"deviceKey":"abc","extraFlag":true}}"""

        val decoded = serializer.readFrom(ByteArrayInputStream(payload.encodeToByteArray()))

        assertEquals(false, decoded.forwardingEnabled)
        assertEquals(BarkSettings(deviceKey = "abc"), decoded.bark)
    }

    @Test
    fun `json serializer wraps malformed payload in CorruptionException`() {
        val serializer = JsonSerializer(Settings.serializer(), Settings())

        assertThrows(CorruptionException::class.java) {
            runBlocking {
                serializer.readFrom(ByteArrayInputStream("{broken".encodeToByteArray()))
            }
        }
    }

    @Test
    fun `json serializer round trips settings cleanly`() = runTest {
        val serializer = JsonSerializer(Settings.serializer(), Settings())
        val original = Settings(forwardingEnabled = false, bark = BarkSettings(server = "bark.test", deviceKey = "key123"))
        val out = ByteArrayOutputStream()

        serializer.writeTo(original, out)
        val restored = serializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertEquals(original, restored)
    }

    @Test
    fun `encrypted json serializer hides secrets on disk and rejects tampering`() = runTest {
        val cipher = TestAesGcmCipher()
        val serializer = JsonSerializer(Settings.serializer(), Settings(), cipher)
        val original = Settings(
            bark = BarkSettings(
                server = "bark.test",
                deviceKey = "secret-device-key",
                aes = AesConfig(AesKeySize.Aes256, AesMode.Gcm, "0123456789abcdef0123456789abcdef"),
            ),
        )
        val out = ByteArrayOutputStream()

        serializer.writeTo(original, out)
        val raw = out.toByteArray()
        assertFalse(raw.decodeToString().contains("secret-device-key"))
        assertFalse(raw.decodeToString().contains("0123456789abcdef"))
        assertEquals(original, serializer.readFrom(ByteArrayInputStream(raw)))

        val tampered = raw.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0xFF).toByte() }
        assertThrows(CorruptionException::class.java) {
            runBlocking { serializer.readFrom(ByteArrayInputStream(tampered)) }
        }

        val wrongAad = JsonSerializer(Settings.serializer(), Settings(), TestAesGcmCipher(associatedData = "runtime"))
        assertThrows(CorruptionException::class.java) {
            runBlocking { wrongAad.readFrom(ByteArrayInputStream(raw)) }
        }
    }

    @Test
    fun `json serializer preserves settings when template contains unknown variable id`() = runTest {
        val serializer = JsonSerializer(Settings.serializer(), Settings())
        val payload = """
            {
              "bark": {"deviceKey": "keep-me"},
              "smsPush": {
                "sound": "bell",
                "template": {
                  "type": "custom",
                  "template": {
                    "title": [{"type": "var", "id": "future.var"}, {"type": "var", "id": "sms.from"}],
                    "body": [{"type": "text", "value": "ok"}]
                  }
                }
              }
            }
        """.trimIndent()

        val restored = serializer.readFrom(ByteArrayInputStream(payload.encodeToByteArray()))

        assertEquals("keep-me", restored.bark.deviceKey)
    }
}
