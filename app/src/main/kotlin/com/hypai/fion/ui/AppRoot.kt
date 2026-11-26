package com.hypai.fion.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import com.hypai.fion.AppContainer
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.appContainer
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.store.Settings
import com.hypai.fion.store.Stats
import com.hypai.fion.ui.design.AppBackground
import com.hypai.fion.ui.design.AppTheme
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.screen.BarkScreen
import com.hypai.fion.ui.screen.HomeScreen
import com.hypai.fion.ui.screen.KeepAliveScreen
import com.hypai.fion.ui.screen.LocalRefreshReadiness
import com.hypai.fion.ui.screen.SettingsScreen
import com.hypai.fion.ui.screen.forward.ForwardScreen
import com.hypai.fion.ui.screen.onboarding.OnboardingScreen
import com.hypai.fion.ui.screen.privacy.PrivacyScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    @Serializable data object Privacy : Screen
    @Serializable data class Forward(val kind: EventKind) : Screen
}

data class UiState(
    val settings: Settings,
    val onboardingDone: Boolean,
    val stats: Stats,
    val readiness: Readiness,
)

class MainViewModel(private val app: AppContainer) : ViewModel() {
    val state = combine(app.settingsStore.data, app.stateStore.data, app.coordinator.readiness) { settings, runtime, readiness ->
        UiState(settings = settings, onboardingDone = runtime.onboardingDone, stats = runtime.stats, readiness = readiness)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { app.coordinator.updateSettings(transform) }
    }

    fun resetStats() {
        viewModelScope.launch { app.coordinator.resetStats() }
    }

    fun finishOnboarding() {
        viewModelScope.launch { app.coordinator.completeOnboarding() }
    }

    fun refreshReadiness() {
        app.coordinator.refreshReadiness()
    }

    fun onForeground() {
        app.coordinator.refreshReadiness()
        viewModelScope.launch { app.coordinator.onForeground() }
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

@Composable
fun currentLanguage(): Language = Language.valueOf(stringResource(R.string.language))

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val model: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(context.appContainer) } })
    val ui = model.state.collectAsStateWithLifecycle().value
    if (ui == null) {
        AppBackground {}
        return
    }
    LifecycleResumeEffect(Unit) {
        model.onForeground()
        onPauseOrDispose {}
    }

    val backStack = rememberNavBackStack(if (ui.onboardingDone) Screen.Home else Screen.Onboarding)
    val goBack = remember(backStack) { { if (backStack.size > 1) backStack.removeLastOrNull() } }
    val open = remember(backStack) { { screen: Screen -> if (backStack.lastOrNull() != screen) backStack.add(screen) } }
    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val pageBackground = remember { NavEntryDecorator<NavKey> { entry -> AppBackground { entry.Content() } } }
    val decorators = remember(saveableDecorator, pageBackground) { listOf(saveableDecorator, pageBackground) }
    val provider = remember(model, backStack, goBack, open) {
        entryProvider<NavKey> {
            entry<Screen.Onboarding> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                OnboardingScreen(
                    readiness = current.readiness,
                    onFinish = {
                        model.finishOnboarding()
                        backStack[0] = Screen.Home
                    },
                )
            }
            entry<Screen.Home> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                HomeScreen(
                    settings = current.settings,
                    stats = current.stats,
                    readiness = current.readiness,
                    onUpdate = model::updateSettings,
                    onOpenSettings = { open(Screen.Settings) },
                    onOpenBark = { open(Screen.Bark) },
                )
            }
            entry<Screen.Settings> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                SettingsScreen(
                    settings = current.settings,
                    readiness = current.readiness,
                    onUpdate = model::updateSettings,
                    onResetStats = model::resetStats,
                    onOpen = open,
                    onBack = goBack,
                )
            }
            entry<Screen.Bark> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                BarkScreen(
                    saved = current.settings.bark,
                    onChange = { bark -> model.updateSettings { it.copy(bark = bark) } },
                    onBack = goBack,
                )
            }
            entry<Screen.KeepAlive> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                KeepAliveScreen(
                    readiness = current.readiness,
                    persistentNotification = current.settings.persistentNotification,
                    onPersistentNotificationChange = { on -> model.updateSettings { it.copy(persistentNotification = on) } },
                    onBack = goBack,
                )
            }
            entry<Screen.Privacy> {
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                PrivacyScreen(
                    settings = current.settings,
                    readiness = current.readiness,
                    onBack = goBack,
                )
            }
            entry<Screen.Forward> { key ->
                val current = model.state.collectAsStateWithLifecycle().value ?: return@entry
                ForwardScreen(
                    kind = key.kind,
                    enabled = current.settings.forwards(key.kind),
                    saved = current.settings.push(key.kind),
                    readiness = current.readiness,
                    onEnabledChange = { on -> model.updateSettings { it.withChannel(key.kind, on) } },
                    onChange = { profile -> model.updateSettings { it.withPush(key.kind, profile) } },
                    onBack = goBack,
                )
            }
        }
    }

    CompositionLocalProvider(LocalRefreshReadiness provides model::refreshReadiness) {
        NavDisplay(
            backStack = backStack,
            onBack = goBack,
            entryDecorators = decorators,
            transitionSpec = { Motion.PushPage },
            popTransitionSpec = { Motion.PopPage },
            entryProvider = provider,
        )
    }
}
