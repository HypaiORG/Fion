package com.hypai.fion

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import com.hypai.fion.core.BarkClient
import com.hypai.fion.core.Forwarder
import com.hypai.fion.core.MissedCallSync
import com.hypai.fion.core.Runtime
import com.hypai.fion.core.Settings
import com.hypai.fion.platform.HttpEngineTransport
import com.hypai.fion.platform.Jobs
import com.hypai.fion.platform.KeepAliveService
import com.hypai.fion.platform.SystemCallLog
import com.hypai.fion.platform.Telephony
import com.hypai.fion.platform.bootCount
import com.hypai.fion.platform.jsonStore
import com.hypai.fion.platform.language
import com.hypai.fion.platform.notificationsEnabled
import java.security.SecureRandom
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: DataStore<Settings> = context.jsonStore("settings", Settings.serializer(), Settings())
    val runtime: DataStore<Runtime> = context.jsonStore("runtime", Runtime.serializer(), Runtime())

    val telephony = Telephony(context)

    val forwarder = Forwarder(
        settings = settings,
        runtime = runtime,
        bark = BarkClient(HttpEngineTransport(context)),
        bootCount = context.bootCount(),
        now = System::currentTimeMillis,
        zone = ZoneId::systemDefault,
        language = context::language,
        scheduleRetry = { Jobs.scheduleRetry(context) },
        newSecret = { Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes)) },
    )

    val missedCalls = MissedCallSync(
        reader = SystemCallLog(context, telephony, System::currentTimeMillis),
        runtime = runtime,
        forwarder = forwarder,
    )

    private fun keepAliveWanted(settings: Settings, runtime: Runtime): Boolean =
        forwarder.isActive(settings, runtime) && settings.persistentNotification

    suspend fun reconcileKeepAlive() {
        val wanted = keepAliveWanted(settings.data.first(), runtime.data.first()) && context.notificationsEnabled()
        KeepAliveService.reconcile(context, wanted)
    }

    suspend fun watchCallLog() {
        Jobs.watchCallLog(context)
        missedCalls.sync()
    }

    fun start() {
        scope.launch {
            combine(settings.data, runtime.data, ::keepAliveWanted)
                .distinctUntilChanged()
                .collect { reconcileKeepAlive() }
        }
        scope.launch {
            combine(settings.data, runtime.data, ::Pair)
                .distinctUntilChangedBy { (s, r) -> Triple(forwarder.isActive(s, r), s.channels, s.bark) }
                .collect { (_, r) -> if (r.pending.isNotEmpty()) Jobs.scheduleRetry(context) }
        }
        scope.launch { watchCallLog() }
    }
}

class App : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.start()
    }
}

val Context.graph: AppGraph get() = (applicationContext as App).graph
