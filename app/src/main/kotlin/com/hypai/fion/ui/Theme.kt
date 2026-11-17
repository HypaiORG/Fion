package com.hypai.fion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

object Palette {
    val Ink = Color(0xFF14213A)
    val Muted = Color(0xFF55627A)
    val Navy = Color(0xFF2C3E62)
    val NavyDeep = Color(0xFF1E2C4A)
    val NavyMid = Color(0xFF4A6596)
    val NavyLight = Color(0xFF6F88B8)
    val NavyPale = Color(0xFF8FA3C7)
    val NavySurface = Color(0xFFE6EBF4)
    val Red = Color(0xFFC23A30)
    val Crimson = Color(0xFF8E241D)
    val CrimsonSurface = Color(0xFFF6E1DE)
    val RedSurface = Color(0xFFFBE3E0)
    val Green = Color(0xFF2E6B45)
    val GreenSurface = Color(0xFFE1F0E6)
    val Failure = Color(0xFFE5484D)
    val Gold = Color(0xFFE0B84E)
    val Copper = Color(0xFF9A6212)
    val CopperGlow = Color(0xFFE9A23B)
    val Error = Color(0xFFBA1A1A)
    val ErrorSurface = Color(0xFFFFDAD6)
    val OnErrorSurface = Color(0xFF93000A)
    val Amber = Color(0xFF7A4E00)
    val AmberSurface = Color(0xFFFFF1D6)
    val AmberPale = Color(0xFFFFF6E5)
    val AmberBorder = Color(0xFFC98A1A)
    val Neutral = Color(0xFFF0F3F4)
    val Sand = Color(0xFFE6E3DC)
    val Dormant = Color(0xFFECEAE5)
    val Stone = Color(0xFFE2DED6)
    val Sheet = Color(0xFFF8F6F1)
    val Canvas = Color(0xFFECE9E2)
    val CanvasLight = Color(0xFFF6F4EF)
    val Outline = Color(0xFF7A8491)
    val Hairline = Color(0x2414213A)
    val Glass = Color(0x94FFFFFF)
    val GlassBorder = Color(0xBFFFFFFF)
}

private val colorScheme = lightColorScheme(
    primary = Palette.Navy,
    onPrimary = Color.White,
    primaryContainer = Palette.NavySurface,
    onPrimaryContainer = Palette.NavyDeep,
    secondaryContainer = Palette.NavySurface,
    onSecondaryContainer = Palette.NavyDeep,
    background = Palette.Canvas,
    onBackground = Palette.Ink,
    surface = Palette.CanvasLight,
    onSurface = Palette.Ink,
    surfaceContainerHigh = Palette.Sheet,
    surfaceContainerLow = Palette.Sheet,
    surfaceContainer = Palette.Sheet,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Outline,
    outlineVariant = Palette.Hairline,
    error = Palette.Error,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val canvas = Brush.verticalGradient(listOf(Palette.Canvas, Palette.CanvasLight))
            val glows = if (size.isEmpty()) null else renderGlows()
            onDrawBehind {
                drawRect(canvas)
                if (glows != null) {
                    drawImage(glows, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
                }
            }
        },
    ) { content() }
}

private const val GLOW_DOWNSCALE = 4

private fun CacheDrawScope.renderGlows(): ImageBitmap {
    val full = size
    val bitmap = ImageBitmap(ceil(full.width / GLOW_DOWNSCALE).toInt(), ceil(full.height / GLOW_DOWNSCALE).toInt())
    CanvasDrawScope().draw(this, layoutDirection, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
        scale(1f / GLOW_DOWNSCALE, pivot = Offset.Zero) {
            fun glow(color: Color, center: Offset, radius: Dp) = drawCircle(
                Brush.radialGradient(listOf(color, Color.Transparent), center, radius.toPx()),
                radius.toPx(),
                center,
            )
            glow(Palette.NavyMid.copy(alpha = 0.22f), Offset(full.width, 0f), 360.dp)
            glow(Color(0x38D6B482), Offset(0f, full.height * 0.45f), 370.dp)
            glow(Palette.NavyMid.copy(alpha = 0.12f), Offset(full.width, full.height * 0.95f), 360.dp)
        }
    }
    return bitmap
}

object Type {
    private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    val Display = style(34, 40, FontWeight.SemiBold)
    val Headline = style(28, 36, FontWeight.SemiBold)
    val SheetTitle = style(22, 28, FontWeight.SemiBold)
    val Metric = style(28, 34, FontWeight.SemiBold)
    val PageTitle = style(18, 24, FontWeight.SemiBold)
    val Figure = style(17, 22, FontWeight.SemiBold)
    val CardTitle = style(16, 22, FontWeight.Medium)
    val RowTitle = style(15, 22)
    val Field = style(15, 20)
    val FieldMultiline = style(15, 22)
    val Option = style(16, 24)
    val Note = style(12, 18)
    val Label = style(14, 20, FontWeight.Medium)
    val Body = style(14, 20)
    val Secondary = style(13, 18)
    val Caption = style(12, 16)
}

private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        labelLarge = Type.Label,
        headlineSmall = Type.PageTitle,
        bodyMedium = Type.Body,
    )
}
