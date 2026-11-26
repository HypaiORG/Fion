package com.hypai.fion.android

import android.provider.CallLog
import com.hypai.fion.forward.SimIdentity
import com.hypai.fion.forward.SimKey
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariableReaderTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `firstValidIndex skips missing and negative extras to find oem fallback`() {
        val extras = mapOf("android.telephony.extra.SUBSCRIPTION_INDEX" to -1, "subscription" to 3)
        assertEquals(3, firstValidIndex(arrayOf("android.telephony.extra.SUBSCRIPTION_INDEX", "subscription"), extras::get))
        assertNull(firstValidIndex(arrayOf("slot", "phone"), mapOf("slot" to -1)::get))
    }

    @Test
    fun `resolveSmsTimestamp keeps positive smsc time and falls back when invalid`() {
        assertEquals(1_700_000_000_000L, resolveSmsTimestamp(1_700_000_000_000L, 1_700_000_005_000L))
        assertEquals(1_700_000_005_000L, resolveSmsTimestamp(0L, 1_700_000_005_000L))
        assertEquals(1_700_000_005_000L, resolveSmsTimestamp(-10L, 1_700_000_005_000L))
    }

    @Test
    fun `resolveCallSubscriptionId prefers mapped sub id and falls back to matching numeric account id`() {
        assertEquals(4, resolveCallSubscriptionId(mappedSubId = 4, accountId = "2", knownIds = setOf(2, 4)))
        assertEquals(2, resolveCallSubscriptionId(mappedSubId = null, accountId = "2", knownIds = setOf(2, 4)))
        assertNull(resolveCallSubscriptionId(mappedSubId = null, accountId = "9", knownIds = setOf(2, 4)))
        assertNull(resolveCallSubscriptionId(mappedSubId = null, accountId = "89860012345678901234", knownIds = setOf(2, 4)))
        assertEquals(5, resolveCallSubscriptionId(mappedSubId = null, accountId = "5", knownIds = emptySet()))
    }

    @Test
    fun `attachPhoneNumber queries only once per subscription and falls back to via number`() {
        val emptyNum = SimIdentity(SimKey.subscription(1), 0, "CMCC", "主卡", "")
        var reads = 0
        val resolved = attachPhoneNumber(emptyNum, "+8613900000000") { reads++; "" }
        assertEquals(1, reads)
        assertEquals("+8613900000000", resolved.number)

        val direct = attachPhoneNumber(emptyNum, "+8613900000000") { "+8613800000000" }
        assertEquals("+8613800000000", direct.number)

        var skippedReads = 0
        val alreadyPopulated = attachPhoneNumber(direct, "+8613900000000") { skippedReads++; "+8613000000000" }
        assertEquals(0, skippedReads)
        assertEquals("+8613800000000", alreadyPopulated.number)
    }

    @Test
    fun `normalizeCallNumber blanks restricted presentations and sentinel negative numbers`() {
        assertEquals("+8613800138000", normalizeCallNumber("+8613800138000", CallLog.Calls.PRESENTATION_ALLOWED))
        assertEquals("+8613800138000", normalizeCallNumber("+8613800138000", null))
        assertEquals("", normalizeCallNumber("-2", CallLog.Calls.PRESENTATION_RESTRICTED))
        assertEquals("", normalizeCallNumber("Private", CallLog.Calls.PRESENTATION_RESTRICTED))
        assertEquals("", normalizeCallNumber("-1", CallLog.Calls.PRESENTATION_ALLOWED))
        assertEquals("", normalizeCallNumber("-2", null))
        assertEquals("", normalizeCallNumber("-3", null))
    }

    @Test
    fun `callerMatchKey normalizes formatting and counts only same day missed calls`() {
        val keyA = callerMatchKey("138-0013-8000", "+8613800138000")
        val keyB = callerMatchKey("+86 138 0013 8000", "+8613800138000")
        assertEquals(keyA, keyB)
        assertEquals("+8613800138000", callerMatchKey("+86 138-0013-8000", null))
        assertEquals("", callerMatchKey("", "+8613800138000"))

        val yesterday = ZonedDateTime.of(2026, 11, 25, 23, 59, 0, 0, tokyo).toInstant().toEpochMilli()
        val morning = ZonedDateTime.of(2026, 11, 26, 9, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val noon = ZonedDateTime.of(2026, 11, 26, 12, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val history = mapOf(keyA to listOf(yesterday, morning, noon))

        assertEquals(1, countMissedOnDay(keyA, morning, history, tokyo))
        assertEquals(2, countMissedOnDay(keyA, noon, history, tokyo))
        assertEquals(1, countMissedOnDay("", noon, history, tokyo))
    }
}
