package com.hypai.fion.ui.screen

import com.hypai.fion.android.Readiness
import com.hypai.fion.bark.BarkSettings
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.forward.Template
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.store.Settings
import com.hypai.fion.store.TemplateChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNoticeTest {
    private val barkReady = BarkSettings(deviceKey = "k")
    private val allPerms = AppPermission.entries.toSet()
    private val phonePerms = setOf(AppPermission.ReadPhoneState, AppPermission.ReadPhoneNumbers)
    private val allReady = Readiness(granted = allPerms, batteryUnrestricted = true)
    private val noneReady = Readiness(granted = emptySet(), batteryUnrestricted = false)

    @Test
    fun `no banner is shown when all channels are disabled`() {
        val disabled = Settings(channels = emptySet(), bark = BarkSettings())

        assertNull(homeNotice(disabled, noneReady, Language.Zh))
        assertNull(homeNotice(disabled, noneReady, Language.En))
    }

    @Test
    fun `paused master switch still shows banners for enabled channels`() {
        val paused = Settings(forwardingEnabled = false, channels = setOf(EventKind.Sms), bark = BarkSettings())

        assertEquals(HomeNotice.Bark, homeNotice(paused, noneReady, Language.Zh))
    }

    @Test
    fun `banner follows bark then sms then call log then phone priority`() {
        val unset = Settings(bark = BarkSettings())
        assertEquals(HomeNotice.Bark, homeNotice(unset, noneReady, Language.Zh))

        val readyBark = Settings(bark = barkReady)
        assertEquals(HomeNotice.Permission(PermissionGroup.Sms), homeNotice(readyBark, noneReady, Language.Zh))
        assertEquals(
            HomeNotice.Permission(PermissionGroup.CallLog),
            homeNotice(readyBark, noneReady.copy(granted = setOf(AppPermission.ReceiveSms)), Language.Zh),
        )
        assertEquals(
            HomeNotice.Permission(PermissionGroup.Phone),
            homeNotice(readyBark, allReady.copy(granted = allPerms - phonePerms), Language.Zh),
        )
        assertNull(homeNotice(readyBark, allReady, Language.Zh))
    }

    @Test
    fun `disabled channels never trigger or shadow banners`() {
        val smsOnly = Settings(bark = barkReady, channels = setOf(EventKind.Sms))
        val noCallNoPhone = allReady.copy(granted = allPerms - phonePerms - AppPermission.ReadCallLog)

        assertEquals(HomeNotice.Permission(PermissionGroup.Phone), homeNotice(smsOnly, noCallNoPhone, Language.Zh))

        val smsWithoutSimVars = smsOnly.withPush(
            EventKind.Sms,
            smsOnly.smsPush.copy(
                template = TemplateChoice.Custom(
                    Template(
                        title = listOf(TemplatePart.Var(TemplateVariable.SmsFrom)),
                        body = listOf(TemplatePart.Var(TemplateVariable.SmsContent)),
                    ),
                ),
            ),
        )
        assertNull(homeNotice(smsWithoutSimVars, noCallNoPhone, Language.Zh))
    }

    @Test
    fun `any sim variable in an active channel triggers phone banner when its permission is denied`() {
        for (simVar in listOf(TemplateVariable.SimCarrier, TemplateVariable.SimName, TemplateVariable.SimNumber)) {
            val settings = Settings(bark = barkReady, channels = setOf(EventKind.MissedCall)).let { base ->
                base.withPush(
                    EventKind.MissedCall,
                    base.callPush.copy(
                        template = TemplateChoice.Custom(
                            Template(
                                title = listOf(TemplatePart.Var(simVar)),
                                body = listOf(TemplatePart.Var(TemplateVariable.CallCount)),
                            ),
                        ),
                    ),
                )
            }
            assertEquals(
                HomeNotice.Permission(PermissionGroup.Phone),
                homeNotice(settings, allReady.copy(granted = allPerms - phonePerms - AppPermission.ReceiveSms), Language.En),
            )
        }
    }

    @Test
    fun `sim name and carrier only require phone state while sim number requires phone numbers`() {
        val smsNameOnly = Settings(bark = barkReady, channels = setOf(EventKind.Sms)).let { base ->
            base.withPush(
                EventKind.Sms,
                base.smsPush.copy(
                    template = TemplateChoice.Custom(
                        Template(listOf(TemplatePart.Var(TemplateVariable.SimCarrier)), listOf(TemplatePart.Var(TemplateVariable.SimName))),
                    ),
                ),
            )
        }
        val stateOnly = allReady.copy(granted = allPerms - AppPermission.ReadPhoneNumbers)
        assertNull(homeNotice(smsNameOnly, stateOnly, Language.Zh))

        val smsWithNumber = smsNameOnly.withPush(
            EventKind.Sms,
            smsNameOnly.smsPush.copy(
                template = TemplateChoice.Custom(
                    Template(listOf(TemplatePart.Var(TemplateVariable.SmsFrom)), listOf(TemplatePart.Var(TemplateVariable.SimNumber))),
                ),
            ),
        )
        assertEquals(HomeNotice.Permission(PermissionGroup.Phone), homeNotice(smsWithNumber, stateOnly, Language.Zh))
    }
}
