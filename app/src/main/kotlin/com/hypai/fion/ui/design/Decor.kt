package com.hypai.fion.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class Fade { TowardBottomStart, TowardTopStart }

fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

private val EnvelopeFold = svgPath("M24 58 L68 88 L112 58")
private val EnvelopeTrail = svgPath("M84 50 C98 40 108 34 122 28")
private val EnvelopeWingTop = svgPath("M156 6 L120 22 L134 27 Z")
private val EnvelopeWingBottom = svgPath("M156 6 L134 27 L139 40 Z")
private val ShieldOutline = svgPath("M50 6 L88 20 V52 C88 78 70 96 50 104 C30 96 12 78 12 52 V20 Z")
private val ShieldHalf = svgPath("M50 6 L88 20 V52 C88 78 70 96 50 104 Z")
private val ShieldTick = svgPath("M33 54 L46 67 L68 42")
private val SimOutline = svgPath("M22 10 H80 L106 36 V124 A8 8 0 0 1 98 132 H22 A8 8 0 0 1 14 124 V18 A8 8 0 0 1 22 10 Z")
private val SimContacts = svgPath("M60 64 V108 M32 86 H88 M46 64 V76 M74 98 V108")

private fun diagonal(from: Color, to: Color, topLeft: Offset, size: Size): Brush =
    Brush.linearGradient(listOf(from, to), topLeft, Offset(topLeft.x + size.width, topLeft.y + size.height))

private val RimColor = Color.White.copy(alpha = 0.8f)
private val SheenColor = Color.White.copy(alpha = 0.6f)

@Composable
private fun Decor(
    viewport: Size,
    width: Dp,
    height: Dp,
    opacity: Float,
    rotation: Float,
    fade: Fade,
    modifier: Modifier,
    draw: DrawScope.() -> Unit,
) {
    Canvas(
        modifier.size(width, height).graphicsLayer {
            alpha = opacity
            rotationZ = rotation
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        scale(size.width / viewport.width, size.height / viewport.height, pivot = Offset.Zero, block = draw)
        val (start, end) = when (fade) {
            Fade.TowardBottomStart -> Offset(size.width, 0f) to Offset(0f, size.height)
            Fade.TowardTopStart -> Offset(size.width, size.height) to Offset.Zero
        }
        drawRect(
            Brush.linearGradient(0.35f to Color.Black, 0.9f to Color.Transparent, start = start, end = end),
            blendMode = BlendMode.DstIn,
        )
    }
}

@Composable
fun EnvelopeDecor(modifier: Modifier = Modifier) {
    val navy = Palette.Navy
    Decor(Size(160f, 120f), 150.dp, 112.dp, 0.55f, 0f, Fade.TowardBottomStart, modifier) {
        rotate(-10f, Offset(68f, 84f)) {
            val origin = Offset(22f, 54f)
            val box = Size(92f, 60f)
            drawRoundRect(diagonal(SheenColor, navy.copy(alpha = 0.16f), origin, box), origin, box, CornerRadius(10f))
            drawRoundRect(RimColor, origin, box, CornerRadius(10f), style = Stroke(1.2f))
            drawPath(EnvelopeFold, navy.copy(alpha = 0.25f), style = Stroke(1.5f, join = StrokeJoin.Round))
        }
        drawPath(
            EnvelopeTrail,
            navy.copy(alpha = 0.3f),
            style = Stroke(1.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f))),
        )
        drawPath(
            EnvelopeWingTop,
            diagonal(navy.copy(alpha = 0.35f), navy.copy(alpha = 0.6f), Offset(120f, 6f), Size(36f, 21f)),
        )
        drawPath(EnvelopeWingBottom, navy.copy(alpha = 0.45f))
    }
}

@Composable
fun ShieldDecor(modifier: Modifier = Modifier) {
    val navy = Palette.Navy
    Decor(Size(100f, 110f), 92.dp, 100.dp, 0.5f, 10f, Fade.TowardTopStart, modifier) {
        drawPath(ShieldOutline, diagonal(SheenColor, navy.copy(alpha = 0.16f), Offset(12f, 6f), Size(76f, 98f)))
        drawPath(ShieldOutline, RimColor, style = Stroke(1.2f))
        drawPath(ShieldHalf, navy.copy(alpha = 0.10f))
        drawPath(
            ShieldTick,
            navy.copy(alpha = 0.35f),
            style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun StopwatchDecor(modifier: Modifier = Modifier) {
    val copper = Palette.Copper
    Decor(Size(110f, 120f), 96.dp, 104.dp, 0.5f, -8f, Fade.TowardTopStart, modifier) {
        val center = Offset(55f, 68f)
        drawRoundRect(copper.copy(alpha = 0.15f), Offset(47f, 6f), Size(16f, 12f), CornerRadius(3f))
        rotate(40f, Offset(90f, 28f)) {
            drawRoundRect(copper.copy(alpha = 0.15f), Offset(84f, 24f), Size(12f, 9f), CornerRadius(3f))
        }
        drawCircle(diagonal(SheenColor, copper.copy(alpha = 0.14f), Offset(11f, 24f), Size(88f, 88f)), 44f, center)
        drawCircle(RimColor, 44f, center, style = Stroke(1.2f))
        drawCircle(
            copper.copy(alpha = 0.25f), 34f, center,
            style = Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 7f))),
        )
        drawLine(copper.copy(alpha = 0.35f), center, Offset(55f, 42f), 3f, StrokeCap.Round)
        drawLine(copper.copy(alpha = 0.25f), center, Offset(72f, 78f), 2.5f, StrokeCap.Round)
        drawCircle(copper.copy(alpha = 0.4f), 4f, center)
    }
}

@Composable
fun SimDecor(modifier: Modifier = Modifier) {
    val ink = Palette.Ink
    Decor(Size(120f, 140f), 96.dp, 112.dp, 0.5f, 14f, Fade.TowardBottomStart, modifier) {
        drawPath(SimOutline, diagonal(SheenColor, ink.copy(alpha = 0.10f), Offset(14f, 10f), Size(92f, 122f)))
        drawPath(SimOutline, RimColor, style = Stroke(1.2f))
        val chip = Offset(32f, 64f)
        val chipSize = Size(56f, 44f)
        drawRoundRect(diagonal(ink.copy(alpha = 0.06f), ink.copy(alpha = 0.14f), chip, chipSize), chip, chipSize, CornerRadius(8f))
        drawPath(SimContacts, ink.copy(alpha = 0.12f), style = Stroke(1.2f))
    }
}

enum class SimMetal(val stops: List<Color>, val rim: Color, val ink: Color, val bar: Color) {
    Gold(
        listOf(Color(0xFFFBEFC4), Color(0xFFDDB45A), Color(0xFFF7E2A0), Color(0xFFB98A2E), Color(0xFFE6C470)),
        Color(0xFF8A5A12), Color(0xFF6B460C), Color(0xFFB07A22),
    ),
    Silver(
        listOf(Color(0xFFF7F9FC), Color(0xFFAFBBD0), Color(0xFFEEF2F8), Color(0xFF7D8DAA), Color(0xFFCBD3E2)),
        Color(0xFF3E5480), Palette.Navy, Palette.NavyMid,
    ),
    Graphite(
        listOf(Color(0xFFE9EBEE), Color(0xFF9AA1AB), Color(0xFFDADDE2), Color(0xFF5F6670), Color(0xFFB5BAC2)),
        Color(0xFF3A4049), Color(0xFF22272E), Palette.Ink,
    );

    companion object {
        fun of(slot: Int?): SimMetal = when (slot) {
            0 -> Gold
            1 -> Silver
            else -> Graphite
        }
    }
}

private val SimCardViewport = Size(24f, 32f)
private val SimCardOutline = svgPath("M4 0H17L24 7V28A4 4 0 0 1 20 32H4A4 4 0 0 1 0 28V4A4 4 0 0 1 4 0Z")
private val MetalStops = listOf(0f, 0.38f, 0.52f, 0.78f, 1f)

@Composable
fun MetalSim(slot: Int?, modifier: Modifier = Modifier) {
    val metal = SimMetal.of(slot)
    val measurer = rememberTextMeasurer()
    val digit = slot?.let { (it + 1).toString() }
    val numberSize = with(LocalDensity.current) { 15.dp.toSp() }
    Canvas(modifier.size(24.dp, 32.dp)) {
        val unit = size.width / SimCardViewport.width
        scale(unit, Offset.Zero) {
            val body = Brush.linearGradient(
                *MetalStops.zip(metal.stops).toTypedArray(),
                start = Offset.Zero,
                end = Offset(SimCardViewport.width, SimCardViewport.height),
            )
            drawPath(SimCardOutline, body)
            drawPath(SimCardOutline, metal.rim.copy(alpha = 0.55f), style = Stroke(0.8f))
            drawLine(Color.White.copy(alpha = 0.8f), Offset(4f, 1.2f), Offset(16.5f, 1.2f), 0.8f, StrokeCap.Round)
        }
        if (digit != null) {
            val style = TextStyle(fontSize = numberSize, fontWeight = FontWeight.Bold)
            val text = measurer.measure(digit, style)
            val origin = Offset((size.width - text.size.width) / 2f, size.height * 0.56f - text.size.height / 2f)
            drawText(text, Color.White.copy(alpha = 0.7f), origin + Offset(0f, 0.7f * unit))
            drawText(text, metal.ink, origin)
        }
    }
}
