package com.hypai.fion.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hypai.fion.graph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        val restore: suspend () -> Unit = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                { if (graph.settings.data.first().autoStartOnBoot) graph.forwarder.activate() }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> graph::reconcileKeepAlive
            else -> return
        }
        val pending = goAsync()
        graph.scope.launch {
            try {
                restore()
            } finally {
                pending.finish()
            }
        }
    }
}
