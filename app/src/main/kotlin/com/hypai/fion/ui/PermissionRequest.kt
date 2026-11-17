package com.hypai.fion.ui

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hypai.fion.platform.PermissionGroup
import com.hypai.fion.platform.appDetailsIntent

private const val DIALOG_SKIPPED_MS = 350L

@Composable
fun rememberPermissionRequest(onAnswered: () -> Unit = {}): (PermissionGroup) -> Unit {
    val context = LocalContext.current
    var requestedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val dialogSkipped = SystemClock.elapsedRealtime() - requestedAt < DIALOG_SKIPPED_MS
        if (result.values.any { !it } && dialogSkipped) context.startActivity(context.appDetailsIntent()) else onAnswered()
    }
    return { group ->
        requestedAt = SystemClock.elapsedRealtime()
        launcher.launch(arrayOf(*group.permissions))
    }
}
