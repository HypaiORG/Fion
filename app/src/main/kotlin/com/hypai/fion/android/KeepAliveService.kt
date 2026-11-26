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
import android.net.ConnectivityManager
import android.net.Network
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

private sealed interface ServiceMode {
    data object Stopped : ServiceMode
    data class Running(val watchCallLog: Boolean) : ServiceMode
}

class KeepAliveService : Service() {
    private var callLogObserver: ContentObserver? = null
    private var pendingSync: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var lastNetwork: Network? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        registerNetworkCallback()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } catch (_: ForegroundServiceStartNotAllowedException) {
            running = ServiceMode.Stopped
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val target = desired
        if (intent != null && target is ServiceMode.Stopped) {
            running = ServiceMode.Stopped
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent == null) {
            running = ServiceMode.Running(watchCallLog = false)
            appContainer.scope.launch { appContainer.coordinator.reconcile() }
        } else {
            val watch = (target as? ServiceMode.Running)?.watchCallLog
                ?: intent.getBooleanExtra(EXTRA_WATCH_CALL_LOG, false)
            syncCallLogObserver(watch)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        clearCallLogObserver()
        running = ServiceMode.Stopped
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        lastNetwork = connectivity.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = lastNetwork
                lastNetwork = network
                if (previous != network) {
                    appContainer.scope.launch { appContainer.forwarder.onNetworkAvailable() }
                }
            }

            override fun onLost(network: Network) {
                if (lastNetwork == network) lastNetwork = null
            }
        }
        try {
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun syncCallLogObserver(watch: Boolean) {
        val shouldAttach = watch && isGranted(Manifest.permission.READ_CALL_LOG)
        if (!shouldAttach) {
            clearCallLogObserver()
            running = ServiceMode.Running(watchCallLog = false)
            return
        }
        running = ServiceMode.Running(watchCallLog = true)
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
        private var running: ServiceMode = ServiceMode.Stopped

        @Volatile
        private var desired: ServiceMode = ServiceMode.Stopped

        fun reconcile(context: Context, shouldRun: Boolean, watchCallLog: Boolean) {
            val target = if (shouldRun) ServiceMode.Running(watchCallLog) else ServiceMode.Stopped
            val previous = desired
            desired = target
            if (target is ServiceMode.Stopped) {
                if (running != ServiceMode.Stopped || previous != ServiceMode.Stopped) {
                    context.stopService(Intent(context, KeepAliveService::class.java))
                }
                return
            }
            if (running == target && previous == target) return
            val intent = Intent(context, KeepAliveService::class.java).putExtra(EXTRA_WATCH_CALL_LOG, watchCallLog)
            try {
                context.startForegroundService(intent)
            } catch (_: ForegroundServiceStartNotAllowedException) {
                desired = running
                // Android 12+ blocks foreground service starts from the background unless the app is
                // exempt from battery optimization: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
            }
        }
    }
}
