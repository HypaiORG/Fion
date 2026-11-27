package com.hypai.fion.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import com.hypai.fion.AppContainer
import com.hypai.fion.R
import com.hypai.fion.android.Readiness
import com.hypai.fion.android.applyAppLanguage
import com.hypai.fion.appContainer
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.liveChannels
import com.hypai.fion.store.Settings
import com.hypai.fion.store.Stats
import com.hypai.fion.ui.design.AppBackground
import com.hypai.fion.ui.design.AppTheme
import com.hypai.fion.ui.design.Motion
import com.hypai.fion.ui.design.Pane
import com.hypai.fion.ui.design.PaneRow
import com.hypai.fion.ui.design.WindowPanes
import com.hypai.fion.ui.design.currentWindowPanes
import com.hypai.fion.ui.screen.BarkScreen
import com.hypai.fion.ui.screen.HomeScreen
import com.hypai.fion.ui.screen.KeepAliveScreen
import com.hypai.fion.ui.screen.LocalRefreshReadiness
import com.hypai.fion.ui.screen.SettingsScreen
import com.hypai.fion.ui.screen.forward.ForwardScreen
import com.hypai.fion.ui.screen.onboarding.OnboardingScreen
import com.hypai.fion.ui.screen.privacy.PrivacyScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Onboarding : Screen
    @Serializable data object Home : Screen
    @Serializable data object Settings : Screen

    @Serializable
    sealed interface Detail : Screen

    @Serializable data object Bark : Detail
    @Serializable data object KeepAlive : Detail
    @Serializable data object Privacy : Detail
    @Serializable data class Forward(val kind: EventKind) : Detail
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
        viewModelScope.launch { app.stateStore.resetStats() }
    }

    fun finishOnboarding() {
        viewModelScope.launch { app.coordinator.completeOnboarding() }
    }

    fun refreshReadiness() {
        app.coordinator.refreshReadiness()
    }

    fun onForeground() {
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
        setContent { AppTheme { LanguageCrossfade { AppRoot() } } }
    }
}

@Composable
fun currentLanguage(): Language = Language.valueOf(stringResource(R.string.language))

val LocalSwitchLanguage = staticCompositionLocalOf<(Language) -> Unit> { error("LanguageCrossfade is missing") }

private const val LANGUAGE_SWITCH_TIMEOUT_MS = 1_000L

@Composable
private fun LanguageCrossfade(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val language by rememberUpdatedState(currentLanguage())
    var capturing by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    var activeSwitch by remember { mutableStateOf<Job?>(null) }
    val fade = remember { Animatable(1f) }
    val switch: (Language) -> Unit = remember(context, scope, layer) {
        { target ->
            if (target != language) {
                activeSwitch?.cancel()
                activeSwitch = scope.launch {
                    try {
                        capturing = true
                        withFrameNanos {}
                        withFrameNanos {}
                        val snapshot = if (layer.size.width > 0 && layer.size.height > 0) layer.toImageBitmap() else null
                        capturing = false
                        if (snapshot != null) {
                            frozen = snapshot
                            fade.snapTo(1f)
                        }
                        context.applyAppLanguage(target)
                        val switched = withTimeoutOrNull(LANGUAGE_SWITCH_TIMEOUT_MS) {
                            snapshotFlow { language }.first { it == target }
                        }
                        if (snapshot != null && switched != null) {
                            fade.animateTo(0f, tween(300, easing = Motion.Standard))
                        }
                    } finally {
                        capturing = false
                        frozen = null
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalSwitchLanguage provides switch) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (capturing) {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    } else {
                        drawContent()
                    }
                    frozen?.let { drawImage(it, alpha = fade.value) }
                },
        ) { content() }
    }
}

private const val SCREEN_META = "screen"

private fun NavEntry<NavKey>.screen(): Screen = metadata[SCREEN_META] as Screen

private val InstantTransition = EnterTransition.None togetherWith ExitTransition.None

private fun NavBackStack<NavKey>.popOne() {
    if (size > 1) removeAt(lastIndex)
}

private fun NavBackStack<NavKey>.popToRoot() {
    while (size > 1) removeAt(lastIndex)
}

private fun NavBackStack<NavKey>.open(screen: Screen, multi: Boolean) {
    if (screen == last()) return
    if (multi && screen is Screen.Detail) {
        popToRoot()
        add(Screen.Settings)
    }
    add(screen)
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val model: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(context.appContainer) } })
    val uiState = model.state.collectAsStateWithLifecycle()
    val onboardingDone = remember { derivedStateOf { uiState.value?.onboardingDone } }.value
    if (onboardingDone == null) {
        AppBackground {}
        return
    }
    LifecycleResumeEffect(Unit) {
        model.onForeground()
        onPauseOrDispose {}
    }

    val backStack = rememberNavBackStack(if (onboardingDone) Screen.Home else Screen.Onboarding)
    val panes = currentWindowPanes()
    val multi by rememberUpdatedState(panes.multi)
    val horizontalFold by rememberUpdatedState(panes.horizontalFold)
    val stateHolder = rememberSaveableStateHolder()
    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>(stateHolder)
    val decorators = remember(saveableDecorator) { listOf(saveableDecorator) }
    val render: @Composable (Screen) -> Unit = { screen -> Destination(screen, uiState, model, backStack, multi, horizontalFold) }
    val paneStrategy = remember(panes, stateHolder) {
        PaneSceneStrategy(panes, stateHolder, render)
    }
    val sceneStrategies = remember(paneStrategy) { listOf(paneStrategy, SinglePageSceneStrategy) }

    CompositionLocalProvider(LocalRefreshReadiness provides model::refreshReadiness) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (multi) backStack.popToRoot() else backStack.popOne() },
            entryDecorators = decorators,
            sceneStrategies = sceneStrategies,
            transitionSpec = {
                if (initialState is SinglePageScene && targetState is SinglePageScene) Motion.PushPage else InstantTransition
            },
            popTransitionSpec = {
                if (initialState is SinglePageScene && targetState is SinglePageScene) Motion.PopPage else InstantTransition
            },
            entryProvider = { key ->
                val screen = key as Screen
                NavEntry(key, contentKey = key.toString(), metadata = mapOf(SCREEN_META to screen)) { render(screen) }
            },
        )
    }
}

private data class SinglePageScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
) : Scene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val content: @Composable () -> Unit = { AppBackground { entry.Content() } }
}

private object SinglePageSceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val last = entries.lastOrNull() ?: return null
        return SinglePageScene(last, entries.dropLast(1))
    }
}

private class PaneScene(
    private val panes: WindowPanes,
    override val entries: List<NavEntry<NavKey>>,
    private val stateHolder: SaveableStateHolder,
    private val render: @Composable (Screen) -> Unit,
) : Scene<NavKey> {
    private val home = entries.first()
    private val settings = entries.lastOrNull { it.screen() == Screen.Settings }
    private val detail = entries.last().takeIf { it.screen() is Screen.Detail }

    override val key: Any = "pane"
    override val previousEntries: List<NavEntry<NavKey>> = if (detail != null) listOf(home) else emptyList()
    override val content: @Composable () -> Unit = {
        AppBackground {
            PaneRow(panes) {
                Pane { home.Content() }
                val active = detail?.screen() ?: Screen.Settings
                Pane {
                    AnimatedContent(
                        targetState = active,
                        transitionSpec = {
                            when {
                                initialState is Screen.Detail && targetState is Screen.Detail -> Motion.Swap
                                targetState is Screen.Detail -> Motion.PushPage
                                else -> Motion.PopPage
                            }
                        },
                        label = "pane",
                    ) { screen ->
                        when {
                            screen is Screen.Detail -> if (detail?.screen() == screen) detail.Content() else render(screen)
                            settings != null -> settings.Content()
                            screen == active -> stateHolder.SaveableStateProvider(Screen.Settings.toString()) { render(screen) }
                            else -> render(screen)
                        }
                    }
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is PaneScene && panes == other.panes && entries == other.entries

    override fun hashCode(): Int = 31 * panes.hashCode() + entries.hashCode()
}

private class PaneSceneStrategy(
    private val panes: WindowPanes,
    private val stateHolder: SaveableStateHolder,
    private val render: @Composable (Screen) -> Unit,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? =
        if (panes.multi && entries.firstOrNull()?.screen() == Screen.Home) PaneScene(panes, entries, stateHolder, render) else null
}

@Composable
private fun Destination(
    screen: Screen,
    uiState: State<UiState?>,
    model: MainViewModel,
    backStack: NavBackStack<NavKey>,
    multi: Boolean,
    horizontalFold: Rect?,
) {
    val current = uiState.value ?: return
    val onOpen: (Screen) -> Unit = { backStack.open(it, multi) }
    val onBack: (() -> Unit)? = when {
        screen is Screen.Detail -> ({ if (multi) backStack.popToRoot() else backStack.popOne() })
        screen == Screen.Settings && !multi -> ({ backStack.popOne() })
        else -> null
    }
    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            readiness = current.readiness,
            onFinish = {
                model.finishOnboarding()
                backStack[0] = Screen.Home
            },
        )
        Screen.Home -> HomeScreen(
            settings = current.settings,
            stats = current.stats,
            readiness = current.readiness,
            horizontalFold = horizontalFold,
            onUpdate = model::updateSettings,
            onOpenSettings = if (multi) null else ({ onOpen(Screen.Settings) }),
            onOpenBark = { onOpen(Screen.Bark) },
        )
        Screen.Settings -> SettingsScreen(
            settings = current.settings,
            readiness = current.readiness,
            onBack = onBack,
            onUpdate = model::updateSettings,
            onResetStats = model::resetStats,
            onOpen = onOpen,
        )
        Screen.Bark -> BarkScreen(
            saved = current.settings.bark,
            onChange = { bark -> model.updateSettings { it.copy(bark = bark) } },
            onBack = onBack,
        )
        Screen.KeepAlive -> KeepAliveScreen(
            readiness = current.readiness,
            persistentNotification = current.settings.persistentNotification,
            onPersistentNotificationChange = { on -> model.updateSettings { it.copy(persistentNotification = on) } },
            onBack = onBack,
        )
        Screen.Privacy -> PrivacyScreen(
            settings = current.settings,
            readiness = current.readiness,
            onBack = onBack,
        )
        is Screen.Forward -> ForwardScreen(
            kind = screen.kind,
            enabled = screen.kind in current.settings.liveChannels(current.readiness),
            saved = current.settings.push(screen.kind),
            readiness = current.readiness,
            onEnabledChange = { on -> model.updateSettings { it.withChannel(screen.kind, on) } },
            onChange = { profile -> model.updateSettings { it.withPush(screen.kind, profile) } },
            onBack = onBack,
        )
    }
}
