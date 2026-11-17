package com.hypai.fion.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms.Intents
import android.telephony.SubscriptionManager
import com.hypai.fion.core.ForwardEvent
import com.hypai.fion.graph
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION) return
        val receivedAt = System.currentTimeMillis()
        val graph = context.graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                val sim = graph.telephony.sim(
                    subscriptionId = intent.intExtraOrNull(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX),
                    slotIndex = intent.intExtraOrNull(SubscriptionManager.EXTRA_SLOT_INDEX),
                )
                Intents.getMessagesFromIntent(intent).orEmpty()
                    .groupBy { it.displayOriginatingAddress.orEmpty() }
                    .forEach { (from, parts) ->
                        graph.forwarder.forward(
                            ForwardEvent.Sms(
                                from = from,
                                sim = sim,
                                occurredAtMs = parts.first().timestampMillis,
                                receivedAtMs = receivedAt,
                                content = parts.joinToString("") { it.displayMessageBody.orEmpty() },
                            ),
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }
}

private fun Intent.intExtraOrNull(name: String): Int? = if (hasExtra(name)) getIntExtra(name, -1) else null
