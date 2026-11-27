package com.hypai.fion.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.hypai.fion.R

private val PrimaryButtonBrush = Brush.verticalGradient(listOf(Palette.ButtonTop, Palette.Ink))
private val FieldCorner = 14.dp
private val RowBleed = 8.dp

val FieldShape = RoundedCornerShape(FieldCorner)
val RowShape = RoundedCornerShape(20.dp)

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Ink,
    style: TextStyle = Type.Body,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    autoSize: TextAutoSize? = null,
) {
    val resolved = style.merge(
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = resolved,
        overflow = overflow,
        maxLines = maxLines,
        autoSize = autoSize,
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Ink,
) {
    Icon(rememberVectorPainter(imageVector), contentDescription, modifier, tint)
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Ink,
) {
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = remember(tint) { if (tint == Color.Unspecified) null else ColorFilter.tint(tint) },
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun StatusPill(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    dot: Color? = null,
    style: TextStyle = Type.Caption,
    fontWeight: FontWeight = FontWeight.SemiBold,
    padding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interaction, pressedScale = 0.97f)
            .height(54.dp)
            .clip(CircleShape)
            .background(PrimaryButtonBrush)
            .clickable(interaction, appRipple(color = Color.White), role = Role.Button, onClick = onClick),
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

private val ControlWidth = 52.dp
private val ControlHeight = 32.dp

@Composable
fun ActionPill(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .height(ControlHeight)
            .widthIn(min = ControlWidth)
            .clip(CircleShape)
            .background(Palette.NavySurface)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.Secondary, color = Palette.NavyDeep, maxLines = 1)
    }
}

@Composable
internal fun FormFieldFrame(
    label: String,
    focused: Boolean,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    prefix: String? = null,
    textStyle: TextStyle = Type.Field,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
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
    Row(
        modifier
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
                Box(Modifier.weight(1f)) { content() }
            }
        }
        trailing()
    }
}

@Composable
fun FormSelectRow(
    label: String,
    value: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormFieldFrame(
        label = label,
        focused = false,
        singleLine = true,
        modifier = modifier.clip(FieldShape).clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        trailing = { Box(Modifier.padding(end = 12.dp)) { Chevron() } },
    ) {
        Text(value, style = Type.Field, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
    textStyle: TextStyle = Type.Field,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth().animateContentSize(tween(200, easing = Motion.Standard)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            textStyle = textStyle.copy(color = Palette.Ink),
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            interactionSource = interaction,
            cursorBrush = SolidColor(Palette.Navy),
            decorationBox = { field ->
                FormFieldFrame(
                    label = label,
                    focused = focused,
                    singleLine = true,
                    isError = isError,
                    prefix = prefix,
                    textStyle = textStyle,
                    trailing = trailing,
                    content = field,
                )
            },
        )
        if (hint != null) {
            val color by animateColorAsState(hintColor, tween(150), label = "fieldHint")
            Text(hint, Modifier.padding(horizontal = 16.dp), style = Type.Caption, color = color)
        }
    }
}

@Composable
fun ActionRow(
    badge: BadgeStyle,
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ready: Boolean = false,
    clickableRow: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (clickableRow) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier)
            .heightIn(min = 56.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(badge, size = 40.dp, iconSize = 19.dp, alert = !ready)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Type.RowTitle, fontWeight = FontWeight.Medium, color = Palette.Ink)
            Text(subtitle, style = Type.Secondary, color = Palette.Muted)
        }
        if (clickableRow) {
            ActionPill(action)
        } else {
            AnimatedContent(ready, contentAlignment = Alignment.Center, transitionSpec = { Motion.Swap }, label = "rowAction") { done ->
                if (done) {
                    Text(
                        stringResource(R.string.keep_enabled),
                        Modifier.widthIn(min = ControlWidth),
                        style = Type.Secondary,
                        color = Palette.Navy,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    ActionPill(action, onClick = onAction)
                }
            }
        }
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
private fun AppSwitch(checked: Boolean) {
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(200, easing = Motion.EmphasizedDecelerate), label = "switchProgress")
    val trackColor by animateColorAsState(if (checked) Palette.Navy else Palette.Sand, tween(180), label = "switchTrack")
    val borderColor by animateColorAsState(if (checked) Color.Transparent else Palette.Hairline, tween(180), label = "switchBorder")
    val thumbColor by animateColorAsState(if (checked) Color.White else Palette.Outline, tween(180), label = "switchThumb")
    val checkPainter = rememberVectorPainter(Icons.Check)
    val checkTint = remember { ColorFilter.tint(Palette.Navy) }
    Box(
        Modifier
            .size(ControlWidth, ControlHeight)
            .drawBehind {
                val radius = size.height / 2f
                drawRoundRect(trackColor, cornerRadius = CornerRadius(radius))
                if (borderColor.alpha > 0f) {
                    val strokePx = 1.5.dp.toPx()
                    drawRoundRect(
                        borderColor,
                        topLeft = Offset(strokePx / 2f, strokePx / 2f),
                        size = Size(size.width - strokePx, size.height - strokePx),
                        cornerRadius = CornerRadius(radius - strokePx / 2f),
                        style = Stroke(strokePx),
                    )
                }
                val thumbSize = lerp(16.dp, 24.dp, progress).toPx()
                val startCenter = 16.dp.toPx()
                val centerX = startCenter + (size.width - 2f * startCenter) * progress
                val centerY = radius
                drawCircle(thumbColor, radius = thumbSize / 2f, center = Offset(centerX, centerY))
                if (progress > 0.35f) {
                    val iconPx = 14.dp.toPx()
                    translate(centerX - iconPx / 2f, centerY - iconPx / 2f) {
                        with(checkPainter) {
                            draw(Size(iconPx, iconPx), alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f), colorFilter = checkTint)
                        }
                    }
                }
            },
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
        modifier = Modifier.semantics { toggleableState = ToggleableState(checked) },
        onClick = {
            haptics.toggle(!checked)
            onCheckedChange(!checked)
        },
        role = Role.Switch,
    ) { AppSwitch(checked) }
}

private fun Modifier.bleed(inset: Dp = RowBleed): Modifier = layout { measurable, constraints ->
    val px = inset.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * px, 2 * px))
    layout(placeable.width - 2 * px, placeable.height - 2 * px) { placeable.place(-px, -px) }
}

@Composable
fun BleedSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: TextStyle = Type.Body,
    minHeight: Dp = 48.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .bleed()
            .fillMaxWidth()
            .clip(RowShape)
            .toggleable(value = checked, role = Role.Switch) {
                haptics.toggle(it)
                onCheckedChange(it)
            }
            .padding(RowBleed)
            .heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = titleStyle, color = Palette.Ink)
            Text(subtitle, style = Type.Caption, color = Palette.Muted)
        }
        AppSwitch(checked)
    }
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
fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, Palette.Outline, CircleShape)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, value ->
            val isSelected = value == selected
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Outline))
            }
            val bg by animateColorAsState(
                if (isSelected) Palette.NavySurface else Color.Transparent,
                tween(150),
                label = "segmentBg",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind { drawRect(bg) }
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(value)
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(value),
                    style = Type.Secondary,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Palette.NavyDeep else Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = Type.Secondary.fontSize),
                )
            }
        }
    }
}
