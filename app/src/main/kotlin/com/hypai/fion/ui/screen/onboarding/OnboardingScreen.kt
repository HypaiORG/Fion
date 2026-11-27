package com.hypai.fion.ui.screen.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.android.openBatteryExemption
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.Pane
import com.hypai.fion.ui.design.PaneRow
import com.hypai.fion.ui.design.PrimaryButton
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.design.WindowPanes
import com.hypai.fion.ui.design.currentWindowPanes
import com.hypai.fion.ui.design.glass
import com.hypai.fion.ui.screen.PermissionOutcome
import com.hypai.fion.ui.screen.rememberPermissionRequest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal sealed interface StepAction {
    data class Grant(val group: PermissionGroup) : StepAction
    data object ExemptBattery : StepAction
}

internal enum class OnboardingStep(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val button: Int,
    val noteIcon: ImageVector,
    @param:StringRes val noteTitle: Int,
    @param:StringRes val note: Int,
    val action: StepAction,
) {
    Sms(
        R.string.onb_sms_name, Icons.Sms,
        R.string.onb_sms_title, R.string.onb_sms_button,
        Icons.LockKey, R.string.onb_sms_note_title, R.string.onb_sms_note,
        StepAction.Grant(PermissionGroup.Sms),
    ),
    CallLog(
        R.string.onb_call_name, Icons.MissedCall,
        R.string.onb_call_title, R.string.onb_call_button,
        Icons.Shield, R.string.onb_call_note_title, R.string.onb_call_note,
        StepAction.Grant(PermissionGroup.CallLog),
    ),
    Phone(
        R.string.onb_phone_name, Icons.Sim,
        R.string.onb_phone_title, R.string.onb_phone_button,
        Icons.Sim, R.string.onb_phone_note_title, R.string.onb_phone_note,
        StepAction.Grant(PermissionGroup.Phone),
    ),
    Notify(
        R.string.onb_notify_name, Icons.Bell,
        R.string.onb_notify_title, R.string.onb_notify_button,
        Icons.Pulse, R.string.onb_notify_note_title, R.string.onb_notify_note,
        StepAction.Grant(PermissionGroup.Notifications),
    ),
    Battery(
        R.string.onb_battery_name, Icons.Battery,
        R.string.onb_battery_title, R.string.onb_battery_button,
        Icons.Bolt, R.string.onb_battery_note_title, R.string.onb_battery_note,
        StepAction.ExemptBattery,
    ),
    ;

    fun done(readiness: Readiness): Boolean = when (this) {
        Sms -> readiness.sms
        CallLog -> readiness.callLog
        Phone -> readiness.phone
        Notify -> readiness.notifications
        Battery -> readiness.batteryUnrestricted
    }
}

@Composable
fun OnboardingScreen(readiness: Readiness, onFinish: () -> Unit) {
    val context = LocalContext.current
    val plan = rememberSaveable { OnboardingStep.entries.filterNot { it.done(readiness) } }
    var cursor by rememberSaveable { mutableIntStateOf(0) }
    var outcomes by rememberSaveable { mutableStateOf(emptyMap<OnboardingStep, StepOutcome>()) }
    val index = plan.indices.firstOrNull { it >= cursor && !plan[it].done(readiness) }
    val request = rememberPermissionRequest { result ->
        val at = index ?: return@rememberPermissionRequest
        when (result) {
            PermissionOutcome.Granted -> Unit
            PermissionOutcome.Denied -> {
                outcomes = outcomes + (plan[at] to StepOutcome.Denied)
                cursor = at + 1
            }
            PermissionOutcome.Blocked -> outcomes = outcomes + (plan[at] to StepOutcome.Blocked)
        }
    }
    if (index == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    val step = plan[index]
    val previous = (0 until index).lastOrNull { !plan[it].done(readiness) }
    BackHandler(enabled = previous != null) { previous?.let { cursor = it } }
    val blocked = outcomes[step] == StepOutcome.Blocked
    val buttons: @Composable (Modifier) -> Unit = { modifier ->
        StepButtons(
            label = stringResource(if (blocked) R.string.onb_open_settings else step.button),
            modifier = modifier,
            onPrimary = {
                when (val action = step.action) {
                    is StepAction.Grant if blocked -> context.openAppDetails()
                    is StepAction.Grant -> request(action.group)
                    StepAction.ExemptBattery -> context.openBatteryExemption()
                }
            },
            onSkip = {
                if (!blocked) outcomes = outcomes + (step to StepOutcome.Skipped)
                cursor = index + 1
            },
        )
    }
    val panes = currentWindowPanes()
    if (panes.multi) {
        val statuses = plan.mapIndexed { i, it -> stepStatus(it.done(readiness), i == index, outcomes[it]) }
        PaneStepPage(panes, plan, statuses, step, buttons)
    } else {
        StepPage(plan, index, buttons)
    }
}

private val MinArtStageHeight = 160.dp
private val MinPaneGap = 16.dp
private val ContentMaxWidth = 560.dp
private const val PaneArtMaxScale = 1.38f
private val PaneTitle = Type.Headline.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)

@Composable
private fun StepPage(plan: List<OnboardingStep>, index: Int, buttons: @Composable (Modifier) -> Unit) {
    val step = plan[index]
    val scroll = rememberScrollState()
    OnboardingLayout(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0),
        minArt = MinArtStageHeight,
        header = {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val progress = stringResource(R.string.onb_step, index + 1, plan.size)
                Row(
                    Modifier.clearAndSetSemantics { contentDescription = progress },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    plan.forEachIndexed { i, _ ->
                        val color by animateColorAsState(
                            when {
                                i == index -> Palette.Navy
                                i < index -> Palette.Ink.copy(alpha = 0.45f)
                                else -> Palette.Ink.copy(alpha = 0.15f)
                            },
                            tween(300),
                            label = "stepDot",
                        )
                        val width by animateDpAsState(if (i == index) 28.dp else 6.dp, tween(300, easing = Motion.EmphasizedDecelerate), label = "stepWidth")
                        Box(Modifier.height(6.dp).width(width).clip(CircleShape).background(color))
                    }
                }
                StepTitle(step, Type.Headline)
            }
        },
        art = { StepArt(step, Modifier.fillMaxSize(), maxScale = 1f) },
        footer = {
            Column(Modifier.fillMaxWidth()) {
                StepNote(step)
                buttons(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp))
            }
        },
    )
}

@Composable
private fun PaneStepPage(
    panes: WindowPanes,
    plan: List<OnboardingStep>,
    statuses: List<StepStatus>,
    step: OnboardingStep,
    buttons: @Composable (Modifier) -> Unit,
) {
    PaneRow(panes, lead = panes.columns.lastIndex) {
        StepArt(step, Modifier.fillMaxSize().clipToBounds().artHalo().padding(24.dp), maxScale = PaneArtMaxScale)
        Pane {
            val scroll = rememberScrollState()
            OnboardingLayout(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll, enabled = scroll.maxValue > 0)
                    .padding(top = 24.dp, bottom = 14.dp),
                minArt = MinPaneGap,
                header = {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_notification),
                                contentDescription = stringResource(R.string.app_name),
                                Modifier.size(34.dp),
                                tint = Palette.Ink,
                            )
                            StepTitle(step, PaneTitle)
                        }
                        StepTimeline(plan, statuses, Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp))
                    }
                },
                art = { Spacer(Modifier) },
                footer = {
                    Column(Modifier.fillMaxWidth()) {
                        StepNote(step)
                        buttons(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun StepTitle(step: OnboardingStep, style: TextStyle) {
    AnimatedContent(step, transitionSpec = { stepTransition() }, label = "stepTitle") {
        Text(stringResource(it.title), style = style, color = Palette.Ink)
    }
}

@Composable
private fun StepArt(step: OnboardingStep, modifier: Modifier, maxScale: Float) {
    ArtStage(modifier, maxScale) {
        AnimatedContent(step, Modifier.fillMaxSize(), transitionSpec = { stepTransition() }, label = "stepArt") {
            Box(Modifier.fillMaxSize()) { OnboardingArt(it) }
        }
    }
}

@Composable
private fun StepNote(step: OnboardingStep) {
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .animateContentSize(tween(200, easing = Motion.Standard))
            .glass(RoundedCornerShape(22.dp)),
    ) {
        AnimatedContent(step, Modifier.fillMaxWidth(), transitionSpec = { stepTransition() }, label = "stepNote") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(it.noteIcon, Palette.Navy.copy(alpha = 0.10f), Palette.Navy, RoundedCornerShape(14.dp), 44.dp, 22.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(it.noteTitle),
                        style = Type.Field,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Ink,
                    )
                    Text(stringResource(it.note), style = Type.Secondary, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
private fun StepButtons(label: String, modifier: Modifier, onPrimary: () -> Unit, onSkip: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PrimaryButton(label, Modifier.fillMaxWidth(), onClick = onPrimary)
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSkip),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.onb_later), style = Type.Label, color = Palette.Muted) }
    }
}

private fun Modifier.artHalo(): Modifier = drawWithCache {
    val diameter = size.minDimension * 0.90f
    val halo = diameter * 1.085f / 2
    val glow = Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.85f),
        0.45f to Color.White.copy(alpha = 0.35f),
        0.7f to Color.Transparent,
        center = size.center,
        radius = halo * sqrt(2f),
    )
    val hairline = 1.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    onDrawBehind {
        drawCircle(glow, halo, size.center)
        drawCircle(Palette.Ink.copy(alpha = 0.07f), diameter * 0.97f / 2, size.center, style = Stroke(hairline))
        drawCircle(Palette.Ink.copy(alpha = 0.09f), diameter * 0.78f / 2, size.center, style = Stroke(hairline, pathEffect = dash))
    }
}

@Composable
private fun OnboardingLayout(
    modifier: Modifier,
    minArt: Dp,
    header: @Composable () -> Unit,
    art: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Layout(contents = listOf(header, art, footer), modifier = modifier) { (hSlot, aSlot, fSlot), constraints ->
        val width = constraints.maxWidth
        val contentWidth = min(width, ContentMaxWidth.roundToPx())
        val loose = Constraints(maxWidth = contentWidth)
        val headerPlaceable = hSlot.single().measure(loose)
        val footerPlaceable = fSlot.single().measure(loose)
        val artHeight = max(constraints.minHeight - headerPlaceable.height - footerPlaceable.height, minArt.roundToPx())
        val artPlaceable = aSlot.single().measure(Constraints.fixed(contentWidth, artHeight))
        val height = max(headerPlaceable.height + artHeight + footerPlaceable.height, constraints.minHeight)
        val x = (width - contentWidth) / 2
        layout(width, height) {
            headerPlaceable.place(x, 0)
            artPlaceable.place(x, headerPlaceable.height)
            footerPlaceable.place(x, height - footerPlaceable.height)
        }
    }
}

private fun AnimatedContentTransitionScope<OnboardingStep>.stepTransition(): ContentTransform =
    Motion.sharedAxisX(forward = targetState > initialState)
