package com.hypai.fion.core

import androidx.datastore.core.DataStore
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class Forwarder(
    private val settings: DataStore<Settings>,
    private val runtime: DataStore<Runtime>,
    private val bark: BarkClient,
    private val bootCount: Int,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
    private val language: () -> Language,
    private val scheduleRetry: () -> Unit,
    private val newSecret: () -> String,
) {
    private val retryLock = Mutex()

    suspend fun activate() {
        settle { it.copy(activatedBootCount = bootCount) }
    }

    fun isActive(settings: Settings, runtime: Runtime): Boolean =
        settings.forwarding && runtime.activatedBootCount == bootCount

    private fun route(settings: Settings, runtime: Runtime, kind: EventKind): BarkEndpoint? =
        settings.bark.endpoint()?.takeIf { isActive(settings, runtime) && settings.forwards(kind) }

    suspend fun forward(event: ForwardEvent, progress: (Runtime) -> Runtime = { it }) {
        val current = settings.data.first()
        val state = runtime.data.first()
        val endpoint = route(current, state, event.kind) ?: return settle(progress)
        val message = event.toMessage(current.push(event.kind).template, zone(), language(), state.deliverySecret ?: createDeliverySecret())
        when (push(endpoint, current, message)) {
            Delivery.Delivered -> settle {
                progress(it).copy(stats = it.stats.withDelivered(message, now() - message.receivedAtMs))
            }
            is Delivery.Rejected -> settle { progress(it).copy(stats = it.stats.withFailed()) }
            Delivery.Unreachable -> {
                settle { progress(it).copy(pending = it.pending + message) }
                scheduleRetry()
            }
        }
    }

    suspend fun retryPending(): Boolean = retryLock.withLock {
        for (queued in runtime.data.first().pending) {
            val current = settings.data.first()
            val delivery = when {
                now() - queued.receivedAtMs > PENDING_EXPIRY.inWholeMilliseconds -> Delivery.Rejected("expired")
                else -> push(route(current, runtime.data.first(), queued.kind) ?: continue, current, queued)
            }
            when (delivery) {
                Delivery.Unreachable -> return@withLock false
                Delivery.Delivered -> settle {
                    it.copy(pending = it.pending - queued, stats = it.stats.withDelivered(queued, firstAttemptLatencyMs = null))
                }
                is Delivery.Rejected -> settle {
                    it.copy(pending = it.pending - queued, stats = it.stats.withFailed())
                }
            }
        }
        true
    }

    private suspend fun createDeliverySecret(): String {
        val created = newSecret()
        val stored = withContext(NonCancellable) {
            runtime.updateData { if (it.deliverySecret == null) it.copy(deliverySecret = created) else it }
        }
        currentCoroutineContext().ensureActive()
        return stored.deliverySecret ?: created
    }

    private suspend fun settle(transform: (Runtime) -> Runtime) {
        withContext(NonCancellable) { runtime.updateData { transform(it) } }
    }

    private suspend fun push(endpoint: BarkEndpoint, settings: Settings, message: Message): Delivery =
        withTimeoutOrNull(PUSH_TIMEOUT) { bark.push(endpoint, settings.push(message.kind), language(), message) } ?: Delivery.Unreachable

    companion object {
        val PUSH_TIMEOUT = 8.seconds
        val PENDING_EXPIRY = 24.hours
    }
}

private fun ForwardEvent.deliveryId(secret: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
    return mac.doFinal("$kind|${sim.key.value}|$from|$occurredAtMs|$smsContent".toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }
}

fun ForwardEvent.toMessage(choice: TemplateChoice, zone: ZoneId, language: Language, deliverySecret: String): Message {
    val template = choice.resolve(kind, language)
    val values = variables(zone, language)
    return Message(
        id = deliveryId(deliverySecret),
        kind = kind,
        simKey = sim.key,
        sim = sim.tag,
        title = renderTemplate(template.title, values),
        body = renderTemplate(template.body, values),
        receivedAtMs = receivedAtMs,
    )
}
