package com.hypai.fion.ui.screen

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.missingGroups
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.bark.endpoint
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.liveChannels
import com.hypai.fion.store.Settings
import com.hypai.fion.store.Stats
import com.hypai.fion.ui.design.ActionRow
import com.hypai.fion.ui.design.BadgeStyle
import com.hypai.fion.ui.design.EnvelopeDecor
import com.hypai.fion.ui.design.GlassCard
import com.hypai.fion.ui.design.Icon
import com.hypai.fion.ui.design.IconButton
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.LocalPaneOverlay
import com.hypai.fion.ui.design.MetalSim
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.ShieldDecor
import com.hypai.fion.ui.design.SimDecor
import com.hypai.fion.ui.design.SimMetal
import com.hypai.fion.ui.design.StatusPill
import com.hypai.fion.ui.design.StopwatchDecor
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.design.glass
import com.hypai.fion.ui.design.pressScale
import com.hypai.fion.ui.design.toggle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    val floor = remember(style, minFontSize) { style.copy(fontSize = minFontSize) }
    val stepAutoSize = remember(minFontSize, style.fontSize) {
        TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize)
    }
    Layout(
        content = {
            candidates.forEach {
                Text(it, style = style, maxLines = 1, autoSize = stepAutoSize, color = color)
            }
        },
        modifier = modifier.clearAndSetSemantics { contentDescription = candidates.first() },
    ) { measurables, constraints ->
        val fitting = candidates.indexOfFirst { measurer.measure(it, floor, maxLines = 1).size.width <= constraints.maxWidth }
        val text = measurables[if (fitting >= 0) fitting else candidates.lastIndex].measure(constraints)
        val baseline = text[FirstBaseline]
        val lines: Map<AlignmentLine, Int> =
            if (baseline != AlignmentLine.Unspecified) mapOf(FirstBaseline to baseline) else emptyMap()
        layout(text.width, text.height, lines) { text.place(0, 0) }
    }
}

internal sealed interface HomeNotice {
    data object Bark : HomeNotice
    data class Permission(val group: PermissionGroup) : HomeNotice
}

internal fun homeNotice(settings: Settings, readiness: Readiness): HomeNotice? {
    if (EventKind.entries.none(settings::forwards)) return null
    if (settings.bark.endpoint() == null) return HomeNotice.Bark
    return readiness.missingGroups(settings).firstOrNull()?.let(HomeNotice::Permission)
}

@Composable
fun HomeScreen(
    settings: Settings,
    stats: Stats,
    readiness: Readiness,
    horizontalFold: Rect?,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onOpenSettings: (() -> Unit)?,
    onOpenBark: () -> Unit,
) {
    val scroll = rememberScrollState()
    var askingPermission by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) context.openAppDetails() }
    val notice = homeNotice(settings, readiness)
    val effectiveForwarding = settings.liveChannels(readiness).isNotEmpty()
    val targetKinds = settings.channels.ifEmpty { EventKind.entries.toSet() }
    val insetsModifier = if (LocalPaneOverlay.current == null) {
        Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
    } else {
        Modifier
    }
    FitHeroLayout(
        Modifier
            .fillMaxSize()
            .then(insetsModifier)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp),
        scroll = scroll,
        fold = horizontalFold,
        top = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_notification), contentDescription = stringResource(R.string.app_name), Modifier.size(30.dp), tint = Palette.Ink)
                }
                Box(Modifier.weight(1f))
                if (onOpenSettings != null) {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Gear, contentDescription = stringResource(R.string.settings_title), tint = Palette.Ink) }
                } else {
                    StatusPill(
                        text = stringResource(if (effectiveForwarding) R.string.home_status_on else R.string.home_status_off),
                        background = if (effectiveForwarding) Palette.NavySurface else Palette.Dormant,
                        color = if (effectiveForwarding) Palette.NavyDeep else Palette.Muted,
                        modifier = Modifier.padding(end = 6.dp).height(28.dp),
                        dot = if (effectiveForwarding) Palette.Navy else Palette.Outline,
                        style = Type.Secondary,
                        fontWeight = FontWeight.Medium,
                        padding = PaddingValues(horizontal = 12.dp),
                    )
                }
            }
        },
        hero = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PowerButton(
                    on = effectiveForwarding,
                    onToggle = {
                        when {
                            effectiveForwarding -> onUpdate { it.withForwarding(false) }
                            targetKinds.any(readiness::allows) -> onUpdate { it.withForwarding(true) }
                            else -> askingPermission = true
                        }
                    },
                )
            }
        },
    ) {
        Column {
            AnimatedContent(
                targetState = notice,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(100)) using
                        SizeTransform { _, _ -> tween(300, easing = Motion.Standard) }
                },
                label = "notice",
            ) { shown ->
                val gap = Modifier.padding(bottom = SectionGap)
                when (shown) {
                    HomeNotice.Bark -> NoticeBanner(shown, gap, onOpenBark)
                    is HomeNotice.Permission -> NoticeBanner(shown, gap) { request(shown.group) }
                    null -> Box(Modifier.fillMaxWidth())
                }
            }
            TotalCard(stats)
        }
        Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(SectionGap)) {
            MetricCard(
                icon = Icons.Check,
                tint = Palette.Navy,
                iconBackground = Palette.Navy.copy(alpha = 0.14f),
                title = stringResource(R.string.home_success_rate),
                value = listOf(stats.successRate?.let { String.format(Locale.US, "%.1f", it * 100) } ?: "--"),
                unit = "%",
                decoration = { ShieldDecor(Modifier.align(Alignment.BottomEnd).offset(20.dp, 26.dp)) },
            )
            MetricCard(
                icon = Icons.Bolt,
                tint = Palette.Copper,
                iconBackground = Palette.CopperGlow.copy(alpha = 0.18f),
                title = stringResource(R.string.home_latency),
                value = stats.averageLatencyMs?.fitCandidates() ?: listOf("--"),
                unit = "ms",
                decoration = { StopwatchDecor(Modifier.align(Alignment.BottomEnd).offset(22.dp, 30.dp)) },
            )
        }
        SimCard(stats)
    }

    if (askingPermission) {
        PermissionSheet(
            prompt = PermissionPrompt.Forwarding(targetKinds.toList()) { onUpdate { it.withForwarding(true) } },
            readiness = readiness,
            onDismiss = { askingPermission = false },
        )
    }
}

private val PowerMax = 208.dp
private val PowerMin = 108.dp
private const val POWER_MIDDLE_RATIO = 168f / 208f
private const val POWER_CORE_RATIO = 128f / 168f
private const val POWER_ICON_RATIO = 40f / 128f
private val ContentMaxWidth = 560.dp
private val SectionGap = 10.dp
private val CardPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private val SimShareMax = 140.dp

internal data class HeroSplit(val heroHeight: Int, val statsY: Int, val totalHeight: Int)

internal fun heroSplit(
    minHeight: Int,
    topHeight: Int,
    statsHeight: Int,
    minHeroHeight: Int,
    gap: Int,
    foldTop: Int? = null,
    foldBottom: Int? = null,
): HeroSplit {
    if (foldTop != null && foldBottom != null && foldTop > topHeight + minHeroHeight + gap && foldBottom < minHeight) {
        val heroHeight = foldTop - topHeight - gap
        val statsY = max(foldBottom, minHeight - statsHeight)
        return HeroSplit(heroHeight, statsY, max(statsY + statsHeight, minHeight))
    }
    val heroHeight = max(minHeight - topHeight - statsHeight - 2 * gap, minHeroHeight)
    val totalHeight = max(topHeight + heroHeight + statsHeight + 2 * gap, minHeight)
    return HeroSplit(heroHeight, totalHeight - statsHeight, totalHeight)
}

@Composable
private fun FitHeroLayout(
    modifier: Modifier,
    scroll: ScrollState,
    fold: Rect?,
    top: @Composable () -> Unit,
    hero: @Composable () -> Unit,
    stats: @Composable ColumnScope.() -> Unit,
) {
    val statsColumn: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(SectionGap), content = stats) }
    var originY by remember { mutableIntStateOf(0) }
    Layout(
        contents = listOf(top, hero, statsColumn),
        modifier = modifier.onPlaced { originY = it.positionInWindow().y.roundToInt() + scroll.value },
    ) { (topSlot, heroSlot, statsSlot), constraints ->
        val width = constraints.maxWidth
        val contentWidth = min(width, ContentMaxWidth.roundToPx())
        val loose = Constraints(maxWidth = contentWidth)
        val gap = SectionGap.roundToPx()
        val topBar = topSlot.single().measure(loose)
        val statsBlock = statsSlot.single().measure(loose)
        val split = heroSplit(
            minHeight = constraints.minHeight,
            topHeight = topBar.height,
            statsHeight = statsBlock.height,
            minHeroHeight = PowerMin.roundToPx(),
            gap = gap,
            foldTop = fold?.let { it.top.roundToInt() - originY },
            foldBottom = fold?.let { it.bottom.roundToInt() - originY },
        )
        val heroBlock = heroSlot.single().measure(Constraints.fixed(contentWidth, split.heroHeight))
        val x = (width - contentWidth) / 2
        layout(width, split.totalHeight) {
            topBar.place(x, 0)
            heroBlock.place(x, topBar.height + gap)
            statsBlock.place(x, split.statsY)
        }
    }
}

@Composable
private fun PowerButton(on: Boolean, onToggle: () -> Unit) {
    val label = stringResource(R.string.home_power)
    val state = stringResource(if (on) R.string.home_power_on else R.string.home_power_off)
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(250, easing = Motion.EmphasizedDecelerate), label = "power")
    Box(
        Modifier
            .sizeIn(minWidth = PowerMin, minHeight = PowerMin, maxWidth = PowerMax, maxHeight = PowerMax)
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(CircleShape)
            .drawWithCache {
                val offGlow = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.4f),
                    0.72f to Color.White.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                )
                val onGlow = Brush.radialGradient(
                    0f to Palette.NavyMid.copy(alpha = 0.35f),
                    0.72f to Color.White.copy(alpha = 0.18f),
                    1f to Color.Transparent,
                )
                onDrawBehind {
                    val p = progress
                    if (p < 1f) drawCircle(offGlow, alpha = 1f - p)
                    if (p > 0f) drawCircle(onGlow, alpha = p)
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            .clickable(interaction, LocalIndication.current, role = Role.Switch) {
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
                .fillMaxSize(POWER_MIDDLE_RATIO)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.32f))
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize(POWER_CORE_RATIO)
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
                    Modifier.fillMaxSize(POWER_ICON_RATIO).drawBehind {
                        with(power) { draw(size, colorFilter = ColorFilter.tint(lerp(Palette.Muted, Color.White, progress))) }
                    },
                )
            }
        }
    }
}

private class NoticeLook(
    val badge: BadgeStyle,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:StringRes val action: Int,
)

private val BarkBadge = BadgeStyle(Icons.Bell, Palette.AmberSurface, Palette.Copper)
private val BannerPadding = PaddingValues(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)

private val HomeNotice.look: NoticeLook
    get() = when (this) {
        HomeNotice.Bark -> NoticeLook(BarkBadge, R.string.home_notice_bark, R.string.home_notice_bark_summary, R.string.home_notice_setup)
        is HomeNotice.Permission -> {
            val (title, summary) = when (group) {
                PermissionGroup.Sms -> R.string.home_notice_sms to R.string.home_notice_sms_summary
                PermissionGroup.CallLog -> R.string.home_notice_call to R.string.home_notice_call_summary
                PermissionGroup.Phone -> R.string.home_notice_phone to R.string.home_notice_phone_summary
                PermissionGroup.Notifications -> R.string.keep_notification to R.string.keep_notification_blocked
            }
            NoticeLook(group.look.badge, title, summary, R.string.home_notice_grant)
        }
    }

@Composable
private fun NoticeBanner(notice: HomeNotice, modifier: Modifier, onClick: () -> Unit) {
    val look = notice.look
    ActionRow(
        badge = look.badge,
        title = stringResource(look.title),
        subtitle = stringResource(look.summary),
        action = stringResource(look.action),
        onAction = onClick,
        modifier = modifier.glass(),
        contentPadding = BannerPadding,
        clickableRow = true,
    )
}

@Composable
private fun TotalCard(stats: Stats) {
    GlassCard(
        padding = CardPadding,
        spacing = 10.dp,
        decoration = { EnvelopeDecor(Modifier.align(Alignment.TopEnd).offset((-14).dp, (-2).dp)) },
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
            Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { contentDescription = breakdown },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val bar = RoundedCornerShape(4.dp)
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
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(label, style = Type.Secondary, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    GlassCard(Modifier.weight(1f).fillMaxHeight(), PaddingValues(horizontal = 16.dp, vertical = 12.dp), spacing = 8.dp, decoration = decoration) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = tint)
            }
            Text(title, style = Type.Label, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(value, Type.Metric, 16.sp, Palette.Ink, Modifier.alignByBaseline().weight(1f, fill = false))
            Text(unit, Modifier.alignByBaseline(), style = Type.Body, color = Palette.Muted)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    val shown by animateFloatAsState(fraction, tween(600, easing = Motion.EmphasizedDecelerate), label = "progress")
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.Track).drawBehind {
            drawRoundRect(color, size = size.copy(width = size.width * shown), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

@Composable
private fun SimCard(stats: Stats) {
    GlassCard(
        Modifier.animateContentSize(tween(300, easing = Motion.Standard)),
        padding = CardPadding,
        spacing = 10.dp,
        decoration = { SimDecor(Modifier.align(Alignment.TopEnd).offset(8.dp, (-26).dp)) },
    ) {
        Text(stringResource(R.string.home_by_sim), style = Type.CardTitle, color = Palette.Ink)
        if (stats.sims.isEmpty()) {
            Text(stringResource(R.string.home_no_records), style = Type.Body, color = Palette.Muted)
        }
        val total = stats.delivered.coerceAtLeast(1L)
        stats.sims.values.sortedByDescending { it.count }.forEach { (sim, count) ->
            val share = count.toFloat() / total
            val slotName = sim.slot?.let { stringResource(R.string.sim_slot_label, it + 1) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetalSim(sim.slot, Modifier.semantics { slotName?.let { contentDescription = it } })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
