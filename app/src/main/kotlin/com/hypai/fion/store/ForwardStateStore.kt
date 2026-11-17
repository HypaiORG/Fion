package com.hypai.fion.store

import androidx.datastore.core.DataStore
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.SimKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val kind: EventKind,
    val simKey: SimKey,
    val sim: SimTag,
    val title: String,
    val body: String,
    val receivedAtMs: Long,
)

@Serializable
data class SimTag(val slot: Int? = null, val name: String = "", val carrier: String = "") {
    val title: String get() = listOf(name, carrier).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    fun over(previous: SimTag?): SimTag = when {
        previous == null -> this
        title.isEmpty() -> previous.copy(slot = slot ?: previous.slot)
        else -> copy(slot = slot ?: previous.slot)
    }
}

@Serializable
data class SimTally(val sim: SimTag, val count: Long)

@Serializable
data class Stats(
    val sms: Long = 0,
    val missedCalls: Long = 0,
    val failed: Long = 0,
    val latencyTotalMs: Long = 0,
    val latencySamples: Long = 0,
    val sims: Map<SimKey, SimTally> = emptyMap(),
) {
    val delivered: Long get() = sms + missedCalls

    val successRate: Double?
        get() = (delivered + failed).takeIf { it > 0 }?.let { delivered.toDouble() / it }

    val averageLatencyMs: Long?
        get() = latencySamples.takeIf { it > 0 }?.let { latencyTotalMs / it }

    fun withDelivered(message: Message, firstAttemptLatencyMs: Long?): Stats {
        val previous = sims[message.simKey]
        return copy(
            sms = sms + if (message.kind == EventKind.Sms) 1 else 0,
            missedCalls = missedCalls + if (message.kind == EventKind.MissedCall) 1 else 0,
            latencyTotalMs = latencyTotalMs + (firstAttemptLatencyMs ?: 0),
            latencySamples = latencySamples + if (firstAttemptLatencyMs != null) 1 else 0,
            sims = sims + (message.simKey to SimTally(message.sim.over(previous?.sim), (previous?.count ?: 0) + 1)),
        )
    }

    fun withFailed(): Stats = copy(failed = failed + 1)
}

@Serializable
data class ForwardState(
    val onboardingDone: Boolean = false,
    val activatedBootCount: Int? = null,
    val callLogWatermark: Long? = null,
    val pending: List<Message> = emptyList(),
    val stats: Stats = Stats(),
    val deliverySecret: String? = null,
)

class ForwardStateStore(private val backing: DataStore<ForwardState>) {
    val data: Flow<ForwardState> get() = backing.data

    suspend fun snapshot(): ForwardState = backing.data.first()

    suspend fun settle(transform: (ForwardState) -> ForwardState): ForwardState =
        withContext(NonCancellable) { backing.updateData { transform(it) } }

    suspend fun completeOnboarding() {
        settle { if (it.onboardingDone) it else it.copy(onboardingDone = true) }
    }

    suspend fun resetStats() {
        settle { if (it.stats == Stats()) it else it.copy(stats = Stats()) }
    }
}
