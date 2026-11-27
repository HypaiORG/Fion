package com.hypai.fion.android

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypai.fion.RuntimeCoordinator
import com.hypai.fion.appContainer
import kotlinx.coroutines.launch

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handle: suspend (RuntimeCoordinator) -> Unit = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> { coordinator -> coordinator.onBootCompleted() }
            Intent.ACTION_MY_PACKAGE_REPLACED -> { coordinator -> coordinator.onPackageReplaced() }
            NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED -> { coordinator -> coordinator.reconcile() }
            else -> return
        }
        val app = context.appContainer
        val pending = goAsync()
        app.scope.launch {
            try {
                handle(app.coordinator)
            } finally {
                pending.finish()
            }
        }
    }
}
