package com.hypai.fion.android

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
import com.hypai.fion.appContainer
import com.hypai.fion.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeepAliveService : Service() {
    private var callLogObserver: ContentObserver? = null
    private var pendingSync: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            active = false
            watchingCallLog = false
            stopSelf(startId)
            return START_NOT_STICKY
        }
        active = true
        if (intent == null) {
            val app = appContainer
            app.scope.launch {
                if (!app.coordinator.shouldKeepAlive()) {
                    stopSelf(startId)
                } else {
                    syncCallLogObserver(app.coordinator.shouldWatchCallLog())
                }
            }
        } else {
            syncCallLogObserver(intent.getBooleanExtra(EXTRA_WATCH_CALL_LOG, false))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        clearCallLogObserver()
        active = false
        watchingCallLog = false
        super.onDestroy()
    }

    private fun syncCallLogObserver(watch: Boolean) {
        val shouldAttach = watch && isGranted(Manifest.permission.READ_CALL_LOG)
        if (!shouldAttach) {
            clearCallLogObserver()
            watchingCallLog = false
            return
        }
        watchingCallLog = true
        if (callLogObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pendingSync?.cancel()
                pendingSync = appContainer.scope.launch {
                    delay(CALL_LOG_DEBOUNCE_MS)
                    if (appContainer.coordinator.shouldWatchCallLog()) appContainer.missedCalls.sync()
                }
            }
        }
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        callLogObserver = observer
    }

    private fun clearCallLogObserver() {
        pendingSync?.cancel()
        pendingSync = null
        callLogObserver?.let(contentResolver::unregisterContentObserver)
        callLogObserver = null
    }

    private fun notification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
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
        private const val CALL_LOG_DEBOUNCE_MS = 300L
        private const val EXTRA_WATCH_CALL_LOG = "watch_call_log"

        @Volatile
        private var active = false

        @Volatile
        private var watchingCallLog = false

        fun reconcile(context: Context, shouldRun: Boolean, watchCallLog: Boolean) {
            if (!shouldRun) {
                if (active) context.stopService(Intent(context, KeepAliveService::class.java))
                return
            }
            val effectiveWatch = watchCallLog && context.isGranted(Manifest.permission.READ_CALL_LOG)
            if (active && watchingCallLog == effectiveWatch) return
            val intent = Intent(context, KeepAliveService::class.java).putExtra(EXTRA_WATCH_CALL_LOG, effectiveWatch)
            try {
                context.startForegroundService(intent)
            } catch (_: ForegroundServiceStartNotAllowedException) {
                // Android 12+ blocks foreground service starts from the background unless the app is
                // exempt from battery optimization: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
            }
        }
    }
}
