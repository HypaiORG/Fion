package com.hypai.fion.android

import com.hypai.fion.forward.Language
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
    fun `background readiness and permission health follow persistent notification switch`() {
        val full = Readiness(sms = true, callLog = true, phone = true, batteryUnrestricted = true, notifications = true)

        assertTrue(full.backgroundReady(persistentNotification = true))
        assertEquals(PermissionHealth.Ready, full.health(persistentNotification = true))
        assertFalse(full.backgroundReady(persistentNotification = false))
        assertEquals(PermissionHealth.Partial, full.health(persistentNotification = false))
        assertEquals(PermissionHealth.Blocked, full.copy(sms = false).health(persistentNotification = true))
    }
}
