package com.hypai.fion.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

enum class PermissionGroup(vararg val permissions: String) {
    Sms(Manifest.permission.RECEIVE_SMS),
    CallLog(Manifest.permission.READ_CALL_LOG),
    Phone(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS),
    Notifications(Manifest.permission.POST_NOTIFICATIONS),
}

fun Context.isGranted(group: PermissionGroup): Boolean = group.permissions.all(::isGranted)

fun Context.notificationsEnabled(): Boolean = getSystemService(NotificationManager::class.java).areNotificationsEnabled()

data class Readiness(
    val sms: Boolean,
    val callLog: Boolean,
    val phone: Boolean,
    val batteryUnrestricted: Boolean,
    val notifications: Boolean,
) {
    fun keepAliveNotice(persistentNotification: Boolean): Boolean = notifications && persistentNotification
}

enum class PermissionHealth { Ready, Partial, Blocked }

fun Readiness.health(persistentNotification: Boolean): PermissionHealth = when {
    !sms || !phone -> PermissionHealth.Blocked
    callLog && batteryUnrestricted && keepAliveNotice(persistentNotification) -> PermissionHealth.Ready
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

fun Context.batteryExemptionIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

fun Context.bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
