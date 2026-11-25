package com.hypai.fion.android

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypai.fion.appContainer
import kotlinx.coroutines.launch

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED
        ) return
        val app = context.appContainer
        val pending = goAsync()
        app.scope.launch {
            try {
                when (action) {
                    Intent.ACTION_BOOT_COMPLETED -> app.coordinator.onBootCompleted()
                    Intent.ACTION_MY_PACKAGE_REPLACED -> app.coordinator.onPackageReplaced()
                    else -> app.coordinator.reconcile()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
