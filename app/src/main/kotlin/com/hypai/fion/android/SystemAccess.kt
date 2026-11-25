package com.hypai.fion.android

import android.Manifest
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.LocaleList
import android.os.PowerManager
import android.provider.Settings
import com.hypai.fion.R
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language

fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

enum class PermissionGroup(vararg val permissions: String) {
    Sms(Manifest.permission.RECEIVE_SMS),
    CallLog(Manifest.permission.READ_CALL_LOG),
    Phone(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS),
    Notifications(Manifest.permission.POST_NOTIFICATIONS),
}

val EventKind.permission: PermissionGroup
    get() = when (this) {
        EventKind.Sms -> PermissionGroup.Sms
        EventKind.MissedCall -> PermissionGroup.CallLog
    }

fun Context.isGranted(group: PermissionGroup): Boolean = group.permissions.all(::isGranted)

fun Context.notificationsEnabled(): Boolean =
    getSystemService(NotificationManager::class.java).areNotificationsEnabled()

data class Readiness(
    val sms: Boolean,
    val callLog: Boolean,
    val phone: Boolean,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean,
) {
    fun allows(kind: EventKind): Boolean = when (kind) {
        EventKind.Sms -> sms
        EventKind.MissedCall -> callLog
    }

    val forwardable: Boolean get() = EventKind.entries.any(::allows)

    fun keepAliveNotice(persistentNotification: Boolean): Boolean = notifications && persistentNotification

    fun backgroundReady(persistentNotification: Boolean): Boolean =
        batteryUnrestricted && keepAliveNotice(persistentNotification)
}

enum class PermissionHealth { Ready, Partial, Blocked }

fun Readiness.health(persistentNotification: Boolean): PermissionHealth = when {
    !sms || !phone -> PermissionHealth.Blocked
    callLog && backgroundReady(persistentNotification) -> PermissionHealth.Ready
    else -> PermissionHealth.Partial
}

fun Context.readiness(): Readiness = Readiness(
    sms = isGranted(PermissionGroup.Sms),
    callLog = isGranted(PermissionGroup.CallLog),
    phone = isGranted(PermissionGroup.Phone),
    batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
    notifications = notificationsEnabled(),
)

private fun Context.packageUri(): Uri = Uri.fromParts("package", packageName, null)

private fun Context.batteryExemptionIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

private fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

private fun Context.safeStartActivity(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

fun Context.openAppDetails(): Boolean = safeStartActivity(appDetailsIntent())

fun Context.openBatteryExemption() {
    if (!safeStartActivity(batteryExemptionIntent())) openAppDetails()
}

fun Context.bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)

fun Context.language(): Language = Language.valueOf(getString(R.string.language))

fun Context.applyAppLanguage(language: Language) {
    getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
}

fun Context.isNetworkAvailable(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return true
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork ?: return false) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
