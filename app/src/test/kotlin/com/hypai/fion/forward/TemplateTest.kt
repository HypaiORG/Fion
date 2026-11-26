package com.hypai.fion.forward

import com.hypai.fion.store.SimTag
import com.hypai.fion.store.TemplateChoice
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val at = ZonedDateTime.of(2026, 11, 15, 14, 32, 8, 0, tokyo).toInstant().toEpochMilli()

    @Test
    fun `variables have unique readable domain ids bound to permission and kinds`() {
        val ids = TemplateVariable.entries.map(TemplateVariable::id)
        assertEquals(TemplateVariable.entries.size, ids.toSet().size)
        assertTrue(ids.all { it.matches(Regex("""^(sms|call|sim)\.[a-z]+$""")) })
        for (variable in TemplateVariable.entries) {
            assertEquals(variable, TemplateVariable.fromId(variable.id))
            assertEquals("\"${variable.id}\"", Json.encodeToString(TemplateVariable.serializer(), variable))
        }

        assertEquals(
            setOf(
                TemplateVariable.SmsFrom,
                TemplateVariable.SmsContent,
                TemplateVariable.SmsTime,
                TemplateVariable.SimCarrier,
                TemplateVariable.SimName,
                TemplateVariable.SimNumber,
            ),
            EventKind.Sms.templateVariables().toSet(),
        )
        assertEquals(
            setOf(
                TemplateVariable.CallFrom,
                TemplateVariable.CallCount,
                TemplateVariable.CallTime,
                TemplateVariable.SimCarrier,
                TemplateVariable.SimName,
                TemplateVariable.SimNumber,
            ),
            EventKind.MissedCall.templateVariables().toSet(),
        )

        assertEquals(PermissionGroup.Sms, TemplateVariable.SmsFrom.permission)
        assertEquals(PermissionGroup.Sms, TemplateVariable.SmsContent.permission)
        assertEquals(PermissionGroup.Sms, TemplateVariable.SmsTime.permission)
        assertEquals(PermissionGroup.CallLog, TemplateVariable.CallFrom.permission)
        assertEquals(PermissionGroup.CallLog, TemplateVariable.CallCount.permission)
        assertEquals(PermissionGroup.CallLog, TemplateVariable.CallTime.permission)
        assertEquals(PermissionGroup.Phone, TemplateVariable.SimCarrier.permission)
        assertEquals(PermissionGroup.Phone, TemplateVariable.SimName.permission)
        assertEquals(PermissionGroup.Phone, TemplateVariable.SimNumber.permission)
    }

    @Test
    fun `structured rendering preserves literal braces and resolves variable nodes`() {
        val parts = listOf(
            TemplatePart.Text("a "),
            TemplatePart.Var(TemplateVariable.SmsFrom),
            TemplatePart.Text(" {sms.from} {time} ["),
            TemplatePart.Var(TemplateVariable.SmsContent),
            TemplatePart.Text("] {}"),
        )
        val event = ForwardEvent.Sms("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "")

        assertEquals("a 1 {sms.from} {time} [] {}", renderParts(parts, event, tokyo, Language.Zh))
    }

    @Test
    fun `sms event renders default template as plain text`() {
        val content = "您的临时密码为 <AbC123>\n**1** --- `x`"
        val event = ForwardEvent.Sms("+818000000000", SimIdentity(SimKey.subscription(2), 1, "中国联通", "SH", "+8615500000000"), at, at, content)

        val message = event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")

        assertEquals("短信 · +818000000000", message.title)
        assertEquals(
            "您的临时密码为 <AbC123>\n**1** --- `x`\n────────\n到达时间：2026-11-15 14:32\n接收号码：SH · +8615500000000",
            message.body,
        )
        assertEquals(EventKind.Sms, message.kind)
    }

    @Test
    fun `missed call renders default template`() {
        val event = ForwardEvent.MissedCall("+819000000000", SimIdentity(SimKey.subscription(2), 1, "Rakuten", "副卡", "+817000000000"), at, at, 3)

        val message = event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")

        assertEquals("未接来电 · +819000000000", message.title)
        assertEquals("2026-11-15 14:32 呼叫 +817000000000，今日第 3 次未接", message.body)
    }

    @Test
    fun `each kind renders its own variables and blanks variables of the other kind`() {
        val mixedBody = listOf(
            TemplatePart.Var(TemplateVariable.SmsContent),
            TemplatePart.Text("|"),
            TemplatePart.Var(TemplateVariable.CallCount),
        )
        val smsTemplate = TemplateChoice.Custom(Template(listOf(TemplatePart.Text("S {unknown}")), mixedBody))
        val callTemplate = TemplateChoice.Custom(Template(listOf(TemplatePart.Text("C")), mixedBody))

        val sms = ForwardEvent.Sms("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "hi").toMessage(smsTemplate, tokyo, Language.Zh, "k")
        val call = ForwardEvent.MissedCall("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, 2).toMessage(callTemplate, tokyo, Language.Zh, "k")

        assertEquals("S {unknown}", sms.title)
        assertEquals("C", call.title)
        assertEquals("hi|", sms.body)
        assertEquals("|2", call.body)
    }

    @Test
    fun `normalizeParts merges adjacent text and strips empty segments`() {
        val raw = listOf(
            TemplatePart.Text(""),
            TemplatePart.Text("a"),
            TemplatePart.Text("b"),
            TemplatePart.Var(TemplateVariable.SmsFrom),
            TemplatePart.Text(""),
            TemplatePart.Text("c"),
        )

        assertEquals(
            listOf(
                TemplatePart.Text("ab"),
                TemplatePart.Var(TemplateVariable.SmsFrom),
                TemplatePart.Text("c"),
            ),
            normalizeParts(raw),
        )
        assertEquals(emptyList<TemplatePart>(), normalizeParts(listOf(TemplatePart.Text(""))))
    }

    @Test
    fun `template required permissions filter by event kind and stay consistent across languages`() {
        for (kind in EventKind.entries) {
            val zhPerms = kind.defaultTemplate(Language.Zh).requiredPermissions(kind)
            val enPerms = kind.defaultTemplate(Language.En).requiredPermissions(kind)
            assertEquals(zhPerms, enPerms)
            assertTrue(PermissionGroup.Phone in zhPerms)
        }

        val crossKind = Template(
            title = listOf(TemplatePart.Var(TemplateVariable.CallCount)),
            body = listOf(TemplatePart.Var(TemplateVariable.SmsContent)),
        )
        assertEquals(setOf(PermissionGroup.Sms), crossKind.requiredPermissions(EventKind.Sms))
        assertEquals(setOf(PermissionGroup.CallLog), crossKind.requiredPermissions(EventKind.MissedCall))
    }

    @Test
    fun `template json serializes readable domain ids`() {
        val template = Template(
            title = listOf(TemplatePart.Text("T "), TemplatePart.Var(TemplateVariable.SmsFrom)),
            body = listOf(TemplatePart.Var(TemplateVariable.SimNumber)),
        )
        val encoded = Json.encodeToString(Template.serializer(), template)

        assertTrue(encoded.contains("\"sms.from\""))
        assertTrue(encoded.contains("\"sim.number\""))
        assertEquals(template, Json.decodeFromString(Template.serializer(), encoded))
    }

    @Test
    fun `the same event always maps to the same delivery id whatever the language`() {
        val event = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "hello")
        val first = event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")

        assertEquals(first.id, event.copy(receivedAtMs = at + 5_000).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertEquals(32, first.id.length)
        assertFalse(first.id == event.copy(content = "hello!").toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertEquals(first.id, event.copy(sim = event.sim.copy(carrier = "China Unicom")).toMessage(TemplateChoice.Default, tokyo, Language.En, "k").id)
        assertFalse(first.id == event.copy(sim = event.sim.copy(key = SimKey.subscription(2))).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertFalse(first.id == event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "another install").id)
    }

    @Test
    fun `hidden number is labelled and sim fields are exposed`() {
        val known = ForwardEvent.MissedCall("+819000000000", SimIdentity(SimKey.subscription(1), 0, "docomo", "SH", "+817000000000"), at, at, 2)
        val hidden = known.copy(from = "", sim = known.sim.copy(carrier = "", name = "", number = ""))

        assertEquals("+819000000000", TemplateVariable.CallFrom.resolve(known, tokyo, Language.Zh))
        assertEquals("+817000000000", TemplateVariable.SimNumber.resolve(known, tokyo, Language.Zh))
        assertEquals("docomo", TemplateVariable.SimCarrier.resolve(known, tokyo, Language.Zh))
        assertEquals("SH", TemplateVariable.SimName.resolve(known, tokyo, Language.Zh))
        assertEquals("未知号码", TemplateVariable.CallFrom.resolve(hidden, tokyo, Language.Zh))
        assertEquals("", TemplateVariable.SimNumber.resolve(hidden, tokyo, Language.Zh))
        assertEquals("", TemplateVariable.SimCarrier.resolve(hidden, tokyo, Language.Zh))
        assertEquals("", TemplateVariable.SimName.resolve(hidden, tokyo, Language.Zh))
    }

    @Test
    fun `english defaults render in english`() {
        val sms = ForwardEvent.Sms("+818000000000", SimIdentity(SimKey.subscription(1), 0, "docomo", "Main", "+817000000000"), at, at, "hi")
        val call = ForwardEvent.MissedCall("", SimIdentity(SimKey.subscription(1), 0, "docomo", "Main", "+817000000000"), at, at, 2)

        val smsMessage = sms.toMessage(TemplateChoice.Default, tokyo, Language.En, "k")
        val callMessage = call.toMessage(TemplateChoice.Default, tokyo, Language.En, "k")

        assertEquals("SMS · +818000000000", smsMessage.title)
        assertEquals("hi\n────────\nReceived: 2026-11-15 14:32\nTo: Main · +817000000000", smsMessage.body)
        assertEquals("Missed call · Unknown number", callMessage.title)
        assertEquals("Called +817000000000 at 2026-11-15 14:32, call #2 today", callMessage.body)
    }

    @Test
    fun `an unknown sim keeps one identity in every language`() {
        val call = ForwardEvent.MissedCall("+818000000000", SimIdentity.resolve(emptyMap(), null, null), at, at, 1)

        val messages = Language.entries.map { call.toMessage(TemplateChoice.Default, tokyo, it, "k") }

        assertEquals(setOf(SimKey.Unknown to SimTag()), messages.map { it.simKey to it.sim }.toSet())
        assertEquals(1, messages.map { it.id }.toSet().size)
    }

    @Test
    fun `english display names are capitalized and zh names are non blank`() {
        for (variable in TemplateVariable.entries) {
            val en = variable.displayName(Language.En)
            assertTrue(en.first().isUpperCase())
            assertTrue(variable.displayName(Language.Zh).isNotBlank())
        }
        assertEquals("Sender", TemplateVariable.SmsFrom.displayName(Language.En))
        assertEquals("Caller", TemplateVariable.CallFrom.displayName(Language.En))
        assertEquals("对方号码", TemplateVariable.SmsFrom.displayName(Language.Zh))
        assertEquals("对方号码", TemplateVariable.CallFrom.displayName(Language.Zh))
    }

    @Test
    fun `default templates collapse missing sim fields cleanly without dangling separators`() {
        val nameOnly = SimIdentity(SimKey.subscription(1), 0, "中国移动", "主卡", "")
        val carrierOnly = SimIdentity(SimKey.subscription(1), 0, "docomo", "", "")
        val emptySim = SimIdentity(SimKey.Unknown, null, "", "", "")

        val smsNameOnly = ForwardEvent.Sms("10086", nameOnly, at, at, "hi").toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")
        assertEquals("hi\n────────\n到达时间：2026-11-15 14:32\n接收号码：主卡", smsNameOnly.body)

        val smsEmpty = ForwardEvent.Sms("10086", emptySim, at, at, "hi").toMessage(TemplateChoice.Default, tokyo, Language.En, "k")
        assertEquals("hi\n────────\nReceived: 2026-11-15 14:32", smsEmpty.body)

        val callNameOnly = ForwardEvent.MissedCall("10086", nameOnly, at, at, 2).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")
        assertEquals("2026-11-15 14:32 呼叫 主卡，今日第 2 次未接", callNameOnly.body)

        val callCarrierOnly = ForwardEvent.MissedCall("10086", carrierOnly, at, at, 1).toMessage(TemplateChoice.Default, tokyo, Language.En, "k")
        assertEquals("Called docomo at 2026-11-15 14:32, call #1 today", callCarrierOnly.body)

        val callEmptyZh = ForwardEvent.MissedCall("10086", emptySim, at, at, 3).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")
        assertEquals("2026-11-15 14:32 未接来电，今日第 3 次未接", callEmptyZh.body)

        val callEmptyEn = ForwardEvent.MissedCall("10086", emptySim, at, at, 3).toMessage(TemplateChoice.Default, tokyo, Language.En, "k")
        assertEquals("Missed at 2026-11-15 14:32, call #3 today", callEmptyEn.body)
    }

    @Test
    fun `template json skips unknown future variables instead of failing deserialization`() {
        val raw = """{"title":[{"type":"text","value":"A "},{"type":"var","id":"future.field"},{"type":"var","id":"sms.from"}],"body":[{"type":"var","id":"sms.content"},{"type":"unknown","foo":1}]}"""
        val decoded = Json.decodeFromString(Template.serializer(), raw)

        assertEquals(
            Template(
                title = listOf(TemplatePart.Text("A "), TemplatePart.Var(TemplateVariable.SmsFrom)),
                body = listOf(TemplatePart.Var(TemplateVariable.SmsContent)),
            ),
            decoded,
        )
    }
}
