package com.hypai.fion.ui.screen.forward

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplateToken
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.findTemplateTokens
import com.hypai.fion.forward.templateVariables
import com.hypai.fion.ui.design.FormFieldFrame
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
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

private val VariableChipColors: List<VariableChipColor> = TemplateVariable.entries.map { variable ->
    val accent = when (variable) {
        TemplateVariable.From -> Color(0xFF6E4CC2)
        TemplateVariable.Content -> Color(0xFFD1455F)
        TemplateVariable.CallCount -> Color(0xFFC9443A)
        TemplateVariable.Time -> Color(0xFFB8760B)
        TemplateVariable.Carrier -> Color(0xFF0F8B8D)
        TemplateVariable.SimName -> Color(0xFF238653)
        TemplateVariable.Number -> Color(0xFF1D6FD8)
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
)

@Composable
internal fun TemplateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: EventKind,
    language: Language,
    modifier: Modifier = Modifier,
    hint: String? = null,
    lines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val singleLine = lines == 1
    val variables = remember(kind) { kind.templateVariables() }
    val textState = rememberTextFieldState(initialText = value)
    val scrollState = rememberScrollState()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val insetPx = with(density) { ChipVerticalInset.toPx() }

    var lastObservedText by remember { mutableStateOf(value) }
    var lastExternalValue by remember { mutableStateOf(value) }
    var pickerTarget by remember { mutableStateOf<VariablePickerTarget?>(null) }
    var pickerScrollOffset by remember { mutableFloatStateOf(0f) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textBoxOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    val placements by remember(language, kind) {
        derivedStateOf { computeChipPlacements(textState.text.toString(), language, kind) }
    }
    val drawnChips = remember(textLayoutResult, placements, insetPx) {
        val layout = textLayoutResult ?: return@remember emptyList()
        placements.map { placement -> DrawnChip(placement, chipRects(layout, placement, insetPx)) }
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestVariables by rememberUpdatedState(variables)
    val latestKind by rememberUpdatedState(kind)
    val latestFocused by rememberUpdatedState(focused)

    LaunchedEffect(value) {
        if (value == lastExternalValue) return@LaunchedEffect
        lastExternalValue = value
        if (textState.text.toString() != value) {
            lastObservedText = value
            pickerTarget = null
            textState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(kind, language) {
        pickerTarget = null
    }

    LaunchedEffect(focused) {
        if (!focused && pickerTarget is VariablePickerTarget.Insert) {
            pickerTarget = null
        }
    }

    LaunchedEffect(pickerTarget != null, scrollState) {
        if (pickerTarget == null) return@LaunchedEffect
        snapshotFlow { scrollState.value.toFloat() }.collect { pickerScrollOffset = it }
    }

    LaunchedEffect(textState) {
        snapshotFlow {
            Triple(textState.text.toString(), textState.selection, textState.composition != null)
        }.collect { (currentText, selection, hasComposition) ->
            val prevText = lastObservedText
            val textChanged = currentText != prevText
            if (textChanged) {
                lastObservedText = currentText
                lastExternalValue = currentText
                latestOnValueChange(currentText)
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
                    kind = latestKind,
                )
                textChanged -> detectNewSlashTrigger(prevText, currentText, selection.start, selection.end)?.also {
                    pickerScrollOffset = scrollState.value.toFloat()
                }
                else -> null
            }
        }
    }

    val inputTransformation = remember { atomicTokenInputTransformation() }
    val outputTransformation = remember(language, kind) { chipOutputTransformation(language, kind) }
    val activeReplaceToken = (pickerTarget as? VariablePickerTarget.Replace)?.token

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
                    .semantics { contentDescription = label },
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
                            drawnChips = drawnChips,
                            activeReplaceToken = activeReplaceToken,
                            scrollState = scrollState,
                            singleLine = singleLine,
                            onOriginChanged = { textBoxOriginInWindow = it },
                            onChipTap = { token ->
                                pickerScrollOffset = scrollState.value.toFloat()
                                pickerTarget = VariablePickerTarget.Replace(token)
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
        val currentLayout = textLayoutResult
        if (currentTarget != null && currentLayout != null) {
            val items = remember(currentTarget, lastObservedText, variables, kind) {
                pickerItemsForTarget(currentTarget, lastObservedText, variables, kind)
            }
            val anchor = remember(
                currentTarget,
                currentLayout,
                placements,
                drawnChips,
                textBoxOriginInWindow,
                pickerScrollOffset,
                singleLine,
            ) {
                computeAnchorInWindow(
                    target = currentTarget,
                    layout = currentLayout,
                    placements = placements,
                    drawnChips = drawnChips,
                    boxOrigin = textBoxOriginInWindow,
                    scrollOffset = pickerScrollOffset,
                    singleLine = singleLine,
                )
            }
            if (anchor != null && items.isNotEmpty()) {
                VariablePickerPopup(
                    items = items,
                    activeVariable = activeReplaceToken?.variable,
                    language = language,
                    kind = kind,
                    anchor = anchor,
                    onSelect = { variable ->
                        val replacement = variable.token
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
                    onDismiss = { pickerTarget = null },
                )
            }
        }
    }
}

private fun atomicTokenInputTransformation(): InputTransformation = InputTransformation {
    val originalTokens = findTemplateTokens(originalText.toString())
    if (originalTokens.isEmpty()) return@InputTransformation
    val change = diffSingleChange(
        old = originalText,
        new = asCharSequence(),
        oldSelectionMin = originalSelection.min,
        newSelectionMin = selection.min,
    ) ?: return@InputTransformation
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

private fun chipOutputTransformation(language: Language, kind: EventKind): OutputTransformation = OutputTransformation {
    for (placement in computeChipPlacements(asCharSequence().toString(), language, kind)) {
        val rawLen = placement.token.end - placement.token.start
        replace(placement.visualStart, placement.visualStart + rawLen, placement.renderedText)
        addStyle(
            SpanStyle(
                fontSize = ChipFontSize,
                color = placement.token.variable.chipColor().foreground,
                fontWeight = FontWeight.Medium,
            ),
            start = placement.pillStart,
            end = placement.pillEnd,
        )
    }
}

@Composable
private fun TemplateTextSurface(
    drawnChips: List<DrawnChip>,
    activeReplaceToken: TemplateToken?,
    scrollState: ScrollState,
    singleLine: Boolean,
    onOriginChanged: (Offset) -> Unit,
    onChipTap: (TemplateToken) -> Unit,
    innerTextField: @Composable () -> Unit,
) {
    val latestChips by rememberUpdatedState(drawnChips)
    val latestOnChipTap by rememberUpdatedState(onChipTap)

    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned { coords -> onOriginChanged(coords.positionInWindow()) }
            .drawBehind {
                val scrollX = if (singleLine) -scrollState.value.toFloat() else 0f
                val scrollY = if (!singleLine) -scrollState.value.toFloat() else 0f
                val radius = CornerRadius(ChipCorner.toPx())
                val borderWidth = 0.75.dp.toPx()
                val activeBorderWidth = 1.25.dp.toPx()
                for ((placement, rects) in drawnChips) {
                    val chipColor = placement.token.variable.chipColor()
                    val isSelected = placement.token == activeReplaceToken
                    val stroke = Stroke(if (isSelected) activeBorderWidth else borderWidth)
                    val borderColor = if (isSelected) chipColor.foreground.copy(alpha = 0.55f) else chipColor.border
                    for (rect in rects) {
                        val topLeft = Offset(rect.left + scrollX, rect.top + scrollY)
                        val rectSize = Size(rect.width, rect.height)
                        drawRoundRect(chipColor.background, topLeft, rectSize, radius)
                        drawRoundRect(borderColor, topLeft, rectSize, radius, style = stroke)
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
                            drawnChips = latestChips,
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
                                    latestOnChipTap(hit.token)
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

private fun findHitChip(
    drawnChips: List<DrawnChip>,
    point: Offset,
    touchPaddingPx: Float,
): ChipPlacement? {
    for ((placement, rects) in drawnChips) {
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

private fun computeAnchorInWindow(
    target: VariablePickerTarget,
    layout: TextLayoutResult,
    placements: List<ChipPlacement>,
    drawnChips: List<DrawnChip>,
    boxOrigin: Offset,
    scrollOffset: Float,
    singleLine: Boolean,
): PopupAnchor? {
    val scrollX = if (singleLine) -scrollOffset else 0f
    val scrollY = if (!singleLine) -scrollOffset else 0f
    val localRect = when (target) {
        is VariablePickerTarget.Insert -> {
            val visualOffset = rawOffsetToVisual(target.slashStart, placements)
                .coerceIn(0, layout.layoutInput.text.length)
            layout.getCursorRect(visualOffset)
        }
        is VariablePickerTarget.Replace -> {
            drawnChips.firstOrNull { it.placement.token == target.token }?.rects?.firstOrNull() ?: return null
        }
    }
    return PopupAnchor(
        left = (boxOrigin.x + localRect.left + scrollX).roundToInt(),
        top = (boxOrigin.y + localRect.top + scrollY).roundToInt(),
        bottom = (boxOrigin.y + localRect.bottom + scrollY).roundToInt(),
    )
}

@Composable
private fun VariablePickerPopup(
    items: List<TemplateVariable>,
    activeVariable: TemplateVariable?,
    language: Language,
    kind: EventKind,
    anchor: PopupAnchor,
    onSelect: (TemplateVariable) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val safeTopPx = WindowInsets.statusBars.getTop(density)
    val safeBottomInsetPx = maxOf(
        WindowInsets.ime.getBottom(density),
        WindowInsets.navigationBars.getBottom(density),
    )

    val edgeMarginPx = with(density) { 16.dp.roundToPx() }
    val popupWidthPx = minOf(
        with(density) { 144.dp.roundToPx() },
        (containerSize.width - edgeMarginPx * 2).coerceAtLeast(1),
    )
    val spec = computePopupLayout(
        viewport = PopupViewport(
            width = containerSize.width,
            height = containerSize.height,
            safeTop = safeTopPx,
            safeBottom = (containerSize.height - safeBottomInsetPx).coerceAtLeast(safeTopPx),
            edgeMargin = edgeMarginPx,
            anchorGap = with(density) { 8.dp.roundToPx() },
        ),
        anchor = anchor,
        constraints = PopupConstraints(
            width = popupWidthPx,
            minHeightCap = with(density) { 148.dp.roundToPx() },
            maxHeightCap = with(density) { 236.dp.roundToPx() },
            minAboveHeight = with(density) { 120.dp.roundToPx() },
        ),
    )
    if (spec.maxHeight <= 0) return

    val shadowPadH = 20.dp
    val shadowPadTop = 16.dp
    val shadowPadBottom = 26.dp
    val shadowPadHPx = with(density) { shadowPadH.roundToPx() }
    val shadowPadTopPx = with(density) { shadowPadTop.roundToPx() }
    val shadowPadBottomPx = with(density) { shadowPadBottom.roundToPx() }

    val positionProvider = remember(spec, shadowPadHPx, shadowPadTopPx, shadowPadBottomPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val cardHeight = (popupContentSize.height - shadowPadTopPx - shadowPadBottomPx).coerceAtLeast(0)
                return IntOffset(spec.x - shadowPadHPx, spec.resolveY(cardHeight) - shadowPadTopPx)
            }
        }
    }

    val popupWidthDp = with(density) { popupWidthPx.toDp() }
    val maxHeightDp = with(density) { spec.maxHeight.toDp() }
    val pickerLabel = stringResource(R.string.forward_variable_picker)
    val origin = if (spec.placeAbove) TransformOrigin(0.5f, 1f) else TransformOrigin(0.5f, 0f)

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
            dismissOnBackPress = true,
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
            Column(
                Modifier
                    .width(popupWidthDp)
                    .heightIn(max = maxHeightDp)
                    .popupSoftShadow(cornerRadius = PickerCorner)
                    .clip(PickerShape)
                    .background(Color.White)
                    .border(1.dp, Palette.Ink.copy(alpha = 0.10f), PickerShape)
                    .semantics { contentDescription = pickerLabel }
                    .verticalScroll(rememberScrollState())
                    .padding(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { variable ->
                    val selected = variable == activeVariable
                    val chipColors = variable.chipColor()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(PickerRowShape)
                            .background(if (selected) chipColors.background else Color.Transparent)
                            .clickable(role = Role.Button) { onSelect(variable) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(chipColors.accent))
                        Text(
                            text = variable.displayName(language, kind),
                            style = Type.Label.copy(
                                fontSize = ChipFontSize,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            color = if (selected) chipColors.foreground else Palette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.popupSoftShadow(cornerRadius: Dp): Modifier = drawWithCache {
    val radiusPx = cornerRadius.toPx()
    val ambientOffsetY = 8.dp.toPx()
    val ambientSpread = 1.dp.toPx()
    val ambientPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Ink.copy(alpha = 0.16f).toArgb()
        maskFilter = BlurMaskFilter(18.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
    }
    val contactOffsetY = 2.dp.toPx()
    val contactPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Ink.copy(alpha = 0.10f).toArgb()
        maskFilter = BlurMaskFilter(6.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
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
