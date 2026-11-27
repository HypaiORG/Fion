package com.hypai.fion.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.manifestName
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.permission
import com.hypai.fion.forward.requirements
import com.hypai.fion.ui.currentLanguage
import com.hypai.fion.ui.design.ActionRow
import com.hypai.fion.ui.design.AppSheet
import com.hypai.fion.ui.design.BadgeStyle
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.InfoNote
import com.hypai.fion.ui.design.Palette

private const val DIALOG_SKIPPED_MS = 350L

val LocalRefreshReadiness = staticCompositionLocalOf<() -> Unit> { {} }

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
    val refreshReadiness = LocalRefreshReadiness.current
    val latestRefresh by rememberUpdatedState(refreshReadiness)
    val latestOnResult by rememberUpdatedState(onResult)
    var requestedAt by rememberSaveable { mutableLongStateOf(0L) }
    var hadRationale by rememberSaveable { mutableStateOf(false) }
    var lastRequested by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        latestRefresh()
        val outcome = when {
            result.isEmpty() -> PermissionOutcome.Denied
            result.values.all { it } -> PermissionOutcome.Granted
            else -> {
                val showRationaleNow = activity != null &&
                    lastRequested.any { activity.shouldShowRequestPermissionRationale(it) }
                val elapsed = SystemClock.elapsedRealtime() - requestedAt
                if (!hadRationale && !showRationaleNow && elapsed < DIALOG_SKIPPED_MS) PermissionOutcome.Blocked
                else PermissionOutcome.Denied
            }
        }
        latestOnResult(outcome)
    }
    return { group ->
        val names = group.requirements.map(AppPermission::manifestName)
        lastRequested = names
        hadRationale = activity != null && names.any { activity.shouldShowRequestPermissionRationale(it) }
        requestedAt = SystemClock.elapsedRealtime()
        launcher.launch(names.toTypedArray())
    }
}

class PermissionLook(
    val badge: BadgeStyle,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
)

val PermissionGroup.look: PermissionLook
    get() = when (this) {
        PermissionGroup.Sms -> PermissionLook(BadgeStyle(Icons.Sms, Palette.NavySurface, Palette.Navy), R.string.keep_sms, R.string.perm_sheet_sms_use)
        PermissionGroup.CallLog -> PermissionLook(BadgeStyle(Icons.MissedCall, Palette.CrimsonSurface, Palette.Crimson), R.string.keep_call_log, R.string.perm_sheet_call_use)
        PermissionGroup.Phone -> PermissionLook(BadgeStyle(Icons.Sim, Palette.AmberSurface, Palette.Copper), R.string.keep_phone, R.string.keep_phone_summary)
        PermissionGroup.Notifications -> PermissionLook(BadgeStyle(Icons.Bell, Palette.NavySurface, Palette.Navy), R.string.keep_notification, R.string.keep_notification_on)
    }

val EventKind.look: PermissionLook get() = permission.look

sealed interface PermissionPrompt {
    data class Forwarding(val kinds: List<EventKind>, val onStart: () -> Unit) : PermissionPrompt
    data class Variable(val variable: TemplateVariable) : PermissionPrompt
}

private val SheetRowPadding = PaddingValues(start = 20.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)

@Composable
fun PermissionSheet(prompt: PermissionPrompt, readiness: Readiness, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.openAppDetails() }
    val items: List<PermissionGroup>
    val title: String
    val subtitle: String
    val onStart: (() -> Unit)?
    when (prompt) {
        is PermissionPrompt.Forwarding -> {
            items = prompt.kinds.map(EventKind::permission)
            val (titleRes, subtitleRes) = when (prompt.kinds.singleOrNull()) {
                null -> R.string.perm_sheet_title to R.string.service_text
                EventKind.Sms -> R.string.perm_sheet_sms_title to R.string.forward_sms_on
                EventKind.MissedCall -> R.string.perm_sheet_call_title to R.string.forward_call_on
            }
            title = stringResource(titleRes)
            subtitle = stringResource(subtitleRes)
            onStart = prompt.onStart
        }
        is PermissionPrompt.Variable -> {
            items = listOf(prompt.variable.permission)
            title = stringResource(R.string.perm_sheet_var_title, prompt.variable.displayName(currentLanguage()))
            subtitle = stringResource(R.string.perm_sheet_var_subtitle)
            onStart = null
        }
    }

    val anyGranted = items.any(readiness::allows)
    val allGranted = items.all(readiness::allows)
    val latestStart by rememberUpdatedState(onStart)
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(anyGranted) { if (anyGranted) latestStart?.invoke() }
    LaunchedEffect(allGranted) { if (allGranted) latestDismiss() }

    AppSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        items.forEach { group ->
            val look = group.look
            ActionRow(
                badge = look.badge,
                title = stringResource(look.title),
                subtitle = stringResource(look.summary),
                action = stringResource(R.string.grant),
                onAction = { request(group) },
                contentPadding = SheetRowPadding,
                ready = readiness.allows(group),
            )
        }
        InfoNote(stringResource(R.string.perm_sheet_note), Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), Icons.Shield)
    }
}
