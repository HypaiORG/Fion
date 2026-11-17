package com.hypai.fion.forward

import com.hypai.fion.bark.AesConfig
import com.hypai.fion.bark.AesKeySize
import com.hypai.fion.bark.AesMode
import com.hypai.fion.bark.BarkClient
import com.hypai.fion.bark.BarkLevel
import com.hypai.fion.bark.BarkSettings
import com.hypai.fion.bark.HttpResult
import com.hypai.fion.bark.HttpTransport
import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import com.hypai.fion.store.Settings
import com.hypai.fion.store.SettingsStore
import com.hypai.fion.store.SimTag
import com.hypai.fion.store.SimTally
import com.hypai.fion.store.Stats
import com.hypai.fion.store.TemplateChoice
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwarderTest {
    private val configured = Settings(bark = BarkSettings(deviceKey = "k"))
    private val settingsBacking = MemoryStore(configured)
    private val stateBacking = MemoryStore(ForwardState(activatedBootCount = 7))
    private val settingsStore = SettingsStore(settingsBacking)
    private val stateStore = ForwardStateStore(stateBacking)
    private val stats get() = stateBacking.value.stats
    private var clock = 1_000L
    private var retriesScheduled = 0
    private var retriesCancelled = 0
    private var secretsCreated = 0
    private var language = Language.Zh

    private fun forwarder(transport: HttpTransport, newSecret: () -> String = { "secret-${secretsCreated++}" }) = Forwarder(
        settingsStore = settingsStore,
        stateStore = stateStore,
        bark = BarkClient(transport),
        bootCount = { 7 },
        now = { clock },
        zone = { ZoneOffset.UTC },
        language = { language },
        scheduleRetry = { retriesScheduled++ },
        cancelRetry = { retriesCancelled++ },
        newSecret = newSecret,
    )

    private fun sms(receivedAt: Long = 1_000L) = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "A", "主卡", ""), receivedAt, receivedAt, "hi")
    private fun call(receivedAt: Long = 1_000L) = ForwardEvent.MissedCall("10010", SimIdentity(SimKey.subscription(2), 1, "B", "副卡", ""), receivedAt, receivedAt, 1)

    @Test
    fun `delivered sms is counted with latency and sim`() = runTest {
        val transport = ScriptedTransport(ok)
        clock = 1_214L

        forwarder(transport).forward(sms())

        assertEquals(Stats(sms = 1, latencyTotalMs = 214, latencySamples = 1, sims = mapOf(SimKey.subscription(1) to SimTally(SimTag(0, "主卡", "A"), 1))), stats)
        assertEquals("https://api.day.app/push", transport.requests.single().first)
        assertTrue(stateBacking.value.pending.isEmpty())
    }

    @Test
    fun `one sim is counted once across carrier names in different languages`() = runTest {
        val unicom = SimIdentity(SimKey.subscription(5), 1, "中国联通", "SH", "")
        val forwarder = forwarder(ScriptedTransport(ok, ok, ok))

        forwarder.forward(sms().copy(sim = unicom))
        forwarder.forward(sms().copy(sim = unicom.copy(carrier = "China Unicom"), content = "2"))
        forwarder.forward(sms().copy(sim = SimIdentity.resolve(emptyMap(), subscriptionId = 5, slotIndex = null), content = "3"))

        assertEquals(mapOf(SimKey.subscription(5) to SimTally(SimTag(1, "SH", "China Unicom"), 3)), stats.sims)
    }

    @Test
    fun `each kind is pushed with its own sound level and archive`() = runTest {
        val transport = ScriptedTransport(ok, ok)
        settingsBacking.updateData { it.withPush(EventKind.MissedCall, it.callPush.copy(level = BarkLevel.Critical, archive = false)) }
        fun sent(index: Int) = Json.parseToJsonElement(transport.requests[index].second).jsonObject.mapValues { it.value.jsonPrimitive.content }

        forwarder(transport).forward(sms())
        forwarder(transport).forward(call())

        assertEquals(listOf("bell", "active", "1"), listOf("sound", "level", "isArchive").map(sent(0)::getValue))
        assertEquals(listOf("telegraph", "critical", "0"), listOf("sound", "level", "isArchive").map(sent(1)::getValue))
    }

    @Test
    fun `delivery ids stay stable for one install and survive a statistics reset`() = runTest {
        val transport = ScriptedTransport(ok, ok, ok)
        val forwarder = forwarder(transport)
        fun sentId(index: Int) = Json.parseToJsonElement(transport.requests[index].second).jsonObject.getValue("id").jsonPrimitive.content

        forwarder.forward(sms())
        stateStore.resetStats()
        forwarder.forward(sms())
        forwarder.forward(sms().copy(sim = SimIdentity(SimKey.subscription(2), 1, "B", "副卡", "")))

        assertEquals(sentId(0), sentId(1))
        assertFalse(sentId(0) == sentId(2))
        assertEquals("secret-0", stateBacking.value.deliverySecret)
        assertEquals(1, secretsCreated)
    }

    @Test
    fun `a forward cancelled while the delivery secret is created sends nothing`() = runTest {
        val transport = ScriptedTransport(ok)
        lateinit var job: Job
        val forwarder = forwarder(transport) { job.cancel(); "secret" }

        job = launch { forwarder.forward(sms()) }
        job.join()

        assertTrue(transport.requests.isEmpty())
        assertEquals("secret", stateBacking.value.deliverySecret)
        assertEquals(Stats(), stats)
    }

    @Test
    fun `rejected push counts as failure without retry`() = runTest {
        forwarder(ScriptedTransport(badKey)).forward(call())

        assertEquals(Stats(failed = 1), stats)
        assertTrue(stateBacking.value.pending.isEmpty())
        assertEquals(0, retriesScheduled)
    }

    @Test
    fun `unreachable push is queued and retry is scheduled`() = runTest {
        forwarder(ScriptedTransport(HttpResult.Failure)).forward(sms())

        assertEquals(Stats(), stats)
        assertEquals(1, stateBacking.value.pending.size)
        assertEquals(1, retriesScheduled)
    }

    @Test
    fun `events are dropped when paused disabled unconfigured or not activated since boot`() = runTest {
        val transport = ScriptedTransport()
        val forwarder = forwarder(transport)

        settingsBacking.updateData { configured.copy(forwardingEnabled = false) }
        forwarder.forward(sms())
        settingsBacking.updateData { configured.withChannel(EventKind.Sms, false) }
        forwarder.forward(sms())
        settingsBacking.updateData { configured.withChannel(EventKind.MissedCall, false) }
        forwarder.forward(call())
        settingsBacking.updateData { Settings() }
        forwarder.forward(sms())
        settingsBacking.updateData { configured.copy(bark = configured.bark.copy(aes = AesConfig(AesKeySize.Aes128, AesMode.Cbc, "short"))) }
        forwarder.forward(sms())
        settingsBacking.updateData { configured }
        stateBacking.updateData { it.copy(activatedBootCount = 6) }
        forwarder.forward(sms())

        assertTrue(transport.requests.isEmpty())
        assertEquals(Stats(), stats)
    }

    @Test
    fun `activate marks the current boot`() = runTest {
        stateBacking.updateData { ForwardState() }
        val forwarder = forwarder(ScriptedTransport(ok))

        forwarder.activate()
        forwarder.forward(sms())

        assertEquals(7, stateBacking.value.activatedBootCount)
        assertEquals(1, stats.delivered)
    }

    @Test
    fun `retry delivers in order without latency sample and stops at first unreachable`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, HttpResult.Failure, ok, HttpResult.Failure, ok)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        forwarder.forward(call())

        assertFalse(forwarder.retryPending())
        assertEquals(listOf(EventKind.MissedCall), stateBacking.value.pending.map { it.kind })
        assertTrue(forwarder.retryPending())

        assertTrue(stateBacking.value.pending.isEmpty())
        assertEquals(1, retriesCancelled)
        assertEquals(
            Stats(sms = 1, missedCalls = 1, sims = mapOf(SimKey.subscription(1) to SimTally(SimTag(0, "主卡", "A"), 1), SimKey.subscription(2) to SimTally(SimTag(1, "副卡", "B"), 1))),
            stats,
        )
    }

    @Test
    fun `a queued message is retried with the group of the current language`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, ok)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())

        language = Language.En
        assertTrue(forwarder.retryPending())

        assertEquals(listOf("短信", "SMS"), transport.requests.map { Json.parseToJsonElement(it.second).jsonObject.getValue("group").jsonPrimitive.content })
    }

    @Test
    fun `expired and rejected queue entries count as failures`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, HttpResult.Failure, badKey)
        val forwarder = forwarder(transport)
        forwarder.forward(sms(receivedAt = 1_000L))
        clock = 1_000L + Forwarder.PENDING_EXPIRY.inWholeMilliseconds
        forwarder.forward(call(receivedAt = clock))
        clock += 1

        assertTrue(forwarder.retryPending())

        assertEquals(Stats(failed = 2), stats)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `first sync only records the watermark`() = runTest {
        val transport = ScriptedTransport()
        val sync = MissedCallSync({ CallLogSnapshot(41, listOf(LoggedMissedCall(41, call()))) }, stateStore, forwarder(transport))

        sync.sync()

        assertEquals(41L, stateBacking.value.callLogWatermark)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `later sync forwards new missed calls and advances the watermark`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        val asked = mutableListOf<Long?>()
        val sync = MissedCallSync(
            { after -> asked += after; CallLogSnapshot(43, listOf(LoggedMissedCall(42, call()), LoggedMissedCall(43, call()))) },
            stateStore,
            forwarder(ScriptedTransport(ok, ok)),
        )

        sync.sync()

        assertEquals(listOf<Long?>(41L), asked)
        assertEquals(43L, stateBacking.value.callLogWatermark)
        assertEquals(2, stats.missedCalls)
    }

    @Test
    fun `interrupted sync does not resend calls that were already forwarded`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        val log = listOf(LoggedMissedCall(42, call()), LoggedMissedCall(43, call()))
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String): HttpResult {
                posts++
                if (posts == 2) throw kotlinx.coroutines.CancellationException("job stopped")
                return ok
            }
        }
        val sync = MissedCallSync(
            { after -> CallLogSnapshot(43, log.filter { it.id > (after ?: 0) }) },
            stateStore,
            forwarder(transport),
        )

        runCatching { sync.sync() }
        val interrupted = stateBacking.value.callLogWatermark
        sync.sync()

        assertEquals(3, transport.posts)
        assertEquals(42L, interrupted)
        assertEquals(2, stats.missedCalls)
        assertEquals(43L, stateBacking.value.callLogWatermark)
    }

    @Test
    fun `retry holds queued messages while forwarding is paused and sends them after resume`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, HttpResult.Failure, ok, ok)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        forwarder.forward(call())

        settingsBacking.updateData { configured.copy(forwardingEnabled = false) }
        assertTrue(forwarder.retryPending())
        settingsBacking.updateData { configured.withChannel(EventKind.Sms, false) }
        assertTrue(forwarder.retryPending())

        assertEquals(listOf(EventKind.Sms), stateBacking.value.pending.map { it.kind })
        assertEquals(3, transport.requests.size)
        settingsBacking.updateData { configured }
        assertTrue(forwarder.retryPending())
        assertTrue(stateBacking.value.pending.isEmpty())
        assertEquals(2, stats.delivered)
    }

    @Test
    fun `pausing forwarding during a retry keeps the remaining messages queued`() = runTest {
        val failing = ScriptedTransport(HttpResult.Failure, HttpResult.Failure)
        forwarder(failing).forward(sms())
        forwarder(failing).forward(call())
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String): HttpResult {
                posts++
                settingsBacking.updateData { it.copy(forwardingEnabled = false) }
                return ok
            }
        }

        assertTrue(forwarder(transport).retryPending())

        assertEquals(1, transport.posts)
        assertEquals(listOf(EventKind.MissedCall), stateBacking.value.pending.map { it.kind })
    }

    @Test
    fun `retry does not run for a boot that was never activated`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        stateBacking.updateData { it.copy(activatedBootCount = 6) }

        assertTrue(forwarder.retryPending())

        assertEquals(1, stateBacking.value.pending.size)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `every persisted state has counted exactly the calls below its watermark`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        val log = listOf(LoggedMissedCall(42, call()), LoggedMissedCall(43, call()))
        val sync = MissedCallSync({ CallLogSnapshot(43, log) }, stateStore, forwarder(ScriptedTransport(ok, badKey)))

        sync.sync()

        val settled = stateBacking.history.map { Triple(it.callLogWatermark, it.stats.missedCalls, it.stats.failed) }.distinct()
        assertEquals(listOf(Triple(null, 0L, 0L), Triple(41L, 0L, 0L), Triple(42L, 1L, 0L), Triple(43L, 1L, 1L)), settled)
    }

    @Test
    fun `every persisted state holds a retried message either in the queue or in the stats`() = runTest {
        val forwarder = forwarder(ScriptedTransport(HttpResult.Failure, ok))
        forwarder.forward(sms())

        forwarder.retryPending()

        val settled = stateBacking.history.map { it.pending.size to it.stats.delivered }.distinct()
        assertEquals(listOf(0 to 0L, 1 to 0L, 0 to 1L), settled)
    }

    @Test
    fun `sync without permission leaves the watermark untouched`() = runTest {
        MissedCallSync({ null }, stateStore, forwarder(ScriptedTransport())).sync()

        assertEquals(null, stateBacking.value.callLogWatermark)
    }

    @Test
    fun `paused forwarding still advances the watermark`() = runTest {
        settingsBacking.updateData { configured.copy(forwardingEnabled = false) }
        stateBacking.updateData { it.copy(callLogWatermark = 1) }
        val transport = ScriptedTransport()

        MissedCallSync({ CallLogSnapshot(5, listOf(LoggedMissedCall(5, call()))) }, stateStore, forwarder(transport)).sync()

        assertEquals(5L, stateBacking.value.callLogWatermark)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `pending queue deduplicates identical events and caps at max size`() = runTest {
        val total = Forwarder.MAX_PENDING + 10
        val transport = ScriptedTransport(*Array(total + 1) { HttpResult.Failure })
        val forwarder = forwarder(transport)

        forwarder.forward(sms(receivedAt = 1_000L))
        forwarder.forward(sms(receivedAt = 1_000L))
        assertEquals(1, stateBacking.value.pending.size)

        for (i in 1 until total) {
            forwarder.forward(sms(receivedAt = 1_000L + i).copy(content = "msg-$i"))
        }

        assertEquals(Forwarder.MAX_PENDING, stateBacking.value.pending.size)
        assertTrue(stateBacking.value.pending.last().body.contains("msg-${total - 1}"))
    }

    @Test
    fun `monotonic elapsed clock isolates latency from wall clock jumps`() = runTest {
        var monotonic = 50_000L
        clock = 10_000L
        val forwarder = Forwarder(
            settingsStore = settingsStore,
            stateStore = stateStore,
            bark = BarkClient(ScriptedTransport(ok)),
            bootCount = { 7 },
            now = { clock },
            elapsedMs = { monotonic },
            zone = { ZoneOffset.UTC },
            language = { language },
            scheduleRetry = { retriesScheduled++ },
            newSecret = { "secret" },
        )

        monotonic = 50_320L
        clock = 5_000L
        forwarder.forward(sms(receivedAt = 10_000L), receivedElapsedMs = 50_000L)

        assertEquals(320L, stats.averageLatencyMs)
    }

    @Test
    fun `zero timeout queues immediately without issuing a network request`() = runTest {
        val transport = ScriptedTransport(ok)

        forwarder(transport).forward(sms(), timeout = Duration.ZERO)

        assertTrue(transport.requests.isEmpty())
        assertEquals(1, stateBacking.value.pending.size)
        assertEquals(1, retriesScheduled)
    }

    @Test
    fun `activate skips redundant persistence when boot is already active`() = runTest {
        val beforeWrites = stateBacking.history.size

        forwarder(ScriptedTransport()).activate()

        assertEquals(beforeWrites, stateBacking.history.size)
    }

    @Test
    fun `advanceWatermark updates watermark without forwarding missed calls`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 10) }
        val transport = ScriptedTransport()
        val sync = MissedCallSync(
            { after ->
                assertEquals(null, after)
                CallLogSnapshot(42, emptyList())
            },
            stateStore,
            forwarder(transport),
        )

        sync.advanceWatermark()

        assertEquals(42L, stateBacking.value.callLogWatermark)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `retryPending drains messages enqueued during an active retry pass`() = runTest {
        val failing = ScriptedTransport(HttpResult.Failure)
        val initial = forwarder(failing)
        initial.forward(sms(receivedAt = 1_000L))

        var injected = false
        lateinit var retryForwarder: Forwarder
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String): HttpResult {
                posts++
                if (!injected) {
                    injected = true
                    stateBacking.updateData {
                        it.copy(
                            pending = it.pending + sms(receivedAt = 2_000L).copy(content = "late")
                                .toMessage(TemplateChoice.Default, ZoneOffset.UTC, Language.Zh, it.deliverySecret!!),
                        )
                    }
                }
                return ok
            }
        }
        retryForwarder = forwarder(transport)

        assertTrue(retryForwarder.retryPending())
        assertEquals(2, transport.posts)
        assertTrue(stateBacking.value.pending.isEmpty())
        assertEquals(2L, stats.delivered)
    }
}
