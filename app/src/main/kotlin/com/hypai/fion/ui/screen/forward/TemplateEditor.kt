package com.hypai.fion.ui.screen.forward

import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplateToken
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.findTemplateTokens

private const val CHIP_OUTER_GAP = '\u200A'
private const val CHIP_INNER_PAD = '\u00A0'
private const val WORD_JOINER = '\u2060'

data class ChipPlacement(
    val token: TemplateToken,
    val visualStart: Int,
    val visualEnd: Int,
    val pillStart: Int,
    val pillEnd: Int,
    val renderedText: String,
)

fun formatChipText(label: String): String {
    if (label.isEmpty()) return "$CHIP_OUTER_GAP$CHIP_INNER_PAD$CHIP_INNER_PAD$CHIP_OUTER_GAP"
    val out = StringBuilder(label.length * 2 + 4)
    out.append(CHIP_OUTER_GAP).append(CHIP_INNER_PAD)
    for (i in label.indices) {
        if (i > 0) out.append(WORD_JOINER)
        out.append(label[i])
    }
    out.append(CHIP_INNER_PAD).append(CHIP_OUTER_GAP)
    return out.toString()
}

fun computeChipPlacements(
    rawText: String,
    language: Language,
    kind: EventKind? = null,
): List<ChipPlacement> {
    val tokens = findTemplateTokens(rawText)
    if (tokens.isEmpty()) return emptyList()
    var delta = 0
    return tokens.map { token ->
        val rendered = formatChipText(token.variable.displayName(language, kind))
        val visualStart = token.start + delta
        val visualEnd = visualStart + rendered.length
        delta += rendered.length - (token.end - token.start)
        ChipPlacement(
            token = token,
            visualStart = visualStart,
            visualEnd = visualEnd,
            pillStart = visualStart + 1,
            pillEnd = visualEnd - 1,
            renderedText = rendered,
        )
    }
}

fun rawOffsetToVisual(rawOffset: Int, placements: List<ChipPlacement>): Int {
    var delta = 0
    for (placement in placements) {
        val token = placement.token
        if (rawOffset <= token.start) return rawOffset + delta
        if (rawOffset < token.end) {
            return if (rawOffset - token.start < token.end - rawOffset) placement.visualStart else placement.visualEnd
        }
        delta += (placement.visualEnd - placement.visualStart) - (token.end - token.start)
    }
    return rawOffset + delta
}

data class BufferChange(
    val originalStart: Int,
    val originalEnd: Int,
    val newStart: Int,
    val newEnd: Int,
)

sealed interface BufferFix {
    data class DeleteRange(val start: Int, val end: Int) : BufferFix
    data class MoveInsertedOutsideToken(val insertedStart: Int, val insertedEnd: Int, val targetIndex: Int) : BufferFix
}

fun diffSingleChange(
    old: CharSequence,
    new: CharSequence,
    oldSelectionMin: Int,
    newSelectionMin: Int,
): BufferChange? {
    val minLen = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < minLen && old[prefix] == new[prefix]) prefix++
    if (prefix == old.length && prefix == new.length) return null

    var oldEnd = old.length
    var newEnd = new.length
    while (oldEnd > prefix && newEnd > prefix && old[oldEnd - 1] == new[newEnd - 1]) {
        oldEnd--
        newEnd--
    }

    val bias = minOf(oldSelectionMin, newSelectionMin).coerceAtLeast(0)
    while (prefix > bias && oldEnd > prefix && old[prefix - 1] == old[oldEnd - 1]) {
        prefix--
        oldEnd--
        newEnd--
    }
    while (prefix > bias && newEnd > prefix && new[prefix - 1] == new[newEnd - 1]) {
        prefix--
        oldEnd--
        newEnd--
    }
    return BufferChange(prefix, oldEnd, prefix, newEnd)
}

fun computeAtomicEditFixes(originalTokens: List<TemplateToken>, change: BufferChange): List<BufferFix> {
    if (originalTokens.isEmpty()) return emptyList()
    if (change.originalStart == change.originalEnd) {
        val inside = originalTokens.firstOrNull { change.originalStart > it.start && change.originalStart < it.end }
            ?: return emptyList()
        if (change.newEnd <= change.newStart) return emptyList()
        return listOf(
            BufferFix.MoveInsertedOutsideToken(
                insertedStart = change.newStart,
                insertedEnd = change.newEnd,
                targetIndex = change.newStart + (inside.end - change.originalStart),
            ),
        )
    }
    val touched = originalTokens.filter { change.originalStart < it.end && change.originalEnd > it.start }
    if (touched.isEmpty()) return emptyList()
    val expandedOrigStart = minOf(change.originalStart, touched.first().start)
    val expandedOrigEnd = maxOf(change.originalEnd, touched.last().end)
    val leftExtra = change.originalStart - expandedOrigStart
    val rightExtra = expandedOrigEnd - change.originalEnd
    val fixes = mutableListOf<BufferFix>()
    if (rightExtra > 0) fixes.add(BufferFix.DeleteRange(change.newEnd, change.newEnd + rightExtra))
    if (leftExtra > 0) fixes.add(BufferFix.DeleteRange(change.newStart - leftExtra, change.newStart))
    return fixes
}

sealed interface VariablePickerTarget {
    data class Insert(val slashStart: Int, val replaceEnd: Int) : VariablePickerTarget
    data class Replace(val token: TemplateToken) : VariablePickerTarget
}

fun isSlashTriggerChar(c: Char): Boolean = c == '/' || c == '／'

fun detectNewSlashTrigger(
    previousText: String,
    currentText: String,
    selectionStart: Int,
    selectionEnd: Int,
): VariablePickerTarget.Insert? {
    if (selectionStart != selectionEnd || selectionStart <= 0) return null
    if (currentText.length != previousText.length + 1) return null
    val slashIndex = selectionStart - 1
    if (!isSlashTriggerChar(currentText[slashIndex])) return null
    if (!currentText.regionMatches(0, previousText, 0, slashIndex)) return null
    val tailLen = previousText.length - slashIndex
    if (!currentText.regionMatches(selectionStart, previousText, slashIndex, tailLen)) return null
    val insideToken = findTemplateTokens(currentText).any { slashIndex > it.start && slashIndex < it.end }
    if (insideToken) return null
    return VariablePickerTarget.Insert(slashStart = slashIndex, replaceEnd = selectionStart)
}

fun filterVariables(
    variables: List<TemplateVariable>,
    query: String,
    language: Language,
    kind: EventKind? = null,
): List<TemplateVariable> {
    if (query.isEmpty()) return variables
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return variables
    return variables.filter { variable ->
        variable.displayName(language, kind).lowercase().contains(needle) ||
            Language.entries.any { lang ->
                variable.displayName(lang, kind).lowercase().contains(needle) ||
                    variable.label(lang).lowercase().contains(needle)
            }
    }
}

fun resolveSlashTarget(
    current: VariablePickerTarget.Insert,
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    hasComposition: Boolean,
    variables: List<TemplateVariable>,
    language: Language,
    kind: EventKind? = null,
): VariablePickerTarget.Insert? {
    if (selectionStart != selectionEnd) return null
    if (current.slashStart < 0 || current.slashStart >= text.length) return null
    if (!isSlashTriggerChar(text[current.slashStart])) return null
    if (selectionStart <= current.slashStart || selectionStart > text.length) return null
    val query = text.substring(current.slashStart + 1, selectionStart)
    if (query.any { it.isWhitespace() || it == '{' || it == '}' || isSlashTriggerChar(it) }) return null
    val matches = filterVariables(variables, query, language, kind)
    if (matches.isEmpty() && !hasComposition) return null
    return current.copy(replaceEnd = selectionStart)
}

fun pickerItemsForTarget(
    target: VariablePickerTarget,
    text: String,
    variables: List<TemplateVariable>,
    language: Language,
    kind: EventKind? = null,
): List<TemplateVariable> = when (target) {
    is VariablePickerTarget.Replace -> variables
    is VariablePickerTarget.Insert -> {
        val start = (target.slashStart + 1).coerceIn(0, text.length)
        val end = target.replaceEnd.coerceIn(start, text.length)
        filterVariables(variables, text.substring(start, end), language, kind).ifEmpty { variables }
    }
}

data class PopupViewport(
    val width: Int,
    val height: Int,
    val safeTop: Int,
    val safeBottom: Int,
    val edgeMargin: Int,
    val anchorGap: Int,
)

data class PopupAnchor(
    val left: Int,
    val top: Int,
    val bottom: Int,
)

data class PopupConstraints(
    val width: Int,
    val minHeightCap: Int,
    val maxHeightCap: Int,
    val minAboveHeight: Int,
)

data class PopupLayoutSpec(
    val x: Int,
    val placeAbove: Boolean,
    val maxHeight: Int,
    private val viewport: PopupViewport,
    private val anchor: PopupAnchor,
) {
    fun resolveY(measuredHeight: Int): Int {
        val minY = viewport.safeTop + viewport.edgeMargin
        val maxY = (viewport.safeBottom - viewport.edgeMargin - measuredHeight).coerceAtLeast(minY)
        val preferred = if (placeAbove) {
            anchor.top - viewport.anchorGap - measuredHeight
        } else {
            anchor.bottom + viewport.anchorGap
        }
        return preferred.coerceIn(minY, maxY)
    }
}

fun computePopupLayout(
    viewport: PopupViewport,
    anchor: PopupAnchor,
    constraints: PopupConstraints,
): PopupLayoutSpec {
    val maxX = (viewport.width - constraints.width - viewport.edgeMargin).coerceAtLeast(viewport.edgeMargin)
    val x = anchor.left.coerceIn(viewport.edgeMargin, maxX)
    val screenCap = (viewport.height * 0.32f).toInt().coerceIn(constraints.minHeightCap, constraints.maxHeightCap)
    val spaceAbove = (anchor.top - viewport.safeTop - viewport.edgeMargin - viewport.anchorGap).coerceAtLeast(0)
    val spaceBelow = (viewport.safeBottom - anchor.bottom - viewport.edgeMargin - viewport.anchorGap).coerceAtLeast(0)
    val placeAbove = spaceAbove >= constraints.minAboveHeight || spaceAbove >= spaceBelow
    val available = if (placeAbove) spaceAbove else spaceBelow
    return PopupLayoutSpec(
        x = x,
        placeAbove = placeAbove,
        maxHeight = minOf(screenCap, available).coerceAtLeast(0),
        viewport = viewport,
        anchor = anchor,
    )
}
