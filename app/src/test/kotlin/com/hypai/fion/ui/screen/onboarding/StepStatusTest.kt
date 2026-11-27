package com.hypai.fion.ui.screen.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class StepStatusTest {
    @Test
    fun `granted permission wins over any recorded outcome`() {
        StepOutcome.entries.forEach { assertEquals(StepStatus.Allowed, stepStatus(done = true, current = false, outcome = it)) }
        assertEquals(StepStatus.Allowed, stepStatus(done = true, current = true, outcome = StepOutcome.Blocked))
    }

    @Test
    fun `current step shows blocked only after permanent denial`() {
        assertEquals(StepStatus.Current, stepStatus(done = false, current = true, outcome = null))
        assertEquals(StepStatus.Current, stepStatus(done = false, current = true, outcome = StepOutcome.Denied))
        assertEquals(StepStatus.Blocked, stepStatus(done = false, current = true, outcome = StepOutcome.Blocked))
    }

    @Test
    fun `other steps keep their outcome or stay pending`() {
        assertEquals(StepStatus.Skipped, stepStatus(done = false, current = false, outcome = StepOutcome.Skipped))
        assertEquals(StepStatus.Denied, stepStatus(done = false, current = false, outcome = StepOutcome.Denied))
        assertEquals(StepStatus.Denied, stepStatus(done = false, current = false, outcome = StepOutcome.Blocked))
        assertEquals(StepStatus.Pending, stepStatus(done = false, current = false, outcome = null))
    }
}
