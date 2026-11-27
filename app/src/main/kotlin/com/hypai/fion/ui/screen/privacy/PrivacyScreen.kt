package com.hypai.fion.ui.screen.privacy

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.bark.DEFAULT_BARK_SERVER
import com.hypai.fion.bark.displayHost
import com.hypai.fion.bark.endpoint
import com.hypai.fion.forward.PermissionGroup
import com.hypai.fion.store.Settings
import com.hypai.fion.ui.design.BadgeStyle
import com.hypai.fion.ui.design.GlassCard
import com.hypai.fion.ui.design.Icon
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.InfoNote
import com.hypai.fion.ui.design.LazyScreenScaffold
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.SegmentedChoice
import com.hypai.fion.ui.design.ShieldDecor
import com.hypai.fion.ui.design.StatusPill
import com.hypai.fion.ui.design.Text
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.screen.look

internal enum class PermissionStatus { Granted, Missing, Ready, Partial }

internal enum class PermissionGuide(
    val group: PermissionGroup?,
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
        group = PermissionGroup.Sms,
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
        group = PermissionGroup.CallLog,
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
        group = PermissionGroup.Phone,
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
        group = null,
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

    val badge: BadgeStyle get() = group?.look?.badge ?: BackgroundBadge

    fun status(readiness: Readiness, persistentNotification: Boolean): PermissionStatus =
        group?.let { if (readiness.allows(it)) PermissionStatus.Granted else PermissionStatus.Missing }
            ?: if (readiness.backgroundReady(persistentNotification)) PermissionStatus.Ready else PermissionStatus.Partial
}

private val BackgroundBadge = BadgeStyle(Icons.Battery, Palette.GreenSurface, Palette.Green)

private data class FeatureItem(
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeTint: Color,
    @param:StringRes val title: Int,
    @param:StringRes val desc: Int,
)

private val StorageItems = listOf(
    FeatureItem(Icons.Gear, Palette.NavySurface, Palette.Navy, R.string.privacy_storage_config_title, R.string.privacy_storage_config_desc),
    FeatureItem(Icons.Pulse, Palette.AmberSurface, Palette.Copper, R.string.privacy_storage_stats_title, R.string.privacy_storage_stats_desc),
    FeatureItem(Icons.Forward, Palette.GreenSurface, Palette.Green, R.string.privacy_storage_queue_title, R.string.privacy_storage_queue_desc),
)

private data class RedLine(@param:StringRes val title: Int, @param:StringRes val desc: Int)

private val RedLines = listOf(
    RedLine(R.string.privacy_never_1_title, R.string.privacy_never_1_desc),
    RedLine(R.string.privacy_never_2_title, R.string.privacy_never_2_desc),
    RedLine(R.string.privacy_never_3_title, R.string.privacy_never_3_desc),
    RedLine(R.string.privacy_never_4_title, R.string.privacy_never_4_desc),
)

private val ControlTiles = listOf(
    FeatureItem(Icons.Power, Palette.NavySurface, Palette.Navy, R.string.privacy_control_1_title, R.string.privacy_control_1_desc),
    FeatureItem(Icons.Shield, Palette.GreenSurface, Palette.Green, R.string.privacy_control_2_title, R.string.privacy_control_2_desc),
    FeatureItem(Icons.Reset, Palette.CrimsonSurface, Palette.Crimson, R.string.privacy_control_3_title, R.string.privacy_control_3_desc),
)

@Composable
fun PrivacyScreen(
    settings: Settings,
    readiness: Readiness,
    onBack: (() -> Unit)?,
) {
    val cipherLabel = settings.bark.endpoint()?.cipher?.cipherLabel
    val serverHost = settings.bark.displayHost()

    LazyScreenScaffold(title = stringResource(R.string.settings_privacy), onBack = onBack, spacing = 14.dp) {
        item(key = "hero") {
            HeroOverviewCard(encrypted = cipherLabel != null, serverHost = serverHost)
        }
        item(key = "permHeader") {
            Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.privacy_section_permissions), style = Type.CardTitle, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                Text(stringResource(R.string.privacy_section_permissions_sub), style = ReadableLine, color = Palette.Muted)
            }
        }
        items(PermissionGuide.entries, key = { "perm_${it.name}" }) { guide ->
            PermissionGuideCard(guide, guide.status(readiness, settings.persistentNotification))
        }
        item(key = "barkServer") { BarkServerCard(serverHost = serverHost) }
        item(key = "aesLock") { AesLockCard(cipherLabel = cipherLabel) }
        item(key = "storage") { LocalStorageCard() }
        item(key = "redLines") { RedLinesCard() }
        item(key = "control") { ControlCard() }
        item(key = "footer") {
            Text(
                stringResource(R.string.privacy_footer),
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                style = Type.Caption.copy(lineHeight = 18.sp),
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
        }
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
private fun PermissionGuideCard(guide: PermissionGuide, status: PermissionStatus) {
    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(guide.badge, size = 40.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(guide.title),
                        Modifier.weight(1f),
                        style = Type.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PermissionStatusPill(status)
                }
                Text(
                    stringResource(guide.code),
                    style = MonoCaption.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
private fun BarkServerCard(serverHost: String) {
    val isSelfHosted = !serverHost.equals(DEFAULT_BARK_SERVER, ignoreCase = true)

    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        StatusCardHeader(
            icon = Icons.Cloud,
            iconBg = if (isSelfHosted) Palette.GreenSurface else Palette.NavySurface,
            iconTint = if (isSelfHosted) Palette.Green else Palette.Navy,
            iconSize = 19.dp,
            title = stringResource(R.string.privacy_server_title),
            pillText = stringResource(
                if (isSelfHosted) R.string.privacy_server_badge_custom else R.string.privacy_server_badge_default,
                serverHost,
            ),
            pillBg = if (isSelfHosted) Palette.GreenSurface else Palette.NavySurface,
            pillColor = if (isSelfHosted) Palette.Green else Palette.NavyDeep,
        )

        Text(stringResource(R.string.privacy_server_desc), style = ReadableLine, color = Palette.Muted)

        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().privacyPanel().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconBadge(Icons.Globe, Palette.NavySurface, Palette.Navy, RoundedCornerShape(10.dp), size = 30.dp, iconSize = 16.dp)
                Text(stringResource(R.string.privacy_server_official_title), style = Type.Label, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                Text(DEFAULT_BARK_SERVER, style = MonoCaption.copy(fontSize = 11.sp), color = Palette.Navy)
                Text(stringResource(R.string.privacy_server_official_note), style = Type.Caption, color = Palette.Muted)
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .privacyPanel(PanelShape, Palette.GreenSurface.copy(alpha = 0.72f), Palette.Green.copy(alpha = 0.22f))
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

        val chainServerLabel = if (isSelfHosted) serverHost else stringResource(R.string.privacy_server_chain_cf)
        val chainDesc = stringResource(R.string.privacy_server_chain_desc, chainServerLabel)
        Row(
            Modifier
                .fillMaxWidth()
                .privacyPanel(PanelShape, PanelSurface, Palette.Green.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = chainDesc },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChainStep(Icons.Smartphone, stringResource(R.string.privacy_server_chain_phone), Modifier.weight(1f))
            FlowArrow(Modifier.width(16.dp).height(14.dp), Palette.Green)
            ChainStep(Icons.Cloud, chainServerLabel, Modifier.weight(1.3f), highlight = true)
            FlowArrow(Modifier.width(16.dp).height(14.dp), Palette.Green)
            ChainStep(Icons.Bell, stringResource(R.string.privacy_server_chain_device), Modifier.weight(1f))
        }

        Text(stringResource(R.string.privacy_server_tip), style = Type.Caption.copy(lineHeight = 18.sp), color = Palette.Green)
    }
}

private val PreviewStates = listOf(true, false)

@Composable
private fun AesLockCard(cipherLabel: String?) {
    var previewLocked by rememberSaveable { mutableStateOf(true) }
    val encrypted = cipherLabel != null
    val lockedLabel = stringResource(R.string.privacy_aes_mode_locked)
    val plainLabel = stringResource(R.string.privacy_aes_mode_plain)

    GlassCard(padding = PaddingValues(16.dp), spacing = 12.dp) {
        StatusCardHeader(
            icon = Icons.Lock,
            iconBg = if (encrypted) Palette.GreenSurface else Palette.AmberSurface,
            iconTint = if (encrypted) Palette.Green else Palette.Copper,
            iconSize = 18.dp,
            title = stringResource(R.string.privacy_aes_title),
            pillText = if (cipherLabel != null) {
                stringResource(R.string.privacy_aes_badge_on, cipherLabel)
            } else {
                stringResource(R.string.privacy_aes_badge_off)
            },
            pillBg = if (encrypted) Palette.GreenSurface else Palette.AmberSurface,
            pillColor = if (encrypted) Palette.Green else Palette.Amber,
        )

        Text(stringResource(R.string.privacy_aes_desc), style = ReadableLine, color = Palette.Muted)

        SegmentedChoice(
            options = PreviewStates,
            selected = previewLocked,
            label = { if (it) lockedLabel else plainLabel },
            onSelect = { previewLocked = it },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .privacyPanel(DiagramShape)
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
                    val codeColor = if (locked) Palette.PacketInk else Palette.Ink
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .privacyPanel(PanelShape, boxBg, boxBorder)
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
        InfoNote(stringResource(R.string.privacy_storage_no_backup), icon = Icons.Shield)
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
                        Modifier.weight(1f).fillMaxHeight().privacyPanel().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconBadge(Icons.Ban, Palette.CrimsonSurface, Palette.Crimson, CircleShape, size = 26.dp, iconSize = 14.dp)
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
                    Modifier.weight(1f).fillMaxHeight().privacyPanel().padding(10.dp),
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
