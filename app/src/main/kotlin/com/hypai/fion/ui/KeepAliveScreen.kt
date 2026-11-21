package com.hypai.fion.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.hypai.fion.platform.PermissionGroup
import com.hypai.fion.platform.Readiness
import com.hypai.fion.platform.appDetailsIntent
import com.hypai.fion.platform.openBatteryExemption

private sealed interface Control {
    data class Fix(@param:StringRes val action: Int, val onFix: () -> Unit) : Control
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : Control
}

private class Requirement(@param:StringRes val title: Int, @param:StringRes val subtitle: Int, val ready: Boolean, val control: Control)

@Composable
fun KeepAliveScreen(
    readiness: Readiness,
    persistentNotification: Boolean,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.startActivity(context.appDetailsIntent()) }
    fun grant(group: PermissionGroup) = Control.Fix(R.string.keep_grant) { request(group) }

    val permissions = listOf(
        Requirement(R.string.keep_sms, R.string.keep_sms_summary, readiness.sms, grant(PermissionGroup.Sms)),
        Requirement(R.string.keep_call_log, R.string.keep_call_log_summary, readiness.callLog, grant(PermissionGroup.CallLog)),
        Requirement(R.string.keep_phone, R.string.keep_phone_summary, readiness.phone, grant(PermissionGroup.Phone)),
    )
    val battery = Requirement(
        R.string.keep_battery, R.string.keep_battery_summary, readiness.batteryUnrestricted,
        Control.Fix(R.string.keep_open_settings) { context.openBatteryExemption(context::startActivity) },
    )
    val notification = Requirement(
        R.string.keep_notification,
        when {
            !readiness.notifications -> R.string.keep_notification_blocked
            persistentNotification -> R.string.keep_notification_on
            else -> R.string.keep_notification_off
        },
        readiness.keepAliveNotice(persistentNotification),
        if (readiness.notifications) Control.Toggle(persistentNotification, onPersistentNotificationChange)
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
    ListRow(
        title = stringResource(item.title),
        subtitle = stringResource(item.subtitle),
        leading = {
            val background by animateColorAsState(if (item.ready) Palette.NavySurface else Palette.AmberPale, tween(300), label = "statusFill")
            val border by animateColorAsState(if (item.ready) Palette.NavySurface else Palette.AmberBorder, tween(300), label = "statusBorder")
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(background)
                    .border(1.5.dp, border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(item.ready, label = "statusIcon") { ready ->
                    Icon(
                        if (ready) Icons.Check else Icons.Alert,
                        contentDescription = stringResource(if (ready) R.string.keep_ready else R.string.keep_pending),
                        Modifier.size(18.dp),
                        tint = if (ready) Palette.NavyDeep else Palette.Amber,
                    )
                }
            }
        },
    ) {
        when (val control = item.control) {
            is Control.Toggle -> AppSwitch(control.checked, control.onChange)
            is Control.Fix -> AnimatedContent(
                item.ready,
                contentAlignment = Alignment.CenterEnd,
                transitionSpec = { Motion.Swap },
                label = "statusAction",
            ) { ready ->
                if (ready) {
                    Text(stringResource(R.string.keep_enabled), style = Type.Secondary, color = Palette.Navy)
                } else {
                    FilledTonalButton(
                        onClick = control.onFix,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Palette.NavySurface,
                            contentColor = Palette.NavyDeep,
                        ),
                    ) { Text(stringResource(control.action), style = Type.Label) }
                }
            }
        }
    }
}
