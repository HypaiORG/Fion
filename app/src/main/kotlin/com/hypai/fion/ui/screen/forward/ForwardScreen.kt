package com.hypai.fion.ui.screen.forward

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
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
import com.hypai.fion.ui.design.ActionPill
import com.hypai.fion.ui.design.AppSheet
import com.hypai.fion.ui.design.BleedSwitchRow
import com.hypai.fion.ui.design.FormCard
import com.hypai.fion.ui.design.FormField
import com.hypai.fion.ui.design.FormSelectRow
import com.hypai.fion.ui.design.GlassCard
import com.hypai.fion.ui.design.IconBadge
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.RadioOption
import com.hypai.fion.ui.design.ScreenScaffold
import com.hypai.fion.ui.design.SegmentedChoice
import com.hypai.fion.ui.design.SheetButton
import com.hypai.fion.ui.design.Text
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
    onBack: (() -> Unit)?,
) {
    val form = rememberDraft(saved, onChange, key = kind)
    val draft = form.value
    var pickingSound by rememberSaveable { mutableStateOf(false) }
    var askingPermission by rememberSaveable { mutableStateOf(false) }
    var missingVariable by rememberSaveable { mutableStateOf<TemplateVariable?>(null) }
    val language = currentLanguage()
    val template = remember(draft.template, kind, language) { draft.template.resolve(kind, language) }
    fun editTemplate(transform: (Template) -> Template) {
        form.edit { current ->
            val resolved = current.template.resolve(kind, language)
            current.copy(template = kind.templateChoice(transform(resolved)))
        }
    }

    ScreenScaffold(
        title = stringResource(kind.titleRes),
        onBack = onBack,
        gutter = 20.dp,
        spacing = 16.dp,
        clearFocusOnImeHide = true,
    ) {
        GlassCard(padding = PaddingValues(16.dp), spacing = 10.dp) {
            val granted = readiness.allows(kind)
            val active = enabled && granted
            val pausedByPermission = enabled && !granted
            val badge by animateColorAsState(
                when {
                    active -> Palette.NavySurface
                    pausedByPermission -> Palette.AmberSurface
                    else -> Palette.Dormant
                },
                tween(200),
                label = "enableBadge",
            )
            val badgeTint by animateColorAsState(
                when {
                    active -> Palette.Navy
                    pausedByPermission -> Palette.Amber
                    else -> Palette.Outline
                },
                tween(200),
                label = "enableBadgeTint",
            )
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
                        !granted -> when (kind) {
                            EventKind.Sms -> R.string.forward_sms_paused
                            EventKind.MissedCall -> R.string.forward_call_paused
                        }
                        kind == EventKind.Sms -> R.string.forward_sms_on
                        else -> R.string.forward_call_on
                    },
                ),
                checked = enabled,
                onCheckedChange = { on -> if (on && !granted) askingPermission = true else onEnabledChange(on) },
                titleStyle = Type.CardTitle.copy(lineHeight = 24.sp),
                minHeight = 40.dp,
                leading = { IconBadge(Icons.Forward, badge, badgeTint, size = 32.dp, iconSize = 16.dp, alert = pausedByPermission) },
            )
            if (pausedByPermission) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    ActionPill(stringResource(R.string.home_notice_grant), onClick = { askingPermission = true })
                }
            }
        }

        FormCard(Icons.Template, Palette.NavySurface, Palette.Navy, stringResource(R.string.forward_template)) {
            TemplateField(
                value = template.title,
                onValueChange = { parts -> editTemplate { it.copy(title = parts) } },
                label = stringResource(R.string.forward_template_title),
                kind = kind,
                language = language,
                readiness = readiness,
                onMissingPermission = { missingVariable = it },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            )
            TemplateField(
                value = template.body,
                onValueChange = { parts -> editTemplate { it.copy(body = parts) } },
                label = stringResource(R.string.forward_template_body),
                kind = kind,
                language = language,
                readiness = readiness,
                onMissingPermission = { missingVariable = it },
                lines = 4,
                hint = stringResource(R.string.forward_template_hint),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }

        FormCard(Icons.Bell, Palette.NavySurface, Palette.Navy, stringResource(R.string.forward_params)) {
            FormField(
                value = draft.group.resolve(kind, language),
                onValueChange = { text -> form.edit { it.copy(group = kind.groupChoice(text, language)) } },
                label = stringResource(R.string.forward_group),
                hint = stringResource(R.string.forward_group_hint),
                textStyle = Type.Option,
            )
            FormSelectRow(
                label = stringResource(R.string.forward_sound),
                value = draft.sound,
                onClickLabel = stringResource(R.string.forward_pick_sound),
                onClick = { pickingSound = true },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.forward_level), style = Type.Body, color = Palette.Ink)
                SegmentedChoice(BarkLevel.entries, draft.level, { stringResource(it.labelRes) }) { level ->
                    form.edit { it.copy(level = level) }
                }
                Text(stringResource(draft.level.descriptionRes), style = Type.Caption, color = Palette.Muted)
            }
            BleedSwitchRow(
                title = stringResource(R.string.forward_archive),
                subtitle = stringResource(R.string.forward_archive_summary),
                checked = draft.archive,
                onCheckedChange = { archive -> form.edit { it.copy(archive = archive) } },
            )
        }
    }

    if (pickingSound) {
        SoundSheet(
            current = draft.sound,
            default = kind.defaultProfile.sound,
            onPick = { sound ->
                form.edit { it.copy(sound = sound) }
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
private fun SoundSheet(current: String, default: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AppSheet(
        title = stringResource(R.string.forward_sound),
        subtitle = stringResource(R.string.sound_sheet_subtitle),
        onDismiss = onDismiss,
        actions = { close ->
            SheetButton(stringResource(R.string.cancel), filled = false) { close {} }
            SheetButton(stringResource(R.string.ok), filled = true) { close { onPick(selected) } }
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
