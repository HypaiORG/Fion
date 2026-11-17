package com.hypai.fion.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TemplateChoice {
    @Serializable
    @SerialName("default")
    data object Default : TemplateChoice

    @Serializable
    @SerialName("custom")
    data class Custom(val template: Template) : TemplateChoice
}

@Serializable
sealed interface GroupChoice {
    @Serializable
    @SerialName("default")
    data object Default : GroupChoice

    @Serializable
    @SerialName("custom")
    data class Custom(val name: String) : GroupChoice
}

@Serializable
data class PushProfile(
    val sound: String,
    val template: TemplateChoice = TemplateChoice.Default,
    val group: GroupChoice = GroupChoice.Default,
    val level: BarkLevel = BarkLevel.Active,
    val archive: Boolean = true,
)

val EventKind.defaultProfile: PushProfile
    get() = when (this) {
        EventKind.Sms -> PushProfile(sound = "bell")
        EventKind.MissedCall -> PushProfile(sound = "telegraph")
    }

@Serializable
data class Settings(
    val forwardingEnabled: Boolean = true,
    val channels: Set<EventKind> = EventKind.entries.toSet(),
    val autoStartOnBoot: Boolean = true,
    val persistentNotification: Boolean = true,
    val bark: BarkSettings = BarkSettings(),
    val smsPush: PushProfile = EventKind.Sms.defaultProfile,
    val callPush: PushProfile = EventKind.MissedCall.defaultProfile,
) {
    val forwarding: Boolean get() = forwardingEnabled && channels.isNotEmpty()

    fun forwards(kind: EventKind): Boolean = kind in channels

    fun withChannel(kind: EventKind, on: Boolean): Settings = copy(
        forwardingEnabled = forwardingEnabled || on,
        channels = if (on) channels + kind else channels - kind,
    )

    fun withForwarding(on: Boolean): Settings =
        if (on) copy(forwardingEnabled = true, channels = channels.ifEmpty { EventKind.entries.toSet() })
        else copy(forwardingEnabled = false)

    fun push(kind: EventKind): PushProfile = when (kind) {
        EventKind.Sms -> smsPush
        EventKind.MissedCall -> callPush
    }

    fun withPush(kind: EventKind, profile: PushProfile): Settings = when (kind) {
        EventKind.Sms -> copy(smsPush = profile)
        EventKind.MissedCall -> copy(callPush = profile)
    }
}
