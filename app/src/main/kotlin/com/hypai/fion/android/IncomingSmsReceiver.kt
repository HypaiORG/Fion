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
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.usedVariables
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

private const val BROADCAST_BUDGET_MS = 6_000L

private val SUBSCRIPTION_EXTRA_KEYS = arrayOf(
    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
    "subscription",
    "sub_id",
)

private val SLOT_EXTRA_KEYS = arrayOf(
    SubscriptionManager.EXTRA_SLOT_INDEX,
    "slot",
    "phone",
    "simId",
)

internal fun firstValidIndex(keys: Array<String>, read: (String) -> Int?): Int? =
    keys.firstNotNullOfOrNull { key -> read(key)?.takeIf { it >= 0 } }

internal fun resolveSmsTimestamp(pduTimestampMs: Long, receivedAtMs: Long): Long =
    pduTimestampMs.takeIf { it > 0L } ?: receivedAtMs

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
                val includeNumber = TemplateVariable.SimNumber in settings.push(EventKind.Sms).template.usedVariables(EventKind.Sms)
                val sim = app.sims.sim(
                    subscriptionId = firstValidIndex(SUBSCRIPTION_EXTRA_KEYS, intent::intExtraOrNull),
                    slotIndex = firstValidIndex(SLOT_EXTRA_KEYS, intent::intExtraOrNull),
                    includeNumber = includeNumber,
                )
                val events = Intents.getMessagesFromIntent(intent).orEmpty()
                    .groupBy { it.displayOriginatingAddress.orEmpty() }
                    .map { (from, parts) ->
                        ForwardEvent.Sms(
                            from = from,
                            sim = sim,
                            occurredAtMs = resolveSmsTimestamp(parts.first().timestampMillis, receivedAt),
                            receivedAtMs = receivedAt,
                            content = parts.joinToString("") { it.displayMessageBody.orEmpty() },
                        )
                    }
                if (events.isEmpty()) return@launch
                val remaining = (receivedElapsed + BROADCAST_BUDGET_MS - SystemClock.elapsedRealtime()).milliseconds
                app.forwarder.forwardAll(
                    events = events,
                    timeout = if (remaining >= 1.seconds) minOf(Forwarder.SMS_DIRECT_TIMEOUT, remaining) else Duration.ZERO,
                    receivedElapsedMs = receivedElapsed,
                )
            } finally {
                pending.finish()
            }
        }
    }
}

private fun Intent.intExtraOrNull(name: String): Int? = if (hasExtra(name)) getIntExtra(name, -1) else null
