package com.hypai.fion.bark

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

private const val MAX_RESPONSE_BYTES = 256 * 1024
private const val CONNECT_TIMEOUT_MS = 4_000
private const val READ_TIMEOUT_MS = 6_000
private const val MAX_REDIRECTS = 3

class HttpsTransport : HttpTransport {
    override suspend fun postJson(url: String, body: String): HttpResult = withContext(Dispatchers.IO) {
        val active = AtomicReference<HttpsURLConnection>()
        val disconnectOnCancel = coroutineContext.job.invokeOnCompletion { if (it != null) active.get()?.disconnect() }
        try {
            post(httpsUri(url) ?: return@withContext HttpResult.Failure, body.encodeToByteArray(), active::set)
        } catch (_: IOException) {
            ensureActive()
            HttpResult.Failure
        } finally {
            disconnectOnCancel.dispose()
        }
    }

    private fun post(start: URI, payload: ByteArray, track: (HttpsURLConnection) -> Unit): HttpResult {
        var uri = start
        repeat(MAX_REDIRECTS + 1) {
            val connection = uri.toURL().openConnection() as? HttpsURLConnection ?: return HttpResult.Failure
            track(connection)
            var keepAlive = false
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                val text = if (stream == null) "" else readBounded(stream) ?: return HttpResult.Failure
                keepAlive = true
                if (status == 307 || status == 308) {
                    uri = httpsUri(connection.getHeaderField("Location") ?: return HttpResult.Failure, uri) ?: return HttpResult.Failure
                    return@repeat
                }
                return HttpResult.Response(status, text)
            } finally {
                if (!keepAlive) connection.disconnect()
            }
        }
        return HttpResult.Failure
    }

    private fun readBounded(stream: InputStream): String? = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var count = input.read(buffer)
        while (count >= 0) {
            if (out.size() + count > MAX_RESPONSE_BYTES) return null
            out.write(buffer, 0, count)
            count = input.read(buffer)
        }
        out.toString(Charsets.UTF_8)
    }

    companion object {
        internal fun httpsUri(raw: String, base: URI? = null): URI? {
            val parsed = try {
                URI(raw.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val uri = base?.resolve(parsed) ?: parsed
            return uri.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrEmpty() }
        }
    }
}
