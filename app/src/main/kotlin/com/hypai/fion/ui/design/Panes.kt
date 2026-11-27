package com.hypai.fion.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.allVerticalHingeBounds
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.hypai.fion.R
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class WindowPanes(val columns: List<IntRange>, val horizontalFold: Rect? = null) {
    val multi: Boolean get() = columns.size > 1

    fun split(lead: Int): List<IntRange> {
        require(lead in 1 until columns.size) { "lead=$lead must be in 1..${columns.lastIndex}" }
        return listOf(
            columns.first().first..columns[lead - 1].last,
            columns[lead].first..columns.last().last,
        )
    }
}

private val PaneMargin = 12.dp
private val PaneGap = 24.dp
private val PaneMinWidth = 280.dp
private val PaneShape = RoundedCornerShape(28.dp)
private val PaneFill = Color.White.copy(alpha = 0.30f)
private val PaneBorder = Color.White.copy(alpha = 0.6f)
private val Scrim = Palette.Ink.copy(alpha = 0.34f)

private val IntRange.spanWidth: Int get() = last - first + 1

@Composable
fun currentWindowPanes(): WindowPanes {
    val adaptive = currentWindowAdaptiveInfoV2()
    val sizeClass = adaptive.windowSizeClass
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val margin = with(density) { PaneMargin.roundToPx() }
    val widthPx = LocalWindowInfo.current.containerSize.width
    val folds = adaptive.windowPosture.allVerticalHingeBounds
        .map { it.left.roundToInt() until it.right.roundToInt() }
    val horizontalFold = adaptive.windowPosture.separatingHorizontalHingeBounds.firstOrNull()
    val columns = computePaneSpans(
        start = insets.getLeft(density, layoutDirection) + margin,
        end = widthPx - insets.getRight(density, layoutDirection) - margin,
        widthAtLeastExpanded = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND),
        widthAtLeastLarge = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND),
        heightAtLeastMedium = sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        gap = with(density) { PaneGap.roundToPx() },
        minWidth = with(density) { PaneMinWidth.roundToPx() },
        folds = folds,
    )
    return WindowPanes(columns, horizontalFold)
}

internal fun computePaneSpans(
    start: Int,
    end: Int,
    widthAtLeastExpanded: Boolean,
    widthAtLeastLarge: Boolean,
    heightAtLeastMedium: Boolean,
    gap: Int,
    minWidth: Int,
    folds: List<IntRange>,
): List<IntRange> {
    if (!heightAtLeastMedium || end <= start) return listOf(start until max(end, start))
    foldAlignedSpans(start, end, gap, minWidth, folds)?.let { return it }
    val target = when {
        widthAtLeastLarge -> 3
        widthAtLeastExpanded -> 2
        else -> 1
    }
    for (count in target downTo 2) {
        equalSpans(start, end, count, gap, minWidth)?.let { return it }
    }
    return listOf(start until end)
}

private fun foldAlignedSpans(
    start: Int,
    end: Int,
    gap: Int,
    minWidth: Int,
    folds: List<IntRange>,
): List<IntRange>? {
    if (folds.size !in 1..2) return null
    val edges = buildList(folds.size * 2 + 2) {
        add(start)
        folds.sortedBy(IntRange::first).forEach { fold ->
            val foldWidth = max(fold.spanWidth, 0)
            val gutter = max(gap, foldWidth)
            val left = fold.first + foldWidth / 2 - gutter / 2
            add(left)
            add(left + gutter)
        }
        add(end)
    }
    val aligned = edges.chunked(2) { (left, right) -> left until right }
    return aligned.takeIf { spans -> spans.all { it.spanWidth >= minWidth } }
}

private fun equalSpans(start: Int, end: Int, count: Int, gap: Int, minWidth: Int): List<IntRange>? {
    val width = (end - start - gap * (count - 1)) / count
    if (width < minWidth) return null
    return List(count) { i -> (start + i * (width + gap)).let { it until it + width } }
}

@Composable
fun PaneRow(panes: WindowPanes, lead: Int = 1, content: @Composable () -> Unit) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = PaneMargin.roundToPx()
        val top = insets.getTop(this) + margin
        val bottom = insets.getBottom(this) + margin
        val height = max(constraints.maxHeight - top - bottom, 0)
        val spans = panes.split(lead)
        val placeables = measurables.mapIndexed { i, pane ->
            pane.measure(Constraints.fixed(max(spans[i].spanWidth, 0), height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, pane -> pane.place(spans[i].first, top) }
        }
    }
}

@Stable
internal class PaneOverlaySpec(
    onDismiss: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    var onDismiss by mutableStateOf(onDismiss)
    var enter by mutableStateOf(enter)
    var exit by mutableStateOf(exit)
    var contentAlignment by mutableStateOf(contentAlignment)
    var content by mutableStateOf(content)
}

@Stable
internal class PaneOverlayHost {
    var active by mutableStateOf<PaneOverlaySpec?>(null)
        internal set
    var pendingAfterExit: (() -> Unit)? = null
        internal set
}

internal val LocalPaneOverlay = staticCompositionLocalOf<PaneOverlayHost?> { null }

@Composable
fun Pane(content: @Composable () -> Unit) {
    val host = remember { PaneOverlayHost() }
    Box(
        Modifier
            .clip(PaneShape)
            .background(PaneFill)
            .border(1.dp, PaneBorder, PaneShape),
    ) {
        CompositionLocalProvider(LocalPaneOverlay provides host, content = content)
        PaneOverlayLayer(host)
    }
}

@Composable
internal fun PaneOverlay(
    host: PaneOverlayHost,
    onDismiss: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    val spec = remember { PaneOverlaySpec(onDismiss, enter, exit, contentAlignment, content) }
    spec.onDismiss = onDismiss
    spec.enter = enter
    spec.exit = exit
    spec.contentAlignment = contentAlignment
    spec.content = content
    DisposableEffect(host, spec) {
        host.active = spec
        onDispose {
            if (host.active === spec) host.active = null
        }
    }
}

@Composable
private fun BoxScope.PaneOverlayLayer(host: PaneOverlayHost) {
    val active = host.active
    var exiting by remember { mutableStateOf<PaneOverlaySpec?>(null) }
    val shown = remember { MutableTransitionState(false) }
    if (active != null && exiting !== active) exiting = active
    shown.targetState = active != null
    val spec = active ?: exiting
    LaunchedEffect(shown.currentState, shown.isIdle, active) {
        if (active == null && shown.isIdle && !shown.currentState) {
            exiting = null
            val after = host.pendingAfterExit
            host.pendingAfterExit = null
            after?.invoke()
        }
    }
    if (spec == null) return
    BackHandler(enabled = active != null) { active?.onDismiss?.invoke() }
    Box(
        Modifier
            .matchParentSize()
            .semantics {
                dialog()
                isTraversalGroup = true
            },
    ) {
        OverlayBackdrop(
            shown = shown,
            scrimEnabled = active != null,
            onScrimClick = { active?.onDismiss?.invoke() },
            enter = spec.enter,
            exit = spec.exit,
            contentAlignment = spec.contentAlignment,
        ) { key(spec) { spec.content() } }
    }
}

@Composable
internal fun OverlayBackdrop(
    shown: MutableTransitionState<Boolean>,
    scrimEnabled: Boolean,
    onScrimClick: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    val closeLabel = stringResource(R.string.close)
    val transition = rememberTransition(shown, label = "overlay")
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (!scrimEnabled) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        transition.AnimatedVisibility({ it }, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Scrim)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = scrimEnabled,
                        onClickLabel = closeLabel,
                        onClick = onScrimClick,
                    ),
            )
        }
        transition.AnimatedVisibility({ it }, Modifier.align(contentAlignment), enter = enter, exit = exit) {
            content()
        }
    }
}

internal fun Modifier.blockPaneTouches(): Modifier = pointerInput(Unit) {}
