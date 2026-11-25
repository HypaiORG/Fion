package com.hypai.fion.forward

import com.hypai.fion.store.SimTag
import kotlinx.serialization.Serializable

@Serializable
enum class EventKind { Sms, MissedCall }

@Serializable
@JvmInline
value class SimKey private constructor(val value: String) {
    companion object {
        fun subscription(id: Int) = SimKey("sub:$id")
        fun slot(index: Int) = SimKey("slot:$index")
        val Unknown = SimKey("unknown")
    }
}

data class SimIdentity(val key: SimKey, val slot: Int?, val carrier: String, val name: String, val number: String) {
    val tag: SimTag get() = SimTag(slot, name, carrier)

    companion object {
        fun resolve(active: Map<Int, SimIdentity>, subscriptionId: Int?, slotIndex: Int?): SimIdentity {
            val id = subscriptionId?.takeIf { it >= 0 }
            val slot = slotIndex?.takeIf { it >= 0 }
            id?.let(active::get)?.let { return it }
            val key = id?.let(SimKey::subscription) ?: slot?.let(SimKey::slot) ?: SimKey.Unknown
            return SimIdentity(key, slot, "", "", "")
        }
    }
}

sealed interface ForwardEvent {
    val from: String
    val sim: SimIdentity
    val occurredAtMs: Long
    val receivedAtMs: Long

    data class Sms(
        override val from: String,
        override val sim: SimIdentity,
        override val occurredAtMs: Long,
        override val receivedAtMs: Long,
        val content: String,
    ) : ForwardEvent

    data class MissedCall(
        override val from: String,
        override val sim: SimIdentity,
        override val occurredAtMs: Long,
        override val receivedAtMs: Long,
        val countToday: Int,
    ) : ForwardEvent
}

val ForwardEvent.kind: EventKind
    get() = when (this) {
        is ForwardEvent.Sms -> EventKind.Sms
        is ForwardEvent.MissedCall -> EventKind.MissedCall
    }

val ForwardEvent.messageBody: String
    get() = when (this) {
        is ForwardEvent.Sms -> content
        is ForwardEvent.MissedCall -> ""
    }

sealed interface Delivery {
    data object Delivered : Delivery
    data class Rejected(val reason: String) : Delivery
    data class Unreachable(val serverError: Boolean = false) : Delivery
}
