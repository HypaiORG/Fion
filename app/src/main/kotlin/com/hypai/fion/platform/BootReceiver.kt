package com.hypai.fion.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypai.fion.graph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val graph = context.graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                if (graph.settings.data.first().autoStartOnBoot) graph.forwarder.activate()
            } finally {
                pending.finish()
            }
        }
    }
}
