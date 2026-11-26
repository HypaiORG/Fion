package com.hypai.fion.android

import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.Template
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.store.Settings
import com.hypai.fion.store.TemplateChoice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemAccessTest {
    private val res = File("src/main/res")

    @Test
    fun `declared app locales match the supported languages`() {
        val declared = Regex("""android:name="([^"]+)"""")
            .findAll(res.resolve("xml/locales_config.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(Language.entries.map { it.tag }.toSet(), declared)
    }

    @Test
    fun `every supported language has strings and english is the fallback`() {
        val languageByDir = res.listFiles { file -> file.name.startsWith("values") }.orEmpty()
            .mapNotNull { dir ->
                dir.resolve("strings.xml").takeIf(File::exists)?.readText()
                    ?.let { Regex("""<string name="language"[^>]*>([^<]+)</string>""").find(it)?.groupValues?.get(1) }
                    ?.let { dir.name to it }
            }
            .toMap()

        assertEquals(Language.entries.map { it.name }.toSet(), languageByDir.values.toSet())
        assertEquals(Language.En.name, languageByDir["values"])
    }

    @Test
    fun `background readiness and permission health follow active channels and template variables`() {
        val all = AppPermission.entries.toSet()
        val full = Readiness(granted = all, batteryUnrestricted = true)
        val defaults = Settings()

        assertTrue(full.backgroundReady(persistentNotification = true))
        assertEquals(PermissionHealth.Ready, full.health(defaults, Language.Zh))
        assertFalse(full.backgroundReady(persistentNotification = false))
        assertEquals(PermissionHealth.Partial, full.health(defaults.copy(persistentNotification = false), Language.Zh))
        assertEquals(
            PermissionHealth.Partial,
            full.copy(granted = all - setOf(AppPermission.ReadPhoneState, AppPermission.ReadPhoneNumbers)).health(defaults, Language.Zh),
        )
        assertEquals(PermissionHealth.Partial, full.copy(granted = all - AppPermission.ReceiveSms).health(defaults, Language.Zh))
        assertEquals(
            PermissionHealth.Blocked,
            full.copy(granted = all - setOf(AppPermission.ReceiveSms, AppPermission.ReadCallLog)).health(defaults, Language.Zh),
        )

        val callOnly = Settings(channels = setOf(EventKind.MissedCall))
        assertEquals(PermissionHealth.Ready, full.copy(granted = all - AppPermission.ReceiveSms).health(callOnly, Language.En))

        val smsNameOnly = Settings(channels = setOf(EventKind.Sms)).let { s ->
            s.withPush(
                EventKind.Sms,
                s.smsPush.copy(
                    template = TemplateChoice.Custom(
                        Template(listOf(TemplatePart.Var(TemplateVariable.SmsFrom)), listOf(TemplatePart.Var(TemplateVariable.SimName))),
                    ),
                ),
            )
        }
        val stateOnly = full.copy(granted = all - AppPermission.ReadPhoneNumbers)
        assertTrue(stateOnly.allows(TemplateVariable.SimName))
        assertFalse(stateOnly.allows(TemplateVariable.SimNumber))
        assertEquals(PermissionHealth.Ready, stateOnly.health(smsNameOnly, Language.Zh))
    }
}
