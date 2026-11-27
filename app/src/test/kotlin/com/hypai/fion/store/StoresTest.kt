package com.hypai.fion.store

import androidx.datastore.core.CorruptionException
import com.hypai.fion.bark.BarkSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

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
