package com.hypai.fion.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.hypai.fion.R
import com.hypai.fion.core.BARK_SOUNDS
import com.hypai.fion.core.BarkLevel
import com.hypai.fion.core.EventKind
import com.hypai.fion.core.PushProfile
import com.hypai.fion.core.Template
import com.hypai.fion.core.TemplateVariable
import com.hypai.fion.core.defaultProfile
import com.hypai.fion.core.groupChoice
import com.hypai.fion.core.resolve
import com.hypai.fion.core.templateChoice
import kotlinx.serialization.json.Json

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

@get:StringRes
private val TemplateVariable.descriptionRes: Int
    get() = when (this) {
        TemplateVariable.From -> R.string.var_from
        TemplateVariable.Content -> R.string.var_content
        TemplateVariable.CallCount -> R.string.var_call_count
        TemplateVariable.Time -> R.string.var_time
        TemplateVariable.Carrier -> R.string.var_carrier
        TemplateVariable.SimName -> R.string.var_sim_name
        TemplateVariable.Number -> R.string.var_number
    }

private val ProfileSaver = Saver<PushProfile, String>(
    save = { Json.encodeToString(PushProfile.serializer(), it) },
    restore = { Json.decodeFromString(PushProfile.serializer(), it) },
)

@Composable
fun ForwardScreen(
    kind: EventKind,
    enabled: Boolean,
    saved: PushProfile,
    onEnabledChange: (Boolean) -> Unit,
    onChange: (PushProfile) -> Unit,
    onBack: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = ProfileSaver) { mutableStateOf(saved) }
    var showVariables by rememberSaveable { mutableStateOf(false) }
    var pickingSound by rememberSaveable { mutableStateOf(false) }
    val language = currentLanguage()
    val template = draft.template.resolve(kind, language)
    val edit: (PushProfile) -> Unit = {
        draft = it
        onChange(it)
    }
    val editTemplate: (Template) -> Unit = { edit(draft.copy(template = kind.templateChoice(it))) }

    ScreenScaffold(title = stringResource(kind.titleRes), onBack = onBack, gutter = 20.dp, spacing = 16.dp) {
        GlassCard(padding = PaddingValues(16.dp)) {
            EnableLine(kind, enabled, onEnabledChange)
        }

        FormCard(
            Icons.Template,
            Palette.NavySurface,
            Palette.Navy,
            stringResource(R.string.forward_template),
            action = {
                IconButton(onClick = { showVariables = true }, Modifier.offset(x = 8.dp)) {
                    Icon(Icons.Notice, contentDescription = stringResource(R.string.forward_show_variables), Modifier.size(22.dp), tint = Palette.Muted)
                }
            },
        ) {
            FormField(
                value = template.title,
                onValueChange = { editTemplate(template.copy(title = it)) },
                label = stringResource(R.string.forward_template_title),
                textStyle = Type.Option,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FormField(
                value = template.body,
                onValueChange = { editTemplate(template.copy(body = it)) },
                label = stringResource(R.string.forward_template_body),
                lines = 4,
                textStyle = Type.FieldMultiline,
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
            ToggleLine(
                title = stringResource(R.string.forward_archive),
                subtitle = stringResource(R.string.forward_archive_summary),
                checked = draft.archive,
                onCheckedChange = { edit(draft.copy(archive = it)) },
            )
        }
    }

    if (showVariables) VariablesSheet { showVariables = false }

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
}

@Composable
private fun EnableLine(kind: EventKind, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val badge by animateColorAsState(if (enabled) Palette.NavySurface else Palette.Dormant, tween(200), label = "enableBadge")
    val badgeTint by animateColorAsState(if (enabled) Palette.Navy else Palette.Outline, tween(200), label = "enableBadgeTint")
    Row(
        Modifier
            .bleed()
            .fillMaxWidth()
            .clip(RowShape)
            .toggleable(value = enabled, role = Role.Switch) {
                haptics.toggle(it)
                onChange(it)
            }
            .padding(ROW_BLEED)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(Icons.Forward, badge, badgeTint, size = 32.dp, iconSize = 16.dp)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    when (kind) {
                        EventKind.Sms -> R.string.forward_sms_switch
                        EventKind.MissedCall -> R.string.forward_call_switch
                    },
                ),
                style = Type.CardTitle.copy(lineHeight = 24.sp),
                color = Palette.Ink,
            )
            Text(
                stringResource(
                    when {
                        !enabled -> R.string.forward_off
                        kind == EventKind.Sms -> R.string.forward_sms_on
                        else -> R.string.forward_call_on
                    },
                ),
                style = Type.Caption,
                color = Palette.Muted,
            )
        }
        AppSwitch(enabled, onCheckedChange = null)
    }
}

private val ROW_BLEED = 8.dp

private fun Modifier.bleed(): Modifier = layout { measurable, constraints ->
    val bleed = ROW_BLEED.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * bleed, 2 * bleed))
    layout(placeable.width - 2 * bleed, placeable.height - 2 * bleed) { placeable.place(-bleed, -bleed) }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .bleed()
            .fillMaxWidth()
            .clip(RowShape)
            .toggleable(value = checked, role = Role.Switch) {
                haptics.toggle(it)
                onCheckedChange(it)
            }
            .padding(ROW_BLEED)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = Type.Body, color = Palette.Ink)
            Text(subtitle, style = Type.Caption, color = Palette.Muted)
        }
        AppSwitch(checked, onCheckedChange = null)
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

@Composable
private fun VariablesSheet(onDismiss: () -> Unit) {
    val language = currentLanguage()
    AppSheet(
        title = stringResource(R.string.variables_title),
        subtitle = stringResource(R.string.variables_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TemplateVariable.entries.forEach { variable ->
                Column(
                    Modifier.heightIn(min = 64.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Text(variable.token(language), style = Type.FieldMultiline.copy(fontFamily = FontFamily.Monospace), color = Palette.Navy)
                    Text(stringResource(variable.descriptionRes), style = Type.Secondary, color = Palette.Muted)
                }
            }
        }
    }
}
