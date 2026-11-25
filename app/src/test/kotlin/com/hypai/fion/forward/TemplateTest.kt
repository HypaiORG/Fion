package com.hypai.fion.forward

import com.hypai.fion.store.SimTag
import com.hypai.fion.store.TemplateChoice
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val at = ZonedDateTime.of(2026, 11, 15, 14, 32, 8, 0, tokyo).toInstant().toEpochMilli()

    @Test
    fun `replaces known variables and keeps unknown ones`() {
        val values = mapOf(TemplateVariable.From to "1", TemplateVariable.Content to "")

        assertEquals(
            "a 1 {contact} 1 {from} [] {}",
            renderTemplate("a {sender} {contact} { sender } {from} [{message}] {}", values),
        )
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
    fun `each kind renders its own template and blanks variables of the other kind`() {
        val smsTemplate = TemplateChoice.Custom(Template("S {unknown}", "{message}|{callCount}"))
        val callTemplate = TemplateChoice.Custom(Template("C", "{message}|{callCount}"))

        val sms = ForwardEvent.Sms("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "hi").toMessage(smsTemplate, tokyo, Language.Zh, "k")
        val call = ForwardEvent.MissedCall("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, 2).toMessage(callTemplate, tokyo, Language.Zh, "k")

        assertEquals("S {unknown}", sms.title)
        assertEquals("C", call.title)
        assertEquals("hi|", sms.body)
        assertEquals("|2", call.body)
    }

    @Test
    fun `scanner preserves malformed and nested braces while substituting innermost valid tokens`() {
        val values = mapOf(TemplateVariable.From to "1")

        assertEquals("{a 1}", renderTemplate("{a {sender}}", values))
        assertEquals("{1}", renderTemplate("{{sender}}", values))
        assertEquals("{unclosed", renderTemplate("{unclosed", values))
        assertEquals("1}end", renderTemplate("{sender}}end", values))
    }

    @Test
    fun `the same event always maps to the same delivery id whatever the language`() {
        val event = ForwardEvent.Sms("10086", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "hello")
        val first = event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k")

        assertEquals(first.id, event.copy(receivedAtMs = at + 5_000).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertEquals(32, first.id.length)
        assertEquals(false, first.id == event.copy(content = "hello!").toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertEquals(first.id, event.copy(sim = event.sim.copy(carrier = "China Unicom")).toMessage(TemplateChoice.Default, tokyo, Language.En, "k").id)
        assertEquals(false, first.id == event.copy(sim = event.sim.copy(key = SimKey.subscription(2))).toMessage(TemplateChoice.Default, tokyo, Language.Zh, "k").id)
        assertEquals(false, first.id == event.toMessage(TemplateChoice.Default, tokyo, Language.Zh, "another install").id)
    }

    @Test
    fun `hidden number is labelled and sim fields are exposed`() {
        val known = ForwardEvent.MissedCall("+819000000000", SimIdentity(SimKey.subscription(1), 0, "docomo", "SH", "+817000000000"), at, at, 2)
        val hidden = known.copy(from = "", sim = known.sim.copy(carrier = "", name = "", number = ""))

        assertEquals("+819000000000", known.variables(tokyo, Language.Zh)[TemplateVariable.From])
        assertEquals("+817000000000", known.variables(tokyo, Language.Zh)[TemplateVariable.Number])
        assertEquals("docomo", known.variables(tokyo, Language.Zh)[TemplateVariable.Carrier])
        assertEquals("SH", known.variables(tokyo, Language.Zh)[TemplateVariable.SimName])
        assertEquals("未知号码", hidden.variables(tokyo, Language.Zh)[TemplateVariable.From])
        assertEquals("", hidden.variables(tokyo, Language.Zh)[TemplateVariable.Number])
        assertEquals("", hidden.variables(tokyo, Language.Zh)[TemplateVariable.Carrier])
        assertEquals("", hidden.variables(tokyo, Language.Zh)[TemplateVariable.SimName])
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
    fun `variables resolve strictly by canonical id`() {
        val values = mapOf(TemplateVariable.From to "1", TemplateVariable.Number to "2")

        assertEquals("1 2", renderTemplate("{sender} { simNumber }", values))
        assertEquals("{Sender} {发件人}", renderTemplate("{Sender} {发件人}", values))
    }

    @Test
    fun `an unknown sim keeps one identity in every language`() {
        val call = ForwardEvent.MissedCall("+818000000000", SimIdentity.resolve(emptyMap(), null, null), at, at, 1)

        val messages = Language.entries.map { call.toMessage(TemplateChoice.Default, tokyo, it, "k") }

        assertEquals(setOf(SimKey.Unknown to SimTag()), messages.map { it.simKey to it.sim }.toSet())
        assertEquals(1, messages.map { it.id }.toSet().size)
    }

    @Test
    fun `findTemplateTokens matches renderer boundaries and kind variables exclude inapplicable ones`() {
        val tokens = findTemplateTokens("a {sender} {unknown} {{time}}")

        assertEquals(
            listOf(
                TemplateToken(2, 10, TemplateVariable.From),
                TemplateToken(22, 28, TemplateVariable.Time),
            ),
            tokens,
        )
        assertEquals(false, TemplateVariable.CallCount in EventKind.Sms.templateVariables())
        assertEquals(true, TemplateVariable.Content in EventKind.Sms.templateVariables())
        assertEquals(false, TemplateVariable.Content in EventKind.MissedCall.templateVariables())
        assertEquals(true, TemplateVariable.CallCount in EventKind.MissedCall.templateVariables())
    }

    @Test
    fun `english display names are capitalized and from adapts to event kind`() {
        for (kind in EventKind.entries) {
            for (variable in kind.templateVariables()) {
                val en = variable.displayName(Language.En, kind)
                assertEquals(true, en.first().isUpperCase())
                val zh = variable.displayName(Language.Zh, kind)
                assertEquals(true, zh.isNotBlank())
            }
        }
        assertEquals("Sender", TemplateVariable.From.displayName(Language.En, EventKind.Sms))
        assertEquals("Caller", TemplateVariable.From.displayName(Language.En, EventKind.MissedCall))
        assertEquals("对方号码", TemplateVariable.From.displayName(Language.Zh, EventKind.Sms))
    }
}
