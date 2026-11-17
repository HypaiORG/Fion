package com.hypai.fion.ui.screen

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hypai.fion.R
import com.hypai.fion.bark.AesConfig
import com.hypai.fion.bark.AesKeySize
import com.hypai.fion.bark.AesMode
import com.hypai.fion.bark.BarkField
import com.hypai.fion.bark.BarkSettings
import com.hypai.fion.bark.formErrors
import com.hypai.fion.bark.keyError
import com.hypai.fion.bark.valueOf
import com.hypai.fion.bark.withInput
import com.hypai.fion.ui.design.FormCard
import com.hypai.fion.ui.design.FormField
import com.hypai.fion.ui.design.Icons
import com.hypai.fion.ui.design.InfoNote
import com.hypai.fion.ui.design.Palette
import com.hypai.fion.ui.design.ScreenScaffold
import com.hypai.fion.ui.design.SegmentedChoice
import com.hypai.fion.ui.design.Type
import com.hypai.fion.ui.design.rememberDraft

private val MonoField = Type.Field.copy(fontFamily = FontFamily.Monospace)

@Composable
fun BarkScreen(saved: BarkSettings, onChange: (BarkSettings) -> Unit, onBack: () -> Unit) {
    val form = rememberDraft(saved, onChange)
    val draft = form.value
    val edit = form::edit
    val haptics = LocalHapticFeedback.current
    fun input(field: BarkField, text: String) {
        val next = draft.withInput(field, text)
        if (next.valueOf(field) != text) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        edit(next)
    }
    var showDeviceKey by rememberSaveable { mutableStateOf(false) }
    val errors = draft.formErrors()

    ScreenScaffold(
        title = stringResource(R.string.settings_bark),
        onBack = onBack,
        gutter = 20.dp,
        spacing = 16.dp,
        clearFocusOnImeHide = true,
    ) {
        FormCard(Icons.Link, Palette.AmberSurface, Palette.Amber, stringResource(R.string.bark_endpoint_title)) {
            FormField(
                value = draft.server,
                onValueChange = { input(BarkField.Server, it) },
                label = stringResource(R.string.bark_endpoint_host),
                prefix = "https://",
                isError = errors.invalidServer,
                hint = if (errors.invalidServer) stringResource(R.string.bark_endpoint_host_error) else null,
                hintColor = Palette.Error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            FormField(
                value = draft.deviceKey,
                onValueChange = { input(BarkField.DeviceKey, it) },
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
        isError = keyError != null,
        hint = when {
            aes.key.isEmpty() -> stringResource(R.string.bark_key_empty, aes.keySize.bytes)
            keyError != null -> stringResource(R.string.bark_key_mismatch, keyError.expected, keyError.actual)
            else -> stringResource(R.string.bark_key_ok, aes.keySize.bytes)
        },
        hintColor = when {
            keyError != null -> Palette.Error
            aes.key.isNotEmpty() -> Palette.Navy
            else -> Palette.Muted
        },
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
