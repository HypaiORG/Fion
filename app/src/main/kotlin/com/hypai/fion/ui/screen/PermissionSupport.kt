package com.hypai.fion.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.android.PermissionGroup
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.android.permission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.ui.design.ActionPill
import com.hypai.fion.ui.design.AppSheet
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.InfoNote
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.SheetButton
import com.hypai.fion.ui.design.Type

private const val DIALOG_SKIPPED_MS = 350L

enum class PermissionOutcome { Granted, Denied, Blocked }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun rememberPermissionRequest(onResult: (PermissionOutcome) -> Unit): (PermissionGroup) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var requestedAt by rememberSaveable { mutableLongStateOf(0L) }
    var hadRationale by rememberSaveable { mutableStateOf(false) }
    var lastGroup by rememberSaveable { mutableStateOf<PermissionGroup?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val group = lastGroup
        val outcome = when {
            result.isEmpty() -> PermissionOutcome.Denied
            result.values.all { it } -> PermissionOutcome.Granted
            else -> {
                val showRationaleNow = activity != null && group != null &&
                    group.permissions.any { activity.shouldShowRequestPermissionRationale(it) }
                val elapsed = SystemClock.elapsedRealtime() - requestedAt
                if (!hadRationale && !showRationaleNow && elapsed < DIALOG_SKIPPED_MS) PermissionOutcome.Blocked
                else PermissionOutcome.Denied
            }
        }
        onResult(outcome)
    }
    return { group ->
        lastGroup = group
        hadRationale = activity != null && group.permissions.any { activity.shouldShowRequestPermissionRationale(it) }
        requestedAt = SystemClock.elapsedRealtime()
        launcher.launch(arrayOf(*group.permissions))
    }
}

class EventKindLook(
    val icon: ImageVector,
    val background: Color,
    val tint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
)

val EventKind.look: EventKindLook
    get() = when (this) {
        EventKind.Sms -> EventKindLook(Icons.Sms, Palette.NavySurface, Palette.Navy, R.string.keep_sms, R.string.perm_sheet_sms_use)
        EventKind.MissedCall -> EventKindLook(Icons.MissedCall, Palette.CrimsonSurface, Palette.Crimson, R.string.keep_call_log, R.string.perm_sheet_call_use)
    }

@Composable
fun PermissionSheet(kinds: List<EventKind>, readiness: Readiness, onStart: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.openAppDetails() }
    val (title, subtitle) = when (kinds.singleOrNull()) {
        null -> R.string.perm_sheet_title to R.string.service_text
        EventKind.Sms -> R.string.perm_sheet_sms_title to R.string.forward_sms_on
        EventKind.MissedCall -> R.string.perm_sheet_call_title to R.string.forward_call_on
    }
    AppSheet(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        onDismiss = onDismiss,
        actions = {
            SheetButton(stringResource(R.string.onb_later), filled = false, onClick = onDismiss)
            SheetButton(stringResource(R.string.perm_sheet_start), filled = true, enabled = kinds.any(readiness::allows), onClick = onStart)
        },
    ) {
        kinds.forEach { kind -> PermissionLine(kind.look, readiness.allows(kind)) { request(kind.permission) } }
        InfoNote(stringResource(R.string.perm_sheet_note), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), Icons.Shield)
    }
}

@Composable
private fun PermissionLine(look: EventKindLook, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(look.icon, look.background, look.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(look.title), style = Type.Option, color = Palette.Ink)
            Text(stringResource(look.summary), style = Type.Secondary, color = Palette.Muted)
        }
        AnimatedContent(
            granted,
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = { Motion.Swap },
            label = "permissionState",
        ) { done ->
            if (done) {
                Row(
                    Modifier.height(36.dp).padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Check, contentDescription = null, Modifier.size(18.dp), tint = Palette.Navy)
                    Text(stringResource(R.string.keep_enabled), style = Type.Label, color = Palette.Navy)
                }
            } else {
                ActionPill(stringResource(R.string.keep_grant), onClick = onGrant)
            }
        }
    }
}
