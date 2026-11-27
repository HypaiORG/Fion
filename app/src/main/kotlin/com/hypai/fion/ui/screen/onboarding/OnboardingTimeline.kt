package com.hypai.fion.ui.screen.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.ui.design.Icon
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.StatusPill
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type

internal enum class StepOutcome { Denied, Skipped, Blocked }

internal enum class StepStatus(@param:StringRes val label: Int) {
    Allowed(R.string.onb_status_allowed),
    Denied(R.string.onb_status_denied),
    Skipped(R.string.onb_status_skipped),
    Current(R.string.onb_status_current),
    Blocked(R.string.onb_status_blocked),
    Pending(R.string.onb_status_pending),
}

internal fun stepStatus(done: Boolean, current: Boolean, outcome: StepOutcome?): StepStatus = when {
    done -> StepStatus.Allowed
    current -> if (outcome == StepOutcome.Blocked) StepStatus.Blocked else StepStatus.Current
    outcome == StepOutcome.Skipped -> StepStatus.Skipped
    outcome != null -> StepStatus.Denied
    else -> StepStatus.Pending
}

private val StepStatus.active: Boolean get() = this == StepStatus.Current || this == StepStatus.Blocked

private val RowHeight = 56.dp
private val RowShape = RoundedCornerShape(20.dp)
private val NodeSize = 40.dp
private val LinkX = 27.dp
private val LinkWidth = 2.dp
private val LinkGap = NodeSize / 2 + 1.dp
private val ActiveRow = Color.White.copy(alpha = 0.72f)
private val QuietNode = Color.White.copy(alpha = 0.55f)
private val RedOrb = Radial(0.35f, 0.3f, 0.955f, 0f to Color(0xFFE58C82), 0.5f to Palette.Red, 1f to Palette.Crimson)

@Composable
internal fun StepTimeline(plan: List<OnboardingStep>, statuses: List<StepStatus>, modifier: Modifier) {
    val idleLink = Palette.Ink.copy(alpha = 0.12f)
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val width = LinkWidth.toPx()
                val x = LinkX.toPx()
                val rowStep = size.height / statuses.size
                val gap = LinkGap.toPx()
                val radius = CornerRadius(width / 2)
                for (i in 0 until statuses.lastIndex) {
                    drawRoundRect(
                        if (statuses[i] == StepStatus.Allowed) Palette.Navy else idleLink,
                        Offset(x, (i + 0.5f) * rowStep + gap),
                        Size(width, rowStep - 2 * gap),
                        radius,
                    )
                }
            },
    ) {
        plan.forEachIndexed { i, step -> StepRow(step, statuses[i]) }
    }
}

@Composable
private fun StepRow(step: OnboardingStep, status: StepStatus) {
    val statusText = stringResource(status.label)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .then(if (status.active) Modifier.background(ActiveRow, RowShape) else Modifier)
            .semantics(mergeDescendants = true) { stateDescription = statusText }
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepNode(step, status)
        Text(
            stringResource(step.label),
            Modifier.weight(1f),
            style = Type.RowTitle,
            fontWeight = if (status.active) FontWeight.SemiBold else FontWeight.Medium,
            color = when (status) {
                StepStatus.Allowed -> Palette.Navy
                StepStatus.Current, StepStatus.Blocked -> Palette.Ink
                StepStatus.Denied, StepStatus.Skipped, StepStatus.Pending -> Palette.Muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val tag = when (status) {
            StepStatus.Allowed -> Palette.NavySurface to Palette.Navy
            StepStatus.Denied, StepStatus.Blocked -> Palette.CrimsonSurface to Palette.Crimson
            StepStatus.Skipped -> Palette.Ink.copy(alpha = 0.06f) to Palette.Muted
            StepStatus.Current, StepStatus.Pending -> null
        }
        if (tag != null) {
            StatusPill(
                text = statusText,
                background = tag.first,
                color = tag.second,
                modifier = Modifier.clearAndSetSemantics {}.heightIn(min = 24.dp),
                padding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StepNode(step: OnboardingStep, status: StepStatus) {
    val icon = when (status) {
        StepStatus.Allowed -> Icons.Check
        StepStatus.Denied -> Icons.Close
        StepStatus.Skipped -> Icons.Skip
        StepStatus.Current, StepStatus.Blocked, StepStatus.Pending -> step.icon
    }
    val tint = when (status) {
        StepStatus.Allowed, StepStatus.Current, StepStatus.Blocked -> Color.White
        StepStatus.Denied -> Palette.Crimson
        StepStatus.Skipped, StepStatus.Pending -> Palette.Muted
    }
    val look = when (status) {
        StepStatus.Allowed -> Modifier.background(Palette.Navy, CircleShape)
        StepStatus.Denied -> Modifier.background(Palette.CrimsonSurface, CircleShape)
        StepStatus.Skipped -> Modifier.background(QuietNode, CircleShape).dashedRing(Palette.Ink.copy(alpha = 0.32f))
        StepStatus.Pending -> Modifier.background(QuietNode, CircleShape).border(1.dp, Palette.Ink.copy(alpha = 0.14f), CircleShape)
        StepStatus.Current -> Modifier.orb(NavyOrb, Palette.Navy.copy(alpha = 0.10f), Palette.NavyDeep)
        StepStatus.Blocked -> Modifier.orb(RedOrb, Palette.Red.copy(alpha = 0.12f), Palette.Crimson)
    }
    Box(Modifier.size(NodeSize).then(look), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = tint)
    }
}

private fun Modifier.orb(fill: Brush, ring: Color, glow: Color): Modifier =
    drawBehind { drawCircle(ring, size.minDimension / 2 + 5.dp.toPx()) }
        .shadow(6.dp, CircleShape, ambientColor = glow, spotColor = glow)
        .background(fill, CircleShape)

private fun Modifier.dashedRing(color: Color): Modifier = drawWithCache {
    val width = 1.5.dp.toPx()
    val dash = 3.dp.toPx()
    val stroke = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
    val radius = size.minDimension / 2 - width / 2
    onDrawBehind { drawCircle(color, radius = radius, style = stroke) }
}
