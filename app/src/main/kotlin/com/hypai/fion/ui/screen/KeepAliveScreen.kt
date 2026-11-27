package com.hypai.fion.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.android.openBatteryExemption
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.ui.design.ActionRow
import com.hypai.fion.ui.design.BadgeStyle
import com.hypai.fion.ui.design.GlassCard
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.ScreenScaffold
import com.hypai.fion.ui.design.SectionCard
import com.hypai.fion.ui.design.SwitchRow
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type

private sealed interface Control {
    data class Fix(@param:StringRes val action: Int, val onFix: () -> Unit) : Control
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : Control
}

private data class Requirement(
    val badge: BadgeStyle,
    @param:StringRes val title: Int,
    @param:StringRes val subtitle: Int,
    val ready: Boolean,
    val control: Control,
)

private val BatteryBadge = BadgeStyle(Icons.Battery, Palette.GreenSurface, Palette.Green)

@Composable
fun KeepAliveScreen(
    readiness: Readiness,
    persistentNotification: Boolean,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.openAppDetails() }
    fun permissionRow(group: PermissionGroup, @StringRes summary: Int) = Requirement(
        badge = group.look.badge,
        title = group.look.title,
        subtitle = summary,
        ready = readiness.allows(group),
        control = Control.Fix(R.string.keep_grant) { request(group) },
    )

    val permissions = listOf(
        permissionRow(PermissionGroup.Sms, R.string.keep_sms_summary),
        permissionRow(PermissionGroup.CallLog, R.string.keep_call_log_summary),
        permissionRow(PermissionGroup.Phone, R.string.keep_phone_summary),
    )
    val battery = Requirement(
        badge = BatteryBadge,
        title = R.string.keep_battery,
        subtitle = R.string.keep_battery_summary,
        ready = readiness.batteryUnrestricted,
        control = Control.Fix(R.string.keep_open_settings) { context.openBatteryExemption() },
    )
    val notification = Requirement(
        badge = PermissionGroup.Notifications.look.badge,
        title = R.string.keep_notification,
        subtitle = when {
            !readiness.notifications -> R.string.keep_notification_blocked
            persistentNotification -> R.string.keep_notification_on
            else -> R.string.keep_notification_off
        },
        ready = readiness.keepAliveNotice(persistentNotification),
        control = if (readiness.notifications) Control.Toggle(persistentNotification, onPersistentNotificationChange)
        else Control.Fix(R.string.keep_allow) { request(PermissionGroup.Notifications) },
    )
    val all = permissions + battery + notification
    val done = all.count { it.ready }

    ScreenScaffold(title = stringResource(R.string.settings_keep_alive), onBack = onBack) {
        GlassCard(spacing = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.keep_ready), style = Type.Secondary, color = Palette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnimatedContent(
                            targetState = done,
                            modifier = Modifier.alignByBaseline(),
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInVertically(tween(300, easing = Motion.EmphasizedDecelerate)) { direction * it / 2 } + fadeIn(tween(200))) togetherWith
                                    (slideOutVertically(tween(200, easing = Motion.EmphasizedAccelerate)) { -direction * it / 2 } + fadeOut(tween(120)))
                            },
                            label = "readyCount",
                        ) { count -> Text(count.toString(), style = Type.Display, color = Palette.Ink) }
                        Text(
                            "/ ${all.size}",
                            Modifier.alignByBaseline(),
                            style = Type.Body,
                            color = Palette.Muted,
                        )
                    }
                }
                val complete = done == all.size
                val pill by animateColorAsState(if (complete) Palette.NavySurface else Palette.AmberSurface, tween(300), label = "pill")
                val pillText by animateColorAsState(if (complete) Palette.NavyDeep else Palette.Amber, tween(300), label = "pillText")
                Text(
                    if (complete) stringResource(R.string.keep_all_ready) else stringResource(R.string.keep_remaining, all.size - done),
                    Modifier
                        .clip(CircleShape)
                        .background(pill)
                        .animateContentSize(tween(200, easing = Motion.Standard))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    style = Type.Secondary,
                    color = pillText,
                )
            }
            val progress = stringResource(R.string.keep_progress, done, all.size)
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(all.size) { index ->
                    val color by animateColorAsState(
                        if (index < done) Palette.Navy else Palette.Stone,
                        tween(300, delayMillis = index * 40),
                        label = "readySegment",
                    )
                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(color))
                }
            }
            Text(stringResource(R.string.keep_intro), style = Type.Secondary, color = Palette.Muted)
        }

        SectionCard(stringResource(R.string.keep_permissions)) { permissions.forEach { RequirementRow(it) } }

        SectionCard(stringResource(R.string.keep_settings)) {
            RequirementRow(battery)
            RequirementRow(notification)
        }
    }
}

@Composable
private fun RequirementRow(item: Requirement) {
    when (val control = item.control) {
        is Control.Toggle -> SwitchRow(
            title = stringResource(item.title),
            subtitle = stringResource(item.subtitle),
            checked = control.checked,
            onCheckedChange = control.onChange,
            leading = { IconBadge(item.badge, size = 40.dp, iconSize = 19.dp, alert = !item.ready) },
        )
        is Control.Fix -> ActionRow(
            badge = item.badge,
            title = stringResource(item.title),
            subtitle = stringResource(item.subtitle),
            action = stringResource(control.action),
            onAction = control.onFix,
            ready = item.ready,
        )
    }
}
