package com.hypai.fion.ui.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.hypai.fion.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(24.dp)

fun Modifier.glass(shape: Shape = CardShape): Modifier =
    clip(shape).background(Palette.Glass).border(1.dp, Palette.GlassBorder, shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 0.dp,
    decoration: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().glass()) {
        decoration()
        Column(
            Modifier.fillMaxWidth().padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImeFocusClearEffect() {
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val imeVisible = WindowInsets.isImeVisible
    var leftWindow by remember { mutableStateOf(false) }
    LaunchedEffect(windowFocused, imeVisible) {
        when {
            !windowFocused -> leftWindow = true
            imeVisible -> leftWindow = false
            !leftWindow -> focusManager.clearFocus()
        }
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = stringResource(R.string.back), tint = Palette.Ink) }
        }
        Text(
            title,
            Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 16.dp),
            style = Type.PageTitle,
            color = Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScreenShell(
    title: String,
    onBack: (() -> Unit)?,
    clearFocusOnImeHide: Boolean,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (clearFocusOnImeHide) ImeFocusClearEffect()
    val insetsModifier = when {
        LocalPaneOverlay.current == null -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        clearFocusOnImeHide -> Modifier.imePadding()
        else -> Modifier
    }
    Column(Modifier.fillMaxSize().then(insetsModifier)) {
        ScreenTopBar(title, onBack)
        body()
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    gutter: Dp = 16.dp,
    spacing: Dp = 12.dp,
    clearFocusOnImeHide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenShell(title, onBack, clearFocusOnImeHide) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = gutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

@Composable
fun LazyScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    gutter: Dp = 16.dp,
    spacing: Dp = 12.dp,
    clearFocusOnImeHide: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    ScreenShell(title, onBack, clearFocusOnImeHide) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val DialogShape = RoundedCornerShape(28.dp)
private val SheetTopGap = 48.dp
private val SheetDismissDistance = 96.dp
private val SheetDismissVelocity = 400.dp
private val FullscreenDialogProperties = DialogProperties(
    dismissOnBackPress = true,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false,
)

private val SheetEnter = slideInVertically(tween(300, easing = Motion.EmphasizedDecelerate)) { it }
private val SheetExit = slideOutVertically(tween(200, easing = Motion.EmphasizedAccelerate)) { it }
private val DialogEnter = fadeIn(tween(150)) + scaleIn(tween(250, easing = Motion.EmphasizedDecelerate), initialScale = 0.9f)
private val DialogExit = fadeOut(tween(120)) + scaleOut(tween(150, easing = Motion.EmphasizedAccelerate), targetScale = 0.94f)

@Composable
private fun FullscreenOverlay(
    onDismiss: () -> Unit,
    enter: EnterTransition,
    exit: ExitTransition,
    contentAlignment: Alignment,
    content: @Composable (requestClose: (after: () -> Unit) -> Unit) -> Unit,
) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    var pendingAfter by remember { mutableStateOf<(() -> Unit)?>(null) }
    val latestDismiss by rememberUpdatedState(onDismiss)

    fun startClose(after: () -> Unit) {
        if (!shown.targetState) return
        pendingAfter = after
        shown.targetState = false
    }

    LaunchedEffect(shown.currentState, shown.isIdle, shown.targetState) {
        if (!shown.targetState && shown.isIdle && !shown.currentState) {
            val after = pendingAfter
            pendingAfter = null
            after?.invoke()
        }
    }

    Dialog(onDismissRequest = { startClose(latestDismiss) }, properties = FullscreenDialogProperties) {
        val view = LocalView.current
        DisposableEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.run {
                setDimAmount(0f)
                setWindowAnimations(0)
            }
            onDispose {}
        }
        OverlayBackdrop(shown, shown.targetState, { startClose(latestDismiss) }, enter, exit, contentAlignment) {
            content(::startClose)
        }
    }
}

@Composable
fun AppSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.(close: (after: () -> Unit) -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.(close: (after: () -> Unit) -> Unit) -> Unit,
) {
    val host = LocalPaneOverlay.current
    if (host != null) {
        val close: (() -> Unit) -> Unit = { after ->
            host.pendingAfterExit = after
            onDismiss()
        }
        PaneOverlay(host, onDismiss, SheetEnter, SheetExit, Alignment.BottomCenter) {
            SheetSurface(inPane = true, onDismiss = onDismiss) {
                SheetBody(title, subtitle, actions?.let { { it(close) } }) { content(close) }
            }
        }
        return
    }
    FullscreenOverlay(onDismiss = onDismiss, enter = SheetEnter, exit = SheetExit, contentAlignment = Alignment.BottomCenter) { requestClose ->
        val close: (() -> Unit) -> Unit = { after ->
            requestClose {
                onDismiss()
                after()
            }
        }
        SheetSurface(inPane = false, onDismiss = { close {} }) {
            SheetBody(title, subtitle, actions?.let { { it(close) } }) { content(close) }
        }
    }
}

@Composable
private fun ColumnScope.SheetBody(
    title: String,
    subtitle: String,
    actions: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(title, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp), style = Type.SheetTitle, color = Palette.Ink)
    Text(subtitle, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), style = Type.Body, color = Palette.Muted)
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
    if (actions != null) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SheetSurface(
    inPane: Boolean,
    onDismiss: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    var dragY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val dismissDistance = with(density) { SheetDismissDistance.toPx() }
    val dismissVelocity = with(density) { SheetDismissVelocity.toPx() }

    fun updateDrag(next: Float) {
        settleJob?.cancel()
        settleJob = null
        dragY = next.coerceAtLeast(0f)
    }

    fun settle(velocity: Float) {
        if (dragY > dismissDistance || velocity > dismissVelocity) {
            latestDismiss()
        } else if (dragY > 0f) {
            val start = dragY
            settleJob?.cancel()
            settleJob = scope.launch {
                val anim = Animatable(start)
                anim.animateTo(0f, tween(200, easing = Motion.EmphasizedDecelerate)) {
                    dragY = value
                }
            }
        }
    }

    val nestedScroll = remember(dismissDistance, dismissVelocity) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragY > 0f) {
                    val consumed = available.y.coerceAtLeast(-dragY)
                    updateDrag(dragY + consumed)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    updateDrag(dragY + available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val dragState = rememberDraggableState { delta -> updateDrag(dragY + delta) }
    val safeModifier = if (inPane) Modifier else Modifier.statusBarsPadding().imePadding()
    val bottomInset = if (inPane) Modifier else Modifier.navigationBarsPadding()

    Column(
        Modifier
            .then(safeModifier)
            .padding(top = SheetTopGap)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .offset { IntOffset(0, dragY.roundToInt()) }
            .clip(SheetShape)
            .background(Palette.Sheet)
            .blockPaneTouches()
            .nestedScroll(nestedScroll)
            .draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) })
            .then(bottomInset),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 18.dp)
                .size(32.dp, 4.dp)
                .clip(CircleShape)
                .background(Palette.Ink.copy(alpha = 0.28f)),
        )
        body()
    }
}

@Composable
fun SheetButton(text: String, filled: Boolean, color: Color = Palette.Navy, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (filled) color else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (filled) 24.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Label, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else color, maxLines = 1)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val card: @Composable (dismissAction: () -> Unit, confirmAction: () -> Unit) -> Unit = { doDismiss, doConfirm ->
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(DialogShape)
                .background(Palette.Sheet)
                .blockPaneTouches()
                .padding(24.dp),
        ) {
            Text(title, style = Type.PageTitle, color = Palette.Ink)
            Text(text, Modifier.padding(top = 16.dp), style = Type.Body, color = Palette.Muted)
            Row(
                Modifier.align(Alignment.End).padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetButton(dismiss, filled = false, onClick = doDismiss)
                SheetButton(confirm, filled = false, color = Palette.Error, onClick = doConfirm)
            }
        }
    }
    val host = LocalPaneOverlay.current
    if (host != null) {
        PaneOverlay(host, onDismiss, DialogEnter, DialogExit, Alignment.Center) {
            card(onDismiss, onConfirm)
        }
        return
    }
    FullscreenOverlay(onDismiss = onDismiss, enter = DialogEnter, exit = DialogExit, contentAlignment = Alignment.Center) { requestClose ->
        card({ requestClose(onDismiss) }, { requestClose(onConfirm) })
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Info) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Neutral).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = Palette.Muted)
        Text(text, style = Type.Note, color = Palette.Muted)
    }
}

@Composable
fun Chevron() {
    Icon(Icons.Chevron, contentDescription = null, Modifier.size(20.dp), tint = Palette.Muted)
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(padding = PaddingValues(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 8.dp)) {
        Text(
            title,
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            style = Type.Label,
            color = Palette.Navy,
        )
        content()
    }
}

@Composable
fun FormCard(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp), spacing = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, iconBackground, iconTint, size = 32.dp, iconSize = 16.dp)
            Text(title, Modifier.weight(1f), style = Type.CardTitle, color = Palette.Ink)
        }
        content()
    }
}

@Immutable
data class BadgeStyle(
    val icon: ImageVector,
    val background: Color = Palette.Neutral,
    val tint: Color = Palette.Ink,
)

@Composable
fun IconBadge(
    icon: ImageVector,
    background: Color = Palette.Neutral,
    tint: Color = Palette.Ink,
    shape: Shape = CircleShape,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    alert: Boolean = false,
) {
    Box {
        Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(iconSize), tint = tint)
        }
        if (alert) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Palette.CopperGlow)
                    .border(2.dp, Palette.Sheet, CircleShape),
            )
        }
    }
}

@Composable
fun IconBadge(
    style: BadgeStyle,
    shape: Shape = CircleShape,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    alert: Boolean = false,
) = IconBadge(style.icon, style.background, style.tint, shape, size, iconSize, alert)
