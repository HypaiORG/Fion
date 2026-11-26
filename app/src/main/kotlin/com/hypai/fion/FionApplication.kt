package com.hypai.fion

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.SystemClock
import com.hypai.fion.android.ForwardJobs
import com.hypai.fion.android.KeepAliveService
import com.hypai.fion.android.SimDirectory
import com.hypai.fion.android.SystemCallLog
import com.hypai.fion.android.bootCount
import com.hypai.fion.android.isNetworkAvailable
import com.hypai.fion.android.language
import com.hypai.fion.android.readiness
import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.HttpsTransport
import com.hypai.fion.forward.Forwarder
import com.hypai.fion.forward.MissedCallSync
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.KeystoreCipher
import com.hypai.fion.store.Outbox
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
import com.hypai.fion.store.SqliteOutbox
import com.hypai.fion.store.jsonStore
import com.hypai.fion.ui.design.clearGlowCache
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore: SettingsStore by lazy {
        SettingsStore(context.jsonStore("settings", Settings.serializer(), Settings(), KeystoreCipher("settings")))
    }

    val stateStore: ForwardStateStore by lazy {
        ForwardStateStore(context.jsonStore("runtime", ForwardState.serializer(), ForwardState(), KeystoreCipher("runtime")))
    }

    val outbox: Outbox by lazy { SqliteOutbox(context) }

    val sims: SimDirectory by lazy { SimDirectory(context) }

    val forwarder: Forwarder by lazy {
        Forwarder(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
            bark = BarkClient(HttpsTransport(context)),
            bootCount = context::bootCount,
            now = System::currentTimeMillis,
            elapsedMs = SystemClock::elapsedRealtime,
            zone = ZoneId::systemDefault,
            language = context::language,
            scheduleRetry = { ForwardJobs.scheduleRetry(context) },
            cancelRetry = { ForwardJobs.cancelRetry(context) },
            newSecret = { Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes)) },
            isNetworkAvailable = context::isNetworkAvailable,
        )
    }

    val missedCalls: MissedCallSync by lazy {
        MissedCallSync(
            reader = SystemCallLog(context, sims, System::currentTimeMillis, ZoneId::systemDefault),
            stateStore = stateStore,
            forwarder = forwarder,
        )
    }

    val coordinator: RuntimeCoordinator by lazy {
        RuntimeCoordinator(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
            forwarder = forwarder,
            missedCalls = missedCalls,
            readReadiness = context::readiness,
            scheduler = object : SystemScheduler {
                override fun reconcileKeepAlive(active: Boolean, watchCallLog: Boolean) =
                    KeepAliveService.reconcile(context, active, watchCallLog)

                override fun ensureCallLogWatch(runningJobId: Int?) =
                    ForwardJobs.ensureCallLogWatch(context, runningJobId)
                override fun cancelCallLogWatch() = ForwardJobs.cancelCallLogWatch(context)
                override fun syncCallLogAsync() = ForwardJobs.syncCallLogAsync(context)
                override fun scheduleRetry(replace: Boolean) = ForwardJobs.scheduleRetry(context, replace)
                override fun cancelRetry() = ForwardJobs.cancelRetry(context)
            },
        )
    }
}

class FionApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) clearGlowCache()
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as FionApplication).container
