package com.hypai.fion.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.core.Settings
import com.hypai.fion.core.endpoint
import com.hypai.fion.platform.Readiness

private const val DEFAULT_BARK_HOST = "api.day.app"

private val DiagramShape = RoundedCornerShape(18.dp)
private val PanelShape = RoundedCornerShape(14.dp)
private val TagShape = RoundedCornerShape(8.dp)
private val ReadableLine = Type.Secondary.copy(lineHeight = 20.sp)
private val MonoCaption = Type.Caption.copy(fontFamily = FontFamily.Monospace)
private val MonoPacket = Type.Caption.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp)

private enum class PermissionStatus { Granted, Missing, Ready, Partial }

private enum class PermissionGuide(
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val code: Int,
    @param:StringRes val allowTitle: Int,
    @param:StringRes val allowSub: Int,
    @param:StringRes val blockTitle: Int,
    @param:StringRes val blockSub: Int,
    @param:StringRes val purpose: Int,
    @param:StringRes val guard: Int,
) {
    Sms(
        icon = Icons.Sms,
        badgeBg = Palette.NavySurface,
        badgeTint = Palette.Navy,
        title = R.string.privacy_perm_sms_title,
        code = R.string.privacy_perm_sms_code,
        allowTitle = R.string.privacy_perm_sms_allow_title,
        allowSub = R.string.privacy_perm_sms_allow_sub,
        blockTitle = R.string.privacy_perm_sms_block_title,
        blockSub = R.string.privacy_perm_sms_block_sub,
        purpose = R.string.privacy_perm_sms_purpose,
        guard = R.string.privacy_perm_sms_guard,
    ),
    CallLog(
        icon = Icons.MissedCall,
        badgeBg = Palette.CrimsonSurface,
        badgeTint = Palette.Crimson,
        title = R.string.privacy_perm_call_title,
        code = R.string.privacy_perm_call_code,
        allowTitle = R.string.privacy_perm_call_allow_title,
        allowSub = R.string.privacy_perm_call_allow_sub,
        blockTitle = R.string.privacy_perm_call_block_title,
        blockSub = R.string.privacy_perm_call_block_sub,
        purpose = R.string.privacy_perm_call_purpose,
        guard = R.string.privacy_perm_call_guard,
    ),
    Sim(
        icon = Icons.Sim,
        badgeBg = Palette.AmberSurface,
        badgeTint = Palette.Copper,
        title = R.string.privacy_perm_sim_title,
        code = R.string.privacy_perm_sim_code,
        allowTitle = R.string.privacy_perm_sim_allow_title,
        allowSub = R.string.privacy_perm_sim_allow_sub,
        blockTitle = R.string.privacy_perm_sim_block_title,
        blockSub = R.string.privacy_perm_sim_block_sub,
        purpose = R.string.privacy_perm_sim_purpose,
        guard = R.string.privacy_perm_sim_guard,
    ),
    Background(
        icon = Icons.Battery,
        badgeBg = Palette.GreenSurface,
        badgeTint = Palette.Green,
        title = R.string.privacy_perm_bg_title,
        code = R.string.privacy_perm_bg_code,
        allowTitle = R.string.privacy_perm_bg_allow_title,
        allowSub = R.string.privacy_perm_bg_allow_sub,
        blockTitle = R.string.privacy_perm_bg_block_title,
        blockSub = R.string.privacy_perm_bg_block_sub,
        purpose = R.string.privacy_perm_bg_purpose,
        guard = R.string.privacy_perm_bg_guard,
    ),
    ;

    fun status(readiness: Readiness): PermissionStatus = when (this) {
        Sms -> if (readiness.sms) PermissionStatus.Granted else PermissionStatus.Missing
        CallLog -> if (readiness.callLog) PermissionStatus.Granted else PermissionStatus.Missing
        Sim -> if (readiness.phone) PermissionStatus.Granted else PermissionStatus.Missing
        Background -> if (readiness.notifications && readiness.batteryUnrestricted) PermissionStatus.Ready else PermissionStatus.Partial
    }
}

private data class StorageItem(
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val desc: Int,
)

private val StorageItems = listOf(
    StorageItem(Icons.Gear, Palette.NavySurface, Palette.Navy, R.string.privacy_storage_config_title, R.string.privacy_storage_config_desc),
    StorageItem(Icons.Pulse, Palette.AmberSurface, Palette.Copper, R.string.privacy_storage_stats_title, R.string.privacy_storage_stats_desc),
    StorageItem(Icons.Forward, Palette.GreenSurface, Palette.Green, R.string.privacy_storage_queue_title, R.string.privacy_storage_queue_desc),
)

private data class RedLine(@param:StringRes val title: Int, @param:StringRes val desc: Int)

private val RedLines = listOf(
    RedLine(R.string.privacy_never_1_title, R.string.privacy_never_1_desc),
    RedLine(R.string.privacy_never_2_title, R.string.privacy_never_2_desc),
    RedLine(R.string.privacy_never_3_title, R.string.privacy_never_3_desc),
    RedLine(R.string.privacy_never_4_title, R.string.privacy_never_4_desc),
)

private data class ControlTile(
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val desc: Int,
)

private val ControlTiles = listOf(
    ControlTile(Icons.Power, Palette.NavySurface, Palette.Navy, R.string.privacy_control_1_title, R.string.privacy_control_1_desc),
    ControlTile(Icons.Shield, Palette.GreenSurface, Palette.Green, R.string.privacy_control_2_title, R.string.privacy_control_2_desc),
    ControlTile(Icons.Reset, Palette.CrimsonSurface, Palette.Crimson, R.string.privacy_control_3_title, R.string.privacy_control_3_desc),
)

@Composable
fun PrivacyScreen(
    settings: Settings,
    readiness: Readiness,
    onBack: () -> Unit,
) {
    val endpoint = settings.bark.endpoint()
    val cipherLabel = endpoint?.cipher?.let { "${it.keySize.label}-${it.mode.label}" }
    val serverHost = (endpoint?.server ?: settings.bark.server.trim().removePrefix("https://"))
        .substringBefore('/')
        .ifBlank { DEFAULT_BARK_HOST }

    ScreenScaffold(title = stringResource(R.string.settings_privacy), onBack = onBack, spacing = 14.dp) {
        HeroOverviewCard(encrypted = cipherLabel != null, serverHost = serverHost)

        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.privacy_section_permissions), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Text(stringResource(R.string.privacy_section_permissions_sub), style = ReadableLine, color = Palette.Muted)
        }

        PermissionGuide.entries.forEach { guide ->
            PermissionGuideCard(guide, guide.status(readiness))
        }

        BarkServerCard(serverHost = serverHost)

        AesLockCard(cipherLabel = cipherLabel)

        LocalStorageCard()

        RedLinesCard()

        ControlCard()

        Text(
            stringResource(R.string.privacy_footer),
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            style = Type.Caption.copy(lineHeight = 18.sp),
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroOverviewCard(encrypted: Boolean, serverHost: String) {
    GlassCard(
        spacing = 14.dp,
        decoration = { ShieldDecor(Modifier.align(Alignment.TopEnd).offset(16.dp, (-16).dp)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.privacy_hero_title), style = Type.PageTitle, color = Palette.Ink)
            Text(stringResource(R.string.privacy_hero_desc), style = ReadableLine, color = Palette.Muted)
        }

        DataJourneyDiagram(encrypted = encrypted, serverHost = serverHost)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(stringResource(R.string.privacy_trust_no_server), Palette.GreenSurface, Palette.Green)
            StatusPill(stringResource(R.string.privacy_trust_no_history), Palette.NavySurface, Palette.NavyDeep)
            StatusPill(stringResource(R.string.privacy_trust_no_backup), Palette.NavySurface, Palette.NavyDeep)
        }
    }
}

@Composable
private fun DataJourneyDiagram(encrypted: Boolean, serverHost: String) {
    val flowDescription = stringResource(R.string.privacy_flow_desc)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(DiagramShape)
            .background(Color.White.copy(alpha = 0.64f))
            .border(1.dp, Palette.GlassBorder, DiagramShape)
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
                title = stringResource(if (encrypted) R.string.privacy_flow_aes_on else R.string.privacy_flow_https_only),
                subtitle = stringResource(if (encrypted) R.string.privacy_flow_aes_on_sub else R.string.privacy_flow_https_only_sub),
                modifier = Modifier.weight(1.1f),
            ) {
                IconBadge(
                    icon = if (encrypted) Icons.Lock else Icons.ShieldCheck,
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
                    Image(painterResource(R.drawable.bark_icon), contentDescription = null, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
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
private fun FlowArrow(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val midY = size.height / 2f
        val endX = size.width - 3.dp.toPx()
        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(0f, midY),
            end = Offset(endX, midY),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        val tip = 4.dp.toPx()
        val head = Path().apply {
            moveTo(endX - tip, midY - tip)
            lineTo(endX + 1.dp.toPx(), midY)
            lineTo(endX - tip, midY + tip)
        }
        drawPath(head, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun PermissionGuideCard(guide: PermissionGuide, status: PermissionStatus) {
    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(guide.icon, guide.badgeBg, guide.badgeTint, PanelShape, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(guide.title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                Text(
                    stringResource(guide.code),
                    style = MonoCaption.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = Palette.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PermissionStatusPill(status)
        }

        BoundaryStrip(guide)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaggedExplanation(
                tag = stringResource(R.string.privacy_tag_purpose),
                tagBg = Palette.NavySurface,
                tagColor = Palette.NavyDeep,
                text = stringResource(guide.purpose),
            )
            TaggedExplanation(
                tag = stringResource(R.string.privacy_tag_guard),
                tagBg = Palette.GreenSurface,
                tagColor = Palette.Green,
                text = stringResource(guide.guard),
            )
        }
    }
}

@Composable
private fun PermissionStatusPill(status: PermissionStatus) {
    val (labelRes, bg, tint) = when (status) {
        PermissionStatus.Granted -> Triple(R.string.privacy_status_granted, Palette.GreenSurface, Palette.Green)
        PermissionStatus.Missing -> Triple(R.string.privacy_status_not_granted, Palette.Neutral, Palette.Muted)
        PermissionStatus.Ready -> Triple(R.string.privacy_status_ready, Palette.GreenSurface, Palette.Green)
        PermissionStatus.Partial -> Triple(R.string.privacy_status_partial, Palette.AmberSurface, Palette.Amber)
    }
    StatusPill(stringResource(labelRes), bg, tint)
}

@Composable
private fun BoundaryStrip(guide: PermissionGuide) {
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
            .clip(PanelShape)
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.16f), PanelShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) { visual() }
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(badgeIcon, contentDescription = null, Modifier.size(12.dp), tint = accent)
            }
        }
        Text(title, style = Type.Caption, fontWeight = FontWeight.SemiBold, color = accent)
        Text(subtitle, style = Type.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Palette.Muted)
    }
}

@Composable
private fun AllowVisual(guide: PermissionGuide) {
    when (guide) {
        PermissionGuide.Sms -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Sms, contentDescription = null, Modifier.size(15.dp), tint = Palette.Green)
            }
            Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Green))
        }
        PermissionGuide.CallLog -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(Icons.MissedCall, contentDescription = null, Modifier.size(15.dp), tint = Palette.Green)
            }
            Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Green))
        }
        PermissionGuide.Sim -> Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetalSim(0, Modifier.scale(0.76f))
            MetalSim(1, Modifier.scale(0.76f))
        }
        PermissionGuide.Background -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Bell, contentDescription = null, Modifier.size(14.dp), tint = Palette.Green)
            }
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Battery, contentDescription = null, Modifier.size(15.dp), tint = Palette.Green)
            }
        }
    }
}

@Composable
private fun BlockVisual(guide: PermissionGuide) {
    when (guide) {
        PermissionGuide.Sms -> Canvas(Modifier.size(34.dp, 26.dp)) {
            val stroke = Palette.Crimson.copy(alpha = 0.55f)
            val fill = Color.White.copy(alpha = 0.72f)
            drawRoundRect(fill, Offset(0f, 7.dp.toPx()), Size(22.dp.toPx(), 15.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(stroke, Offset(0f, 7.dp.toPx()), Size(22.dp.toPx(), 15.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            drawRoundRect(fill, Offset(7.dp.toPx(), 2.dp.toPx()), Size(22.dp.toPx(), 15.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(stroke, Offset(7.dp.toPx(), 2.dp.toPx()), Size(22.dp.toPx(), 15.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            drawLine(Palette.Crimson, Offset(4.dp.toPx(), 23.dp.toPx()), Offset(28.dp.toPx(), 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
        PermissionGuide.CallLog -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Phone, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson.copy(alpha = 0.55f))
            }
            Icon(Icons.Ban, contentDescription = null, Modifier.size(15.dp), tint = Palette.Crimson)
        }
        PermissionGuide.Sim -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Phone, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson.copy(alpha = 0.55f))
            }
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Sms, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson.copy(alpha = 0.55f))
            }
        }
        PermissionGuide.Background -> MiniBadgeRow {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Globe, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson.copy(alpha = 0.55f))
            }
            Icon(Icons.Ban, contentDescription = null, Modifier.size(15.dp), tint = Palette.Crimson)
        }
    }
}

@Composable
private fun MiniBadgeRow(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        content()
    }
}

@Composable
private fun TaggedExplanation(tag: String, tagBg: Color, tagColor: Color, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .width(52.dp)
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
private fun BarkServerCard(serverHost: String) {
    val isSelfHosted = !serverHost.equals(DEFAULT_BARK_HOST, ignoreCase = true)
    val chainDesc = stringResource(R.string.privacy_server_chain_desc)

    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(
                icon = Icons.Cloud,
                background = if (isSelfHosted) Palette.GreenSurface else Palette.NavySurface,
                tint = if (isSelfHosted) Palette.Green else Palette.Navy,
                shape = PanelShape,
                size = 38.dp,
                iconSize = 19.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.privacy_server_title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                StatusPill(
                    text = stringResource(
                        if (isSelfHosted) R.string.privacy_server_badge_custom else R.string.privacy_server_badge_default,
                        serverHost,
                    ),
                    background = if (isSelfHosted) Palette.GreenSurface else Palette.NavySurface,
                    color = if (isSelfHosted) Palette.Green else Palette.NavyDeep,
                )
            }
        }

        Text(stringResource(R.string.privacy_server_desc), style = ReadableLine, color = Palette.Muted)

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(PanelShape)
                    .background(Color.White.copy(alpha = 0.64f))
                    .border(1.dp, Palette.GlassBorder, PanelShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconBadge(Icons.Globe, Palette.NavySurface, Palette.Navy, RoundedCornerShape(10.dp), size = 30.dp, iconSize = 16.dp)
                Text(stringResource(R.string.privacy_server_official_title), style = Type.Label, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                Text(stringResource(R.string.privacy_server_official_sub), style = MonoCaption.copy(fontSize = 11.sp), color = Palette.Navy)
                Text(stringResource(R.string.privacy_server_official_note), style = Type.Caption, color = Palette.Muted)
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(PanelShape)
                    .background(Palette.GreenSurface.copy(alpha = 0.72f))
                    .border(1.dp, Palette.Green.copy(alpha = 0.22f), PanelShape)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconBadge(Icons.Cloud, Color.White.copy(alpha = 0.85f), Palette.Green, RoundedCornerShape(10.dp), size = 30.dp, iconSize = 16.dp)
                    StatusPill(stringResource(R.string.privacy_server_cf_tag), Color.White.copy(alpha = 0.85f), Palette.Green)
                }
                Text(stringResource(R.string.privacy_server_cf_title), style = Type.Label, fontWeight = FontWeight.SemiBold, color = Palette.Green)
                Text(stringResource(R.string.privacy_server_cf_sub), style = Type.Caption, fontWeight = FontWeight.Medium, color = Palette.Ink)
                Text(stringResource(R.string.privacy_server_cf_note), style = Type.Caption, color = Palette.Muted)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(Color.White.copy(alpha = 0.64f))
                .border(1.dp, Palette.Green.copy(alpha = 0.18f), PanelShape)
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = chainDesc },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChainStep(Icons.Smartphone, stringResource(R.string.privacy_server_chain_phone), Modifier.weight(1f))
            FlowArrow(Modifier.width(16.dp).height(14.dp), Palette.Green)
            ChainStep(Icons.Cloud, stringResource(R.string.privacy_server_chain_cf), Modifier.weight(1.3f), highlight = true)
            FlowArrow(Modifier.width(16.dp).height(14.dp), Palette.Green)
            ChainStep(Icons.Bell, stringResource(R.string.privacy_server_chain_device), Modifier.weight(1f))
        }

        Text(stringResource(R.string.privacy_server_tip), style = Type.Caption.copy(lineHeight = 18.sp), color = Palette.Green)
    }
}

@Composable
private fun ChainStep(icon: ImageVector, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
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

@Composable
private fun AesLockCard(cipherLabel: String?) {
    var previewLocked by rememberSaveable { mutableStateOf(true) }
    val encrypted = cipherLabel != null

    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(
                icon = Icons.Lock,
                background = if (encrypted) Palette.GreenSurface else Palette.AmberSurface,
                tint = if (encrypted) Palette.Green else Palette.Copper,
                shape = PanelShape,
                size = 38.dp,
                iconSize = 18.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.privacy_aes_title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                StatusPill(
                    text = if (cipherLabel != null) {
                        stringResource(R.string.privacy_aes_badge_on, cipherLabel)
                    } else {
                        stringResource(R.string.privacy_aes_badge_off)
                    },
                    background = if (encrypted) Palette.GreenSurface else Palette.AmberSurface,
                    color = if (encrypted) Palette.Green else Palette.Amber,
                )
            }
        }

        Text(stringResource(R.string.privacy_aes_desc), style = ReadableLine, color = Palette.Muted)

        SegmentedChoice(
            options = listOf(
                true to stringResource(R.string.privacy_aes_mode_locked),
                false to stringResource(R.string.privacy_aes_mode_plain),
            ),
            selected = previewLocked,
            onSelect = { previewLocked = it },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(DiagramShape)
                .background(Color.White.copy(alpha = 0.64f))
                .border(1.dp, Palette.GlassBorder, DiagramShape)
                .animateContentSize(tween(220, easing = Motion.Standard))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.privacy_aes_sample_label), style = Type.Caption, color = Palette.Muted)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(PanelShape)
                        .background(Palette.Neutral)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Sms, contentDescription = null, Modifier.size(16.dp), tint = Palette.Navy)
                    Text(stringResource(R.string.privacy_aes_sample_body), style = ReadableLine, color = Palette.Ink)
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                val stepBg = if (previewLocked) Palette.GreenSurface else Palette.AmberSurface
                val stepColor = if (previewLocked) Palette.Green else Palette.Amber
                Icon(Icons.ArrowDown, contentDescription = null, Modifier.size(15.dp), tint = stepColor)
                Row(
                    Modifier.clip(CircleShape).background(stepBg).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(if (previewLocked) Icons.Lock else Icons.Shield, contentDescription = null, Modifier.size(13.dp), tint = stepColor)
                    Text(
                        stringResource(if (previewLocked) R.string.privacy_aes_step_locked else R.string.privacy_aes_step_plain),
                        style = Type.Caption,
                        fontWeight = FontWeight.Medium,
                        color = stepColor,
                    )
                }
            }

            AnimatedContent(
                targetState = previewLocked,
                transitionSpec = { Motion.Swap },
                label = "aesPreview",
            ) { locked ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (locked) R.string.privacy_aes_out_locked_label else R.string.privacy_aes_out_plain_label),
                        style = Type.Caption,
                        color = Palette.Muted,
                    )
                    val boxBg = if (locked) Palette.NavyDeep else Palette.AmberPale
                    val boxBorder = if (locked) Palette.NavyMid.copy(alpha = 0.4f) else Palette.AmberBorder.copy(alpha = 0.35f)
                    val codeColor = if (locked) Color(0xFFDCE7FB) else Palette.Ink
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(PanelShape)
                            .background(boxBg)
                            .border(1.dp, boxBorder, PanelShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            stringResource(if (locked) R.string.privacy_aes_out_locked_code else R.string.privacy_aes_out_plain_code),
                            style = MonoPacket,
                            color = codeColor,
                        )
                    }
                    Text(
                        stringResource(if (locked) R.string.privacy_aes_out_locked_hint else R.string.privacy_aes_out_plain_hint),
                        style = Type.Caption.copy(lineHeight = 18.sp),
                        color = if (locked) Palette.Green else Palette.Amber,
                    )
                }
            }
        }

        InfoNote(stringResource(R.string.privacy_aes_setting_hint), icon = Icons.Gear)
    }
}

@Composable
private fun LocalStorageCard() {
    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Text(stringResource(R.string.privacy_storage_title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageItems.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge(item.icon, item.badgeBg, item.badgeTint, PanelShape, size = 36.dp, iconSize = 18.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(stringResource(item.title), style = Type.RowTitle, fontWeight = FontWeight.Medium, color = Palette.Ink)
                        Text(stringResource(item.desc), style = ReadableLine, color = Palette.Muted)
                    }
                }
            }
        }
        InfoNote(stringResource(R.string.privacy_storage_no_backup), icon = Icons.ShieldCheck)
    }
}

@Composable
private fun RedLinesCard() {
    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Text(stringResource(R.string.privacy_never_title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
        RedLines.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { line ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(PanelShape)
                            .background(Color.White.copy(alpha = 0.64f))
                            .border(1.dp, Palette.GlassBorder, PanelShape)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(Palette.CrimsonSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Ban, contentDescription = null, Modifier.size(14.dp), tint = Palette.Crimson)
                        }
                        Text(stringResource(line.title), style = Type.Label, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                        Text(stringResource(line.desc), style = Type.Caption, color = Palette.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlCard() {
    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Text(stringResource(R.string.privacy_control_title), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ControlTiles.forEach { tile ->
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(Color.White.copy(alpha = 0.64f))
                        .border(1.dp, Palette.GlassBorder, PanelShape)
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconBadge(tile.icon, tile.badgeBg, tile.badgeTint, CircleShape, size = 34.dp, iconSize = 16.dp)
                    Text(
                        stringResource(tile.title),
                        style = Type.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(tile.desc),
                        style = Type.Caption.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = Palette.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, background: Color, color: Color) {
    Text(
        text,
        Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        style = Type.Caption,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
