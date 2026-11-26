package com.hypai.fion.ui.screen.forward

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.bark.BARK_SOUNDS
import com.hypai.fion.bark.BarkLevel
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Template
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.forward.groupChoice
import com.hypai.fion.forward.resolve
import com.hypai.fion.forward.templateChoice
import com.hypai.fion.store.PushProfile
import com.hypai.fion.store.defaultProfile
import com.hypai.fion.ui.currentLanguage
import com.hypai.fion.ui.design.AppSheet
import com.hypai.fion.ui.design.BleedSwitchRow
import com.hypai.fion.ui.design.Chevron
import com.hypai.fion.ui.design.FieldShape
import com.hypai.fion.ui.design.FormCard
import com.hypai.fion.ui.design.FormField
import com.hypai.fion.ui.design.GlassCard
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.RadioOption
import com.hypai.fion.ui.design.ScreenScaffold
import com.hypai.fion.ui.design.SegmentedChoice
import com.hypai.fion.ui.design.SheetButton
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.design.rememberDraft
import com.hypai.fion.ui.screen.PermissionPrompt
import com.hypai.fion.ui.screen.PermissionSheet

@get:StringRes
val EventKind.titleRes: Int
    get() = when (this) {
        EventKind.Sms -> R.string.forward_sms_title
        EventKind.MissedCall -> R.string.forward_call_title
    }

@get:StringRes
val BarkLevel.labelRes: Int
    get() = when (this) {
        BarkLevel.Passive -> R.string.level_passive
        BarkLevel.Active -> R.string.level_active
        BarkLevel.TimeSensitive -> R.string.level_time_sensitive
        BarkLevel.Critical -> R.string.level_critical
    }

@get:StringRes
private val BarkLevel.descriptionRes: Int
    get() = when (this) {
        BarkLevel.Passive -> R.string.level_passive_desc
        BarkLevel.Active -> R.string.level_active_desc
        BarkLevel.TimeSensitive -> R.string.level_time_sensitive_desc
        BarkLevel.Critical -> R.string.level_critical_desc
    }

@Composable
fun ForwardScreen(
    kind: EventKind,
    enabled: Boolean,
    saved: PushProfile,
    readiness: Readiness,
    onEnabledChange: (Boolean) -> Unit,
    onChange: (PushProfile) -> Unit,
    onBack: () -> Unit,
) {
    val form = rememberDraft(saved, onChange, key = kind)
    val draft = form.value
    val edit = form::edit
    var pickingSound by rememberSaveable { mutableStateOf(false) }
    var askingPermission by rememberSaveable { mutableStateOf(false) }
    var missingVariable by rememberSaveable { mutableStateOf<TemplateVariable?>(null) }
    val language = currentLanguage()
    val template = draft.template.resolve(kind, language)
    val editTemplate: (Template) -> Unit = { edit(draft.copy(template = kind.templateChoice(it))) }

    ScreenScaffold(
        title = stringResource(kind.titleRes),
        onBack = onBack,
        gutter = 20.dp,
        spacing = 16.dp,
        clearFocusOnImeHide = true,
    ) {
        GlassCard(padding = PaddingValues(16.dp)) {
            val active = enabled && readiness.allows(kind)
            val badge by animateColorAsState(if (active) Palette.NavySurface else Palette.Dormant, tween(200), label = "enableBadge")
            val badgeTint by animateColorAsState(if (active) Palette.Navy else Palette.Outline, tween(200), label = "enableBadgeTint")
            BleedSwitchRow(
                title = stringResource(
                    when (kind) {
                        EventKind.Sms -> R.string.forward_sms_switch
                        EventKind.MissedCall -> R.string.forward_call_switch
                    },
                ),
                subtitle = stringResource(
                    when {
                        !enabled -> R.string.forward_off
                        !readiness.allows(kind) -> when (kind) {
                            EventKind.Sms -> R.string.forward_sms_paused
                            EventKind.MissedCall -> R.string.forward_call_paused
                        }
                        kind == EventKind.Sms -> R.string.forward_sms_on
                        else -> R.string.forward_call_on
                    },
                ),
                checked = active,
                onCheckedChange = { on -> if (on && !readiness.allows(kind)) askingPermission = true else onEnabledChange(on) },
                titleStyle = Type.CardTitle.copy(lineHeight = 24.sp),
                minHeight = 40.dp,
                leading = { IconBadge(Icons.Forward, badge, badgeTint, size = 32.dp, iconSize = 16.dp) },
            )
        }

        FormCard(Icons.Template, Palette.NavySurface, Palette.Navy, stringResource(R.string.forward_template)) {
            TemplateField(
                value = template.title,
                onValueChange = { editTemplate(template.copy(title = it)) },
                label = stringResource(R.string.forward_template_title),
                kind = kind,
                language = language,
                readiness = readiness,
                onMissingPermission = { missingVariable = it },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            TemplateField(
                value = template.body,
                onValueChange = { editTemplate(template.copy(body = it)) },
                label = stringResource(R.string.forward_template_body),
                kind = kind,
                language = language,
                readiness = readiness,
                onMissingPermission = { missingVariable = it },
                lines = 4,
                hint = stringResource(R.string.forward_template_hint),
            )
        }

        FormCard(Icons.Bell, Palette.NavySurface, Palette.Navy, stringResource(R.string.forward_params)) {
            FormField(
                value = draft.group.resolve(kind, language),
                onValueChange = { edit(draft.copy(group = kind.groupChoice(it, language))) },
                label = stringResource(R.string.forward_group),
                hint = stringResource(R.string.forward_group_hint),
                textStyle = Type.Option,
            )
            SoundRow(draft.sound) { pickingSound = true }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.forward_level), style = Type.Body, color = Palette.Ink)
                SegmentedChoice(BarkLevel.entries.map { it to stringResource(it.labelRes) }, draft.level) { edit(draft.copy(level = it)) }
                Text(stringResource(draft.level.descriptionRes), style = Type.Caption, color = Palette.Muted)
            }
            BleedSwitchRow(
                title = stringResource(R.string.forward_archive),
                subtitle = stringResource(R.string.forward_archive_summary),
                checked = draft.archive,
                onCheckedChange = { edit(draft.copy(archive = it)) },
            )
        }
    }

    if (pickingSound) {
        SoundSheet(
            current = draft.sound,
            default = kind.defaultProfile.sound,
            onPick = {
                edit(draft.copy(sound = it))
                pickingSound = false
            },
            onDismiss = { pickingSound = false },
        )
    }

    val prompt = when {
        askingPermission -> PermissionPrompt.Forwarding(listOf(kind)) { onEnabledChange(true) }
        else -> missingVariable?.let(PermissionPrompt::Variable)
    }
    if (prompt != null) {
        PermissionSheet(prompt, readiness) {
            askingPermission = false
            missingVariable = null
        }
    }
}

@Composable
private fun SoundRow(sound: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(FieldShape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Palette.Ink.copy(alpha = 0.14f), FieldShape)
            .clickable(onClickLabel = stringResource(R.string.forward_pick_sound), role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.forward_sound), style = Type.Caption, color = Palette.Muted)
            Text(sound, style = Type.Field, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chevron()
    }
}

@Composable
private fun SoundSheet(current: String, default: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AppSheet(
        title = stringResource(R.string.forward_sound),
        subtitle = stringResource(R.string.sound_sheet_subtitle),
        onDismiss = onDismiss,
        actions = {
            SheetButton(stringResource(R.string.cancel), filled = false, onClick = onDismiss)
            SheetButton(stringResource(R.string.ok), filled = true) { onPick(selected) }
        },
    ) {
        Column(Modifier.selectableGroup()) {
            BARK_SOUNDS.forEach { sound ->
                RadioOption(
                    sound,
                    selected = sound == selected,
                    trailing = { if (sound == default) Text(stringResource(R.string.sound_default), style = Type.Body, color = Palette.Muted) },
                ) { selected = sound }
            }
        }
    }
}
