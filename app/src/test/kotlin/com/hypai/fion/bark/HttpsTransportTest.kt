package com.hypai.fion.bark

import java.net.URI
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsTransportTest {
    @Test
    fun `httpsUri accepts only valid https urls`() {
        assertEquals("https://api.day.app/push", HttpsTransport.httpsUri("https://api.day.app/push")?.toString())
        assertEquals("HTTPS://api.day.app/push", HttpsTransport.httpsUri("HTTPS://api.day.app/push")?.toString())
        assertNull(HttpsTransport.httpsUri("http://api.day.app/push"))
        assertNull(HttpsTransport.httpsUri("file:///etc/passwd"))
        assertNull(HttpsTransport.httpsUri("https:///missing-host"))
        assertNull(HttpsTransport.httpsUri("not a url"))
    }

    @Test
    fun `sameOriginHttpsRedirect allows same origin and blocks cross host cross port and downgrades`() {
        val base = URI("https://bark.example.com/v1/push")

        assertEquals("https://bark.example.com/v2/push", HttpsTransport.sameOriginHttpsRedirect(base, "/v2/push")?.toString())
        assertEquals("https://BARK.EXAMPLE.COM:443/v2/push", HttpsTransport.sameOriginHttpsRedirect(base, "https://BARK.EXAMPLE.COM:443/v2/push")?.toString())
        assertNull(HttpsTransport.sameOriginHttpsRedirect(base, "https://other.example.com/push"))
        assertNull(HttpsTransport.sameOriginHttpsRedirect(base, "https://bark.example.com:8443/v2/push"))
        assertNull(HttpsTransport.sameOriginHttpsRedirect(base, "http://bark.example.com/v2/push"))
        assertNull(HttpsTransport.sameOriginHttpsRedirect(base, "javascript:alert(1)"))
        assertNull(HttpsTransport.sameOriginHttpsRedirect(base, null))
    }

    @Test
    fun `bounded response buffer enforces size cap for heap and direct buffers`() {
        val buffer = BoundedResponseBuffer(maxBytes = 8)
        val first = ByteBuffer.allocateDirect(4).apply {
            put("hell".toByteArray())
            flip()
        }
        val second = ByteBuffer.wrap("o!".toByteArray())
        val overflow = ByteBuffer.wrap("123".toByteArray())

        assertTrue(buffer.append(first))
        assertTrue(buffer.append(second))
        assertFalse(buffer.append(overflow))
        assertEquals("hello!", buffer.asUtf8())
    }

    @Test
    fun `bounded response buffer preserves multibyte utf8 split across direct chunks`() {
        val expected = "飞鸿通知验证通过🚀"
        val raw = expected.toByteArray(Charsets.UTF_8)
        val buffer = BoundedResponseBuffer(maxBytes = 64)
        val chunk = ByteBuffer.allocateDirect(2)

        var offset = 0
        while (offset < raw.size) {
            chunk.clear()
            val len = minOf(2, raw.size - offset)
            chunk.put(raw, offset, len)
            chunk.flip()
            assertTrue(buffer.append(chunk))
            offset += len
        }

        assertEquals(expected, buffer.asUtf8())
    }
}
