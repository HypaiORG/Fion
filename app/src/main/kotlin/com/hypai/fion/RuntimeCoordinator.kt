package com.hypai.fion

import com.hypai.fion.android.Readiness
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Forwarder
import com.hypai.fion.forward.MissedCallSync
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Outbox
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun Settings.effectiveChannels(readiness: Readiness): Set<EventKind> =
    if (forwarding) channels.filterTo(LinkedHashSet(), readiness::allows) else emptySet()

interface SystemScheduler {
    fun reconcileKeepAlive(active: Boolean, watchCallLog: Boolean)
    fun ensureCallLogWatch(runningJobId: Int?)
    fun cancelCallLogWatch()
    fun syncCallLogAsync()
    fun scheduleRetry(replace: Boolean)
    fun cancelRetry()
}

class RuntimeCoordinator(
    private val settingsStore: SettingsStore,
    private val stateStore: ForwardStateStore,
    private val outbox: Outbox,
    private val forwarder: Forwarder,
    private val missedCalls: MissedCallSync,
    private val readReadiness: () -> Readiness,
    private val scheduler: SystemScheduler,
) {
    private val reconcileLock = Mutex()
    private val _readiness = MutableStateFlow(readReadiness())
    val readiness: StateFlow<Readiness> = _readiness.asStateFlow()

    fun refreshReadiness(): Readiness = readReadiness().also { _readiness.value = it }

    private fun activeChannels(settings: Settings, state: ForwardState, readiness: Readiness): Set<EventKind> =
        forwarder.enabledKinds(settings, state).filterTo(LinkedHashSet(), readiness::allows)

    suspend fun shouldWatchCallLog(): Boolean =
        EventKind.MissedCall in activeChannels(settingsStore.snapshot(), stateStore.snapshot(), refreshReadiness())

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val (before, after) = settingsStore.update(
            beforeCommit = { prev, next ->
                val state = stateStore.snapshot()
                val current = refreshReadiness()
                if (EventKind.MissedCall !in activeChannels(prev, state, current) &&
                    EventKind.MissedCall in activeChannels(next, state, current)
                ) {
                    missedCalls.advanceWatermark()
                }
            },
            transform = transform,
        )
        if (before == after) return
        val retryReset = after.bark != before.bark ||
            (!before.forwarding && after.forwarding) ||
            (after.channels - before.channels).isNotEmpty()
        reconcile(replaceRetry = retryReset)
    }

    suspend fun completeOnboarding() {
        stateStore.completeOnboarding()
        reconcile()
    }

    suspend fun onForeground() {
        forwarder.activate()
        if (reconcile()) missedCalls.sync()
    }

    suspend fun onBootCompleted() {
        if (settingsStore.snapshot().autoStartOnBoot) forwarder.activate()
        if (reconcile()) scheduler.syncCallLogAsync()
    }

    suspend fun onPackageReplaced() {
        if (reconcile()) scheduler.syncCallLogAsync()
    }

    suspend fun onCallLogTriggered(runningJobId: Int) {
        if (reconcile(runningCallLogJobId = runningJobId)) missedCalls.sync()
    }

    suspend fun reconcile(replaceRetry: Boolean = false, runningCallLogJobId: Int? = null): Boolean =
        reconcileLock.withLock {
            val settings = settingsStore.snapshot()
            val state = stateStore.snapshot()
            val current = refreshReadiness()
            val channels = activeChannels(settings, state, current)
            val watchCallLog = EventKind.MissedCall in channels
            val keepAlive = channels.isNotEmpty() && current.keepAliveNotice(settings.persistentNotification)
            scheduler.reconcileKeepAlive(active = keepAlive, watchCallLog = watchCallLog)
            if (watchCallLog) scheduler.ensureCallLogWatch(runningCallLogJobId) else scheduler.cancelCallLogWatch()
            when {
                !outbox.hasAny() -> scheduler.cancelRetry()
                outbox.hasAny(forwarder.enabledKinds(settings, state)) -> scheduler.scheduleRetry(replace = replaceRetry)
            }
            watchCallLog
        }
}
