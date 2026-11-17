package com.hypai.fion.store

import androidx.datastore.core.CorruptionException
import com.hypai.fion.bark.BarkSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
            kotlinx.coroutines.runBlocking {
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
}
