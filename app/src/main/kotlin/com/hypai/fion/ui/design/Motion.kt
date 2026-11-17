package com.hypai.fion.ui.design

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun sharedAxisX(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return (
            slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { direction * it / 8 } +
                fadeIn(tween(250, delayMillis = 60, easing = Standard))
            ) togetherWith (
            slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { -direction * it / 8 } +
                fadeOut(tween(120, easing = Standard))
            )
    }

    val PushPage: ContentTransform =
        slideInHorizontally(tween(300, easing = EmphasizedDecelerate)) { it } togetherWith
            slideOutHorizontally(tween(300, easing = EmphasizedDecelerate)) { -it / 3 }

    val Swap: ContentTransform =
        ContentTransform(fadeIn(tween(200, delayMillis = 90)), fadeOut(tween(90)), sizeTransform = SizeTransform(clip = false))

    val PopPage: ContentTransform =
        slideInHorizontally(tween(250, easing = EmphasizedDecelerate)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(250, easing = EmphasizedDecelerate)) { it }
}

@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun HapticFeedback.toggle(on: Boolean) =
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
