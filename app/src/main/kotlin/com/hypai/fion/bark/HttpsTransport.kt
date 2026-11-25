package com.hypai.fion.bark

import android.content.Context
import android.net.http.ConnectionMigrationOptions
import android.net.http.DnsOptions
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_RESPONSE_BYTES = 256 * 1024
private const val READ_BUFFER_BYTES = 4 * 1024
private const val MAX_REDIRECTS = 3
private val DIRECT_EXECUTOR = Executor { it.run() }

internal class BoundedResponseBuffer(private val maxBytes: Int = MAX_RESPONSE_BYTES) {
    private val out = ByteArrayOutputStream(512.coerceAtMost(maxBytes))
    private var scratch = ByteArray(0)

    fun append(chunk: ByteBuffer): Boolean {
        val count = chunk.remaining()
        if (count == 0) return true
        if (out.size() + count > maxBytes) return false
        if (chunk.hasArray()) {
            out.write(chunk.array(), chunk.arrayOffset() + chunk.position(), count)
            chunk.position(chunk.limit())
        } else {
            if (scratch.size < count) scratch = ByteArray(count)
            chunk.get(scratch, 0, count)
            out.write(scratch, 0, count)
        }
        return true
    }

    fun asUtf8(): String = out.toString(Charsets.UTF_8)
}

private class ByteArrayUploadProvider(private val payload: ByteArray) : UploadDataProvider() {
    private var offset = 0

    override fun getLength(): Long = payload.size.toLong()

    override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
        val count = minOf(buffer.remaining(), payload.size - offset)
        if (count > 0) {
            buffer.put(payload, offset, count)
            offset += count
        }
        sink.onReadSucceeded(false)
    }

    override fun rewind(sink: UploadDataSink) {
        offset = 0
        sink.onRewindSucceeded()
    }
}

class HttpsTransport(context: Context) : HttpTransport {
    private val appContext = context.applicationContext
    private val engine: HttpEngine by lazy { buildEngine(appContext) }

    override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
        val startUri = httpsUri(url) ?: return HttpResult.Failure
        if (timeoutMs <= 0L) return HttpResult.Failure
        return withTimeoutOrNull(timeoutMs) { execute(startUri, body.encodeToByteArray()) } ?: HttpResult.Failure
    }

    private suspend fun execute(startUri: URI, payload: ByteArray): HttpResult = suspendCancellableCoroutine { cont ->
        val settled = AtomicBoolean(false)
        val buffer = BoundedResponseBuffer()
        var currentUri = startUri
        var redirects = 0

        fun finish(result: HttpResult) {
            if (settled.compareAndSet(false, true) && cont.isActive) cont.resume(result)
        }

        val callback = object : UrlRequest.Callback {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                val allowed = (info.httpStatusCode == 307 || info.httpStatusCode == 308) && redirects < MAX_REDIRECTS
                val next = if (allowed) sameOriginHttpsRedirect(currentUri, newLocationUrl) else null
                if (next == null) {
                    finish(HttpResult.Failure)
                    request.cancel()
                } else {
                    redirects++
                    currentUri = next
                    request.followRedirect()
                }
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(READ_BUFFER_BYTES))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                if (!buffer.append(byteBuffer)) {
                    finish(HttpResult.Failure)
                    request.cancel()
                    return
                }
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                finish(HttpResult.Response(info.httpStatusCode, buffer.asUtf8()))
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) = finish(HttpResult.Failure)

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) = finish(HttpResult.Failure)
        }

        val request = engine.newUrlRequestBuilder(startUri.toASCIIString(), DIRECT_EXECUTOR, callback)
            .setHttpMethod("POST")
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .setCacheDisabled(true)
            .setDirectExecutorAllowed(true)
            .setPriority(UrlRequest.REQUEST_PRIORITY_HIGHEST)
            .setUploadDataProvider(ByteArrayUploadProvider(payload), DIRECT_EXECUTOR)
            .build()

        cont.invokeOnCancellation { request.cancel() }
        request.start()
    }

    companion object {
        private fun buildEngine(context: Context): HttpEngine {
            fun create(storage: File?): HttpEngine {
                val dns = DnsOptions.Builder()
                    .setUseHttpStackDnsResolver(DnsOptions.DNS_OPTION_ENABLED)
                    .setStaleDns(DnsOptions.DNS_OPTION_ENABLED)
                val builder = HttpEngine.Builder(context)
                    .setEnableHttp2(true)
                    .setEnableQuic(true)
                    .setEnableBrotli(true)
                    .setConnectionMigrationOptions(
                        ConnectionMigrationOptions.Builder()
                            .setDefaultNetworkMigration(ConnectionMigrationOptions.MIGRATION_OPTION_ENABLED)
                            .build(),
                    )
                if (storage != null) {
                    builder.setStoragePath(storage.absolutePath)
                    builder.setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISK_NO_HTTP, 0L)
                    dns.setPersistHostCache(DnsOptions.DNS_OPTION_ENABLED)
                } else {
                    builder.setEnableHttpCache(HttpEngine.Builder.HTTP_CACHE_DISABLED, 0L)
                }
                return builder.setDnsOptions(dns.build()).build()
            }

            val storage = File(context.cacheDir, "http_engine").apply { mkdirs() }.takeIf { it.isDirectory && it.canWrite() }
            return storage?.let { runCatching { create(it) }.getOrNull() } ?: create(null)
        }

        internal fun httpsUri(raw: String, base: URI? = null): URI? {
            val parsed = try {
                URI(raw.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val uri = base?.resolve(parsed) ?: parsed
            return uri.takeIf { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrEmpty() }
        }

        internal fun sameOriginHttpsRedirect(origin: URI, location: String?): URI? {
            val target = httpsUri(location ?: return null, origin) ?: return null
            val originPort = origin.port.takeIf { it > 0 } ?: 443
            val targetPort = target.port.takeIf { it > 0 } ?: 443
            return target.takeIf { origin.host.equals(target.host, ignoreCase = true) && originPort == targetPort }
        }
    }
}
