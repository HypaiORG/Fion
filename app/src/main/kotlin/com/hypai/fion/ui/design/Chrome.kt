package com.hypai.fion.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypai.fion.R

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
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Back, contentDescription = stringResource(R.string.back), tint = Palette.Ink) }
        Text(
            title,
            Modifier.weight(1f).padding(start = 4.dp),
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
    onBack: () -> Unit,
    clearFocusOnImeHide: Boolean,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (clearFocusOnImeHide) ImeFocusClearEffect()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ScreenTopBar(title, onBack)
        body()
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
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
    onBack: () -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Sheet,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 32.dp, height = 4.dp, color = Palette.Ink.copy(alpha = 0.28f)) },
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
}

@Composable
fun SheetButton(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (filled) {
        Button(
            onClick = onClick,
            Modifier.height(40.dp),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Navy,
                contentColor = Color.White,
                disabledContainerColor = Palette.Ink.copy(alpha = 0.10f),
                disabledContentColor = Palette.Ink.copy(alpha = 0.38f),
            ),
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) { Text(text, style = Type.Label, fontWeight = FontWeight.SemiBold) }
    } else {
        TextButton(onClick = onClick, Modifier.height(40.dp)) {
            Text(text, style = Type.Label, fontWeight = FontWeight.SemiBold, color = Palette.Navy)
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Palette.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = Palette.Navy) } },
    )
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
fun Chevron(tint: Color = Palette.Muted, size: Dp = 20.dp) {
    Icon(Icons.Chevron, contentDescription = null, Modifier.size(size), tint = tint)
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
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
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
    modifier: Modifier = Modifier,
) = IconBadge(style.icon, style.background, style.tint, shape, size, iconSize, alert, modifier)
