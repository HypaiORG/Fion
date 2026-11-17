package com.hypai.fion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    gutter: Dp = 16.dp,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = gutter).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

private val PrimaryButtonBrush = Brush.verticalGradient(listOf(Color(0xFF25375C), Palette.Ink))

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressedScale = 0.97f)
            .height(54.dp)
            .clip(CircleShape)
            .background(PrimaryButtonBrush)
            .clickable(interaction, ripple(color = Color.White), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 20.dp),
            style = Type.Field,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val FieldCorner = 14.dp

val FieldShape = RoundedCornerShape(FieldCorner)

val RowShape = RoundedCornerShape(20.dp)

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    hintColor: Color = Palette.Muted,
    isError: Boolean = false,
    prefix: String? = null,
    lines: Int = 1,
    textStyle: TextStyle = Type.Field,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = animateColorAsState(
        when {
            focused -> Palette.Navy
            isError -> Palette.Error
            else -> Palette.Ink.copy(alpha = 0.14f)
        },
        tween(150),
        label = "fieldBorder",
    )
    val borderWidth = animateDpAsState(if (focused) 2.dp else 1.dp, tween(150), label = "fieldBorderWidth")
    val singleLine = lines == 1
    Column(modifier.fillMaxWidth().animateContentSize(tween(200, easing = Motion.Standard)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            textStyle = textStyle.copy(color = Palette.Ink),
            singleLine = singleLine,
            minLines = lines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            cursorBrush = SolidColor(Palette.Navy),
            decorationBox = { field ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(FieldShape)
                        .background(Color.White.copy(alpha = 0.72f))
                        .drawWithContent {
                            drawContent()
                            val width = borderWidth.value.toPx()
                            val radius = FieldCorner.toPx() - width / 2
                            drawRoundRect(
                                borderColor.value,
                                topLeft = Offset(width / 2, width / 2),
                                size = Size(size.width - width, size.height - width),
                                cornerRadius = CornerRadius(radius),
                                style = Stroke(width),
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (singleLine) 6.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (singleLine) 2.dp else 4.dp),
                    ) {
                        Text(label, style = Type.Caption, color = Palette.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (prefix != null) Text(prefix, style = textStyle, color = Palette.Muted)
                            Box(Modifier.weight(1f)) { field() }
                        }
                    }
                    trailing()
                }
            },
        )
        if (hint != null) {
            val color by animateColorAsState(hintColor, tween(150), label = "fieldHint")
            Text(hint, Modifier.padding(horizontal = 16.dp), style = Type.Caption, color = color)
        }
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
        Column(Modifier.weight(1f, fill = false), content = content)
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
    action: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(padding = PaddingValues(horizontal = 16.dp, vertical = 20.dp), spacing = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, iconBackground, iconTint, size = 32.dp, iconSize = 16.dp)
            Text(title, Modifier.weight(1f), style = Type.CardTitle, color = Palette.Ink)
            Box(Modifier.overhangVertically()) { action() }
        }
        content()
    }
}

private fun Modifier.overhangVertically(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.place(0, -placeable.height / 2) }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    background: Color = Palette.Neutral,
    tint: Color = Palette.Ink,
    shape: Shape = CircleShape,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
) {
    Box(Modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = Palette.Ink,
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RowShape)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Type.RowTitle, color = titleColor)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Type.Secondary, color = Palette.Muted)
        }
        trailing()
    }
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { on: Boolean ->
                haptics.toggle(on)
                change(on)
            }
        },
        thumbContent = if (checked) {
            { Icon(Icons.Check, contentDescription = null, Modifier.size(14.dp)) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedTrackColor = Palette.Navy,
            checkedThumbColor = Color.White,
            checkedIconColor = Palette.Navy,
            uncheckedTrackColor = Palette.Sand,
            uncheckedThumbColor = Palette.Outline,
            uncheckedBorderColor = Palette.Hairline,
        ),
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leading: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = leading,
        onClick = {
            haptics.toggle(!checked)
            onCheckedChange(!checked)
        },
        role = Role.Switch,
    ) { AppSwitch(checked, onCheckedChange = null) }
}

@Composable
fun RadioOption(label: String, selected: Boolean, trailing: @Composable () -> Unit = {}, onSelect: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(selected = selected, role = Role.RadioButton) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect()
            }
            .padding(start = 16.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioMark(selected)
        Text(label, Modifier.weight(1f), style = Type.Option, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
private fun RadioMark(on: Boolean) {
    val ring by animateColorAsState(if (on) Palette.Navy else Palette.Muted, tween(150), label = "radioRing")
    val dot by animateDpAsState(if (on) 10.dp else 0.dp, tween(200, easing = Motion.EmphasizedDecelerate), label = "radioDot")
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(dot).clip(CircleShape).background(Palette.Navy))
        }
    }
}

@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val haptics = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(40.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = {
                    if (value != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(value)
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                border = SegmentedButtonDefaults.borderStroke(Palette.Outline),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Palette.NavySurface,
                    activeContentColor = Palette.NavyDeep,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Palette.Ink,
                ),
            ) { Text(label, style = Type.Secondary, maxLines = 1) }
        }
    }
}
