package com.hypai.fion.forward

import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.BarkEndpoint
import com.hypai.fion.bark.endpoint
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Message
import com.hypai.fion.store.Outbox
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
    private val outbox: Outbox,
    private val bark: BarkClient,
    private val bootCount: () -> Int,
    private val now: () -> Long,
    private val elapsedMs: () -> Long = now,
    private val zone: () -> ZoneId,
    private val language: () -> Language,
    private val scheduleRetry: () -> Unit,
    private val cancelRetry: () -> Unit = {},
    private val newSecret: () -> String,
    private val isNetworkAvailable: () -> Boolean = { true },
) {
    private val drainLock = Mutex()
    private val currentBootCount: Int by lazy(bootCount)

    suspend fun activate() {
        val boot = currentBootCount
        if (stateStore.snapshot().activatedBootCount == boot) return
        stateStore.settle { if (it.activatedBootCount == boot) it else it.copy(activatedBootCount = boot) }
    }

    fun isActive(settings: Settings, state: ForwardState): Boolean =
        settings.forwarding && state.activatedBootCount == currentBootCount

    fun enabledKinds(settings: Settings, state: ForwardState): Set<EventKind> =
        if (isActive(settings, state) && settings.bark.endpoint() != null) settings.channels else emptySet()

    fun accepts(settings: Settings, state: ForwardState, kind: EventKind): Boolean =
        kind in enabledKinds(settings, state)

    suspend fun usedVariables(kind: EventKind): Set<TemplateVariable> =
        settingsStore.snapshot().push(kind).template.usedVariables(kind, language())

    suspend fun forward(
        event: ForwardEvent,
        timeout: Duration = PUSH_TIMEOUT,
        receivedElapsedMs: Long? = null,
        progress: ((ForwardState) -> ForwardState)? = null,
    ) = forwardAll(listOf(event), timeout, receivedElapsedMs, progress)

    suspend fun forwardAll(
        events: List<ForwardEvent>,
        timeout: Duration = PUSH_TIMEOUT,
        receivedElapsedMs: Long? = null,
        progress: ((ForwardState) -> ForwardState)? = null,
    ) {
        val current = settingsStore.snapshot()
        val state = stateStore.snapshot()
        val allowed = enabledKinds(current, state)
        val routable = events.filter { it.kind in allowed }
        if (routable.isEmpty()) {
            if (progress != null) stateStore.settle(progress)
            return
        }
        val secret = state.deliverySecret ?: createDeliverySecret()
        val currentZone = zone()
        val currentLanguage = language()
        val messages = routable.map { it.toMessage(current.push(it.kind).template, currentZone, currentLanguage, secret) }
        outbox.enqueue(messages, MAX_PENDING)
        if (progress != null) stateStore.settle(progress)

        if (timeout <= Duration.ZERO || !isNetworkAvailable()) {
            scheduleRetry()
            return
        }
        if (!drain(timeout, messages.mapTo(HashSet(messages.size)) { it.id }, receivedElapsedMs)) {
            scheduleRetry()
        }
    }

    suspend fun retryPending(): Boolean = drain()

    suspend fun onNetworkAvailable() {
        if (outbox.hasAny() && !drain()) scheduleRetry()
    }

    private suspend fun drain(
        budget: Duration? = null,
        liveIds: Set<String> = emptySet(),
        receivedElapsedMs: Long? = null,
    ): Boolean = drainLock.withLock {
        val deadlineMs = budget?.let { elapsedMs() + it.inWholeMilliseconds }
        val expired = outbox.purgeExpired(now() - PENDING_EXPIRY.inWholeMilliseconds)
        if (expired > 0) stateStore.settle { it.copy(stats = it.stats.withFailed(expired)) }

        val attemptedIds = HashSet<String>()
        var consecutiveServerErrors = 0
        var aborted = false

        loop@ while (true) {
            val kinds = enabledKinds(settingsStore.snapshot(), stateStore.snapshot())
            if (kinds.isEmpty()) break
            val snapshot = outbox.list(kinds).filterNot { it.id in attemptedIds }
            if (snapshot.isEmpty()) break

            for (message in snapshot) {
                val current = settingsStore.snapshot()
                val state = stateStore.snapshot()
                val endpoint = current.bark.endpoint()
                    ?.takeIf { isActive(current, state) && current.forwards(message.kind) }
                    ?: continue

                val reqTimeoutMs = deadlineMs?.let { (it - elapsedMs()).coerceAtMost(PUSH_TIMEOUT.inWholeMilliseconds) }
                    ?: PUSH_TIMEOUT.inWholeMilliseconds
                if (reqTimeoutMs < MIN_REQUEST_TIMEOUT_MS || !isNetworkAvailable()) {
                    aborted = true
                    break@loop
                }

                attemptedIds += message.id
                when (val delivery = push(endpoint, current, message, reqTimeoutMs)) {
                    Delivery.Delivered -> {
                        consecutiveServerErrors = 0
                        val latency = if (message.id in liveIds) {
                            if (receivedElapsedMs != null) (elapsedMs() - receivedElapsedMs).coerceAtLeast(0L)
                            else (now() - message.receivedAtMs).coerceAtLeast(0L)
                        } else {
                            null
                        }
                        outbox.remove(message.id)
                        stateStore.settle { it.copy(stats = it.stats.withDelivered(message, latency)) }
                    }
                    is Delivery.Rejected -> {
                        consecutiveServerErrors = 0
                        outbox.remove(message.id)
                        stateStore.settle { it.copy(stats = it.stats.withFailed()) }
                    }
                    is Delivery.Unreachable -> {
                        if (delivery.serverError && ++consecutiveServerErrors < MAX_CONSECUTIVE_SERVER_ERRORS) {
                            continue
                        }
                        aborted = true
                        break@loop
                    }
                }
            }
        }

        if (!outbox.hasAny()) {
            cancelRetry()
            return@withLock true
        }
        !aborted && !outbox.hasAny(enabledKinds(settingsStore.snapshot(), stateStore.snapshot()))
    }

    private suspend fun createDeliverySecret(): String {
        val created = newSecret()
        val stored = stateStore.settle { if (it.deliverySecret == null) it.copy(deliverySecret = created) else it }
        currentCoroutineContext().ensureActive()
        return stored.deliverySecret ?: created
    }

    private suspend fun push(endpoint: BarkEndpoint, settings: Settings, message: Message, timeoutMs: Long): Delivery =
        withTimeoutOrNull(timeoutMs) {
            bark.push(endpoint, settings.push(message.kind), language(), message, timeoutMs)
        } ?: Delivery.Unreachable()

    companion object {
        val PUSH_TIMEOUT = 8.seconds
        val SMS_DIRECT_TIMEOUT = 5.seconds
        val PENDING_EXPIRY = 24.hours
        const val MAX_PENDING = 200
        private const val MAX_CONSECUTIVE_SERVER_ERRORS = 2
        private const val MIN_REQUEST_TIMEOUT_MS = 1_000L
    }
}

private fun ForwardEvent.deliveryId(secret: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
    val digest = mac.doFinal("$kind|${sim.key.value}|$from|$occurredAtMs|$messageBody".toByteArray())
    return HexFormat.of().formatHex(digest, 0, 16)
}

fun ForwardEvent.toMessage(choice: TemplateChoice, zone: ZoneId, language: Language, deliverySecret: String): Message {
    val template = choice.resolve(kind, language, sim)
    return Message(
        id = deliveryId(deliverySecret),
        kind = kind,
        simKey = sim.key,
        sim = sim.tag,
        title = renderParts(template.title, this, zone, language),
        body = renderParts(template.body, this, zone, language),
        receivedAtMs = receivedAtMs,
    )
}
