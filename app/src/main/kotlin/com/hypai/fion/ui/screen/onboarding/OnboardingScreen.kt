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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.android.PermissionGroup
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.openAppDetails
import com.hypai.fion.android.openBatteryExemption
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.PrimaryButton
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.design.glass
import com.hypai.fion.ui.screen.PermissionOutcome
import com.hypai.fion.ui.screen.rememberPermissionRequest
import kotlin.math.max

internal sealed interface StepAction {
    data class Grant(val group: PermissionGroup) : StepAction
    data object ExemptBattery : StepAction
}

internal enum class OnboardingStep(
    @param:StringRes val title: Int,
    @param:StringRes val button: Int,
    val noteIcon: ImageVector,
    @param:StringRes val noteTitle: Int,
    @param:StringRes val note: Int,
    val action: StepAction,
) {
    Sms(
        R.string.onb_sms_title, R.string.onb_sms_button,
        Icons.LockKey, R.string.onb_sms_note_title, R.string.onb_sms_note,
        StepAction.Grant(PermissionGroup.Sms),
    ),
    CallLog(
        R.string.onb_call_title, R.string.onb_call_button,
        Icons.Shield, R.string.onb_call_note_title, R.string.onb_call_note,
        StepAction.Grant(PermissionGroup.CallLog),
    ),
    Phone(
        R.string.onb_phone_title, R.string.onb_phone_button,
        Icons.Sim, R.string.onb_phone_note_title, R.string.onb_phone_note,
        StepAction.Grant(PermissionGroup.Phone),
    ),
    Notify(
        R.string.onb_notify_title, R.string.onb_notify_button,
        Icons.Pulse, R.string.onb_notify_note_title, R.string.onb_notify_note,
        StepAction.Grant(PermissionGroup.Notifications),
    ),
    Battery(
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
    val plan = rememberSaveable { OnboardingStep.entries.filterNot { it.done(readiness) } }
    var cursor by rememberSaveable { mutableIntStateOf(0) }
    var blocked by rememberSaveable { mutableStateOf(emptySet<OnboardingStep>()) }
    val index = plan.indices.firstOrNull { it >= cursor && !plan[it].done(readiness) }
    if (index == null) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    val previous = (0 until index).lastOrNull { !plan[it].done(readiness) }
    BackHandler(enabled = previous != null) { previous?.let { cursor = it } }
    StepPage(plan, index, blocked = plan[index] in blocked, onBlocked = { blocked = blocked + it }, onSkip = { cursor = index + 1 })
}

private val MinArtStageHeight = 160.dp

@Composable
private fun StepPage(plan: List<OnboardingStep>, index: Int, blocked: Boolean, onBlocked: (OnboardingStep) -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val step = plan[index]
    val request = rememberPermissionRequest { if (it == PermissionOutcome.Blocked) onBlocked(step) }
    val scroll = rememberScrollState()

    OnboardingLayout(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scroll, enabled = scroll.maxValue > 0),
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
                AnimatedContent(step, transitionSpec = { stepTransition() }, label = "stepTitle") {
                    Text(stringResource(it.title), style = Type.Headline, color = Palette.Ink)
                }
            }
        },
        art = {
            ArtStage(Modifier.fillMaxSize()) {
                AnimatedContent(step, Modifier.fillMaxSize(), transitionSpec = { stepTransition() }, label = "stepArt") {
                    Box(Modifier.fillMaxSize()) { OnboardingArt(it) }
                }
            }
        },
        footer = {
            Column(Modifier.fillMaxWidth()) {
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
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PrimaryButton(stringResource(if (blocked) R.string.onb_open_settings else step.button), Modifier.fillMaxWidth()) {
                        when (val action = step.action) {
                            is StepAction.Grant if blocked -> context.openAppDetails()
                            is StepAction.Grant -> request(action.group)
                            StepAction.ExemptBattery -> context.openBatteryExemption()
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSkip),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.onb_later), style = Type.Label, color = Palette.Muted) }
                }
            }
        },
    )
}

@Composable
private fun OnboardingLayout(
    modifier: Modifier,
    header: @Composable () -> Unit,
    art: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    Layout(contents = listOf(header, art, footer), modifier = modifier) { (hSlot, aSlot, fSlot), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val headerPlaceable = hSlot.single().measure(loose)
        val footerPlaceable = fSlot.single().measure(loose)
        val artHeight = max(constraints.minHeight - headerPlaceable.height - footerPlaceable.height, MinArtStageHeight.roundToPx())
        val artPlaceable = aSlot.single().measure(Constraints.fixed(width, artHeight))
        val height = max(headerPlaceable.height + artHeight + footerPlaceable.height, constraints.minHeight)
        layout(width, height) {
            headerPlaceable.place(0, 0)
            artPlaceable.place(0, headerPlaceable.height)
            footerPlaceable.place(0, height - footerPlaceable.height)
        }
    }
}

private fun AnimatedContentTransitionScope<OnboardingStep>.stepTransition(): ContentTransform =
    Motion.sharedAxisX(forward = targetState > initialState)
