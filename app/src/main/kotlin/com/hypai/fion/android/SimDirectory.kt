package com.hypai.fion.android

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.hypai.fion.forward.SimIdentity
import com.hypai.fion.forward.SimKey

class SimDirectory(private val context: Context) {
    private val manager: SubscriptionManager get() = context.getSystemService(SubscriptionManager::class.java)

    private fun subscriptions(): List<SubscriptionInfo> = try {
        manager.activeSubscriptionInfoList.orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun phoneNumber(subscriptionId: Int): String = try {
        manager.getPhoneNumber(subscriptionId)
    } catch (_: SecurityException) {
        ""
    } catch (_: IllegalStateException) {
        ""
    }

    // Samsung One UI keeps the name set in SIM manager in Settings.Global "select_name_<slot + 1>"
    // and leaves SubscriptionInfo.displayName at the carrier default (verified on SM-S931U, Android 15).
    private fun oneUiSimName(slotIndex: Int): String =
        if (slotIndex < 0) "" else Settings.Global.getString(context.contentResolver, "select_name_${slotIndex + 1}").orEmpty()

    private fun SubscriptionInfo.identity(): SimIdentity {
        val carrier = carrierName?.toString().orEmpty()
        val name = oneUiSimName(simSlotIndex).ifBlank { displayName?.toString().orEmpty() }
        return SimIdentity(SimKey.subscription(subscriptionId), simSlotIndex.takeIf { it >= 0 }, carrier, name, phoneNumber(subscriptionId))
    }

    fun activeSims(): Map<Int, SimIdentity> = subscriptions().associate { it.subscriptionId to it.identity() }

    fun sim(subscriptionId: Int?, slotIndex: Int?): SimIdentity = SimIdentity.resolve(activeSims(), subscriptionId, slotIndex)

    fun subscriptionId(accountComponent: String?, accountId: String?): Int? {
        val component = accountComponent?.let(ComponentName::unflattenFromString) ?: return null
        val account = PhoneAccountHandle(component, accountId ?: return null)
        return try {
            context.getSystemService(TelephonyManager::class.java).getSubscriptionId(account)
                .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }
}
