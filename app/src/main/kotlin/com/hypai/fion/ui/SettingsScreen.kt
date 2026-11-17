package com.hypai.fion.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypai.fion.BuildConfig
import com.hypai.fion.R
import com.hypai.fion.core.BarkSettings
import com.hypai.fion.core.EventKind
import com.hypai.fion.core.Language
import com.hypai.fion.core.Settings
import com.hypai.fion.core.endpoint
import com.hypai.fion.platform.PermissionHealth
import com.hypai.fion.platform.switchLanguage

private const val BARK_WIKI = "https://bark.day.app/#/?id=bark"

@Composable
private fun Language.displayName(): String = stringResource(
    when (this) {
        Language.En -> R.string.language_name_en
        Language.Zh -> R.string.language_name_zh
    },
)

@Composable
fun SettingsScreen(
    settings: Settings,
    permissionHealth: PermissionHealth,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onResetStats: () -> Unit,
    onOpen: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val language = currentLanguage()
    val noBrowser = stringResource(R.string.settings_no_browser)
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var pickingLanguage by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        GlassCard(padding = PaddingValues(0.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(Screen.Bark) }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(painterResource(R.drawable.bark_icon), contentDescription = null, Modifier.size(52.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.settings_bark), style = Type.CardTitle.copy(lineHeight = 24.sp), color = Palette.Ink)
                    Text(
                        settings.bark.summary(),
                        style = Type.Secondary,
                        color = Palette.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Chevron()
            }
        }

        RowsCard {
            EventKind.entries.forEach { kind ->
                val profile = settings.push(kind)
                val (icon, surface, tint) = when (kind) {
                    EventKind.Sms -> Triple(Icons.Sms, Palette.NavySurface, Palette.Navy)
                    EventKind.MissedCall -> Triple(Icons.MissedCall, Palette.CrimsonSurface, Palette.Crimson)
                }
                ListRow(
                    title = stringResource(kind.titleRes),
                    subtitle = stringResource(
                        R.string.forward_summary,
                        stringResource(if (settings.forwards(kind)) R.string.forward_summary_on else R.string.forward_summary_off),
                        profile.sound,
                        stringResource(profile.level.labelRes),
                    ),
                    leading = { IconBadge(icon, surface, tint) },
                    trailing = { Chevron() },
                    onClick = { onOpen(Screen.Forward(kind)) },
                )
            }
        }

        RowsCard {
            ListRow(
                title = stringResource(R.string.settings_keep_alive),
                subtitle = stringResource(R.string.settings_keep_alive_summary),
                trailing = { Chevron() },
                leading = {
                    val (surface, tint) = when (permissionHealth) {
                        PermissionHealth.Ready -> Palette.GreenSurface to Palette.Green
                        PermissionHealth.Partial -> Palette.AmberSurface to Palette.Amber
                        PermissionHealth.Blocked -> Palette.RedSurface to Palette.Red
                    }
                    IconBadge(Icons.Shield, surface, tint, FieldShape)
                },
                onClick = { onOpen(Screen.KeepAlive) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_boot),
                subtitle = stringResource(R.string.settings_boot_summary),
                checked = settings.autoStartOnBoot,
                onCheckedChange = { on -> onUpdate { it.copy(autoStartOnBoot = on) } },
                leading = { IconBadge(Icons.Power) },
            )
        }

        RowsCard {
            ListRow(
                title = stringResource(R.string.settings_language),
                subtitle = language.displayName(),
                leading = { IconBadge(Icons.Globe) },
                trailing = { Chevron() },
                onClick = { pickingLanguage = true },
            )
            ListRow(
                title = "Bark Wiki",
                subtitle = stringResource(R.string.settings_wiki_summary),
                leading = { IconBadge(Icons.Book) },
                onClick = {
                    try {
                        uriHandler.openUri(BARK_WIKI)
                    } catch (_: IllegalArgumentException) {
                        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    Icons.External,
                    contentDescription = stringResource(R.string.settings_open_in_browser),
                    Modifier.size(20.dp),
                    tint = Palette.Muted,
                )
            }
            ListRow(
                title = stringResource(R.string.settings_version),
                subtitle = "",
                leading = { IconBadge(Icons.Info) },
            ) { Text(BuildConfig.VERSION_NAME, style = Type.Body, color = Palette.Muted) }
        }

        RowsCard {
            ListRow(
                title = stringResource(R.string.settings_reset),
                subtitle = stringResource(R.string.settings_reset_summary),
                titleColor = Palette.Error,
                leading = { IconBadge(Icons.Reset, Palette.ErrorSurface, Palette.OnErrorSurface) },
                onClick = { confirmReset = true },
            )
        }
    }

    if (pickingLanguage) {
        LanguageSheet(
            current = language,
            onPick = { picked ->
                pickingLanguage = false
                if (picked != language) context.switchLanguage(picked)
            },
            onDismiss = { pickingLanguage = false },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset),
            text = stringResource(R.string.settings_reset_text),
            confirm = stringResource(R.string.settings_reset_confirm),
            dismiss = stringResource(R.string.cancel),
            onConfirm = {
                confirmReset = false
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onResetStats()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun RowsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(padding = PaddingValues(8.dp), content = content)
}

@Composable
private fun BarkSettings.summary(): String {
    val endpoint = endpoint() ?: return stringResource(if (deviceKey.isBlank()) R.string.settings_bark_unset else R.string.settings_bark_invalid)
    val security = endpoint.cipher?.let { "${it.keySize.label}-${it.mode.label}" } ?: stringResource(R.string.settings_bark_plain)
    return "${endpoint.server.substringBefore('/')} · $security"
}

@Composable
private fun LanguageSheet(current: Language, onPick: (Language) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AppSheet(
        title = stringResource(R.string.settings_language),
        subtitle = stringResource(R.string.settings_language_summary),
        onDismiss = onDismiss,
        actions = {
            SheetButton(stringResource(R.string.cancel), filled = false, onClick = onDismiss)
            SheetButton(stringResource(R.string.ok), filled = true) { onPick(selected) }
        },
    ) {
        Column(Modifier.selectableGroup()) {
            Language.entries.forEach { language ->
                RadioOption(language.displayName(), selected = language == selected) { selected = language }
            }
        }
    }
}
