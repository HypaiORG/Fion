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
            "a 1 {联系人} 1 {from} [] {}",
            renderTemplate("a {发件人} {联系人} { 发件人 } {from} [{正文}] {}", values),
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
        val smsTemplate = TemplateChoice.Custom(Template("S {未定义}", "{正文}|{来电次数}"))
        val callTemplate = TemplateChoice.Custom(Template("C", "{正文}|{来电次数}"))

        val sms = ForwardEvent.Sms("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, "hi").toMessage(smsTemplate, tokyo, Language.Zh, "k")
        val call = ForwardEvent.MissedCall("1", SimIdentity(SimKey.subscription(1), 0, "", "", ""), at, at, 2).toMessage(callTemplate, tokyo, Language.Zh, "k")

        assertEquals("S {未定义}", sms.title)
        assertEquals("C", call.title)
        assertEquals("hi|", sms.body)
        assertEquals("|2", call.body)
    }

    @Test
    fun `scanner preserves malformed and nested braces while substituting innermost valid tokens`() {
        val values = mapOf(TemplateVariable.From to "1")

        assertEquals("{a 1}", renderTemplate("{a {发件人}}", values))
        assertEquals("{1}", renderTemplate("{{发件人}}", values))
        assertEquals("{unclosed", renderTemplate("{unclosed", values))
        assertEquals("1}end", renderTemplate("{发件人}}end", values))
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
    fun `variables resolve by either language name`() {
        val values = mapOf(TemplateVariable.From to "1", TemplateVariable.Number to "2")

        assertEquals("1 1 2 2", renderTemplate("{sender} {发件人} { simNumber } {本机号码}", values))
        assertEquals("{Sender}", renderTemplate("{Sender}", values))
    }

    @Test
    fun `an unknown sim keeps one identity in every language`() {
        val call = ForwardEvent.MissedCall("+818000000000", SimIdentity.resolve(emptyMap(), null, null), at, at, 1)

        val messages = Language.entries.map { call.toMessage(TemplateChoice.Default, tokyo, it, "k") }

        assertEquals(setOf(SimKey.Unknown to SimTag()), messages.map { it.simKey to it.sim }.toSet())
        assertEquals(1, messages.map { it.id }.toSet().size)
    }
}
