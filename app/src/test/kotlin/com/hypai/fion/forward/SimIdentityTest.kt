package com.hypai.fion.forward

import com.hypai.fion.store.SimTag
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
    fun `an inactive subscription falls back to historical metadata or keeps its own key`() {
        val archived = SimIdentity(SimKey.subscription(9), null, "SoftBank", "eSIM", "")

        assertEquals(archived, SimIdentity.resolve(active, subscriptionId = 9, slotIndex = 1, fallback = mapOf(9 to archived)))
        assertEquals(SimIdentity(SimKey.subscription(9), 1, "", "", ""), SimIdentity.resolve(active, subscriptionId = 9, slotIndex = 1))
        assertEquals(SimIdentity(SimKey.subscription(5), null, "", "", ""), SimIdentity.resolve(emptyMap(), subscriptionId = 5, slotIndex = null))
    }

    @Test
    fun `without a valid subscription the slot matches active sim or falls back to slot and single sim`() {
        val mobile = SimIdentity(SimKey.subscription(2), 0, "中国移动", "BJ", "+8613800000000")
        val dual = mapOf(2 to mobile, 5 to unicom)

        assertEquals(unicom, SimIdentity.resolve(dual, subscriptionId = -1, slotIndex = 1))
        assertEquals(SimIdentity(SimKey.slot(2), 2, "", "", ""), SimIdentity.resolve(dual, subscriptionId = -1, slotIndex = 2))
        assertEquals(unicom, SimIdentity.resolve(active, subscriptionId = null, slotIndex = -1))
        assertEquals(SimIdentity(SimKey.Unknown, null, "", "", ""), SimIdentity.resolve(dual, subscriptionId = null, slotIndex = -1))
        assertEquals(SimIdentity(SimKey.Unknown, null, "", "", ""), SimIdentity.resolve(emptyMap(), subscriptionId = null, slotIndex = -1))
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
