package com.hypai.fion.ui.screen.privacy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.ui.design.Icon
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.MetalSim
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.StatusPill
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type

internal val DiagramShape = RoundedCornerShape(18.dp)
internal val PanelShape = RoundedCornerShape(14.dp)
private val TagShape = RoundedCornerShape(8.dp)
internal val PanelSurface = Color.White.copy(alpha = 0.64f)
internal val ReadableLine = Type.Secondary.copy(lineHeight = 20.sp)
internal val MonoCaption = Type.Caption.copy(fontFamily = FontFamily.Monospace)
internal val MonoPacket = Type.Caption.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp)

internal fun Modifier.privacyPanel(
    shape: Shape = PanelShape,
    background: Color = PanelSurface,
    border: Color = Palette.GlassBorder,
): Modifier = clip(shape).background(background).border(1.dp, border, shape)

@Composable
internal fun DataJourneyDiagram(encrypted: Boolean, serverHost: String) {
    val channelLabel = stringResource(if (encrypted) R.string.privacy_flow_aes_on else R.string.privacy_flow_https_only)
    val flowDescription = stringResource(R.string.privacy_flow_desc, channelLabel, serverHost)
    Column(
        Modifier
            .fillMaxWidth()
            .privacyPanel(DiagramShape)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = flowDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            JourneyNode(
                title = stringResource(R.string.privacy_flow_phone),
                subtitle = stringResource(R.string.privacy_flow_phone_sub),
                modifier = Modifier.weight(1f),
            ) {
                IconBadge(Icons.Smartphone, Palette.NavySurface, Palette.Navy, PanelShape, size = 40.dp, iconSize = 20.dp)
            }

            FlowArrow(Modifier.width(18.dp).height(16.dp), Palette.NavyMid)

            JourneyNode(
                title = channelLabel,
                subtitle = stringResource(if (encrypted) R.string.privacy_flow_aes_on_sub else R.string.privacy_flow_https_only_sub),
                modifier = Modifier.weight(1.1f),
            ) {
                IconBadge(
                    icon = if (encrypted) Icons.Lock else Icons.Shield,
                    background = if (encrypted) Palette.GreenSurface else Palette.AmberSurface,
                    tint = if (encrypted) Palette.Green else Palette.Copper,
                    shape = PanelShape,
                    size = 40.dp,
                    iconSize = 20.dp,
                )
            }

            FlowArrow(Modifier.width(18.dp).height(16.dp), Palette.NavyMid)

            JourneyNode(
                title = stringResource(R.string.privacy_flow_bark),
                subtitle = serverHost,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    Modifier.size(40.dp).clip(PanelShape).background(Palette.NavySurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.bark_icon), contentDescription = null, Modifier.size(30.dp).clip(TagShape))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Palette.CrimsonSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Ban, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson)
            Text(
                stringResource(R.string.privacy_flow_no_server),
                style = Type.Caption,
                fontWeight = FontWeight.Medium,
                color = Palette.Crimson,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JourneyNode(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badge()
        Text(
            title,
            style = Type.Caption,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = Type.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = Palette.Muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun FlowArrow(modifier: Modifier, color: Color) {
    Box(
        modifier.drawWithCache {
            val midY = size.height / 2f
            val endX = size.width - 3.dp.toPx()
            val strokePx = 1.8.dp.toPx()
            val dashPx = 4.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
            val tip = 4.dp.toPx()
            val head = Path().apply {
                moveTo(endX - tip, midY - tip)
                lineTo(endX + 1.dp.toPx(), midY)
                lineTo(endX - tip, midY + tip)
            }
            val headStroke = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val lineColor = color.copy(alpha = 0.7f)
            onDrawBehind {
                drawLine(lineColor, Offset(0f, midY), Offset(endX, midY), strokePx, StrokeCap.Round, dash)
                drawPath(head, color, style = headStroke)
            }
        },
    )
}

@Composable
internal fun PermissionStatusPill(status: PermissionStatus) {
    val (labelRes, bg, tint) = when (status) {
        PermissionStatus.Granted -> Triple(R.string.privacy_status_granted, Palette.GreenSurface, Palette.Green)
        PermissionStatus.Missing -> Triple(R.string.privacy_status_not_granted, Palette.Neutral, Palette.Muted)
        PermissionStatus.Ready -> Triple(R.string.privacy_status_ready, Palette.GreenSurface, Palette.Green)
        PermissionStatus.Partial -> Triple(R.string.privacy_status_partial, Palette.AmberSurface, Palette.Amber)
    }
    StatusPill(stringResource(labelRes), bg, tint)
}

@Composable
internal fun BoundaryStrip(guide: PermissionGuide) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoundaryPanel(
            allowed = true,
            title = stringResource(guide.allowTitle),
            subtitle = stringResource(guide.allowSub),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { AllowVisual(guide) }

        BoundaryPanel(
            allowed = false,
            title = stringResource(guide.blockTitle),
            subtitle = stringResource(guide.blockSub),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { BlockVisual(guide) }
    }
}

@Composable
private fun BoundaryPanel(
    allowed: Boolean,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    visual: @Composable () -> Unit,
) {
    val bg = if (allowed) Palette.GreenSurface.copy(alpha = 0.72f) else Palette.CrimsonSurface.copy(alpha = 0.72f)
    val accent = if (allowed) Palette.Green else Palette.Crimson
    val badgeIcon = if (allowed) Icons.Check else Icons.Close
    Column(
        modifier
            .privacyPanel(PanelShape, bg, accent.copy(alpha = 0.16f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) { visual() }
            IconBadge(badgeIcon, accent.copy(alpha = 0.16f), accent, CircleShape, size = 20.dp, iconSize = 12.dp)
        }
        Text(title, style = Type.Caption, fontWeight = FontWeight.SemiBold, color = accent)
        Text(subtitle, style = Type.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Palette.Muted)
    }
}

@Composable
private fun AllowDotBadge(icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        IconBadge(icon, Color.White.copy(alpha = 0.85f), Palette.Green, TagShape, size = 26.dp, iconSize = 15.dp)
        Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Green))
    }
}

@Composable
private fun BlockBanBadge(icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        IconBadge(icon, Color.White.copy(alpha = 0.65f), tint, TagShape, size = 26.dp, iconSize = 14.dp)
        Icon(Icons.Ban, contentDescription = null, Modifier.size(15.dp), tint = Palette.Crimson)
    }
}

@Composable
private fun AllowVisual(guide: PermissionGuide) {
    when (guide) {
        PermissionGuide.Sms -> AllowDotBadge(Icons.Sms)
        PermissionGuide.CallLog -> AllowDotBadge(Icons.MissedCall)
        PermissionGuide.Sim -> Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetalSim(0, width = 18.dp, height = 24.dp)
            MetalSim(1, width = 18.dp, height = 24.dp)
        }
        PermissionGuide.Background -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            IconBadge(Icons.Bell, Color.White.copy(alpha = 0.85f), Palette.Green, TagShape, size = 26.dp, iconSize = 14.dp)
            IconBadge(Icons.Battery, Color.White.copy(alpha = 0.85f), Palette.Green, TagShape, size = 26.dp, iconSize = 15.dp)
        }
    }
}

@Composable
private fun BlockVisual(guide: PermissionGuide) {
    val mutedCrimson = Palette.Crimson.copy(alpha = 0.55f)
    when (guide) {
        PermissionGuide.Sms -> Box(
            Modifier.size(34.dp, 26.dp).drawWithCache {
                val fill = Color.White.copy(alpha = 0.72f)
                val border = Stroke(1.2.dp.toPx())
                val radius = CornerRadius(4.dp.toPx())
                val rectSize = Size(22.dp.toPx(), 15.dp.toPx())
                val backTop = Offset(0f, 7.dp.toPx())
                val frontTop = Offset(7.dp.toPx(), 2.dp.toPx())
                val slashStart = Offset(4.dp.toPx(), 23.dp.toPx())
                val slashEnd = Offset(28.dp.toPx(), 1.dp.toPx())
                val slashWidth = 2.dp.toPx()
                onDrawBehind {
                    drawRoundRect(fill, backTop, rectSize, radius)
                    drawRoundRect(mutedCrimson, backTop, rectSize, radius, style = border)
                    drawRoundRect(fill, frontTop, rectSize, radius)
                    drawRoundRect(mutedCrimson, frontTop, rectSize, radius, style = border)
                    drawLine(Palette.Crimson, slashStart, slashEnd, slashWidth, StrokeCap.Round)
                }
            },
        )
        PermissionGuide.CallLog -> BlockBanBadge(Icons.Phone, mutedCrimson)
        PermissionGuide.Sim -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            IconBadge(Icons.Phone, Color.White.copy(alpha = 0.65f), mutedCrimson, TagShape, size = 26.dp, iconSize = 14.dp)
            IconBadge(Icons.Sms, Color.White.copy(alpha = 0.65f), mutedCrimson, TagShape, size = 26.dp, iconSize = 14.dp)
        }
        PermissionGuide.Background -> BlockBanBadge(Icons.Globe, mutedCrimson)
    }
}

@Composable
internal fun TaggedExplanation(tag: String, tagBg: Color, tagColor: Color, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .widthIn(min = 52.dp)
                .clip(TagShape)
                .background(tagBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tag,
                style = Type.Caption,
                fontWeight = FontWeight.SemiBold,
                color = tagColor,
                maxLines = 1,
            )
        }
        Text(text, Modifier.weight(1f), style = ReadableLine, color = Palette.Ink)
    }
}

@Composable
internal fun StatusCardHeader(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    iconSize: Dp,
    title: String,
    pillText: String,
    pillBg: Color,
    pillColor: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, iconBg, iconTint, PanelShape, size = 38.dp, iconSize = iconSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            StatusPill(pillText, pillBg, pillColor)
        }
    }
}

@Composable
internal fun ChainStep(icon: ImageVector, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) Palette.GreenSurface else Palette.NavySurface
    val tint = if (highlight) Palette.Green else Palette.NavyDeep
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBadge(icon, bg, tint, RoundedCornerShape(10.dp), size = 30.dp, iconSize = 15.dp)
        Text(
            label,
            style = Type.Caption.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
