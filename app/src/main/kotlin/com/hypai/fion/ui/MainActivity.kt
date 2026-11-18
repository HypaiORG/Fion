package com.hypai.fion.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.hypai.fion.AppGraph
import com.hypai.fion.R
import com.hypai.fion.core.EventKind
import com.hypai.fion.core.Language
import com.hypai.fion.core.Settings
import com.hypai.fion.core.Stats
import com.hypai.fion.graph
import com.hypai.fion.platform.Readiness
import com.hypai.fion.platform.health
import com.hypai.fion.platform.readiness
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Onboarding : Screen
    @Serializable data object Home : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Bark : Screen
    @Serializable data object KeepAlive : Screen
    @Serializable data class Forward(val kind: EventKind) : Screen
}

class MainViewModel(private val graph: AppGraph) : ViewModel() {
    val settings = graph.settings.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val runtime = graph.runtime.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { graph.settings.updateData { transform(it) } }
    }

    fun resetStats() {
        viewModelScope.launch { graph.runtime.updateData { it.copy(stats = Stats()) } }
    }

    fun finishOnboarding(readiness: Readiness) {
        viewModelScope.launch {
            graph.runtime.updateData { it.copy(onboardingDone = true) }
            pauseWithoutPermissions(readiness)
        }
    }

    fun onForeground(readiness: Readiness) {
        viewModelScope.launch {
            if (graph.runtime.data.first().onboardingDone) pauseWithoutPermissions(readiness)
            graph.forwarder.activate()
            graph.reconcileKeepAlive()
            graph.watchCallLog()
        }
    }

    private suspend fun pauseWithoutPermissions(readiness: Readiness) {
        if (!readiness.forwardable) graph.settings.updateData { it.withForwarding(false) }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { AppTheme { AppRoot() } }
    }
}

private class ReadinessState(private val read: () -> Readiness) {
    var value: Readiness by mutableStateOf(read())
        private set

    fun refresh() {
        value = read()
    }
}

@Composable
private fun rememberReadiness(onChange: (Readiness) -> Unit): ReadinessState {
    val context = LocalContext.current
    val state = remember { ReadinessState(context::readiness) }
    LifecycleResumeEffect(Unit) {
        state.refresh()
        onChange(state.value)
        onPauseOrDispose {}
    }
    return state
}

@Composable
fun currentLanguage(): Language = Language.valueOf(stringResource(R.string.language))

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val model: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(context.graph) } })
    val settings = model.settings.collectAsStateWithLifecycle().value ?: return
    val runtime = model.runtime.collectAsStateWithLifecycle()
    val onboardingDone = remember { derivedStateOf { runtime.value?.onboardingDone } }.value ?: return
    val readiness = rememberReadiness(onChange = model::onForeground)

    val backStack = rememberNavBackStack(if (onboardingDone) Screen.Home else Screen.Onboarding)
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }
    val open: (Screen) -> Unit = { if (backStack.lastOrNull() != it) backStack.add(it) }
    val pageBackground = remember { NavEntryDecorator<NavKey> { entry -> AppBackground { entry.Content() } } }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), pageBackground),
        transitionSpec = { Motion.PushPage },
        popTransitionSpec = { Motion.PopPage },
        entryProvider = entryProvider {
            entry<Screen.Onboarding> {
                OnboardingScreen(
                    readiness = readiness.value,
                    onFinish = {
                        model.finishOnboarding(readiness.value)
                        backStack[0] = Screen.Home
                    },
                )
            }
            entry<Screen.Home> {
                HomeScreen(
                    settings = settings,
                    stats = runtime.value?.stats ?: return@entry,
                    readiness = readiness.value,
                    onUpdate = model::updateSettings,
                    onOpenSettings = { open(Screen.Settings) },
                    onOpenBark = { open(Screen.Bark) },
                )
            }
            entry<Screen.Settings> {
                SettingsScreen(
                    settings = settings,
                    permissionHealth = readiness.value.health(settings.persistentNotification),
                    onUpdate = model::updateSettings,
                    onResetStats = model::resetStats,
                    onOpen = open,
                    onBack = goBack,
                )
            }
            entry<Screen.Bark> {
                BarkScreen(
                    saved = settings.bark,
                    onChange = { bark -> model.updateSettings { it.copy(bark = bark) } },
                    onBack = goBack,
                )
            }
            entry<Screen.KeepAlive> {
                KeepAliveScreen(
                    readiness = readiness.value,
                    persistentNotification = settings.persistentNotification,
                    onPersistentNotificationChange = { on -> model.updateSettings { it.copy(persistentNotification = on) } },
                    onBack = goBack,
                )
            }
            entry<Screen.Forward> { key ->
                ForwardScreen(
                    kind = key.kind,
                    enabled = settings.forwards(key.kind),
                    saved = settings.push(key.kind),
                    readiness = readiness.value,
                    onEnabledChange = { on -> model.updateSettings { it.withChannel(key.kind, on) } },
                    onChange = { profile -> model.updateSettings { it.withPush(key.kind, profile) } },
                    onBack = goBack,
                )
            }
        },
    )
}
