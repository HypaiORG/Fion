package com.hypai.fion.forward

import com.hypai.fion.store.SimTag
import kotlinx.serialization.Serializable

@Serializable
enum class EventKind { Sms, MissedCall }

enum class PermissionGroup { Sms, CallLog, Phone, Notifications }

enum class AppPermission(val group: PermissionGroup) {
    ReceiveSms(PermissionGroup.Sms),
    ReadCallLog(PermissionGroup.CallLog),
    ReadPhoneState(PermissionGroup.Phone),
    ReadPhoneNumbers(PermissionGroup.Phone),
    Notifications(PermissionGroup.Notifications),
}

val PermissionGroup.requirements: List<AppPermission>
    get() = AppPermission.entries.filter { it.group == this }

val EventKind.requirement: AppPermission
    get() = when (this) {
        EventKind.Sms -> AppPermission.ReceiveSms
        EventKind.MissedCall -> AppPermission.ReadCallLog
    }

val EventKind.permission: PermissionGroup
    get() = requirement.group

@Serializable
@JvmInline
value class SimKey private constructor(val value: String) {
    val subscriptionId: Int? get() = value.removePrefix("sub:").takeIf { it != value }?.toIntOrNull()

    companion object {
        fun subscription(id: Int) = SimKey("sub:$id")
        fun slot(index: Int) = SimKey("slot:$index")
        val Unknown = SimKey("unknown")
    }
}

data class SimIdentity(val key: SimKey, val slot: Int?, val carrier: String, val name: String, val number: String) {
    val tag: SimTag get() = SimTag(slot, name, carrier)

    companion object {
        fun resolve(
            active: Map<Int, SimIdentity>,
            subscriptionId: Int?,
            slotIndex: Int?,
            fallback: Map<Int, SimIdentity> = emptyMap(),
        ): SimIdentity {
            val id = subscriptionId?.takeIf { it >= 0 }
            val slot = slotIndex?.takeIf { it >= 0 }
            if (id != null) {
                active[id]?.let { return it }
                fallback[id]?.let { return it }
                return SimIdentity(SimKey.subscription(id), slot, "", "", "")
            }
            if (slot != null) {
                active.values.singleOrNull { it.slot == slot }?.let { return it }
                return SimIdentity(SimKey.slot(slot), slot, "", "", "")
            }
            active.values.singleOrNull()?.let { return it }
            return SimIdentity(SimKey.Unknown, null, "", "", "")
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
