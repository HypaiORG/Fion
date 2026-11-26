package com.hypai.fion.store

import com.hypai.fion.bark.BarkLevel
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.Template
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.defaultTemplate
import com.hypai.fion.forward.groupChoice
import com.hypai.fion.forward.resolve
import com.hypai.fion.forward.templateChoice
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val all = EventKind.entries.toSet()
    private val smsOnly = setOf(EventKind.Sms)

    @Test
    fun `turning off every channel stops forwarding`() {
        val settings = Settings().withChannel(EventKind.Sms, false).withChannel(EventKind.MissedCall, false)

        assertFalse(settings.forwarding)
    }

    @Test
    fun `turning on a channel while paused starts forwarding`() {
        val settings = Settings(forwardingEnabled = false, channels = smsOnly).withChannel(EventKind.MissedCall, true)

        assertTrue(settings.forwarding)
        assertEquals(all, settings.channels)
    }

    @Test
    fun `stopping forwarding keeps the chosen channels`() {
        val stopped = Settings(channels = smsOnly).withForwarding(false)

        assertFalse(stopped.forwarding)
        assertEquals(smsOnly, stopped.channels)
    }

    @Test
    fun `starting forwarding keeps the chosen channels`() {
        val started = Settings(forwardingEnabled = false, channels = smsOnly).withForwarding(true)

        assertTrue(started.forwarding)
        assertEquals(smsOnly, started.channels)
    }

    @Test
    fun `starting forwarding with no channels enables every channel`() {
        val started = Settings(channels = emptySet()).withForwarding(true)

        assertTrue(started.forwarding)
        assertEquals(all, started.channels)
    }

    @Test
    fun `sms and missed calls default to their own push profiles`() {
        val settings = Settings()

        assertEquals("bell", settings.push(EventKind.Sms).sound)
        assertEquals("telegraph", settings.push(EventKind.MissedCall).sound)
        EventKind.entries.map(settings::push).forEach {
            assertEquals(BarkLevel.Active, it.level)
            assertTrue(it.archive)
        }
    }

    @Test
    fun `saving one push profile leaves the other untouched`() {
        val edited = Settings().push(EventKind.Sms).copy(sound = "glass", archive = false)

        val settings = Settings().withPush(EventKind.Sms, edited)

        assertEquals(edited, settings.push(EventKind.Sms))
        assertEquals(EventKind.MissedCall.defaultProfile, settings.push(EventKind.MissedCall))
    }

    @Test
    fun `untouched templates follow the current language`() {
        val settings = Settings()

        assertEquals(TemplateChoice.Default, settings.smsPush.template)
        assertEquals(EventKind.Sms.defaultTemplate(Language.Zh), settings.smsPush.template.resolve(EventKind.Sms, Language.Zh))
        assertEquals(EventKind.Sms.defaultTemplate(Language.En), settings.smsPush.template.resolve(EventKind.Sms, Language.En))
    }

    @Test
    fun `typing back any default template restores the default`() {
        val kind = EventKind.MissedCall
        val custom = Template(listOf(TemplatePart.Text("T")), listOf(TemplatePart.Text("B")))

        assertEquals(TemplateChoice.Default, kind.templateChoice(kind.defaultTemplate(Language.En)))
        assertEquals(TemplateChoice.Default, kind.templateChoice(kind.defaultTemplate(Language.Zh)))
        assertEquals(TemplateChoice.Custom(custom), kind.templateChoice(custom))
    }

    @Test
    fun `a custom template survives a language switch`() {
        val kind = EventKind.MissedCall
        val custom = Template(
            title = listOf(TemplatePart.Text("T "), TemplatePart.Var(TemplateVariable.CallFrom)),
            body = listOf(TemplatePart.Text("B")),
        )
        val choice = Settings().withPush(kind, kind.defaultProfile.copy(template = kind.templateChoice(custom))).push(kind).template

        assertEquals(listOf(custom, custom), Language.entries.map { choice.resolve(kind, it) })
    }

    @Test
    fun `an untouched group follows the current language`() {
        val group = Settings().smsPush.group

        assertEquals(listOf("短信", "SMS"), listOf(Language.Zh, Language.En).map { group.resolve(EventKind.Sms, it) })
    }

    @Test
    fun `a custom group survives a language switch`() {
        val kind = EventKind.Sms
        val group = Settings().withPush(kind, kind.defaultProfile.copy(group = kind.groupChoice("验证码", Language.Zh))).push(kind).group

        assertEquals(listOf("验证码", "验证码"), Language.entries.map { group.resolve(kind, it) })
    }

    @Test
    fun `typing reads back exactly what was typed in the current language`() {
        val kind = EventKind.Sms
        val typing = "SMS alerts".indices.map { "SMS alerts".substring(0, it + 1) } + listOf("短信", "", "Missed calls")

        Language.entries.forEach { language ->
            typing.forEach { text -> assertEquals(text, kind.groupChoice(text, language).resolve(kind, language)) }
        }
        assertEquals(GroupChoice.Default, kind.groupChoice("短信", Language.Zh))
        assertEquals(GroupChoice.Default, kind.groupChoice("SMS", Language.En))
        assertEquals(GroupChoice.Custom("SMS"), kind.groupChoice("SMS", Language.Zh))
    }

    @Test
    fun `editing a push profile leaves the forwarding switches alone`() {
        val paused = Settings(forwardingEnabled = false, channels = smsOnly)

        val edited = paused.withPush(EventKind.MissedCall, EventKind.MissedCall.defaultProfile.copy(sound = "glass"))

        assertFalse(edited.forwardingEnabled)
        assertEquals(smsOnly, edited.channels)
    }

    @Test
    fun `settings round trip through json and store only customized choices`() {
        val custom = Settings()
            .withPush(
                EventKind.Sms,
                EventKind.Sms.defaultProfile.copy(
                    template = TemplateChoice.Custom(
                        Template(listOf(TemplatePart.Text("T")), listOf(TemplatePart.Var(TemplateVariable.SmsContent))),
                    ),
                ),
            )
            .withPush(EventKind.MissedCall, EventKind.MissedCall.defaultProfile.copy(group = GroupChoice.Custom("")))

        assertEquals(custom, Json.decodeFromString(Settings.serializer(), Json.encodeToString(Settings.serializer(), custom)))
        val untouched = Json.encodeToString(Settings.serializer(), Settings())
        assertFalse(untouched.contains("template"))
        assertFalse(untouched.contains("group"))
    }
}
