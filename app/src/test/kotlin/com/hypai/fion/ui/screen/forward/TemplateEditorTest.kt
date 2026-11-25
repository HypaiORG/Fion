package com.hypai.fion.ui.screen.forward

import androidx.compose.ui.graphics.Color
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplateToken
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.findTemplateTokens
import com.hypai.fion.forward.templateVariables
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditorTest {
    @Test
    fun `chip placements localize labels and map raw offsets accurately`() {
        val raw = "SMS · {sender}/{time}"
        val placements = computeChipPlacements(raw, Language.En, EventKind.Sms)

        assertEquals(2, placements.size)
        assertEquals(TemplateVariable.From, placements[0].token.variable)
        assertEquals(formatChipText("Sender"), placements[0].renderedText)
        assertEquals(TemplateVariable.Time, placements[1].token.variable)
        assertEquals(formatChipText("Date & Time"), placements[1].renderedText)

        assertEquals(0, rawOffsetToVisual(0, placements))
        assertEquals(6, rawOffsetToVisual(6, placements))
        assertEquals(placements[0].visualEnd, rawOffsetToVisual(14, placements))
        assertEquals(placements[0].visualEnd + 1, rawOffsetToVisual(15, placements))
        assertEquals(placements[1].visualEnd, rawOffsetToVisual(raw.length, placements))
    }

    @Test
    fun `diffSingleChange distinguishes cursor-biased repeated characters`() {
        assertNull(diffSingleChange("abc", "abc", 1, 1))
        assertEquals(
            BufferChange(7, 8, 7, 7),
            diffSingleChange("{sender}}", "{sender}", oldSelectionMin = 8, newSelectionMin = 7),
        )
        assertEquals(
            BufferChange(8, 9, 8, 8),
            diffSingleChange("{sender}}", "{sender}", oldSelectionMin = 9, newSelectionMin = 8),
        )
        assertEquals(
            BufferChange(2, 12, 2, 3),
            diffSingleChange("{sender}-{time}", "{sXme}", oldSelectionMin = 2, newSelectionMin = 3),
        )
    }

    @Test
    fun `backspace and forward delete remove entire token atomically`() {
        val original = "短信 · {sender}"
        val tokens = findTemplateTokens(original)

        val backspaceChange = diffSingleChange(original, "短信 · {sender", 13, 12)!!
        assertEquals("短信 · ", applyFixes("短信 · {sender", computeAtomicEditFixes(tokens, backspaceChange)))

        val forwardChange = diffSingleChange(original, "短信 · sender}", 5, 5)!!
        assertEquals("短信 · ", applyFixes("短信 · sender}", computeAtomicEditFixes(tokens, forwardChange)))
    }

    @Test
    fun `partial selection across two chips cleans both touched tokens`() {
        val original = "{sender}-{time}"
        val tokens = findTemplateTokens(original)
        val change = diffSingleChange(original, "{sXme}", 2, 3)!!

        assertEquals("X", applyFixes("{sXme}", computeAtomicEditFixes(tokens, change)))
    }

    @Test
    fun `typing adjacent to a chip leaves token intact and interior insert relocates outside`() {
        val original = "ab{sender}cd"
        val tokens = findTemplateTokens(original)

        assertTrue(computeAtomicEditFixes(tokens, diffSingleChange(original, "abX{sender}cd", 2, 3)!!).isEmpty())
        assertTrue(computeAtomicEditFixes(tokens, diffSingleChange(original, "ab{sender}Xcd", 10, 11)!!).isEmpty())

        val interiorChange = diffSingleChange(original, "ab{sXender}cd", 4, 5)!!
        assertEquals("ab{sender}Xcd", applyFixes("ab{sXender}cd", computeAtomicEditFixes(tokens, interiorChange)))
    }

    @Test
    fun `slash trigger activates only on single slash insertion and derives filtered items`() {
        val vars = EventKind.Sms.templateVariables()
        val trigger = detectNewSlashTrigger("短信 ", "短信 /", 4, 4)
        assertEquals(VariablePickerTarget.Insert(3, 4), trigger)
        assertNull(detectNewSlashTrigger("短信", "短信/a", 4, 4))

        val filteredTarget = resolveSlashTarget(trigger!!, "短信 /sim", 7, 7, false, vars, EventKind.Sms)
        assertEquals(VariablePickerTarget.Insert(3, 7), filteredTarget)
        assertEquals(
            listOf(TemplateVariable.SimName, TemplateVariable.Number),
            pickerItemsForTarget(filteredTarget!!, "短信 /sim", vars, EventKind.Sms),
        )

        val duringComposition = resolveSlashTarget(trigger, "短信 /shijian", 11, 11, true, vars, EventKind.Sms)
        assertEquals(VariablePickerTarget.Insert(3, 11), duringComposition)
        assertEquals(vars, pickerItemsForTarget(duringComposition!!, "短信 /shijian", vars, EventKind.Sms))

        assertNull(resolveSlashTarget(trigger, "短信 /zzz", 7, 7, false, vars, EventKind.Sms))
        assertNull(resolveSlashTarget(trigger, "短信 / ", 5, 5, false, vars, EventKind.Sms))
        assertNull(resolveSlashTarget(trigger, "短信 ", 3, 3, false, vars, EventKind.Sms))
    }

    @Test
    fun `popup layout stays above cursor within screen height cap and flips only when top is cramped`() {
        val viewport = PopupViewport(
            width = 1080,
            height = 2400,
            safeTop = 100,
            safeBottom = 1500,
            edgeMargin = 48,
            anchorGap = 24,
        )
        val constraints = PopupConstraints(width = 700, minHeightCap = 420, maxHeightCap = 720, minAboveHeight = 360)

        val normal = computePopupLayout(viewport, PopupAnchor(left = 950, top = 900, bottom = 960), constraints)
        assertTrue(normal.placeAbove)
        assertEquals(1080 - 700 - 48, normal.x)
        assertEquals(720, normal.maxHeight)
        assertEquals(900 - 24 - 600, normal.resolveY(measuredHeight = 600))

        val crampedTop = computePopupLayout(viewport, PopupAnchor(left = 100, top = 220, bottom = 280), constraints)
        assertFalse(crampedTop.placeAbove)
        assertEquals(280 + 24, crampedTop.resolveY(measuredHeight = 500))
    }

    @Test
    fun `every variable has a distinct chip color with readable contrast`() {
        val colors = TemplateVariable.entries.map { it.chipColor() }

        assertEquals(TemplateVariable.entries.size, colors.map { it.accent }.toSet().size)
        for ((variable, chip) in TemplateVariable.entries.zip(colors)) {
            val ratio = contrastRatio(chip.foreground, chip.background)
            assertTrue("Contrast $ratio too low for $variable", ratio >= 5.0)
        }
    }

    private fun applyFixes(buffer: String, fixes: List<BufferFix>): String {
        val sb = StringBuilder(buffer)
        for (fix in fixes) {
            when (fix) {
                is BufferFix.DeleteRange -> sb.delete(fix.start, fix.end)
                is BufferFix.MoveInsertedOutsideToken -> {
                    val inserted = sb.substring(fix.insertedStart, fix.insertedEnd)
                    sb.delete(fix.insertedStart, fix.insertedEnd)
                    sb.insert(fix.targetIndex, inserted)
                }
            }
        }
        return sb.toString()
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
