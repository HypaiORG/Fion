package com.hypai.fion.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SimIdentityTest {
    private val unicom = SimIdentity(SimKey.subscription(5), 1, "中国联通", "SH", "+8615500000000")
    private val active = mapOf(5 to unicom)

    @Test
    fun `an active subscription resolves to its live identity`() {
        assertEquals(unicom, SimIdentity.resolve(active, subscriptionId = 5, slotIndex = 1))
    }

    @Test
    fun `an inactive subscription keeps its own key`() {
        assertEquals(SimIdentity(SimKey.subscription(9), 1, "", "", ""), SimIdentity.resolve(active, subscriptionId = 9, slotIndex = 1))
        assertEquals(SimIdentity(SimKey.subscription(5), null, "", "", ""), SimIdentity.resolve(emptyMap(), subscriptionId = 5, slotIndex = null))
    }

    @Test
    fun `without a valid subscription the slot and then unknown are used`() {
        assertEquals(SimIdentity(SimKey.slot(1), 1, "", "", ""), SimIdentity.resolve(active, subscriptionId = -1, slotIndex = 1))
        assertEquals(SimIdentity(SimKey.Unknown, null, "", "", ""), SimIdentity.resolve(active, subscriptionId = null, slotIndex = -1))
    }

    @Test
    fun `a sim title joins the custom name and carrier it could read`() {
        assertEquals("SH · 中国联通", SimTag(1, "SH", "中国联通").title)
        assertEquals("中国联通", SimTag(1, "", "中国联通").title)
        assertEquals("SH", SimTag(1, "SH", " ").title)
        assertEquals("China Unicom", SimTag(1, "China Unicom", "China Unicom").title)
        assertEquals("", SimTag(1).title)
    }

    @Test
    fun `a sim tag without text keeps the previous text and fills its slot`() {
        val previous = SimTag(1, "SH", "中国联通")

        assertEquals(previous, SimTag().over(previous))
        assertEquals(SimTag(1, "", "China Unicom"), SimTag(null, "", "China Unicom").over(previous))
        assertEquals(SimTag(0, "SH", "中国联通"), SimTag(0).over(previous))
        assertEquals(SimTag(0), SimTag(0).over(null))
    }
}
