package com.hypai.fion.platform

import android.content.Context
import android.net.http.HttpEngine
import android.net.http.HttpException
import android.net.http.UploadDataProvider
import android.net.http.UploadDataSink
import android.net.http.UrlRequest
import android.net.http.UrlResponseInfo
import com.hypai.fion.core.HttpResult
import com.hypai.fion.core.HttpTransport
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine

private const val MAX_RESPONSE_BYTES = 256 * 1024

class HttpEngineTransport(context: Context) : HttpTransport {
    private val engine: HttpEngine by lazy { HttpEngine.Builder(context).build() }
    private val executor: Executor = Dispatchers.IO.asExecutor()

    override suspend fun postJson(url: String, body: String): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val callback = object : UrlRequest.Callback {
                private val received = ByteArrayOutputStream()

                override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                    request.followRedirect()
                }

                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    request.read(ByteBuffer.allocateDirect(16 * 1024))
                }

                override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer) {
                    buffer.flip()
                    val chunk = ByteArray(buffer.remaining())
                    buffer.get(chunk)
                    received.write(chunk)
                    if (received.size() > MAX_RESPONSE_BYTES) {
                        request.cancel()
                    } else {
                        buffer.clear()
                        request.read(buffer)
                    }
                }

                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    continuation.resume(HttpResult.Response(info.httpStatusCode, received.toString(Charsets.UTF_8)))
                }

                override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: HttpException) {
                    continuation.resume(HttpResult.Failure)
                }

                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                    if (continuation.isActive) continuation.resume(HttpResult.Failure)
                }
            }

            val request = engine.newUrlRequestBuilder(url, executor, callback)
                .setHttpMethod("POST")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .setUploadDataProvider(ByteArrayUpload(body.encodeToByteArray()), executor)
                .build()
            request.start()
            continuation.invokeOnCancellation { request.cancel() }
        }
}

private class ByteArrayUpload(private val bytes: ByteArray) : UploadDataProvider() {
    private var position = 0

    override fun getLength(): Long = bytes.size.toLong()

    override fun read(sink: UploadDataSink, buffer: ByteBuffer) {
        val count = minOf(buffer.remaining(), bytes.size - position)
        buffer.put(bytes, position, count)
        position += count
        sink.onReadSucceeded(false)
    }

    override fun rewind(sink: UploadDataSink) {
        position = 0
        sink.onRewindSucceeded()
    }
}
