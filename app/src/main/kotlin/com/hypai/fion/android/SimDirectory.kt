package com.hypai.fion.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.hypai.fion.forward.SimIdentity
import com.hypai.fion.forward.SimKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PHONE_NUMBER_SOURCES = buildList {
    add(SubscriptionManager.PHONE_NUMBER_SOURCE_IMS)
    add(SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER)
    add(SubscriptionManager.PHONE_NUMBER_SOURCE_UICC)
    if (Build.VERSION.SDK_INT >= 37) add(SubscriptionManager.PHONE_NUMBER_SOURCE_TS43)
}

internal fun resolveCallSubscriptionId(mappedSubId: Int?, accountId: String?, knownIds: Set<Int>): Int? {
    mappedSubId?.takeIf { it >= 0 }?.let { return it }
    return accountId?.trim()?.toIntOrNull()?.takeIf { it >= 0 && (knownIds.isEmpty() || it in knownIds) }
}

internal fun attachPhoneNumber(
    sim: SimIdentity,
    fallbackNumber: String?,
    readNumber: (Int) -> String,
): SimIdentity {
    val number = sim.number
        .ifBlank { sim.key.subscriptionId?.let(readNumber).orEmpty() }
        .ifBlank { fallbackNumber?.trim().orEmpty() }
    return if (number == sim.number) sim else sim.copy(number = number)
}

class SimDirectory(private val context: Context) {
    private val manager: SubscriptionManager get() = context.getSystemService(SubscriptionManager::class.java)
    private val telephony: TelephonyManager get() = context.getSystemService(TelephonyManager::class.java)

    private fun activeSubscriptions(): List<SubscriptionInfo> {
        if (!context.isGranted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            manager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun allSubscriptions(): List<SubscriptionInfo> {
        if (!context.isGranted(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return try {
            manager.allSubscriptionInfoList
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun activeSubscription(subscriptionId: Int): SubscriptionInfo? {
        if (!context.isGranted(Manifest.permission.READ_PHONE_STATE)) return null
        return try {
            manager.getActiveSubscriptionInfo(subscriptionId)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun phoneNumber(subscriptionId: Int): String {
        if (!context.isGranted(Manifest.permission.READ_PHONE_NUMBERS)) return ""
        try {
            manager.getPhoneNumber(subscriptionId).takeIf(String::isNotBlank)?.let { return it }
            for (source in PHONE_NUMBER_SOURCES) {
                manager.getPhoneNumber(subscriptionId, source).takeIf(String::isNotBlank)?.let { return it }
            }
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        } catch (_: IllegalArgumentException) {
        }
        return ""
    }

    private fun simOperatorName(subscriptionId: Int): String = try {
        telephony.createForSubscriptionId(subscriptionId).simOperatorName.orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    // Samsung One UI keeps the name set in SIM manager in Settings.Global "select_name_<slot + 1>"
    // and leaves SubscriptionInfo.displayName at the carrier default (verified on SM-S931U, Android 15).
    private fun oneUiSimName(slotIndex: Int): String =
        if (slotIndex < 0) "" else Settings.Global.getString(context.contentResolver, "select_name_${slotIndex + 1}").orEmpty()

    private fun SubscriptionInfo.toIdentity(): SimIdentity {
        val carrier = carrierName?.toString().orEmpty().ifBlank { simOperatorName(subscriptionId) }
        val name = oneUiSimName(simSlotIndex).ifBlank { displayName?.toString().orEmpty() }
        return SimIdentity(SimKey.subscription(subscriptionId), simSlotIndex.takeIf { it >= 0 }, carrier, name, "")
    }

    suspend fun sim(subscriptionId: Int?, slotIndex: Int?, includeNumber: Boolean = true): SimIdentity =
        withContext(Dispatchers.IO) {
            val id = subscriptionId?.takeIf { it >= 0 }
            val slot = slotIndex?.takeIf { it >= 0 }
            val base = if (id != null) {
                val info = activeSubscription(id) ?: allSubscriptions().firstOrNull { it.subscriptionId == id }
                info?.toIdentity() ?: SimIdentity(SimKey.subscription(id), slot, "", "", "")
            } else {
                val active = activeSubscriptions().associate { it.subscriptionId to it.toIdentity() }
                SimIdentity.resolve(active, subscriptionId = null, slotIndex = slot)
            }
            if (includeNumber) attachPhoneNumber(base, fallbackNumber = null, ::phoneNumber) else base
        }

    fun openCallResolver(includeNumber: Boolean): (accountComponent: String?, accountId: String?, viaNumber: String?) -> SimIdentity {
        val active = activeSubscriptions().associate { it.subscriptionId to it.toIdentity() }
        val all by lazy(LazyThreadSafetyMode.NONE) {
            allSubscriptions().associate { it.subscriptionId to it.toIdentity() }
        }
        val numberCache = HashMap<Int, String>()
        val cachedPhoneNumber: (Int) -> String = { id -> numberCache.getOrPut(id) { phoneNumber(id) } }
        return { accountComponent, accountId, viaNumber ->
            val mapped = subscriptionId(accountComponent, accountId)
            val subId = resolveCallSubscriptionId(mapped, accountId, active.keys)
                ?: accountId?.trim()?.toIntOrNull()?.takeIf { it >= 0 && it in all }
            val fallback = if (subId != null && subId !in active) all else emptyMap()
            val base = SimIdentity.resolve(active, subId, slotIndex = null, fallback = fallback)
            if (includeNumber) attachPhoneNumber(base, viaNumber, cachedPhoneNumber) else base
        }
    }

    private fun subscriptionId(accountComponent: String?, accountId: String?): Int? {
        if (!context.isGranted(Manifest.permission.READ_PHONE_STATE)) return null
        val component = accountComponent?.let(ComponentName::unflattenFromString) ?: return null
        val account = PhoneAccountHandle(component, accountId ?: return null)
        return try {
            telephony.getSubscriptionId(account).takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (_: SecurityException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }
}
