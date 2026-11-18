package com.hypai.fion.ui

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.core.EventKind
import com.hypai.fion.platform.PermissionGroup
import com.hypai.fion.platform.Readiness
import com.hypai.fion.platform.appDetailsIntent
import com.hypai.fion.platform.permission

private const val DIALOG_SKIPPED_MS = 350L

enum class PermissionOutcome { Granted, Denied, Blocked }

@Composable
fun rememberPermissionRequest(onResult: (PermissionOutcome) -> Unit): (PermissionGroup) -> Unit {
    var requestedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult(
            when {
                result.values.all { it } -> PermissionOutcome.Granted
                SystemClock.elapsedRealtime() - requestedAt < DIALOG_SKIPPED_MS -> PermissionOutcome.Blocked
                else -> PermissionOutcome.Denied
            },
        )
    }
    return { group ->
        requestedAt = SystemClock.elapsedRealtime()
        launcher.launch(arrayOf(*group.permissions))
    }
}

private class PermissionLook(
    val icon: ImageVector,
    val background: Color,
    val tint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
)

private val EventKind.permissionLook: PermissionLook
    get() = when (this) {
        EventKind.Sms -> PermissionLook(Icons.Sms, Palette.NavySurface, Palette.Navy, R.string.keep_sms, R.string.perm_sheet_sms_use)
        EventKind.MissedCall -> PermissionLook(Icons.MissedCall, Palette.CrimsonSurface, Palette.Crimson, R.string.keep_call_log, R.string.perm_sheet_call_use)
    }

@Composable
fun PermissionSheet(kinds: List<EventKind>, readiness: Readiness, onStart: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.startActivity(context.appDetailsIntent()) }
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
        kinds.forEach { kind -> PermissionLine(kind.permissionLook, readiness.allows(kind)) { request(kind.permission) } }
        InfoNote(stringResource(R.string.perm_sheet_note), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), Icons.Shield)
    }
}

@Composable
private fun PermissionLine(look: PermissionLook, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(look.icon, look.background, look.tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(look.title), style = Type.Option, color = Palette.Ink)
            Text(stringResource(look.summary), style = Type.Secondary, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                FilledTonalButton(
                    onClick = onGrant,
                    Modifier.height(36.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Palette.Navy.copy(alpha = 0.12f),
                        contentColor = Palette.NavyDeep,
                    ),
                ) { Text(stringResource(R.string.keep_grant), style = Type.Label, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
