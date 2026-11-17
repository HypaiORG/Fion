package com.hypai.fion

import com.hypai.fion.android.Readiness
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Forwarder
import com.hypai.fion.forward.MissedCallSync
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore

interface SystemScheduler {
    fun reconcileKeepAlive(active: Boolean, watchCallLog: Boolean)
    fun ensureCallLogWatch()
    fun cancelCallLogWatch()
    fun syncCallLogAsync()
    fun scheduleRetry(replace: Boolean)
    fun cancelRetry()
}

class RuntimeCoordinator(
    private val settingsStore: SettingsStore,
    private val stateStore: ForwardStateStore,
    private val forwarder: Forwarder,
    private val missedCalls: MissedCallSync,
    private val notificationsEnabled: () -> Boolean,
    private val scheduler: SystemScheduler,
) {
    private fun keepAliveWanted(settings: Settings, state: ForwardState): Boolean =
        forwarder.isActive(settings, state) && settings.persistentNotification

    private fun callLogWatchWanted(settings: Settings, state: ForwardState): Boolean =
        forwarder.isActive(settings, state) && settings.forwards(EventKind.MissedCall)

    suspend fun shouldKeepAlive(): Boolean =
        keepAliveWanted(settingsStore.snapshot(), stateStore.snapshot()) && notificationsEnabled()

    suspend fun shouldWatchCallLog(): Boolean =
        callLogWatchWanted(settingsStore.snapshot(), stateStore.snapshot())

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val (before, after) = settingsStore.update(
            beforeCommit = { prev, next ->
                val state = stateStore.snapshot()
                if (!callLogWatchWanted(prev, state) && callLogWatchWanted(next, state)) {
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

    suspend fun completeOnboarding(readiness: Readiness) {
        stateStore.completeOnboarding()
        if (!readiness.forwardable) updateSettings { it.withForwarding(false) }
    }

    suspend fun resetStats() {
        stateStore.resetStats()
    }

    suspend fun onForeground(readiness: Readiness) {
        if (stateStore.snapshot().onboardingDone && !readiness.forwardable) {
            updateSettings { it.withForwarding(false) }
        }
        forwarder.activate()
        reconcile(replaceRetry = false)
        if (shouldWatchCallLog()) missedCalls.sync()
    }

    suspend fun onBootCompleted() {
        if (settingsStore.snapshot().autoStartOnBoot) forwarder.activate()
        reconcile(replaceRetry = false)
        if (shouldWatchCallLog()) scheduler.syncCallLogAsync()
    }

    suspend fun onPackageReplaced() {
        reconcile(replaceRetry = false)
        if (shouldWatchCallLog()) scheduler.syncCallLogAsync()
    }

    suspend fun reconcile(replaceRetry: Boolean = false) {
        val settings = settingsStore.snapshot()
        val state = stateStore.snapshot()
        val watchCallLog = callLogWatchWanted(settings, state)
        val keepAlive = keepAliveWanted(settings, state) && notificationsEnabled()
        scheduler.reconcileKeepAlive(active = keepAlive, watchCallLog = watchCallLog)
        if (watchCallLog) scheduler.ensureCallLogWatch() else scheduler.cancelCallLogWatch()
        when {
            state.pending.isEmpty() -> scheduler.cancelRetry()
            state.pending.any { forwarder.accepts(settings, state, it.kind) } -> scheduler.scheduleRetry(replace = replaceRetry)
        }
    }
}
