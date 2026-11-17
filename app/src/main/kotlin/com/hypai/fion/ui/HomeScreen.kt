package com.hypai.fion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.core.Settings
import com.hypai.fion.core.Stats
import com.hypai.fion.core.endpoint
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private fun Long.grouped(): String = String.format(Locale.US, "%,d", this)

private fun Long.quantity(): Int = toInt().takeIf { it.toLong() == this } ?: Int.MAX_VALUE

private fun Long.fitCandidates(): List<String> =
    listOf(grouped()) + (toString().length - 1 downTo 1).map { "9".repeat(it).toLong().grouped() + "+" }

@Composable
private fun FitText(
    candidates: List<String>,
    style: TextStyle,
    minFontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val floor = style.copy(fontSize = minFontSize)
    Layout(
        content = {
            candidates.forEach {
                Text(it, style = style, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize), color = color)
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = candidates.first() },
    ) { measurables, constraints ->
        val fitting = candidates.indexOfFirst { measurer.measure(it, floor, maxLines = 1).size.width <= constraints.maxWidth }
        val text = measurables[if (fitting >= 0) fitting else candidates.lastIndex].measure(constraints)
        layout(text.width, text.height) { text.place(0, 0) }
    }
}

@Composable
fun HomeScreen(
    settings: Settings,
    stats: Stats,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBark: () -> Unit,
) {
    val scroll = rememberScrollState()
    FitHeroLayout(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        top = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_notification), contentDescription = stringResource(R.string.app_name), Modifier.size(30.dp), tint = Palette.Ink)
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) { Icon(Icons.Gear, contentDescription = stringResource(R.string.settings_title), tint = Palette.Ink) }
            }
        },
        hero = {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PowerButton(on = settings.forwarding, onToggle = onToggle, diameter = min(maxHeight, PowerMax))
            }
        },
    ) {
        AnimatedVisibility(
            visible = settings.bark.endpoint() == null,
            enter = fadeIn(tween(200, delayMillis = 100)) + expandVertically(tween(300, easing = Motion.EmphasizedDecelerate)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(250, easing = Motion.EmphasizedAccelerate)),
        ) { UnconfiguredBanner(onOpenBark) }
        TotalCard(stats)
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = Icons.Check,
                tint = Palette.Navy,
                iconBackground = Palette.Navy.copy(alpha = 0.14f),
                title = stringResource(R.string.home_success_rate),
                value = listOf(stats.successRate?.let { String.format(Locale.US, "%.1f", it * 100) } ?: "--"),
                unit = "%",
                decoration = { ShieldDecor(Modifier.align(Alignment.BottomEnd).offset(18.dp, 22.dp)) },
            )
            MetricCard(
                icon = Icons.Bolt,
                tint = Palette.Copper,
                iconBackground = Palette.CopperGlow.copy(alpha = 0.18f),
                title = stringResource(R.string.home_latency),
                value = stats.averageLatencyMs?.fitCandidates() ?: listOf("--"),
                unit = "ms",
                decoration = { StopwatchDecor(Modifier.align(Alignment.BottomEnd).offset(20.dp, 26.dp)) },
            )
        }
        SimCard(stats)
    }
}

private val PowerMax = 208.dp
private val PowerMin = 144.dp
private val ContentMaxWidth = 560.dp
private val SectionGap = 12.dp
private val SimShareMax = 140.dp

@Composable
private fun FitHeroLayout(
    modifier: Modifier,
    top: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    stats: @Composable ColumnScope.() -> Unit,
) {
    val statsColumn: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(SectionGap), content = stats) }
    Layout(
        contents = listOf(top, hero, statsColumn),
        modifier = modifier,
    ) { (topSlot, heroSlot, statsSlot), constraints ->
        val width = constraints.maxWidth
        val contentWidth = min(width, ContentMaxWidth.roundToPx())
        val loose = Constraints(maxWidth = contentWidth)
        val gap = SectionGap.roundToPx()
        val topBar = topSlot.single().measure(loose)
        val statsBlock = statsSlot.single().measure(loose)
        val heroHeight = max(constraints.minHeight - topBar.height - statsBlock.height - 2 * gap, PowerMin.roundToPx())
        val heroBlock = heroSlot.single().measure(Constraints.fixed(contentWidth, heroHeight))
        val height = max(topBar.height + heroHeight + statsBlock.height + 2 * gap, constraints.minHeight)
        val x = (width - contentWidth) / 2
        layout(width, height) {
            topBar.place(x, 0)
            heroBlock.place(x, topBar.height + gap)
            statsBlock.place(x, height - statsBlock.height)
        }
    }
}

@Composable
private fun PowerButton(on: Boolean, onToggle: () -> Unit, diameter: Dp) {
    val scale = diameter / PowerMax
    val label = stringResource(R.string.home_power)
    val state = stringResource(if (on) R.string.home_power_on else R.string.home_power_off)
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(250, easing = Motion.EmphasizedDecelerate), label = "power")
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .drawBehind {
                val glow = lerp(Color.White.copy(alpha = 0.4f), Palette.NavyMid.copy(alpha = 0.35f), progress)
                drawCircle(Brush.radialGradient(0f to glow, 0.72f to Color.White.copy(alpha = 0.18f), 1f to Color.Transparent))
            }
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(interaction, ripple(), role = Role.Switch) {
                haptics.toggle(!on)
                onToggle()
            }
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(168.dp * scale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.32f))
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(128.dp * scale)
                    .pressScale(interaction, pressedScale = 0.92f)
                    .graphicsLayer {
                        shadowElevation = lerp(8.dp, 20.dp, progress).toPx()
                        shape = CircleShape
                        ambientShadowColor = Palette.Navy
                        spotShadowColor = Palette.Navy
                    }
                    .drawWithCache {
                        val navy = Brush.radialGradient(
                            0f to Palette.NavyLight,
                            0.45f to Palette.NavyMid,
                            1f to Palette.NavyDeep,
                            center = Offset(size.width * 0.35f, size.height * 0.3f),
                            radius = size.width * 0.95f,
                        )
                        onDrawBehind {
                            drawCircle(Color.White.copy(alpha = 0.75f * (1f - progress)))
                            drawCircle(navy, alpha = progress)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val power = rememberVectorPainter(Icons.Power)
                Box(
                    Modifier.size(40.dp * scale).drawBehind {
                        with(power) { draw(size, colorFilter = ColorFilter.tint(lerp(Palette.Muted, Color.White, progress))) }
                    },
                )
            }
        }
    }
}

@Composable
private fun UnconfiguredBanner(onOpenBark: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.AmberSurface)
            .clickable(role = Role.Button, onClick = onOpenBark)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Bell, contentDescription = null, Modifier.size(20.dp), tint = Palette.Amber)
        Text(
            stringResource(R.string.home_unconfigured),
            Modifier.weight(1f),
            style = Type.Body,
            color = Palette.Amber,
        )
        Text(stringResource(R.string.home_unconfigured_action), style = Type.Label, color = Palette.Amber)
        Chevron(Palette.Amber, 18.dp)
    }
}

@Composable
private fun TotalCard(stats: Stats) {
    GlassCard(
        spacing = 18.dp,
        decoration = { EnvelopeDecor(Modifier.align(Alignment.TopEnd).offset((-14).dp, 6.dp)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_total), style = Type.Secondary, color = Palette.Muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitText(stats.delivered.fitCandidates(), Type.Display, 20.sp, Palette.Ink, Modifier.alignByBaseline().weight(1f, fill = false))
                Text(pluralStringResource(R.plurals.home_total_unit, stats.delivered.quantity()), Modifier.alignByBaseline(), style = Type.Body, color = Palette.Muted)
            }
        }
        val breakdown = stringResource(R.string.home_breakdown, stats.sms, stats.missedCalls)
        Row(
            Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics { contentDescription = breakdown },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val bar = RoundedCornerShape(6.dp)
            if (stats.delivered == 0L) {
                Box(Modifier.weight(1f).fillMaxSize().clip(bar).background(Palette.Hairline))
            }
            if (stats.sms > 0) {
                Box(
                    Modifier.weight(stats.sms.toFloat()).fillMaxSize().clip(bar)
                        .background(Brush.horizontalGradient(listOf(Palette.NavyPale, Palette.Navy))),
                )
            }
            if (stats.missedCalls > 0) {
                Box(Modifier.weight(stats.missedCalls.toFloat()).fillMaxSize().clip(bar).background(Palette.Ink))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Breakdown(stringResource(R.string.home_sms), stats.sms, Palette.Navy)
            Breakdown(stringResource(R.string.home_missed), stats.missedCalls, Palette.Ink)
            Breakdown(stringResource(R.string.home_failed), stats.failed, Palette.Failure)
        }
    }
}

@Composable
private fun RowScope.Breakdown(label: String, count: Long, dot: Color) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(label, style = Type.Secondary, color = Palette.Muted)
        }
        FitText(count.fitCandidates(), Type.Figure, 12.sp, Palette.Ink)
    }
}

@Composable
private fun RowScope.MetricCard(
    icon: ImageVector,
    tint: Color,
    iconBackground: Color,
    title: String,
    value: List<String>,
    unit: String,
    decoration: @Composable BoxScope.() -> Unit,
) {
    GlassCard(Modifier.weight(1f).fillMaxHeight(), PaddingValues(16.dp), spacing = 12.dp, decoration = decoration) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = tint)
            }
            Text(title, style = Type.Label, color = Palette.Ink)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(value, Type.Metric, 16.sp, Palette.Ink, Modifier.alignByBaseline().weight(1f, fill = false))
            Text(unit, Modifier.alignByBaseline(), style = Type.Body, color = Palette.Muted)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val shown by animateFloatAsState(fraction, tween(600, easing = Motion.EmphasizedDecelerate), label = "progress")
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x1414213A)).drawBehind {
            drawRoundRect(color, size = size.copy(width = size.width * shown), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

@Composable
private fun SimCard(stats: Stats) {
    GlassCard(
        Modifier.animateContentSize(tween(300, easing = Motion.Standard)),
        spacing = 14.dp,
        decoration = { SimDecor(Modifier.align(Alignment.TopEnd).offset(8.dp, (-26).dp)) },
    ) {
        Text(stringResource(R.string.home_by_sim), style = Type.CardTitle, color = Palette.Ink)
        if (stats.sims.isEmpty()) {
            Text(stringResource(R.string.home_no_records), style = Type.Body, color = Palette.Muted)
        }
        stats.sims.values.sortedByDescending { it.count }.forEach { (sim, count) ->
            val share = count.toFloat() / stats.delivered
            val slotName = sim.slot?.let { stringResource(R.string.sim_slot, it + 1) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetalSim(sim.slot, Modifier.semantics { slotName?.let { contentDescription = it } })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            sim.title.ifEmpty { stringResource(R.string.unknown_sim) },
                            Modifier.weight(1f),
                            style = Type.Body,
                            color = Palette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val percent = (share * 100).toInt()
                        FitText(
                            count.fitCandidates().map { pluralStringResource(R.plurals.home_sim_share, count.quantity(), it, percent) },
                            Type.Secondary,
                            10.sp,
                            Palette.Muted,
                            Modifier.widthIn(max = SimShareMax),
                        )
                    }
                    ProgressBar(share, SimMetal.of(sim.slot).bar)
                }
            }
        }
    }
}
