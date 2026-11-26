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
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.requirement
import com.hypai.fion.forward.requirements
import com.hypai.fion.forward.usedVariables
import com.hypai.fion.store.Settings as AppSettings

fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

val AppPermission.manifestName: String
    get() = when (this) {
        AppPermission.ReceiveSms -> Manifest.permission.RECEIVE_SMS
        AppPermission.ReadCallLog -> Manifest.permission.READ_CALL_LOG
        AppPermission.ReadPhoneState -> Manifest.permission.READ_PHONE_STATE
        AppPermission.ReadPhoneNumbers -> Manifest.permission.READ_PHONE_NUMBERS
        AppPermission.Notifications -> Manifest.permission.POST_NOTIFICATIONS
    }

val PermissionGroup.permissions: Array<String>
    get() = requirements.map(AppPermission::manifestName).toTypedArray()

fun Context.isGranted(permission: AppPermission): Boolean = when (permission) {
    AppPermission.Notifications -> notificationsEnabled()
    else -> isGranted(permission.manifestName)
}

fun Context.isGranted(group: PermissionGroup): Boolean = group.requirements.all(::isGranted)

fun Context.notificationsEnabled(): Boolean =
    getSystemService(NotificationManager::class.java).areNotificationsEnabled()

data class Readiness(
    val granted: Set<AppPermission>,
    val batteryUnrestricted: Boolean,
) {
    val sms: Boolean get() = allows(AppPermission.ReceiveSms)
    val callLog: Boolean get() = allows(AppPermission.ReadCallLog)
    val phoneState: Boolean get() = allows(AppPermission.ReadPhoneState)
    val phoneNumbers: Boolean get() = allows(AppPermission.ReadPhoneNumbers)
    val phone: Boolean get() = allows(PermissionGroup.Phone)
    val notifications: Boolean get() = allows(AppPermission.Notifications)

    fun allows(permission: AppPermission): Boolean = permission in granted

    fun allows(group: PermissionGroup): Boolean = group.requirements.all(::allows)

    fun allows(kind: EventKind): Boolean = allows(kind.requirement)

    fun allows(variable: TemplateVariable): Boolean = allows(variable.requirement)

    fun keepAliveNotice(persistentNotification: Boolean): Boolean = notifications && persistentNotification

    fun backgroundReady(persistentNotification: Boolean): Boolean =
        batteryUnrestricted && keepAliveNotice(persistentNotification)
}

enum class PermissionHealth { Ready, Partial, Blocked }

fun Readiness.health(settings: AppSettings, language: Language): PermissionHealth {
    val active = EventKind.entries.filter(settings::forwards)
    if (active.isEmpty()) return PermissionHealth.Ready
    if (active.none(::allows)) return PermissionHealth.Blocked
    val channelsReady = active.all(::allows)
    val variablesReady = active.all { kind ->
        settings.push(kind).template.usedVariables(kind, language).all(::allows)
    }
    return if (channelsReady && variablesReady && backgroundReady(settings.persistentNotification)) {
        PermissionHealth.Ready
    } else {
        PermissionHealth.Partial
    }
}

fun Context.readiness(): Readiness = Readiness(
    granted = AppPermission.entries.filterTo(LinkedHashSet(), ::isGranted),
    batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
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
