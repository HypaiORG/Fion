package com.hypai.fion.ui.screen.forward

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.templateVariables
import com.hypai.fion.ui.design.FormFieldFrame
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Immutable
internal data class VariableChipColor(
    val accent: Color,
    val background: Color,
    val foreground: Color,
    val border: Color,
)

private val ChipInk = Color(0xFF111827)

internal val MissingChipColor = VariableChipColor(
    accent = Palette.Outline,
    background = Color(0xFFEEEFF2),
    foreground = Palette.Muted,
    border = Color(0xFFD0D5DD),
)

private val VariableChipColors: List<VariableChipColor> = TemplateVariable.entries.map { variable ->
    val accent = when (variable) {
        TemplateVariable.SmsFrom, TemplateVariable.CallFrom -> Color(0xFF6E4CC2)
        TemplateVariable.SmsContent -> Color(0xFFD1455F)
        TemplateVariable.CallCount -> Color(0xFFC9443A)
        TemplateVariable.SmsTime, TemplateVariable.CallTime -> Color(0xFFB8760B)
        TemplateVariable.SimCarrier -> Color(0xFF0F8B8D)
        TemplateVariable.SimName -> Color(0xFF238653)
        TemplateVariable.SimNumber -> Color(0xFF1D6FD8)
    }
    VariableChipColor(
        accent = accent,
        background = lerp(Color.White, accent, 0.14f),
        foreground = lerp(accent, ChipInk, 0.34f),
        border = lerp(Color.White, accent, 0.34f),
    )
}

internal fun TemplateVariable.chipColor(): VariableChipColor = VariableChipColors[ordinal]

private val ChipCorner = 7.dp
private val PickerCorner = 18.dp
private val PickerShape = RoundedCornerShape(PickerCorner)
private val PickerRowShape = RoundedCornerShape(11.dp)
private val ChipFontSize = 14.sp
private val ChipVerticalInset = 2.dp
private const val DISMISS_REOPEN_GUARD_MS = 180L
private const val MAX_PENDING_ECHOES = 16

private val TemplateTextStyle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 30.sp,
    fontWeight = FontWeight.Normal,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private data class DrawnChip(
    val placement: ChipPlacement,
    val rects: List<Rect>,
    val warningRect: Rect?,
)

@Composable
internal fun TemplateField(
    value: List<TemplatePart>,
    onValueChange: (List<TemplatePart>) -> Unit,
    label: String,
    kind: EventKind,
    language: Language,
    readiness: Readiness,
    onMissingPermission: (TemplateVariable) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    lines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val singleLine = lines == 1
    val variables = remember(kind) { kind.templateVariables() }
    val initialBuffer = remember { encodeTemplateParts(value) }
    val textState = rememberTextFieldState(initialText = initialBuffer)
    val scrollState = rememberScrollState()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val insetPx = with(density) { ChipVerticalInset.toPx() }

    var lastObservedText by remember { mutableStateOf(initialBuffer) }
    val pendingEchoes = remember { ArrayDeque<List<TemplatePart>>() }
    var wasFocused by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<VariablePickerTarget?>(null) }
    var dismissedToken by remember { mutableStateOf<BufferToken?>(null) }
    var dismissedAtMs by remember { mutableLongStateOf(0L) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textBoxOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var textBoxWidthInWindow by remember { mutableIntStateOf(0) }

    val placementsState = remember(language, kind, readiness.granted) {
        derivedStateOf { computeChipPlacements(textState.text.toString(), language, kind, readiness::allows) }
    }
    val placements by placementsState
    val drawnChipsState = remember(placementsState, insetPx) {
        derivedStateOf {
            val layout = textLayoutResult ?: return@derivedStateOf emptyList()
            placementsState.value.map { placement ->
                val rects = chipRects(layout, placement, insetPx)
                DrawnChip(placement, rects, warningRect(layout, placement, rects.firstOrNull()))
            }
        }
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestVariables by rememberUpdatedState(variables)
    val latestKind by rememberUpdatedState(kind)
    val latestFocused by rememberUpdatedState(focused)

    LaunchedEffect(value) {
        val echoIdx = pendingEchoes.indexOf(value)
        if (echoIdx >= 0) {
            repeat(echoIdx + 1) { pendingEchoes.removeFirst() }
            return@LaunchedEffect
        }
        val encoded = encodeTemplateParts(value)
        if (textState.text.toString() != encoded) {
            pendingEchoes.clear()
            lastObservedText = encoded
            pickerTarget = null
            textState.setTextAndPlaceCursorAtEnd(encoded)
        }
    }

    LaunchedEffect(kind, language) {
        pickerTarget = null
    }

    LaunchedEffect(focused) {
        if (!focused && (wasFocused || pickerTarget is VariablePickerTarget.Insert)) {
            pickerTarget = null
        }
        wasFocused = focused
    }

    LaunchedEffect(textState) {
        snapshotFlow {
            Triple(textState.text.toString(), textState.selection, textState.composition != null)
        }.collect { (currentText, selection, hasComposition) ->
            val prevText = lastObservedText
            val textChanged = currentText != prevText
            if (textChanged) {
                lastObservedText = currentText
                val decoded = decodeTemplateParts(currentText, latestKind)
                if (pendingEchoes.size >= MAX_PENDING_ECHOES) pendingEchoes.removeFirst()
                pendingEchoes.addLast(decoded)
                latestOnValueChange(decoded)
            }

            val currentPicker = pickerTarget
            pickerTarget = when {
                currentPicker is VariablePickerTarget.Replace -> if (textChanged) null else currentPicker
                !latestFocused -> null
                currentPicker is VariablePickerTarget.Insert -> resolveSlashTarget(
                    current = currentPicker,
                    text = currentText,
                    selectionStart = selection.start,
                    selectionEnd = selection.end,
                    hasComposition = hasComposition,
                    variables = latestVariables,
                )
                textChanged -> detectNewSlashTrigger(prevText, currentText, selection.start, selection.end, latestKind)
                else -> null
            }
        }
    }

    val inputTransformation = remember(kind) { atomicTokenInputTransformation(kind) }
    val outputTransformation = remember(language, kind, readiness.granted) {
        chipOutputTransformation(language, kind, readiness::allows)
    }
    val activeReplaceToken = (pickerTarget as? VariablePickerTarget.Replace)?.token
    val pickerLabel = stringResource(R.string.forward_variable_picker)
    val grantLabel = stringResource(R.string.grant)

    BackHandler(enabled = pickerTarget != null) { pickerTarget = null }

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(tween(200, easing = Motion.Standard)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicTextField(
                state = textState,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = label
                        if (placements.isNotEmpty()) {
                            customActions = placements.map { placement ->
                                val name = placement.token.variable.displayName(language)
                                val actionLabel = if (placement.missingPermission) "$grantLabel $name" else "$pickerLabel · $name"
                                CustomAccessibilityAction(actionLabel) {
                                    if (placement.missingPermission) {
                                        pickerTarget = null
                                        onMissingPermission(placement.token.variable)
                                    } else {
                                        pickerTarget = VariablePickerTarget.Replace(placement.token)
                                    }
                                    true
                                }
                            }
                        }
                    },
                textStyle = TemplateTextStyle.copy(color = Palette.Ink),
                lineLimits = if (singleLine) {
                    TextFieldLineLimits.SingleLine
                } else {
                    TextFieldLineLimits.MultiLine(minHeightInLines = lines)
                },
                keyboardOptions = keyboardOptions,
                inputTransformation = inputTransformation,
                outputTransformation = outputTransformation,
                onTextLayout = { getResult -> textLayoutResult = getResult() },
                interactionSource = interaction,
                cursorBrush = SolidColor(Palette.Navy),
                scrollState = scrollState,
                decorator = { innerTextField ->
                    FormFieldFrame(
                        label = label,
                        focused = focused,
                        singleLine = singleLine,
                        textStyle = TemplateTextStyle,
                    ) {
                        TemplateTextSurface(
                            drawnChips = { drawnChipsState.value },
                            activeReplaceToken = activeReplaceToken,
                            scrollState = scrollState,
                            singleLine = singleLine,
                            onBoundsChanged = { origin, width ->
                                textBoxOriginInWindow = origin
                                textBoxWidthInWindow = width
                            },
                            onChipTap = { placement ->
                                if (placement.missingPermission) {
                                    pickerTarget = null
                                    onMissingPermission(placement.token.variable)
                                } else {
                                    val now = SystemClock.uptimeMillis()
                                    if (dismissedToken == placement.token && now - dismissedAtMs < DISMISS_REOPEN_GUARD_MS) {
                                        dismissedToken = null
                                    } else {
                                        pickerTarget = VariablePickerTarget.Replace(placement.token)
                                    }
                                }
                            },
                            innerTextField = innerTextField,
                        )
                    }
                },
            )

            if (hint != null) {
                Text(
                    text = hint,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = Type.Caption,
                    color = Palette.Muted.copy(alpha = 0.78f),
                )
            }
        }

        val currentTarget = pickerTarget
        val currentLayout = if (currentTarget != null) textLayoutResult else null
        if (currentTarget != null && currentLayout != null) {
            val drawnChips = drawnChipsState.value
            val items = remember(currentTarget, lastObservedText, variables) {
                pickerItemsForTarget(currentTarget, lastObservedText, variables)
            }
            val localRect = remember(currentTarget, currentLayout, placements, drawnChips) {
                computeAnchorLocalRect(currentTarget, currentLayout, placements, drawnChips)
            }
            if (localRect != null && items.isNotEmpty()) {
                VariablePickerPopup(
                    items = items,
                    activeVariable = activeReplaceToken?.variable,
                    isGranted = readiness::allows,
                    language = language,
                    localAnchorRect = localRect,
                    boxOrigin = textBoxOriginInWindow,
                    scrollState = scrollState,
                    singleLine = singleLine,
                    fieldLeft = textBoxOriginInWindow.x.roundToInt(),
                    fieldWidth = textBoxWidthInWindow,
                    onSelect = { variable ->
                        val replacement = encodeVariableToken(variable)
                        val (rawStart, rawEnd) = when (currentTarget) {
                            is VariablePickerTarget.Insert -> currentTarget.slashStart to currentTarget.replaceEnd
                            is VariablePickerTarget.Replace -> currentTarget.token.start to currentTarget.token.end
                        }
                        pickerTarget = null
                        textState.edit {
                            val start = rawStart.coerceIn(0, length)
                            val end = rawEnd.coerceIn(start, length)
                            replace(start, end, replacement)
                            selection = TextRange(start + replacement.length)
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    },
                    onMissingPermission = { variable ->
                        pickerTarget = null
                        onMissingPermission(variable)
                    },
                    onDismiss = {
                        dismissedToken = activeReplaceToken
                        dismissedAtMs = SystemClock.uptimeMillis()
                        pickerTarget = null
                    },
                )
            }
        }
    }
}

private fun atomicTokenInputTransformation(kind: EventKind): InputTransformation = InputTransformation {
    val originalTokens = findBufferTokens(originalText.toString(), kind)
    if (originalTokens.isNotEmpty()) {
        val change = diffSingleChange(
            old = originalText,
            new = asCharSequence(),
            oldSelectionMin = originalSelection.min,
            newSelectionMin = selection.min,
        )
        if (change != null) {
            for (fix in computeAtomicEditFixes(originalTokens, change)) {
                when (fix) {
                    is BufferFix.DeleteRange -> {
                        if (fix.start >= 0 && fix.end <= length && fix.start < fix.end) {
                            delete(fix.start, fix.end)
                        }
                    }
                    is BufferFix.MoveInsertedOutsideToken -> {
                        if (fix.insertedStart >= 0 && fix.insertedEnd <= length && fix.insertedStart < fix.insertedEnd) {
                            val inserted = asCharSequence().subSequence(fix.insertedStart, fix.insertedEnd).toString()
                            delete(fix.insertedStart, fix.insertedEnd)
                            val dest = fix.targetIndex.coerceIn(0, length)
                            insert(dest, inserted)
                            selection = TextRange(dest + inserted.length)
                        }
                    }
                }
            }
        }
    }
    val invalidSentinels = findInvalidSentinelRanges(asCharSequence().toString(), kind)
    for (range in invalidSentinels.asReversed()) {
        val endExclusive = (range.last + 1).coerceAtMost(length)
        if (range.first in 0 until endExclusive) {
            delete(range.first, endExclusive)
        }
    }
}

private fun chipOutputTransformation(
    language: Language,
    kind: EventKind,
    isGranted: (TemplateVariable) -> Boolean,
): OutputTransformation = OutputTransformation {
    for (placement in computeChipPlacements(asCharSequence().toString(), language, kind, isGranted)) {
        val rawLen = placement.token.end - placement.token.start
        val chipColor = if (placement.missingPermission) MissingChipColor else placement.token.variable.chipColor()
        replace(placement.visualStart, placement.visualStart + rawLen, placement.renderedText)
        addStyle(
            SpanStyle(
                fontSize = ChipFontSize,
                color = chipColor.foreground,
                fontWeight = FontWeight.Medium,
            ),
            start = placement.pillStart,
            end = placement.pillEnd,
        )
    }
}

@Composable
private fun TemplateTextSurface(
    drawnChips: () -> List<DrawnChip>,
    activeReplaceToken: BufferToken?,
    scrollState: ScrollState,
    singleLine: Boolean,
    onBoundsChanged: (Offset, Int) -> Unit,
    onChipTap: (ChipPlacement) -> Unit,
    innerTextField: @Composable () -> Unit,
) {
    val latestChips by rememberUpdatedState(drawnChips)
    val latestOnChipTap by rememberUpdatedState(onChipTap)
    val warningPainter = rememberVectorPainter(Icons.Warning)
    val warningTint = remember { ColorFilter.tint(Palette.Amber) }

    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned { coords -> onBoundsChanged(coords.positionInWindow(), coords.size.width) }
            .drawWithCache {
                val radius = CornerRadius(ChipCorner.toPx())
                val normalStroke = Stroke(0.75.dp.toPx())
                val activeStroke = Stroke(1.25.dp.toPx())
                onDrawBehind {
                    val scrollX = if (singleLine) -scrollState.value.toFloat() else 0f
                    val scrollY = if (!singleLine) -scrollState.value.toFloat() else 0f
                    for ((placement, rects, warningRect) in drawnChips()) {
                        val chipColor = if (placement.missingPermission) MissingChipColor else placement.token.variable.chipColor()
                        val isSelected = !placement.missingPermission && placement.token == activeReplaceToken
                        val stroke = if (isSelected) activeStroke else normalStroke
                        val borderColor = if (isSelected) chipColor.foreground.copy(alpha = 0.55f) else chipColor.border
                        for (rect in rects) {
                            val topLeft = Offset(rect.left + scrollX, rect.top + scrollY)
                            val rectSize = Size(rect.width, rect.height)
                            drawRoundRect(chipColor.background, topLeft, rectSize, radius)
                            drawRoundRect(borderColor, topLeft, rectSize, radius, style = stroke)
                        }
                        if (warningRect != null) {
                            val iconSize = minOf(warningRect.width, warningRect.height) * 0.82f
                            val iconLeft = warningRect.left + scrollX + (warningRect.width - iconSize) / 2f
                            val iconTop = warningRect.top + scrollY + (warningRect.height - iconSize) / 2f
                            translate(iconLeft, iconTop) {
                                with(warningPainter) { draw(Size(iconSize, iconSize), colorFilter = warningTint) }
                            }
                        }
                    }
                }
            }
            .pointerInput(singleLine) {
                val touchPaddingPx = 3.dp.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val scrollX = if (singleLine) scrollState.value.toFloat() else 0f
                        val scrollY = if (!singleLine) scrollState.value.toFloat() else 0f
                        val hit = findHitChip(
                            drawnChips = latestChips(),
                            point = Offset(down.position.x + scrollX, down.position.y + scrollY),
                            touchPaddingPx = touchPaddingPx,
                        ) ?: continue

                        val startPos = down.position
                        val startTime = down.uptimeMillis
                        val slop = viewConfiguration.touchSlop
                        val longPressLimit = viewConfiguration.longPressTimeoutMillis
                        var canceled = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.size > 1) {
                                canceled = true
                                break
                            }
                            if (!change.pressed) {
                                if (!canceled && change.uptimeMillis - startTime < longPressLimit) {
                                    change.consume()
                                    latestOnChipTap(hit)
                                }
                                break
                            }
                            if ((change.position - startPos).getDistance() > slop ||
                                change.uptimeMillis - startTime >= longPressLimit
                            ) {
                                canceled = true
                                break
                            }
                        }
                    }
                }
            },
    ) {
        innerTextField()
    }
}

private fun chipRects(layout: TextLayoutResult, placement: ChipPlacement, insetPx: Float): List<Rect> {
    val textLen = layout.layoutInput.text.length
    if (placement.pillStart < 0 || placement.pillStart >= textLen || placement.pillEnd > textLen || placement.pillStart >= placement.pillEnd) {
        return emptyList()
    }
    val startLine = layout.getLineForOffset(placement.pillStart)
    val endLine = layout.getLineForOffset(placement.pillEnd - 1)
    return buildList(endLine - startLine + 1) {
        for (line in startLine..endLine) {
            val segStart = if (line == startLine) placement.pillStart else layout.getLineStart(line)
            val segEnd = if (line == endLine) placement.pillEnd else layout.getLineEnd(line, visibleEnd = true)
            if (segEnd <= segStart) continue
            val firstBox = layout.getBoundingBox(segStart)
            val lastBox = layout.getBoundingBox(segEnd - 1)
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            val clampedInset = insetPx.coerceAtMost((lineBottom - lineTop) * 0.14f)
            add(
                Rect(
                    left = minOf(firstBox.left, lastBox.left),
                    top = lineTop + clampedInset,
                    right = maxOf(firstBox.right, lastBox.right),
                    bottom = lineBottom - clampedInset,
                ),
            )
        }
    }
}

private fun warningRect(layout: TextLayoutResult, placement: ChipPlacement, pillRect: Rect?): Rect? {
    val slotIndex = placement.warningSlotIndex ?: return null
    val pill = pillRect ?: return null
    if (slotIndex < 0 || slotIndex >= layout.layoutInput.text.length) return null
    val slot = layout.getBoundingBox(slotIndex)
    if (slot.width <= 0f) return null
    return Rect(left = slot.left, top = pill.top, right = slot.right, bottom = pill.bottom)
}

private fun findHitChip(
    drawnChips: List<DrawnChip>,
    point: Offset,
    touchPaddingPx: Float,
): ChipPlacement? {
    for ((placement, rects, _) in drawnChips) {
        if (rects.any { rect ->
                point.x >= rect.left - touchPaddingPx &&
                    point.x <= rect.right + touchPaddingPx &&
                    point.y >= rect.top - touchPaddingPx &&
                    point.y <= rect.bottom + touchPaddingPx
            }
        ) {
            return placement
        }
    }
    return null
}

private fun computeAnchorLocalRect(
    target: VariablePickerTarget,
    layout: TextLayoutResult,
    placements: List<ChipPlacement>,
    drawnChips: List<DrawnChip>,
): Rect? = when (target) {
    is VariablePickerTarget.Insert -> {
        val visualOffset = rawOffsetToVisual(target.slashStart, placements)
            .coerceIn(0, layout.layoutInput.text.length)
        layout.getCursorRect(visualOffset)
    }
    is VariablePickerTarget.Replace -> {
        drawnChips.firstOrNull { it.placement.token == target.token }?.rects?.firstOrNull()
    }
}

@Composable
private fun VariablePickerPopup(
    items: List<TemplateVariable>,
    activeVariable: TemplateVariable?,
    isGranted: (TemplateVariable) -> Boolean,
    language: Language,
    localAnchorRect: Rect,
    boxOrigin: Offset,
    scrollState: ScrollState,
    singleLine: Boolean,
    fieldLeft: Int,
    fieldWidth: Int,
    onSelect: (TemplateVariable) -> Unit,
    onMissingPermission: (TemplateVariable) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val safeTopPx = WindowInsets.statusBars.getTop(density)
    val safeBottomInsetPx = maxOf(
        WindowInsets.ime.getBottom(density),
        WindowInsets.navigationBars.getBottom(density),
    )
    fun anchorFor(scrollX: Int, scrollY: Int) = PopupAnchor(
        left = (boxOrigin.x + localAnchorRect.left + scrollX).roundToInt(),
        top = (boxOrigin.y + localAnchorRect.top + scrollY).roundToInt(),
        bottom = (boxOrigin.y + localAnchorRect.bottom + scrollY).roundToInt(),
    )

    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val safeBottomPx = (containerSize.height - safeBottomInsetPx).coerceAtLeast(safeTopPx)
    val anchorVisible by remember(boxOrigin, localAnchorRect, scrollState, singleLine, safeTopPx, safeBottomPx) {
        derivedStateOf {
            val anchor = anchorFor(0, if (!singleLine) -scrollState.value else 0)
            anchor.bottom >= safeTopPx && anchor.top <= safeBottomPx
        }
    }
    if (!anchorVisible) {
        LaunchedEffect(Unit) { latestOnDismiss() }
        return
    }

    val initialScrollY = remember(localAnchorRect, boxOrigin, singleLine) { if (!singleLine) -scrollState.value else 0 }
    val edgeMarginPx = with(density) { 16.dp.roundToPx() }
    val safeLeft = if (fieldWidth > 0) (fieldLeft - edgeMarginPx * 2).coerceAtLeast(0) else 0
    val safeRight = if (fieldWidth > 0) (fieldLeft + fieldWidth + edgeMarginPx * 2).coerceAtMost(containerSize.width) else containerSize.width
    val popupWidthPx = minOf(
        with(density) { 144.dp.roundToPx() },
        (safeRight - safeLeft - edgeMarginPx * 2).coerceAtLeast(1),
    )

    val viewport = PopupViewport(
        height = containerSize.height,
        safeTop = safeTopPx,
        safeBottom = safeBottomPx,
        edgeMargin = edgeMarginPx,
        anchorGap = with(density) { 8.dp.roundToPx() },
        safeLeft = safeLeft,
        safeRight = safeRight,
    )
    val constraints = PopupConstraints(
        width = popupWidthPx,
        minHeightCap = with(density) { 148.dp.roundToPx() },
        maxHeightCap = with(density) { 236.dp.roundToPx() },
        minAboveHeight = with(density) { 120.dp.roundToPx() },
    )
    val baseSpec = computePopupLayout(viewport, anchorFor(0, initialScrollY), constraints)
    if (baseSpec.maxHeight <= 0) {
        LaunchedEffect(Unit) { latestOnDismiss() }
        return
    }

    val shadowPadH = 14.dp
    val shadowPadTop = 10.dp
    val shadowPadBottom = 16.dp
    val shadowPadHPx = with(density) { shadowPadH.roundToPx() }
    val shadowPadTopPx = with(density) { shadowPadTop.roundToPx() }
    val shadowPadBottomPx = with(density) { shadowPadBottom.roundToPx() }

    val positionProvider = remember(viewport, constraints, localAnchorRect, boxOrigin, scrollState, singleLine, shadowPadHPx, shadowPadTopPx, shadowPadBottomPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val cardHeight = (popupContentSize.height - shadowPadTopPx - shadowPadBottomPx).coerceAtLeast(0)
                val liveAnchor = anchorFor(if (singleLine) -scrollState.value else 0, if (!singleLine) -scrollState.value else 0)
                val spec = computePopupLayout(viewport, liveAnchor, constraints)
                return IntOffset(spec.x - shadowPadHPx, spec.resolveY(cardHeight) - shadowPadTopPx)
            }
        }
    }

    val popupWidthDp = with(density) { popupWidthPx.toDp() }
    val maxHeightDp = with(density) { baseSpec.maxHeight.toDp() }
    val pickerLabel = stringResource(R.string.forward_variable_picker)
    val origin = if (baseSpec.placeAbove) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)

    val revealAlpha = remember { Animatable(0f) }
    val revealScale = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) {
        launch { revealAlpha.animateTo(1f, tween(140, easing = Motion.EmphasizedDecelerate)) }
        launch { revealScale.animateTo(1f, tween(140, easing = Motion.EmphasizedDecelerate)) }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = revealAlpha.value
                    scaleX = revealScale.value
                    scaleY = revealScale.value
                    transformOrigin = origin
                }
                .padding(start = shadowPadH, end = shadowPadH, top = shadowPadTop, bottom = shadowPadBottom),
        ) {
            Box(
                Modifier
                    .width(popupWidthDp)
                    .heightIn(max = maxHeightDp)
                    .popupSoftShadow(cornerRadius = PickerCorner)
                    .clip(PickerShape)
                    .background(Color.White)
                    .border(1.dp, Palette.Ink.copy(alpha = 0.10f), PickerShape)
                    .semantics { contentDescription = pickerLabel },
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items.forEach { variable ->
                        val missing = !isGranted(variable)
                        val selected = !missing && variable == activeVariable
                        val chipColors = variable.chipColor()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(PickerRowShape)
                                .background(if (selected) chipColors.background else Color.Transparent)
                                .clickable(role = Role.Button) {
                                    if (missing) onMissingPermission(variable) else onSelect(variable)
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (missing) Palette.Outline.copy(alpha = 0.45f) else chipColors.accent),
                            )
                            Text(
                                text = variable.displayName(language),
                                style = Type.Label.copy(
                                    fontSize = ChipFontSize,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                color = when {
                                    missing -> Palette.Muted.copy(alpha = 0.62f)
                                    selected -> chipColors.foreground
                                    else -> Palette.Ink
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.popupSoftShadow(cornerRadius: Dp): Modifier = drawWithCache {
    val radiusPx = cornerRadius.toPx()
    val ambientOffsetY = 6.dp.toPx()
    val ambientSpread = 1.dp.toPx()
    val ambientPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Ink.copy(alpha = 0.16f).toArgb()
        maskFilter = BlurMaskFilter(12.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val contactOffsetY = 2.dp.toPx()
    val contactPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Ink.copy(alpha = 0.10f).toArgb()
        maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                -ambientSpread,
                ambientOffsetY - ambientSpread,
                size.width + ambientSpread,
                size.height + ambientOffsetY + ambientSpread,
                radiusPx + ambientSpread,
                radiusPx + ambientSpread,
                ambientPaint,
            )
            canvas.nativeCanvas.drawRoundRect(
                0f,
                contactOffsetY,
                size.width,
                size.height + contactOffsetY,
                radiusPx,
                radiusPx,
                contactPaint,
            )
        }
    }
}
