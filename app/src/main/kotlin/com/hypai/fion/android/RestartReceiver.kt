package com.hypai.fion.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypai.fion.appContainer
import kotlinx.coroutines.launch

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.appContainer
        val pending = goAsync()
        app.scope.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    app.coordinator.onBootCompleted()
                } else {
                    app.coordinator.onPackageReplaced()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
