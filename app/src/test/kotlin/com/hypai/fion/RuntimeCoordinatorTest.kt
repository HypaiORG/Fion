package com.hypai.fion

import com.hypai.fion.android.Readiness
import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.BarkSettings
import com.hypai.fion.bark.HttpResult
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
    var callLogWatches = 0
    var callLogCancels = 0
    var callLogSyncs = 0
    var retryCancels = 0

    override fun reconcileKeepAlive(active: Boolean, watchCallLog: Boolean) {
        keepAliveCalls += active to watchCallLog
    }

    override fun ensureCallLogWatch() {
        callLogWatches++
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

    private data class Harness(
        val coordinator: RuntimeCoordinator,
        val scheduler: RecordingScheduler,
        val settings: MemoryStore<Settings>,
        val state: MemoryStore<ForwardState>,
        val outbox: MemoryOutbox,
    )

    private fun harness(
        initialSettings: Settings = configured,
        initialState: ForwardState = ForwardState(activatedBootCount = 7),
        notificationsOn: Boolean = true,
        transport: ScriptedTransport = ScriptedTransport(),
        watermark: Long = 50L,
    ): Harness {
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
        val missedCalls = MissedCallSync({ CallLogSnapshot(watermark, emptyList()) }, stateStore, forwarder)
        val coordinator = RuntimeCoordinator(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
            forwarder = forwarder,
            missedCalls = missedCalls,
            notificationsEnabled = { notificationsOn },
            scheduler = scheduler,
        )
        return Harness(coordinator, scheduler, rawSettings, rawState, outbox)
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
        env.coordinator.onForeground(Readiness(sms = true, callLog = true, phone = true, batteryUnrestricted = true, notifications = true))

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
    fun `enabling missed call channel advances watermark before committing settings`() = runTest {
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
    fun `completing onboarding without forwarding permissions pauses forwarding`() = runTest {
        val env = harness(initialState = ForwardState(onboardingDone = false, activatedBootCount = 7))

        env.coordinator.completeOnboarding(Readiness(sms = false, callLog = false, phone = false, batteryUnrestricted = false, notifications = false))

        assertTrue(env.state.value.onboardingDone)
        assertFalse(env.settings.value.forwarding)
    }
}
