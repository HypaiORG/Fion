package com.hypai.fion.ui.screen.forward

import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.normalizeParts

internal const val TOKEN_OPEN = '\uFFF9'
internal const val TOKEN_CLOSE = '\uFFFB'
private const val CHIP_OUTER_GAP = '\u200A'
private const val CHIP_INNER_PAD = '\u00A0'
private const val CHIP_WARNING_SLOT = '\u2003'
private const val WORD_JOINER = '\u2060'

internal data class BufferToken(
    val start: Int,
    val end: Int,
    val variable: TemplateVariable,
)

internal fun encodeVariableToken(variable: TemplateVariable): String = "$TOKEN_OPEN${variable.id}$TOKEN_CLOSE"

internal fun encodeTemplateParts(parts: List<TemplatePart>): String = buildString {
    for (part in parts) {
        when (part) {
            is TemplatePart.Text -> append(part.value)
            is TemplatePart.Var -> append(encodeVariableToken(part.id))
        }
    }
}

private inline fun scanSentinels(
    buffer: String,
    kind: EventKind,
    onValid: (start: Int, end: Int, variable: TemplateVariable) -> Unit = { _, _, _ -> },
    onInvalid: (start: Int, end: Int) -> Unit = { _, _ -> },
) {
    var cursor = 0
    val len = buffer.length
    while (cursor < len) {
        when (buffer[cursor]) {
            TOKEN_CLOSE -> {
                onInvalid(cursor, cursor + 1)
                cursor++
            }
            TOKEN_OPEN -> {
                var scan = cursor + 1
                while (scan < len && buffer[scan] != TOKEN_OPEN && buffer[scan] != TOKEN_CLOSE) scan++
                if (scan < len && buffer[scan] == TOKEN_CLOSE) {
                    val variable = TemplateVariable.fromId(buffer.substring(cursor + 1, scan))
                    if (variable != null && variable.appliesTo(kind)) {
                        onValid(cursor, scan + 1, variable)
                    } else {
                        onInvalid(cursor, scan + 1)
                    }
                    cursor = scan + 1
                } else {
                    onInvalid(cursor, cursor + 1)
                    cursor++
                }
            }
            else -> cursor++
        }
    }
}

internal fun findBufferTokens(buffer: String, kind: EventKind): List<BufferToken> {
    if (!buffer.contains(TOKEN_OPEN)) return emptyList()
    return buildList { scanSentinels(buffer, kind, onValid = { s, e, v -> add(BufferToken(s, e, v)) }) }
}

internal fun findInvalidSentinelRanges(buffer: String, kind: EventKind): List<IntRange> {
    if (!buffer.contains(TOKEN_OPEN) && !buffer.contains(TOKEN_CLOSE)) return emptyList()
    return buildList { scanSentinels(buffer, kind, onInvalid = { s, e -> add(s until e) }) }
}

internal fun decodeTemplateParts(buffer: String, kind: EventKind): List<TemplatePart> {
    if (buffer.isEmpty()) return emptyList()
    if (!buffer.contains(TOKEN_OPEN) && !buffer.contains(TOKEN_CLOSE)) return listOf(TemplatePart.Text(buffer))
    val parts = mutableListOf<TemplatePart>()
    var textStart = 0
    fun flush(end: Int) {
        if (end > textStart) parts.add(TemplatePart.Text(buffer.substring(textStart, end)))
    }
    scanSentinels(
        buffer = buffer,
        kind = kind,
        onValid = { s, e, v -> flush(s); parts.add(TemplatePart.Var(v)); textStart = e },
        onInvalid = { s, e -> flush(s); textStart = e },
    )
    flush(buffer.length)
    return normalizeParts(parts)
}

internal data class ChipPlacement(
    val token: BufferToken,
    val visualStart: Int,
    val visualEnd: Int,
    val pillStart: Int,
    val pillEnd: Int,
    val renderedText: String,
    val missingPermission: Boolean = false,
    val warningSlotIndex: Int? = null,
)

internal fun formatChipText(label: String, missingPermission: Boolean = false): String {
    val out = StringBuilder(label.length * 2 + 8)
    out.append(CHIP_OUTER_GAP).append(CHIP_INNER_PAD)
    if (missingPermission) {
        out.append(CHIP_WARNING_SLOT).append(WORD_JOINER).append(CHIP_OUTER_GAP).append(WORD_JOINER)
    }
    for (i in label.indices) {
        if (i > 0) out.append(WORD_JOINER)
        out.append(label[i])
    }
    out.append(CHIP_INNER_PAD).append(CHIP_OUTER_GAP)
    return out.toString()
}

internal fun computeChipPlacements(
    buffer: String,
    language: Language,
    kind: EventKind,
    isGranted: (TemplateVariable) -> Boolean,
): List<ChipPlacement> {
    val tokens = findBufferTokens(buffer, kind)
    if (tokens.isEmpty()) return emptyList()
    var delta = 0
    return tokens.map { token ->
        val missing = !isGranted(token.variable)
        val rendered = formatChipText(token.variable.displayName(language), missing)
        val visualStart = token.start + delta
        val visualEnd = visualStart + rendered.length
        val pillStart = visualStart + 1
        delta += rendered.length - (token.end - token.start)
        ChipPlacement(
            token = token,
            visualStart = visualStart,
            visualEnd = visualEnd,
            pillStart = pillStart,
            pillEnd = visualEnd - 1,
            renderedText = rendered,
            missingPermission = missing,
            warningSlotIndex = if (missing) pillStart + 1 else null,
        )
    }
}

internal fun rawOffsetToVisual(rawOffset: Int, placements: List<ChipPlacement>): Int {
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

internal data class BufferChange(
    val originalStart: Int,
    val originalEnd: Int,
    val newStart: Int,
    val newEnd: Int,
)

internal sealed interface BufferFix {
    data class DeleteRange(val start: Int, val end: Int) : BufferFix
    data class MoveInsertedOutsideToken(val insertedStart: Int, val insertedEnd: Int, val targetIndex: Int) : BufferFix
}

internal fun diffSingleChange(
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

internal fun computeAtomicEditFixes(originalTokens: List<BufferToken>, change: BufferChange): List<BufferFix> {
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

internal sealed interface VariablePickerTarget {
    data class Insert(val slashStart: Int, val replaceEnd: Int) : VariablePickerTarget
    data class Replace(val token: BufferToken) : VariablePickerTarget
}

private fun isSlashTriggerChar(c: Char): Boolean = c == '/' || c == '／'

internal fun detectNewSlashTrigger(
    previousText: String,
    currentText: String,
    selectionStart: Int,
    selectionEnd: Int,
    kind: EventKind,
): VariablePickerTarget.Insert? {
    if (selectionStart != selectionEnd || selectionStart <= 0) return null
    val slashIndex = selectionStart - 1
    if (!isSlashTriggerChar(currentText[slashIndex])) return null
    val change = diffSingleChange(previousText, currentText, slashIndex, selectionStart) ?: return null
    if (change.newStart != slashIndex || change.newEnd != selectionStart) return null
    val insideToken = findBufferTokens(currentText, kind).any { slashIndex > it.start && slashIndex < it.end }
    if (insideToken) return null
    return VariablePickerTarget.Insert(slashStart = slashIndex, replaceEnd = selectionStart)
}

private fun filterVariables(
    variables: List<TemplateVariable>,
    query: String,
): List<TemplateVariable> {
    if (query.isEmpty()) return variables
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return variables
    return variables.filter { variable ->
        variable.id.lowercase().contains(needle) ||
            Language.entries.any { lang ->
                variable.displayName(lang).lowercase().contains(needle)
            }
    }
}

internal fun resolveSlashTarget(
    current: VariablePickerTarget.Insert,
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    hasComposition: Boolean,
    variables: List<TemplateVariable>,
): VariablePickerTarget.Insert? {
    if (selectionStart != selectionEnd) return null
    if (current.slashStart < 0 || current.slashStart >= text.length) return null
    if (!isSlashTriggerChar(text[current.slashStart])) return null
    if (selectionStart <= current.slashStart || selectionStart > text.length) return null
    val query = text.substring(current.slashStart + 1, selectionStart)
    if (query.any { it.isWhitespace() || it == TOKEN_OPEN || it == TOKEN_CLOSE || isSlashTriggerChar(it) }) return null
    val matches = filterVariables(variables, query)
    if (matches.isEmpty() && !hasComposition) return null
    return current.copy(replaceEnd = selectionStart)
}

internal fun pickerItemsForTarget(
    target: VariablePickerTarget,
    text: String,
    variables: List<TemplateVariable>,
): List<TemplateVariable> = when (target) {
    is VariablePickerTarget.Replace -> variables
    is VariablePickerTarget.Insert -> {
        val start = (target.slashStart + 1).coerceIn(0, text.length)
        val end = target.replaceEnd.coerceIn(start, text.length)
        filterVariables(variables, text.substring(start, end)).ifEmpty { variables }
    }
}

internal data class PopupViewport(
    val height: Int,
    val safeTop: Int,
    val safeBottom: Int,
    val edgeMargin: Int,
    val anchorGap: Int,
    val safeLeft: Int = 0,
    val safeRight: Int,
)

internal data class PopupAnchor(
    val left: Int,
    val top: Int,
    val bottom: Int,
)

internal data class PopupConstraints(
    val width: Int,
    val minHeightCap: Int,
    val maxHeightCap: Int,
    val minAboveHeight: Int,
)

internal data class PopupLayoutSpec(
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

internal fun computePopupLayout(
    viewport: PopupViewport,
    anchor: PopupAnchor,
    constraints: PopupConstraints,
): PopupLayoutSpec {
    val minX = viewport.safeLeft + viewport.edgeMargin
    val maxX = (viewport.safeRight - constraints.width - viewport.edgeMargin).coerceAtLeast(minX)
    val x = anchor.left.coerceIn(minX, maxX)
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
