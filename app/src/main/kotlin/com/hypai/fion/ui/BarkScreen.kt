package com.hypai.fion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.core.AesConfig
import com.hypai.fion.core.AesKeySize
import com.hypai.fion.core.AesMode
import com.hypai.fion.core.BarkSettings
import com.hypai.fion.core.formErrors
import com.hypai.fion.core.keyError
import kotlinx.serialization.json.Json

private val BarkSaver = Saver<BarkSettings, String>(
    save = { Json.encodeToString(BarkSettings.serializer(), it) },
    restore = { Json.decodeFromString(BarkSettings.serializer(), it) },
)

private val MonoField = Type.Field.copy(fontFamily = FontFamily.Monospace)

@Composable
fun BarkScreen(saved: BarkSettings, onChange: (BarkSettings) -> Unit, onBack: () -> Unit) {
    var draft by rememberSaveable(stateSaver = BarkSaver) { mutableStateOf(saved) }
    var showDeviceKey by rememberSaveable { mutableStateOf(false) }
    val errors = draft.formErrors()
    val edit: (BarkSettings) -> Unit = {
        draft = it
        onChange(it)
    }

    ScreenScaffold(title = stringResource(R.string.settings_bark), onBack = onBack, gutter = 20.dp, spacing = 16.dp) {
        FormCard(Icons.Link, Palette.AmberSurface, Palette.Amber, stringResource(R.string.bark_server)) {
            FormField(
                value = draft.server,
                onValueChange = { edit(draft.copy(server = it)) },
                label = stringResource(R.string.bark_server_field),
                prefix = "https://",
                isError = errors.invalidServer,
                hint = if (errors.invalidServer) stringResource(R.string.bark_server_error) else null,
                hintColor = Palette.Error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            FormField(
                value = draft.deviceKey,
                onValueChange = { edit(draft.copy(deviceKey = it)) },
                label = "Device Key",
                isError = errors.invalidDeviceKey,
                hint = stringResource(if (errors.invalidDeviceKey) R.string.bark_device_key_error else R.string.bark_device_key_hint),
                hintColor = if (errors.invalidDeviceKey) Palette.Error else Palette.Muted,
                textStyle = MonoField,
                visualTransformation = if (showDeviceKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            ) {
                IconButton(onClick = { showDeviceKey = !showDeviceKey }, Modifier.padding(end = 4.dp)) {
                    Icon(
                        if (showDeviceKey) Icons.Eye else Icons.EyeOff,
                        contentDescription = stringResource(if (showDeviceKey) R.string.bark_hide_device_key else R.string.bark_show_device_key),
                        Modifier.size(22.dp),
                        tint = Palette.Muted,
                    )
                }
            }
        }

        FormCard(Icons.Lock, Palette.NavySurface, Palette.Navy, stringResource(R.string.bark_encryption)) {
            AesFields(draft.aes) { edit(draft.copy(aes = it)) }
        }
    }
}

@Composable
private fun AesFields(aes: AesConfig, onChange: (AesConfig) -> Unit) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    val keyError = aes.keyError()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.bark_algorithm), style = Type.Body, color = Palette.Ink)
        SegmentedChoice(AesKeySize.entries.map { it to it.label }, aes.keySize) { onChange(aes.copy(keySize = it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.bark_mode), style = Type.Body, color = Palette.Ink)
        SegmentedChoice(AesMode.entries.map { it to it.label }, aes.mode) { onChange(aes.copy(mode = it)) }
    }
    FormField(
        value = aes.key,
        onValueChange = { onChange(aes.copy(key = it)) },
        label = stringResource(R.string.bark_key),
        hint = when {
            aes.key.isEmpty() -> stringResource(R.string.bark_key_empty, aes.keySize.bytes)
            keyError != null -> stringResource(R.string.bark_key_mismatch, keyError.expected, keyError.actual)
            else -> stringResource(R.string.bark_key_ok, aes.keySize.bytes)
        },
        hintColor = if (aes.key.isNotEmpty() && keyError == null) Palette.Navy else Palette.Muted,
        textStyle = MonoField,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    ) {
        IconButton(onClick = { showKey = !showKey }, Modifier.padding(end = 4.dp)) {
            Icon(
                if (showKey) Icons.Eye else Icons.EyeOff,
                contentDescription = stringResource(if (showKey) R.string.bark_hide_key else R.string.bark_show_key),
                Modifier.size(22.dp),
                tint = Palette.Muted,
            )
        }
    }
    InfoNote(stringResource(R.string.bark_encryption_note))
}
