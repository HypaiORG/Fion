package com.hypai.fion.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Telephony.Sms.Intents
import android.telephony.SubscriptionManager
import com.hypai.fion.appContainer
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.ForwardEvent
import com.hypai.fion.forward.Forwarder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

private const val BROADCAST_BUDGET_MS = 6_000L

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intents.SMS_RECEIVED_ACTION) return
        val receivedAt = System.currentTimeMillis()
        val receivedElapsed = SystemClock.elapsedRealtime()
        val app = context.appContainer
        val pending = goAsync()
        app.scope.launch {
            try {
                val settings = app.settingsStore.snapshot()
                if (settings.autoStartOnBoot) app.forwarder.activate()
                if (!app.forwarder.accepts(settings, app.stateStore.snapshot(), EventKind.Sms)) return@launch
                val sim = app.sims.sim(
                    subscriptionId = intent.intExtraOrNull(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX),
                    slotIndex = intent.intExtraOrNull(SubscriptionManager.EXTRA_SLOT_INDEX),
                )
                val deadline = receivedElapsed + BROADCAST_BUDGET_MS
                Intents.getMessagesFromIntent(intent).orEmpty()
                    .groupBy { it.displayOriginatingAddress.orEmpty() }
                    .forEach { (from, parts) ->
                        val remaining = (deadline - SystemClock.elapsedRealtime()).milliseconds
                        app.forwarder.forward(
                            event = ForwardEvent.Sms(
                                from = from,
                                sim = sim,
                                occurredAtMs = parts.first().timestampMillis,
                                receivedAtMs = receivedAt,
                                content = parts.joinToString("") { it.displayMessageBody.orEmpty() },
                            ),
                            timeout = if (remaining >= 1.seconds) minOf(Forwarder.SMS_DIRECT_TIMEOUT, remaining) else Duration.ZERO,
                            receivedElapsedMs = receivedElapsed,
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }
}

private fun Intent.intExtraOrNull(name: String): Int? = if (hasExtra(name)) getIntExtra(name, -1) else null
