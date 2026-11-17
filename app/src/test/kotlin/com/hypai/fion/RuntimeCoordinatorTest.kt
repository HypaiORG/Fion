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
import com.hypai.fion.forward.MemoryStore
import com.hypai.fion.forward.MissedCallSync
import com.hypai.fion.forward.ScriptedTransport
import com.hypai.fion.forward.SimIdentity
import com.hypai.fion.forward.SimKey
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
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

    private fun harness(
        initialSettings: Settings = configured,
        initialState: ForwardState = ForwardState(activatedBootCount = 7),
        notificationsOn: Boolean = true,
        transport: ScriptedTransport = ScriptedTransport(),
        watermark: Long = 50L,
    ): Triple<RuntimeCoordinator, RecordingScheduler, Pair<MemoryStore<Settings>, MemoryStore<ForwardState>>> {
        val rawSettings = MemoryStore(initialSettings)
        val rawState = MemoryStore(initialState)
        val settingsStore = SettingsStore(rawSettings)
        val stateStore = ForwardStateStore(rawState)
        val scheduler = RecordingScheduler()
        val forwarder = Forwarder(
            settingsStore = settingsStore,
            stateStore = stateStore,
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
            forwarder = forwarder,
            missedCalls = missedCalls,
            notificationsEnabled = { notificationsOn },
            scheduler = scheduler,
        )
        return Triple(coordinator, scheduler, rawSettings to rawState)
    }

    @Test
    fun `boot and foreground reconciliation preserve existing retry backoff`() = runTest {
        val (coordinator, scheduler, stores) = harness(
            initialState = ForwardState(activatedBootCount = 6),
            transport = ScriptedTransport(HttpResult.Failure),
        )
        val sms = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "A", "SIM1", ""), 1_000L, 1_000L, "hi")
        val rawState = stores.second
        rawState.updateData {
            it.copy(
                activatedBootCount = 7,
                pending = listOf(com.hypai.fion.store.Message("id1", EventKind.Sms, sms.sim.key, sms.sim.tag, "t", "b", 1_000L)),
            )
        }

        coordinator.onBootCompleted()
        coordinator.onForeground(Readiness(sms = true, callLog = true, phone = true, batteryUnrestricted = true, notifications = true))

        assertEquals(listOf(false, false), scheduler.retryCalls)
    }

    @Test
    fun `changing bark settings replaces pending retry job immediately`() = runTest {
        val (coordinator, scheduler, stores) = harness()
        stores.second.updateData {
            it.copy(pending = listOf(com.hypai.fion.store.Message("id1", EventKind.Sms, SimKey.Unknown, com.hypai.fion.store.SimTag(), "t", "b", 1_000L)))
        }

        coordinator.updateSettings { it.copy(bark = it.bark.copy(deviceKey = "newKey")) }

        assertEquals(listOf(true), scheduler.retryCalls)
    }

    @Test
    fun `disabling missed call channel detaches call log observer on keep alive service`() = runTest {
        val (coordinator, scheduler, _) = harness()

        coordinator.updateSettings { it.withChannel(EventKind.MissedCall, false) }

        assertEquals(listOf(true to false), scheduler.keepAliveCalls)
        assertEquals(1, scheduler.callLogCancels)
        assertEquals(0, scheduler.callLogWatches)
    }

    @Test
    fun `enabling missed call channel advances watermark before committing settings`() = runTest {
        val (coordinator, scheduler, stores) = harness(
            initialSettings = configured.withChannel(EventKind.MissedCall, false),
            watermark = 88L,
        )

        coordinator.updateSettings { it.withChannel(EventKind.MissedCall, true) }

        assertEquals(88L, stores.second.value.callLogWatermark)
        assertEquals(listOf(true to true), scheduler.keepAliveCalls)
        assertEquals(1, scheduler.callLogWatches)
    }

    @Test
    fun `completing onboarding without forwarding permissions pauses forwarding`() = runTest {
        val (coordinator, _, stores) = harness(initialState = ForwardState(onboardingDone = false, activatedBootCount = 7))

        coordinator.completeOnboarding(Readiness(sms = false, callLog = false, phone = false, batteryUnrestricted = false, notifications = false))

        assertTrue(stores.second.value.onboardingDone)
        assertFalse(stores.first.value.forwarding)
    }
}
