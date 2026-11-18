package com.hypai.fion.platform

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import com.hypai.fion.R
import com.hypai.fion.graph
import com.hypai.fion.ui.MainActivity
import kotlinx.coroutines.launch

class KeepAliveService : Service() {
    private var callLogObserver: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        observeCallLog()
        return START_STICKY
    }

    override fun onDestroy() {
        callLogObserver?.let(contentResolver::unregisterContentObserver)
        super.onDestroy()
    }

    private fun observeCallLog() {
        if (callLogObserver != null || !isGranted(Manifest.permission.READ_CALL_LOG)) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                graph.scope.launch { graph.missedCalls.sync() }
            }
        }
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        callLogObserver = observer
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        fun reconcile(context: Context, shouldRun: Boolean) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (!shouldRun) {
                context.stopService(intent)
                return
            }
            try {
                context.startForegroundService(intent)
            } catch (_: ForegroundServiceStartNotAllowedException) {
                // Android 12+ blocks foreground service starts from the background unless the app is
                // exempt from battery optimization: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
            }
        }
    }
}
