package com.hypai.fion.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val SAVE_DEBOUNCE_MS = 300L

@Stable
class Draft<T>(initial: T) {
    var value by mutableStateOf(initial)
        private set
    private var dirty = false

    fun edit(transform: (T) -> T) {
        value = transform(value)
        dirty = true
    }

    internal fun sync(saved: T) {
        if (!dirty) value = saved
    }

    internal fun takeDirty(): T? = value.takeIf { dirty }.also { dirty = false }
}

@Composable
fun <T> rememberDraft(saved: T, onSave: (T) -> Unit, key: Any? = Unit): Draft<T> {
    val draft = remember(key) { Draft(saved) }
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(draft, saved) { draft.sync(saved) }
    LaunchedEffect(draft, draft.value) {
        delay(SAVE_DEBOUNCE_MS)
        draft.takeDirty()?.let(save)
    }
    DisposableEffect(draft) { onDispose { draft.takeDirty()?.let(save) } }
    return draft
}
