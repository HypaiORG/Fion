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
    private val outbox = MemoryOutbox()
    private val settingsStore = SettingsStore(settingsBacking)
    private val stateStore = ForwardStateStore(stateBacking)
    private val stats get() = stateBacking.value.stats
    private var clock = 1_000L
    private var retriesScheduled = 0
    private var retriesCancelled = 0
    private var secretsCreated = 0
    private var networkAvailable = true
    private var language = Language.Zh

    private fun forwarder(transport: HttpTransport, newSecret: () -> String = { "secret-${secretsCreated++}" }) = Forwarder(
        settingsStore = settingsStore,
        stateStore = stateStore,
        outbox = outbox,
        bark = BarkClient(transport),
        bootCount = { 7 },
        now = { clock },
        zone = { ZoneOffset.UTC },
        language = { language },
        scheduleRetry = { retriesScheduled++ },
        cancelRetry = { retriesCancelled++ },
        newSecret = newSecret,
        isNetworkAvailable = { networkAvailable },
    )

    private fun sms(receivedAt: Long = 1_000L) = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "A", "主卡", ""), receivedAt, receivedAt, "hi")
    private fun call(receivedAt: Long = 1_000L) = ForwardEvent.MissedCall("10010", SimIdentity(SimKey.subscription(2), 1, "B", "副卡", ""), receivedAt, receivedAt, 1)

    private suspend fun Forwarder.forward(
        event: ForwardEvent,
        timeout: Duration = Forwarder.PUSH_TIMEOUT,
        receivedElapsedMs: Long? = null,
        progress: ((ForwardState) -> ForwardState)? = null,
    ) = forwardAll(listOf(event), timeout, receivedElapsedMs, progress)

    @Test
    fun `delivered sms is counted with latency and sim`() = runTest {
        val transport = ScriptedTransport(ok)
        clock = 1_214L

        forwarder(transport).forward(sms())

        assertEquals(Stats(sms = 1, latencyTotalMs = 214, latencySamples = 1, sims = mapOf(SimKey.subscription(1) to SimTally(SimTag(0, "主卡", "A"), 1))), stats)
        assertEquals("https://api.day.app/push", transport.requests.single().first)
        assertTrue(outbox.items.isEmpty())
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
        assertTrue(outbox.items.isEmpty())
        assertEquals("secret", stateBacking.value.deliverySecret)
        assertEquals(Stats(), stats)
    }

    @Test
    fun `write ahead outbox preserves message if forward is cancelled mid network call`() = runTest {
        val interrupted = object : HttpTransport {
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                throw kotlinx.coroutines.CancellationException("process killed mid-flight")
            }
        }
        runCatching { forwarder(interrupted).forward(sms()) }

        assertEquals(1, outbox.items.size)
        assertEquals(Stats(), stats)

        assertTrue(forwarder(ScriptedTransport(ok)).retryPending())
        assertTrue(outbox.items.isEmpty())
        assertEquals(1L, stats.delivered)
    }

    @Test
    fun `rejected push counts as failure without retry`() = runTest {
        forwarder(ScriptedTransport(badKey)).forward(call())

        assertEquals(Stats(failed = 1), stats)
        assertTrue(outbox.items.isEmpty())
        assertEquals(0, retriesScheduled)
    }

    @Test
    fun `unreachable push remains queued and schedules retry`() = runTest {
        forwarder(ScriptedTransport(HttpResult.Failure)).forward(sms())

        assertEquals(Stats(), stats)
        assertEquals(1, outbox.items.size)
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
        assertTrue(outbox.items.isEmpty())
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
    fun `retryPending delivers strictly in arrival order and stops at first network failure`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, HttpResult.Failure, ok, HttpResult.Failure, ok)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        forwarder.forward(call())

        assertFalse(forwarder.retryPending())
        assertEquals(listOf(EventKind.MissedCall), outbox.items.map { it.kind })
        assertTrue(forwarder.retryPending())

        assertTrue(outbox.items.isEmpty())
        assertEquals(1, retriesCancelled)
        assertEquals(
            Stats(sms = 1, missedCalls = 1, sims = mapOf(SimKey.subscription(1) to SimTally(SimTag(0, "主卡", "A"), 1), SimKey.subscription(2) to SimTally(SimTag(1, "副卡", "B"), 1))),
            stats,
        )
    }

    @Test
    fun `queued backlog is delivered in strict FIFO order`() = runTest {
        val enqueuer = forwarder(ScriptedTransport())
        repeat(5) { idx -> enqueuer.forward(sms(receivedAt = 1_000L + idx).copy(content = "seq-$idx"), timeout = Duration.ZERO) }

        val drainTransport = ScriptedTransport(*Array(5) { ok })
        assertTrue(forwarder(drainTransport).retryPending())

        val bodies = drainTransport.requests.map { Json.parseToJsonElement(it.second).jsonObject.getValue("body").jsonPrimitive.content }
        assertEquals(5, bodies.size)
        for (idx in 0 until 5) assertTrue(bodies[idx].contains("seq-$idx"))
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
        val sync = MissedCallSync({ _, _ -> CallLogSnapshot(41, listOf(call())) }, stateStore, forwarder(transport))

        sync.sync()

        assertEquals(41L, stateBacking.value.callLogWatermark)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `later sync forwards new missed calls and advances the watermark`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        val asked = mutableListOf<Long?>()
        val requestedVars = mutableListOf<Set<TemplateVariable>>()
        val sync = MissedCallSync(
            { after, vars ->
                asked += after
                requestedVars += vars
                CallLogSnapshot(43, listOf(call(receivedAt = 2_000L), call(receivedAt = 3_000L)))
            },
            stateStore,
            forwarder(ScriptedTransport(ok, ok)),
        )

        sync.sync()

        assertEquals(listOf<Long?>(41L), asked)
        assertEquals(
            listOf(setOf(TemplateVariable.CallFrom, TemplateVariable.CallTime, TemplateVariable.SimNumber, TemplateVariable.CallCount)),
            requestedVars,
        )
        assertEquals(43L, stateBacking.value.callLogWatermark)
        assertEquals(2, stats.missedCalls)
    }

    @Test
    fun `sync passes only template used variables to call log reader`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        settingsBacking.updateData {
            it.withPush(
                EventKind.MissedCall,
                it.callPush.copy(
                    template = TemplateChoice.Custom(
                        Template(listOf(TemplatePart.Var(TemplateVariable.CallFrom)), listOf(TemplatePart.Var(TemplateVariable.CallTime))),
                    ),
                ),
            )
        }
        var captured: Set<TemplateVariable>? = null
        val sync = MissedCallSync(
            { _, vars ->
                captured = vars
                CallLogSnapshot(41, emptyList())
            },
            stateStore,
            forwarder(ScriptedTransport()),
        )

        sync.sync()

        assertEquals(setOf(TemplateVariable.CallFrom, TemplateVariable.CallTime), captured)
    }

    @Test
    fun `interrupted sync persists calls to outbox before draining without losing or duplicating`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 41) }
        val log = listOf(42L to call(receivedAt = 2_000L), 43L to call(receivedAt = 3_000L))
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                posts++
                if (posts == 2) throw kotlinx.coroutines.CancellationException("job stopped")
                return ok
            }
        }
        val fwd = forwarder(transport)
        val sync = MissedCallSync(
            { after, _ -> CallLogSnapshot(43, log.filter { it.first > (after ?: 0) }.map { it.second }) },
            stateStore,
            fwd,
        )

        runCatching { sync.sync() }
        assertEquals(43L, stateBacking.value.callLogWatermark)
        assertEquals(1, outbox.items.size)

        assertTrue(fwd.retryPending())
        assertEquals(3, transport.posts)
        assertEquals(2, stats.missedCalls)
        assertTrue(outbox.items.isEmpty())
    }

    @Test
    fun `retryPending holds queued messages while forwarding is paused and sends them after resume`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure, HttpResult.Failure, ok, ok)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        forwarder.forward(call())

        settingsBacking.updateData { configured.copy(forwardingEnabled = false) }
        assertTrue(forwarder.retryPending())
        settingsBacking.updateData { configured.withChannel(EventKind.Sms, false) }
        assertTrue(forwarder.retryPending())

        assertEquals(listOf(EventKind.Sms), outbox.items.map { it.kind })
        assertEquals(3, transport.requests.size)
        settingsBacking.updateData { configured }
        assertTrue(forwarder.retryPending())
        assertTrue(outbox.items.isEmpty())
        assertEquals(2, stats.delivered)
    }

    @Test
    fun `pausing forwarding during a retry pass keeps the remaining messages queued`() = runTest {
        val failing = ScriptedTransport(HttpResult.Failure, HttpResult.Failure)
        forwarder(failing).forward(sms())
        forwarder(failing).forward(call())
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                posts++
                settingsBacking.updateData { it.copy(forwardingEnabled = false) }
                return ok
            }
        }

        assertTrue(forwarder(transport).retryPending())

        assertEquals(1, transport.posts)
        assertEquals(listOf(EventKind.MissedCall), outbox.items.map { it.kind })
    }

    @Test
    fun `retryPending does not run for a boot that was never activated`() = runTest {
        val transport = ScriptedTransport(HttpResult.Failure)
        val forwarder = forwarder(transport)
        forwarder.forward(sms())
        stateBacking.updateData { it.copy(activatedBootCount = 6) }

        assertTrue(forwarder.retryPending())

        assertEquals(1, outbox.items.size)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `sync without permission leaves the watermark untouched`() = runTest {
        MissedCallSync({ _, _ -> null }, stateStore, forwarder(ScriptedTransport())).sync()

        assertEquals(null, stateBacking.value.callLogWatermark)
    }

    @Test
    fun `paused forwarding still advances the watermark`() = runTest {
        settingsBacking.updateData { configured.copy(forwardingEnabled = false) }
        stateBacking.updateData { it.copy(callLogWatermark = 1) }
        val transport = ScriptedTransport()

        MissedCallSync({ _, _ -> CallLogSnapshot(5, listOf(call())) }, stateStore, forwarder(transport)).sync()

        assertEquals(5L, stateBacking.value.callLogWatermark)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `outbox deduplicates identical events and caps at max size`() = runTest {
        val total = Forwarder.MAX_PENDING + 10
        val forwarder = forwarder(ScriptedTransport())

        forwarder.forward(sms(receivedAt = 1_000L), timeout = Duration.ZERO)
        forwarder.forward(sms(receivedAt = 1_000L), timeout = Duration.ZERO)
        assertEquals(1, outbox.items.size)

        for (i in 1 until total) {
            forwarder.forward(sms(receivedAt = 1_000L + i).copy(content = "msg-$i"), timeout = Duration.ZERO)
        }

        assertEquals(Forwarder.MAX_PENDING, outbox.items.size)
        assertTrue(outbox.items.last().body.contains("msg-${total - 1}"))
    }

    @Test
    fun `messages evicted by the outbox cap count as failed`() = runTest {
        val forwarder = forwarder(ScriptedTransport())

        for (i in 0 until Forwarder.MAX_PENDING + 3) {
            forwarder.forward(sms(receivedAt = 1_000L + i).copy(content = "msg-$i"), timeout = Duration.ZERO)
        }

        assertEquals(3, stateBacking.value.stats.failed)
    }

    @Test
    fun `message evicted while in flight is counted once`() = runTest {
        val enqueuer = forwarder(ScriptedTransport())
        repeat(Forwarder.MAX_PENDING) { i -> enqueuer.forward(sms(receivedAt = 1_000L + i).copy(content = "q-$i"), timeout = Duration.ZERO) }
        var evicting = true
        val racing = object : HttpTransport {
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                if (evicting) {
                    evicting = false
                    enqueuer.forward(sms(receivedAt = 9_000L).copy(content = "late"), timeout = Duration.ZERO)
                }
                return ok
            }
        }

        forwarder(racing).retryPending()

        assertEquals(Forwarder.MAX_PENDING + 1L, stats.delivered + stats.failed)
    }

    @Test
    fun `draining a backlog commits its stats in one write`() = runTest {
        val enqueuer = forwarder(ScriptedTransport())
        repeat(5) { i -> enqueuer.forward(sms(receivedAt = 1_000L + i).copy(content = "b-$i"), timeout = Duration.ZERO) }
        val writesBefore = stateBacking.history.size

        assertTrue(forwarder(ScriptedTransport(ok, ok, ok, badKey, ok)).retryPending())

        assertEquals(writesBefore + 1, stateBacking.history.size)
        assertEquals(4L, stats.delivered)
        assertEquals(1L, stats.failed)
    }

    @Test
    fun `monotonic elapsed clock isolates latency from wall clock jumps`() = runTest {
        var monotonic = 50_000L
        clock = 10_000L
        val forwarder = Forwarder(
            settingsStore = settingsStore,
            stateStore = stateStore,
            outbox = outbox,
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
        assertEquals(1, outbox.items.size)
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
            { after, vars ->
                assertEquals(null, after)
                assertEquals(emptySet<TemplateVariable>(), vars)
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
    fun `retryPending processes messages enqueued during an active pass`() = runTest {
        forwarder(ScriptedTransport()).forward(sms(receivedAt = 1_000L), timeout = Duration.ZERO)

        var injected = false
        val transport = object : HttpTransport {
            var posts = 0
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                posts++
                if (!injected) {
                    injected = true
                    outbox.enqueue(
                        listOf(
                            sms(receivedAt = 2_000L).copy(content = "late")
                                .toMessage(TemplateChoice.Default, ZoneOffset.UTC, Language.Zh, stateBacking.value.deliverySecret!!),
                        ),
                        Forwarder.MAX_PENDING,
                    )
                }
                return ok
            }
        }

        assertTrue(forwarder(transport).retryPending())
        assertEquals(2, transport.posts)
        assertTrue(outbox.items.isEmpty())
        assertEquals(2L, stats.delivered)
    }

    @Test
    fun `live forward automatically drains previously queued backlog in one pass`() = runTest {
        forwarder(ScriptedTransport()).forward(sms(receivedAt = 1_000L).copy(content = "old"), timeout = Duration.ZERO)

        val transport = ScriptedTransport(ok, ok)
        forwarder(transport).forward(sms(receivedAt = 2_000L).copy(content = "new"))

        assertEquals(2, transport.requests.size)
        assertTrue(outbox.items.isEmpty())
        assertEquals(2L, stats.delivered)
        assertEquals(1L, stats.latencySamples)
    }

    @Test
    fun `a single poison 500 message is skipped in pass without blocking subsequent healthy messages`() = runTest {
        val enqueuer = forwarder(ScriptedTransport())
        enqueuer.forward(sms(receivedAt = 1_000L).copy(content = "poison"), timeout = Duration.ZERO)
        enqueuer.forward(sms(receivedAt = 2_000L).copy(content = "healthy"), timeout = Duration.ZERO)

        val transport = object : HttpTransport {
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult =
                if ("poison" in body) HttpResult.Response(500, """{"code":500,"message":"internal"}""") else ok
        }
        assertFalse(forwarder(transport).retryPending())

        assertEquals(1L, stats.delivered)
        assertEquals(1, outbox.items.size)
        assertTrue(outbox.items.single().body.contains("poison"))
    }

    @Test
    fun `consecutive 500 server failures abort pass for native backoff`() = runTest {
        val enqueuer = forwarder(ScriptedTransport())
        repeat(3) { i -> enqueuer.forward(sms(receivedAt = 1_000L + i).copy(content = "m-$i"), timeout = Duration.ZERO) }

        var posts = 0
        val transport = object : HttpTransport {
            override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
                posts++
                return HttpResult.Response(500, """{"code":500}""")
            }
        }

        assertFalse(forwarder(transport).retryPending())
        assertEquals(2, posts)
        assertEquals(3, outbox.items.size)
    }

    @Test
    fun `offline precheck enqueues immediately without invoking network`() = runTest {
        networkAvailable = false
        val transport = ScriptedTransport(ok)

        forwarder(transport).forward(sms())

        assertTrue(transport.requests.isEmpty())
        assertEquals(1, outbox.items.size)
        assertEquals(1, retriesScheduled)
    }

    @Test
    fun `forward propagates timeout budget directly to transport`() = runTest {
        val transport = ScriptedTransport(ok)

        forwarder(transport).forward(sms(), timeout = Forwarder.SMS_DIRECT_TIMEOUT)

        assertEquals(listOf(5_000L), transport.timeouts)
    }

    @Test
    fun `call log watermark resets downward when system call log ids drop`() = runTest {
        stateBacking.updateData { it.copy(callLogWatermark = 500) }
        val sync = MissedCallSync({ _, _ -> CallLogSnapshot(0, emptyList()) }, stateStore, forwarder(ScriptedTransport()))

        sync.sync()

        assertEquals(0L, stateBacking.value.callLogWatermark)

        stateBacking.updateData { it.copy(callLogWatermark = 100) }
        val resetSync = MissedCallSync({ _, _ -> CallLogSnapshot(12, emptyList()) }, stateStore, forwarder(ScriptedTransport()))
        resetSync.advanceWatermark()
        assertEquals(12L, stateBacking.value.callLogWatermark)
    }
}
