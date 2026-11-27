package com.hypai.fion

import com.hypai.fion.android.Readiness
import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.BarkSettings
import com.hypai.fion.bark.HttpResult
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.CallLogSnapshot
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.ForwardEvent
import com.hypai.fion.forward.Forwarder
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.MemoryOutbox
import com.hypai.fion.forward.MemoryStore
import com.hypai.fion.forward.MissedCallSync
import com.hypai.fion.forward.ScriptedTransport
import com.hypai.fion.forward.SimIdentity
import com.hypai.fion.forward.SimKey
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Message
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
import com.hypai.fion.store.SimTag
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingScheduler : SystemScheduler {
    val keepAliveCalls = mutableListOf<Pair<Boolean, Boolean>>()
    val retryCalls = mutableListOf<Boolean>()
    val callLogWatchJobIds = mutableListOf<Int?>()
    val callLogWatches: Int get() = callLogWatchJobIds.size
    var callLogCancels = 0
    var callLogSyncs = 0
    var retryCancels = 0

    override fun reconcileKeepAlive(active: Boolean, watchCallLog: Boolean) {
        keepAliveCalls += active to watchCallLog
    }

    override fun ensureCallLogWatch(runningJobId: Int?) {
        callLogWatchJobIds += runningJobId
    }

    override fun cancelCallLogWatch() {
        callLogCancels++
    }

    override fun syncCallLogAsync() {
        callLogSyncs++
    }

    override fun scheduleRetry(replace: Boolean) {
        retryCalls += replace
    }

    override fun cancelRetry() {
        retryCancels++
    }
}

class RuntimeCoordinatorTest {
    private val configured = Settings(bark = BarkSettings(deviceKey = "k"))
    private val allReady = Readiness(granted = AppPermission.entries.toSet(), batteryUnrestricted = true)
    private val noneReady = Readiness(granted = emptySet(), batteryUnrestricted = false)

    private data class Harness(
        val coordinator: RuntimeCoordinator,
        val scheduler: RecordingScheduler,
        val settings: MemoryStore<Settings>,
        val state: MemoryStore<ForwardState>,
        val outbox: MemoryOutbox,
        val setReadiness: (Readiness) -> Unit,
    )

    private fun harness(
        initialSettings: Settings = configured,
        initialState: ForwardState = ForwardState(activatedBootCount = 7),
        initialReadiness: Readiness = allReady,
        transport: ScriptedTransport = ScriptedTransport(),
        watermark: Long = 50L,
    ): Harness {
        var currentReadiness = initialReadiness
        val rawSettings = MemoryStore(initialSettings)
        val rawState = MemoryStore(initialState)
        val outbox = MemoryOutbox()
        val settingsStore = SettingsStore(rawSettings)
        val stateStore = ForwardStateStore(rawState)
        val scheduler = RecordingScheduler()
        val forwarder = Forwarder(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
            bark = BarkClient(transport),
            bootCount = { 7 },
            now = { 1_000L },
            zone = { ZoneOffset.UTC },
            language = { Language.En },
            scheduleRetry = { scheduler.scheduleRetry(replace = false) },
            cancelRetry = { scheduler.cancelRetry() },
            newSecret = { "secret" },
        )
        val missedCalls = MissedCallSync({ _, _ -> CallLogSnapshot(watermark, emptyList()) }, stateStore, forwarder)
        val coordinator = RuntimeCoordinator(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
            forwarder = forwarder,
            missedCalls = missedCalls,
            readReadiness = { currentReadiness },
            scheduler = scheduler,
        )
        return Harness(coordinator, scheduler, rawSettings, rawState, outbox) { currentReadiness = it }
    }

    @Test
    fun `missing bark endpoint keeps background runtime off until a key is saved`() = runTest {
        val env = harness(initialSettings = Settings(), watermark = 90L)

        assertFalse(env.coordinator.reconcile())
        assertEquals(false to false, env.scheduler.keepAliveCalls.last())
        assertEquals(0, env.scheduler.callLogWatches)

        env.coordinator.updateSettings { it.copy(bark = BarkSettings(deviceKey = "k")) }

        assertEquals(true to true, env.scheduler.keepAliveCalls.last())
        assertEquals(90L, env.state.value.callLogWatermark)
    }

    @Test
    fun `boot and foreground reconciliation preserve existing retry backoff`() = runTest {
        val env = harness(
            initialState = ForwardState(activatedBootCount = 7),
            transport = ScriptedTransport(HttpResult.Failure),
        )
        val sms = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "A", "SIM1", ""), 1_000L, 1_000L, "hi")
        env.outbox.enqueue(listOf(Message("id1", EventKind.Sms, sms.sim.key, sms.sim.tag, "t", "b", 1_000L)), Forwarder.MAX_PENDING)

        env.coordinator.onBootCompleted()
        env.coordinator.onForeground()

        assertEquals(listOf(false, false), env.scheduler.retryCalls)
    }

    @Test
    fun `changing bark settings replaces pending retry job immediately`() = runTest {
        val env = harness()
        env.outbox.enqueue(listOf(Message("id1", EventKind.Sms, SimKey.Unknown, SimTag(), "t", "b", 1_000L)), Forwarder.MAX_PENDING)

        env.coordinator.updateSettings { it.copy(bark = it.bark.copy(deviceKey = "newKey")) }

        assertEquals(listOf(true), env.scheduler.retryCalls)
    }

    @Test
    fun `disabling missed call channel detaches call log observer on keep alive service`() = runTest {
        val env = harness()

        env.coordinator.updateSettings { it.withChannel(EventKind.MissedCall, false) }

        assertEquals(listOf(true to false), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogCancels)
        assertEquals(0, env.scheduler.callLogWatches)
    }

    @Test
    fun `enabling missed call channel baselines the call log watermark`() = runTest {
        val env = harness(
            initialSettings = configured.withChannel(EventKind.MissedCall, false),
            watermark = 88L,
        )

        env.coordinator.updateSettings { it.withChannel(EventKind.MissedCall, true) }

        assertEquals(88L, env.state.value.callLogWatermark)
        assertEquals(listOf(true to true), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogWatches)
    }

    @Test
    fun `completing onboarding without forwarding permissions keeps intent while nothing is live`() = runTest {
        val env = harness(
            initialState = ForwardState(onboardingDone = false, activatedBootCount = 7),
            initialReadiness = noneReady,
        )

        env.coordinator.completeOnboarding()

        assertTrue(env.state.value.onboardingDone)
        assertTrue(env.settings.value.forwarding)
        assertTrue(env.settings.value.liveChannels(noneReady).isEmpty())
        assertEquals(listOf(false to false), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogCancels)
    }

    @Test
    fun `revoking the only enabled channel permission stops runtime and keeps the user's choice`() = runTest {
        val smsOnly = configured.withChannel(EventKind.MissedCall, false)
        val env = harness(
            initialSettings = smsOnly,
            initialReadiness = allReady.copy(granted = allReady.granted - AppPermission.ReceiveSms),
        )

        env.coordinator.reconcile()

        assertEquals(smsOnly, env.settings.value)
        assertTrue(smsOnly.liveChannels(env.coordinator.readiness.value).isEmpty())
        assertEquals(listOf(false to false), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogCancels)
    }

    @Test
    fun `revoking call log permission keeps sms keep alive and cancels call log watch`() = runTest {
        val env = harness(
            initialState = ForwardState(activatedBootCount = 7, callLogWatermark = 30L),
            initialReadiness = allReady.copy(granted = allReady.granted - AppPermission.ReadCallLog),
        )

        env.coordinator.reconcile()

        assertEquals(null, env.state.value.callLogWatermark)
        assertEquals(listOf(true to false), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogCancels)
        assertEquals(0, env.scheduler.callLogWatches)
    }

    @Test
    fun `restoring a revoked permission resumes forwarding without replaying calls missed meanwhile`() = runTest {
        val env = harness(
            initialState = ForwardState(activatedBootCount = 7, callLogWatermark = 30L),
            initialReadiness = noneReady.copy(granted = setOf(AppPermission.Notifications)),
            watermark = 99L,
        )

        env.coordinator.reconcile()
        assertEquals(null, env.state.value.callLogWatermark)

        env.setReadiness(allReady)
        env.coordinator.reconcile()

        assertEquals(listOf(false to false, true to true), env.scheduler.keepAliveCalls)
        assertEquals(1, env.scheduler.callLogWatches)
        assertEquals(99L, env.state.value.callLogWatermark)
        assertEquals(configured, env.settings.value)
    }

    @Test
    fun `turning one channel on while the master switch is off revives only that channel`() = runTest {
        val env = harness(initialSettings = configured.withForwarding(false))

        env.coordinator.updateSettings { it.withChannel(EventKind.Sms, true) }

        assertTrue(env.settings.value.forwarding)
        assertEquals(setOf(EventKind.Sms), env.settings.value.channels)
        assertEquals(listOf(true to false), env.scheduler.keepAliveCalls)
    }

    @Test
    fun `blocking notifications stops keep alive while preserving call log watch`() = runTest {
        val env = harness(initialReadiness = allReady)
        env.coordinator.reconcile()

        env.setReadiness(allReady.copy(granted = allReady.granted - AppPermission.Notifications))
        env.coordinator.reconcile()

        assertEquals(listOf(true to true, false to true), env.scheduler.keepAliveCalls)
        assertEquals(2, env.scheduler.callLogWatches)
        assertEquals(0, env.scheduler.callLogCancels)
    }

    @Test
    fun `call log job trigger reconciles with running job id and cancels when permission revoked`() = runTest {
        val env = harness(initialReadiness = allReady, watermark = 77L)

        env.coordinator.onCallLogTriggered(runningJobId = 4)

        assertEquals(listOf<Int?>(4), env.scheduler.callLogWatchJobIds)
        assertEquals(77L, env.state.value.callLogWatermark)

        env.setReadiness(allReady.copy(granted = allReady.granted - setOf(AppPermission.ReceiveSms, AppPermission.ReadCallLog)))
        env.coordinator.onCallLogTriggered(runningJobId = 1)

        assertEquals(1, env.scheduler.callLogCancels)
        assertEquals(false to false, env.scheduler.keepAliveCalls.last())
    }

    @Test
    fun `boot completed without forwarding permissions does not start keep alive or call log sync`() = runTest {
        val env = harness(
            initialState = ForwardState(activatedBootCount = null),
            initialReadiness = noneReady.copy(granted = setOf(AppPermission.Notifications)),
        )

        env.coordinator.onBootCompleted()

        assertEquals(7, env.state.value.activatedBootCount)
        assertEquals(listOf(false to false), env.scheduler.keepAliveCalls)
        assertEquals(0, env.scheduler.callLogSyncs)
        assertEquals(1, env.scheduler.callLogCancels)
    }
}
