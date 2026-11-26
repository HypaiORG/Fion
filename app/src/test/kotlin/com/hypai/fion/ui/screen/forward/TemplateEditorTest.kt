package com.hypai.fion.ui.screen.forward

import androidx.compose.ui.graphics.Color
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.templateVariables
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditorTest {
    private val smsFrom = encodeVariableToken(TemplateVariable.SmsFrom)
    private val smsTime = encodeVariableToken(TemplateVariable.SmsTime)
    private val simNumber = encodeVariableToken(TemplateVariable.SimNumber)

    @Test
    fun `encode and decode round trip structured parts while keeping literal braces as text`() {
        val parts = listOf(
            TemplatePart.Text("SMS · {sms.from} "),
            TemplatePart.Var(TemplateVariable.SmsFrom),
            TemplatePart.Text("/"),
            TemplatePart.Var(TemplateVariable.SimNumber),
        )

        val buffer = encodeTemplateParts(parts)
        assertEquals(parts, decodeTemplateParts(buffer, EventKind.Sms))
        assertEquals(listOf(TemplatePart.Text("plain {sms.from} {sim.number}")), decodeTemplateParts("plain {sms.from} {sim.number}", EventKind.Sms))
    }

    @Test
    fun `chip placements localize labels mark missing permissions and map raw offsets`() {
        val raw = "SMS · $smsFrom/$simNumber"
        val placements = computeChipPlacements(raw, Language.En, EventKind.Sms, isGranted = { it != PermissionGroup.Phone })

        assertEquals(2, placements.size)
        assertEquals(TemplateVariable.SmsFrom, placements[0].token.variable)
        assertFalse(placements[0].missingPermission)
        assertNull(placements[0].warningSlotIndex)
        assertEquals(formatChipText("Sender", missingPermission = false), placements[0].renderedText)

        assertEquals(TemplateVariable.SimNumber, placements[1].token.variable)
        assertTrue(placements[1].missingPermission)
        assertNotNull(placements[1].warningSlotIndex)
        assertEquals(formatChipText("SIM Number", missingPermission = true), placements[1].renderedText)

        assertEquals(0, rawOffsetToVisual(0, placements))
        assertEquals(6, rawOffsetToVisual(6, placements))
        assertEquals(placements[0].visualEnd, rawOffsetToVisual(6 + smsFrom.length, placements))
        assertEquals(placements[0].visualEnd + 1, rawOffsetToVisual(6 + smsFrom.length + 1, placements))
        assertEquals(placements[1].visualEnd, rawOffsetToVisual(raw.length, placements))
    }

    @Test
    fun `findBufferTokens ignores variables inapplicable to the current event kind`() {
        val buffer = "${encodeVariableToken(TemplateVariable.CallCount)}$smsFrom"

        val smsTokens = findBufferTokens(buffer, EventKind.Sms)
        assertEquals(listOf(TemplateVariable.SmsFrom), smsTokens.map { it.variable })

        val callTokens = findBufferTokens(buffer, EventKind.MissedCall)
        assertEquals(listOf(TemplateVariable.CallCount), callTokens.map { it.variable })
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
    }

    @Test
    fun `backspace and forward delete remove entire token atomically`() {
        val original = "短信 · $smsFrom"
        val tokens = findBufferTokens(original, EventKind.Sms)

        val backspaced = original.dropLast(1)
        val backspaceChange = diffSingleChange(original, backspaced, original.length, backspaced.length)!!
        assertEquals("短信 · ", applyFixes(backspaced, computeAtomicEditFixes(tokens, backspaceChange)))

        val forwardDeleted = original.removeRange(5, 6)
        val forwardChange = diffSingleChange(original, forwardDeleted, 5, 5)!!
        assertEquals("短信 · ", applyFixes(forwardDeleted, computeAtomicEditFixes(tokens, forwardChange)))
    }

    @Test
    fun `partial selection across two chips cleans both touched tokens`() {
        val original = "$smsFrom-$smsTime"
        val tokens = findBufferTokens(original, EventKind.Sms)
        val replaced = original.substring(0, 2) + "X" + original.substring(original.length - 3)
        val change = diffSingleChange(original, replaced, 2, 3)!!

        assertEquals("X", applyFixes(replaced, computeAtomicEditFixes(tokens, change)))
    }

    @Test
    fun `typing adjacent to a chip leaves token intact and interior insert relocates outside`() {
        val original = "ab${smsFrom}cd"
        val tokens = findBufferTokens(original, EventKind.Sms)

        assertTrue(computeAtomicEditFixes(tokens, diffSingleChange(original, "abX${smsFrom}cd", 2, 3)!!).isEmpty())
        val afterPos = 2 + smsFrom.length
        assertTrue(computeAtomicEditFixes(tokens, diffSingleChange(original, "ab${smsFrom}Xcd", afterPos, afterPos + 1)!!).isEmpty())

        val interior = original.substring(0, 4) + "X" + original.substring(4)
        val interiorChange = diffSingleChange(original, interior, 4, 5)!!
        assertEquals("ab${smsFrom}Xcd", applyFixes(interior, computeAtomicEditFixes(tokens, interiorChange)))
    }

    @Test
    fun `slash trigger activates only on single slash insertion and derives filtered items`() {
        val vars = EventKind.Sms.templateVariables()
        val trigger = detectNewSlashTrigger("短信 ", "短信 /", 4, 4, EventKind.Sms)
        assertEquals(VariablePickerTarget.Insert(3, 4), trigger)
        assertNull(detectNewSlashTrigger("短信", "短信/a", 4, 4, EventKind.Sms))

        val filteredTarget = resolveSlashTarget(trigger!!, "短信 /sim", 7, 7, false, vars)
        assertEquals(VariablePickerTarget.Insert(3, 7), filteredTarget)
        assertEquals(
            listOf(TemplateVariable.SimCarrier, TemplateVariable.SimName, TemplateVariable.SimNumber),
            pickerItemsForTarget(filteredTarget!!, "短信 /sim", vars),
        )

        val duringComposition = resolveSlashTarget(trigger, "短信 /shijian", 11, 11, true, vars)
        assertEquals(VariablePickerTarget.Insert(3, 11), duringComposition)
        assertEquals(vars, pickerItemsForTarget(duringComposition!!, "短信 /shijian", vars))

        assertNull(resolveSlashTarget(trigger, "短信 /zzz", 7, 7, false, vars))
        assertNull(resolveSlashTarget(trigger, "短信 / ", 5, 5, false, vars))
        assertNull(resolveSlashTarget(trigger, "短信 ", 3, 3, false, vars))
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
    fun `variables within each kind have distinct chip colors and readable contrast`() {
        for (kind in EventKind.entries) {
            val vars = kind.templateVariables()
            val colors = vars.map { it.chipColor() }
            assertEquals(vars.size, colors.map { it.accent }.toSet().size)
        }
        for (variable in TemplateVariable.entries) {
            val chip = variable.chipColor()
            val ratio = contrastRatio(chip.foreground, chip.background)
            assertTrue("Contrast $ratio too low for $variable", ratio >= 5.0)
        }
        val missingRatio = contrastRatio(MissingChipColor.foreground, MissingChipColor.background)
        assertTrue("Contrast $missingRatio too low for MissingChipColor", missingRatio >= 5.0)
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
