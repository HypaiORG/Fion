package com.hypai.fion.bark

import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `httpsUri blocks http downgrades and resolves relative https targets`() {
        val base = URI("https://bark.example.com/v1/push")

        assertEquals("https://bark.example.com/v2/push", HttpsTransport.httpsUri("/v2/push", base)?.toString())
        assertEquals("https://other.example.com/push", HttpsTransport.httpsUri("https://other.example.com/push", base)?.toString())
        assertNull(HttpsTransport.httpsUri("http://bark.example.com/v2/push", base))
        assertNull(HttpsTransport.httpsUri("javascript:alert(1)", base))
    }

    @Test
    fun `postJson rejects non https url immediately without network`() = runTest {
        assertEquals(HttpResult.Failure, HttpsTransport().postJson("http://127.0.0.1:1/push", "{}"))
    }
}
