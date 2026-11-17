package com.hypai.fion.forward

import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.BarkEndpoint
import com.hypai.fion.bark.endpoint
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Message
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
import com.hypai.fion.store.TemplateChoice
import java.time.ZoneId
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class Forwarder(
    private val settingsStore: SettingsStore,
    private val stateStore: ForwardStateStore,
    private val bark: BarkClient,
    private val bootCount: () -> Int,
    private val now: () -> Long,
    private val elapsedMs: () -> Long = now,
    private val zone: () -> ZoneId,
    private val language: () -> Language,
    private val scheduleRetry: () -> Unit,
    private val cancelRetry: () -> Unit = {},
    private val newSecret: () -> String,
) {
    private val retryLock = Mutex()
    private val currentBootCount: Int by lazy(bootCount)

    suspend fun activate() {
        val boot = currentBootCount
        if (stateStore.snapshot().activatedBootCount == boot) return
        stateStore.settle { if (it.activatedBootCount == boot) it else it.copy(activatedBootCount = boot) }
    }

    fun isActive(settings: Settings, state: ForwardState): Boolean =
        settings.forwarding && state.activatedBootCount == currentBootCount

    private fun route(settings: Settings, state: ForwardState, kind: EventKind): BarkEndpoint? =
        settings.bark.endpoint()?.takeIf { isActive(settings, state) && settings.forwards(kind) }

    fun accepts(settings: Settings, state: ForwardState, kind: EventKind): Boolean = route(settings, state, kind) != null

    suspend fun forward(
        event: ForwardEvent,
        timeout: Duration = PUSH_TIMEOUT,
        receivedElapsedMs: Long? = null,
        progress: ((ForwardState) -> ForwardState)? = null,
    ) {
        val current = settingsStore.snapshot()
        val state = stateStore.snapshot()
        val endpoint = route(current, state, event.kind) ?: run {
            if (progress != null) stateStore.settle(progress)
            return
        }
        val message = event.toMessage(current.push(event.kind).template, zone(), language(), state.deliverySecret ?: createDeliverySecret())
        val delivery = if (timeout > Duration.ZERO) push(endpoint, current, message, timeout) else Delivery.Unreachable
        val applyProgress = progress ?: { it }
        when (delivery) {
            Delivery.Delivered -> {
                val latency = if (receivedElapsedMs != null) {
                    (elapsedMs() - receivedElapsedMs).coerceAtLeast(0L)
                } else {
                    (now() - message.receivedAtMs).coerceAtLeast(0L)
                }
                stateStore.settle { applyProgress(it).copy(stats = it.stats.withDelivered(message, latency)) }
            }
            is Delivery.Rejected -> stateStore.settle { applyProgress(it).copy(stats = it.stats.withFailed()) }
            Delivery.Unreachable -> {
                stateStore.settle { applyProgress(it).copy(pending = it.pending.enqueue(message)) }
                scheduleRetry()
            }
        }
    }

    suspend fun retryPending(): Boolean = retryLock.withLock {
        do {
            val snapshot = stateStore.snapshot().pending
            if (snapshot.isEmpty()) {
                cancelRetry()
                return@withLock true
            }
            var progressed = false
            for (queued in snapshot) {
                val current = settingsStore.snapshot()
                val delivery = when {
                    now() - queued.receivedAtMs > PENDING_EXPIRY.inWholeMilliseconds -> Delivery.Rejected("expired")
                    else -> push(route(current, stateStore.snapshot(), queued.kind) ?: continue, current, queued, PUSH_TIMEOUT)
                }
                when (delivery) {
                    Delivery.Unreachable -> return@withLock false
                    Delivery.Delivered -> {
                        progressed = true
                        stateStore.settle {
                            it.copy(pending = it.pending.removeById(queued.id), stats = it.stats.withDelivered(queued, firstAttemptLatencyMs = null))
                        }
                    }
                    is Delivery.Rejected -> {
                        progressed = true
                        stateStore.settle {
                            it.copy(pending = it.pending.removeById(queued.id), stats = it.stats.withFailed())
                        }
                    }
                }
            }
        } while (progressed)
        true
    }

    private suspend fun createDeliverySecret(): String {
        val created = newSecret()
        val stored = stateStore.settle { if (it.deliverySecret == null) it.copy(deliverySecret = created) else it }
        currentCoroutineContext().ensureActive()
        return stored.deliverySecret ?: created
    }

    private suspend fun push(endpoint: BarkEndpoint, settings: Settings, message: Message, timeout: Duration): Delivery =
        withTimeoutOrNull(timeout) { bark.push(endpoint, settings.push(message.kind), language(), message) } ?: Delivery.Unreachable

    companion object {
        val PUSH_TIMEOUT = 8.seconds
        val SMS_DIRECT_TIMEOUT = 5.seconds
        val PENDING_EXPIRY = 24.hours
        const val MAX_PENDING = 200
    }
}

private fun List<Message>.enqueue(message: Message): List<Message> =
    if (any { it.id == message.id }) this else (this + message).takeLast(Forwarder.MAX_PENDING)

private fun List<Message>.removeById(id: String): List<Message> = filterNot { it.id == id }

private fun ForwardEvent.deliveryId(secret: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
    val digest = mac.doFinal("$kind|${sim.key.value}|$from|$occurredAtMs|$messageBody".toByteArray())
    return HexFormat.of().formatHex(digest, 0, 16)
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
