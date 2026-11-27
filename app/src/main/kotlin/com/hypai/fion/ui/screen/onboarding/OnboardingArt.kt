package com.hypai.fion.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.ui.design.Icon
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.glass
import com.hypai.fion.ui.design.svgPath

private val ArtWidth = 412.dp
private val ArtHeight = 384.dp
private val NotifyCardShape = RoundedCornerShape(22.dp)

private object OnboardingSimShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { 18.dp.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            moveTo(r, 0f)
            lineTo((size.width * 0.74f).coerceAtLeast(r), 0f)
            lineTo(size.width, (size.height * 0.2f).coerceAtLeast(r))
            lineTo(size.width, size.height - r)
            arcTo(Rect(size.width - 2 * r, size.height - 2 * r, size.width, size.height), 0f, 90f, false)
            lineTo(r, size.height)
            arcTo(Rect(0f, size.height - 2 * r, 2 * r, size.height), 90f, 90f, false)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
internal fun ArtStage(modifier: Modifier, maxScale: Float, content: @Composable BoxScope.() -> Unit) {
    Layout(
        content = { Box(Modifier.fillMaxSize(), content = content) },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val artW = ArtWidth.roundToPx()
        val artH = ArtHeight.roundToPx()
        val placeable = measurables.single().measure(Constraints.fixed(artW, artH))
        val scale = minOf(maxScale, constraints.maxWidth.toFloat() / artW, constraints.maxHeight.toFloat() / artH)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer((constraints.maxWidth - artW) / 2, (constraints.maxHeight - artH) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
internal fun OnboardingArt(step: OnboardingStep) {
    when (step) {
        OnboardingStep.Sms -> SmsArt()
        OnboardingStep.CallLog -> CallArt()
        OnboardingStep.Phone -> PhoneArt()
        OnboardingStep.Notify -> NotifyArt()
        OnboardingStep.Battery -> BatteryArt()
    }
}

private fun Modifier.at(x: Int, y: Int) = offset(x.dp, y.dp)

private fun DrawScope.inDp(block: DrawScope.() -> Unit) = scale(density, density, Offset.Zero, block)

private fun DrawScope.ellipse(cx: Float, cy: Float, rx: Float, ry: Float, color: Color, stroke: Stroke) =
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = stroke)

private fun Modifier.halo(color: Color, spread: Dp): Modifier = drawWithCache {
    val inner = size.minDimension / 2
    val outer = inner + spread.toPx()
    val glow = Brush.radialGradient(0f to color, inner / outer to color, 1f to Color.Transparent, center = size.center, radius = outer)
    onDrawBehind { drawCircle(glow, outer, size.center) }
}

internal class Radial(
    private val cx: Float,
    private val cy: Float,
    private val reach: Float,
    private vararg val stops: Pair<Float, Color>,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader = RadialGradientShader(
        center = Offset(size.width * cx, size.height * cy),
        radius = size.width * reach,
        colors = stops.map { it.second },
        colorStops = stops.map { it.first },
    )
}

private val SmsDiamonds by lazy(LazyThreadSafetyMode.NONE) {
    listOf(
        "M-20 180 L100 60 L220 180 L100 300 Z",
        "M190 180 L310 60 L430 180 L310 300 Z",
        "M86 290 L206 170 L326 290 L206 410 Z",
        "M86 70 L206 -50 L326 70 L206 190 Z",
    ).map(::svgPath)
}

private val ChipContacts by lazy(LazyThreadSafetyMode.NONE) {
    svgPath("M0 12 H14 M0 24 H14 M32 12 H46 M32 24 H46 M14 0 V36 M32 0 V36 M14 18 H32")
}

private val CallOrb = Radial(0.38f, 0.3f, 0.935f, 0f to Color.White.copy(alpha = 0.95f), 0.55f to Color(0xBFE2ECF3), 1f to Color(0xA6C4D6E4))

private class CallBall(val ball: Pair<Int, Int>, val dot: Pair<Int, Int>, val color: Color) {
    val fill = Radial(0.35f, 0.3f, 0.955f, 0f to Color.White.copy(alpha = 0.6f), 0.7f to color)
}

private val CallBalls = listOf(
    CallBall(174 to 30, 200 to 114, Palette.Gold),
    CallBall(62 to 150, 88 to 234, Palette.Red),
    CallBall(286 to 140, 312 to 224, Palette.Navy),
)

internal val NavyOrb = Radial(0.35f, 0.3f, 0.955f, 0f to Palette.NavyLight, 0.45f to Color(0xFF3A5080), 1f to Palette.NavyDeep)

private val ThinStroke = Stroke(1f)
private val MediumStroke = Stroke(1.5f)
private val DiamondStroke = Stroke(22f)
private val ChipStroke = Stroke(1.2f)

@Composable
private fun Skeleton(width: Dp, alpha: Float, height: Dp = 6.dp) {
    Box(Modifier.size(width, height).clip(CircleShape).background(Palette.Ink.copy(alpha = alpha)))
}

@Composable
private fun Spark(x: Int, y: Int, size: Int, color: Color) {
    Box(Modifier.at(x, y).size(size.dp).halo(color.copy(alpha = 0.55f), 14.dp).clip(CircleShape).background(color))
}

@Composable
private fun Rings() {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val faint = Palette.Ink.copy(alpha = 0.08f)
            val bold = Palette.Ink.copy(alpha = 0.16f)
            onDrawBehind {
                inDp {
                    ellipse(206f, 196f, 186f, 60f, faint, ThinStroke)
                    ellipse(206f, 196f, 150f, 140f, faint, ThinStroke)
                    ellipse(206f, 250f, 208f, 54f, bold, MediumStroke)
                }
            }
        },
    )
}

@Composable
private fun SmsArt() {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val strokeColor = Palette.Ink.copy(alpha = 0.04f)
            onDrawBehind {
                inDp { SmsDiamonds.forEach { drawPath(it, strokeColor, style = DiamondStroke) } }
            }
        },
    )
    Box(
        Modifier.at(86, 72).size(240.dp).background(
            Brush.radialGradient(
                0f to Palette.NavyMid.copy(alpha = 0.40f),
                0.64f to Palette.NavyMid.copy(alpha = 0.10f),
                1f to Color.Transparent,
            ),
            CircleShape,
        ),
    )
    Box(
        Modifier.at(195, 181).size(22.dp).halo(Palette.Red.copy(alpha = 0.55f), 18.dp).clip(CircleShape).background(Color.White)
            .border(3.dp, Palette.Red, CircleShape),
    )
    listOf(
        Triple(146 to 24, 56, 34),
        Triple(14 to 138, 48, 40),
        Triple(278 to 138, 60, 30),
        Triple(146 to 252, 44, 36),
    ).forEach { (position, first, second) ->
        Column(
            Modifier.at(position.first, position.second).size(120.dp, 84.dp).glass(RoundedCornerShape(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Bubble, contentDescription = null, Modifier.size(20.dp), tint = Palette.Navy)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Skeleton(first.dp, 0.16f)
                Skeleton(second.dp, 0.08f)
            }
        }
    }
}

@Composable
private fun CallArt() {
    Box(
        Modifier.at(76, 60)
            .size(260.dp)
            .clip(CircleShape)
            .background(CallOrb)
            .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    )
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val faint = Palette.Ink.copy(alpha = 0.08f)
            val bold = Palette.Ink.copy(alpha = 0.22f)
            onDrawBehind {
                inDp {
                    ellipse(206f, 190f, 130f, 40f, faint, ThinStroke)
                    ellipse(206f, 190f, 130f, 90f, faint, ThinStroke)
                    ellipse(206f, 190f, 60f, 130f, faint, ThinStroke)
                    ellipse(206f, 232f, 210f, 56f, bold, MediumStroke)
                }
            }
        },
    )
    CallBalls.forEach { call ->
        Box(
            Modifier.at(call.ball.first, call.ball.second)
                .size(64.dp)
                .halo(call.color.copy(alpha = 0.42f), 36.dp)
                .clip(CircleShape)
                .background(call.fill)
                .border(1.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.MissedCall, contentDescription = null, Modifier.size(24.dp), tint = Color.White) }
        Box(
            Modifier.at(call.dot.first, call.dot.second)
                .size(12.dp)
                .halo(call.color.copy(alpha = 0.42f), 12.dp)
                .drawBehind { drawCircle(Palette.Ink.copy(alpha = 0.08f), size.minDimension / 2 + 4.dp.toPx()) }
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private val ChipBrush = Brush.linearGradient(
    0f to Color(0xFFF1D58B),
    0.55f to Color(0xFFD8A94A),
    1f to Color(0xFFB98A32),
    start = Offset.Zero,
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)
private val ChipLineColor = Color(0xFF8A6420).copy(alpha = 0.45f)

@Composable
private fun SimCard(x: Int, y: Int, rotation: Float, alpha: Float) {
    Box(
        Modifier.at(x, y)
            .size(132.dp, 176.dp)
            .graphicsLayer { rotationZ = rotation }
            .clip(OnboardingSimShape)
            .background(Color.White.copy(alpha = alpha))
            .border(1.dp, Color.White.copy(alpha = 0.85f), OnboardingSimShape),
    ) {
        Box(
            Modifier
                .at(22, 52)
                .size(46.dp, 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ChipBrush)
                .drawWithCache {
                    onDrawBehind {
                        inDp { drawPath(ChipContacts, ChipLineColor, style = ChipStroke) }
                    }
                },
        )
        Column(Modifier.at(22, 112), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(76.dp, 0.2f, 7.dp)
            Skeleton(48.dp, 0.1f)
        }
    }
}

@Composable
private fun PhoneArt() {
    Rings()
    SimCard(96, 44, -12f, 0.42f)
    SimCard(178, 38, 7f, 0.7f)
    Row(
        Modifier.at(66, 246).size(280.dp, 64.dp).glass(RoundedCornerShape(32.dp)).padding(start = 10.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(NavyOrb), contentAlignment = Alignment.Center) {
            Icon(Icons.Phone, contentDescription = null, Modifier.size(20.dp), tint = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(3, 4, 4).forEach { count ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(count) { Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Navy)) }
                }
            }
        }
    }
    Spark(60, 70, 12, Palette.Red)
    Spark(350, 96, 8, Palette.Navy)
    Spark(274, 344, 10, Palette.Gold)
}

@Composable
private fun NotifyArt() {
    val frame = RoundedCornerShape(44.dp)
    Box(
        Modifier.at(76, 24)
            .size(260.dp, 360.dp)
            .clip(frame)
            .background(Brush.verticalGradient(listOf(Color(0xE6ECF2F6), Color(0xCCD6E2EA))))
            .border(6.dp, Color.White.copy(alpha = 0.95f), frame),
    ) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(88.dp, 26.dp).clip(CircleShape).background(Palette.Ink))
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 150.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            listOf(0.07f, 0.055f, 0.04f).forEach { alpha ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) {
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Palette.Ink.copy(alpha = alpha)))
                    }
                }
            }
        }
    }
    Row(
        Modifier.at(32, 82)
            .width(348.dp)
            .clip(NotifyCardShape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.95f), NotifyCardShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.app_icon), contentDescription = null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(64.dp, 0.22f, 8.dp)
                Box(Modifier.weight(1f))
                Skeleton(22.dp, 0.10f)
            }
            Box(Modifier.fillMaxWidth(0.82f).height(6.dp).clip(CircleShape).background(Palette.Ink.copy(alpha = 0.12f)))
            Box(Modifier.fillMaxWidth(0.56f).height(6.dp).clip(CircleShape).background(Palette.Ink.copy(alpha = 0.08f)))
        }
    }
}

@Composable
private fun RadioLine(selected: Boolean, first: Int, second: Int) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Palette.Navy.copy(alpha = 0.09f)).border(1.dp, Palette.Navy.copy(alpha = 0.16f), shape) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(20.dp).border(2.dp, if (selected) Palette.Navy else Palette.Ink.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.Navy)) }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Skeleton(first.dp, if (selected) 0.24f else 0.16f, 7.dp)
            Skeleton(second.dp, if (selected) 0.12f else 0.08f)
        }
    }
}

@Composable
private fun BatteryArt() {
    Rings()
    Column(
        Modifier.at(56, 104).width(270.dp).glass(RoundedCornerShape(26.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp)) { Skeleton(86.dp, 0.26f, 9.dp) }
        RadioLine(false, 118, 72)
        RadioLine(true, 140, 96)
        RadioLine(false, 104, 60)
    }
    Box(
        Modifier.at(270, 48)
            .size(96.dp)
            .shadow(18.dp, CircleShape, ambientColor = Palette.NavyDeep, spotColor = Palette.NavyDeep)
            .background(NavyOrb, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Battery, contentDescription = null, Modifier.size(40.dp), tint = Color.White) }
    Spark(62, 64, 12, Palette.Red)
    Spark(360, 336, 8, Palette.Navy)
    Spark(118, 328, 10, Palette.Gold)
}
