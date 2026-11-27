package com.hypai.fion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.hypai.fion.android.Readiness
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.store.Settings
import com.hypai.fion.store.Stats
import com.hypai.fion.ui.design.AppSheet
import com.hypai.fion.ui.design.AppTheme
import com.hypai.fion.ui.design.ConfirmDialog
import com.hypai.fion.ui.design.Pane
import com.hypai.fion.ui.design.RadioOption
import com.hypai.fion.ui.design.SheetButton
import com.hypai.fion.ui.screen.HomeScreen
import com.hypai.fion.ui.screen.forward.TemplateField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiBehaviorRobolectricTest {
    @get:Rule
    val compose = createComposeRule()

    private val allReady = Readiness(AppPermission.entries.toSet(), batteryUnrestricted = true)

    @Test
    fun `home screen renders intrinsic metric cards without crashing`() {
        compose.setContent {
            AppTheme {
                HomeScreen(
                    settings = Settings(),
                    stats = Stats(sms = 12, missedCalls = 3, latencyTotalMs = 1500, latencySamples = 15),
                    readiness = allReady,
                    horizontalFold = null,
                    onUpdate = {},
                    onOpenSettings = {},
                    onOpenBark = {},
                )
            }
        }
        val breakdown = compose.onAllNodes(hasText("Includes 12 SMS and 3 missed calls") or hasContentDescription("Includes 12 SMS and 3 missed calls"), useUnmergedTree = true)
        assertTrue(breakdown.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `app sheet supports clicks, gradual drag snap-back, fling dismiss, and body nested-scroll dismiss`() {
        var visible by mutableStateOf(true)
        var picked by mutableStateOf("Item 1")
        var confirmed = ""
        compose.setContent {
            AppTheme {
                if (visible) {
                    AppSheet(
                        title = "Pick Sound",
                        subtitle = "Subtitle",
                        onDismiss = { visible = false },
                        actions = { close ->
                            SheetButton("Cancel", filled = false) { close {} }
                            SheetButton("OK", filled = true) { close { confirmed = picked } }
                        },
                    ) {
                        (1..20).forEach { idx ->
                            val label = "Item $idx"
                            RadioOption(label, selected = picked == label) { picked = label }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Pick Sound").performClick()
        compose.waitForIdle()
        assertTrue(visible)

        compose.onNodeWithText("Item 2").performClick()
        compose.waitForIdle()
        assertTrue(visible)
        assertEquals("Item 2", picked)

        val initialTop = compose.onNodeWithText("Pick Sound").fetchSemanticsNode().boundsInWindow.top
        compose.onNodeWithText("Pick Sound").performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f), delayMillis = 150)
            moveBy(Offset(0f, 25f), delayMillis = 150)
            advanceEventTime(200)
        }
        compose.waitForIdle()
        val draggedTop = compose.onNodeWithText("Pick Sound").fetchSemanticsNode().boundsInWindow.top
        assertTrue("Sheet should follow finger down gradually (initial=$initialTop, dragged=$draggedTop)", draggedTop > initialTop + 20f)

        compose.onNodeWithText("Pick Sound").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue("Slow drag under dismiss distance should snap back", visible)
        val settledTop = compose.onNodeWithText("Pick Sound").fetchSemanticsNode().boundsInWindow.top
        assertEquals(initialTop, settledTop, 2f)

        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertFalse(visible)
        assertEquals("Item 2", confirmed)

        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("Pick Sound").performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f), delayMillis = 150)
            moveBy(Offset(0f, 70f), delayMillis = 150)
            advanceEventTime(200)
            up()
        }
        compose.waitForIdle()
        assertFalse("Slow drag past dismiss distance should dismiss", visible)

        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("Pick Sound").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 60f, durationMillis = 30)
        }
        compose.waitForIdle()
        assertFalse("Short fast fling on sheet header should dismiss", visible)

        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("Item 1").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 260f, durationMillis = 250)
        }
        compose.waitForIdle()
        assertFalse("Dragging down on scrollable list at top should dismiss", visible)
    }

    @Test
    fun `pane overlay sheet supports clicks and downward drag dismiss`() {
        var visible by mutableStateOf(true)
        var picked by mutableStateOf("A")
        var confirmed = ""
        compose.setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    Pane {
                        Box(Modifier.fillMaxSize())
                        if (visible) {
                            AppSheet(
                                title = "Pane Sheet",
                                subtitle = "Subtitle",
                                onDismiss = { visible = false },
                                actions = { close ->
                                    SheetButton("OK", filled = true) { close { confirmed = picked } }
                                },
                            ) {
                                RadioOption("Option A", selected = picked == "A") { picked = "A" }
                                RadioOption("Option B", selected = picked == "B") { picked = "B" }
                            }
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Option B").performClick()
        compose.waitForIdle()
        assertEquals("B", picked)

        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertFalse(visible)
        assertEquals("B", confirmed)

        compose.runOnUiThread { visible = true }
        compose.waitForIdle()
        compose.onNodeWithText("Pane Sheet").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 260f, durationMillis = 200)
        }
        compose.waitForIdle()
        assertFalse(visible)
    }

    @Test
    fun `sheet stays composed until its slide-out finishes after the scrim fades`() {
        var inPane by mutableStateOf(false)
        var visible by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                Box(Modifier.fillMaxSize()) {
                    if (inPane) {
                        Pane {
                            Box(Modifier.fillMaxSize())
                            if (visible) ExitSheet { visible = false }
                        }
                    } else if (visible) {
                        ExitSheet { visible = false }
                    }
                }
            }
        }
        for (pane in listOf(true, false)) {
            compose.runOnUiThread {
                inPane = pane
                visible = true
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val startTop = sheetTop()
            compose.onNodeWithText("Close").performClick()
            compose.mainClock.advanceTimeBy(110)
            val midTop = sheetTop()
            assertTrue(
                "inPane=$pane sheet vanished instead of sliding out (start=$startTop, mid=$midTop)",
                midTop != null && startTop != null && midTop > startTop + 10f,
            )
            compose.mainClock.advanceTimeBy(400)
            assertFalse(visible)
            assertEquals(null, sheetTop())
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun `confirm dialog blocks body clicks and dispatches confirm without onDismiss`() {
        var visible by mutableStateOf(true)
        var dismissed = false
        var confirmed = false
        compose.setContent {
            AppTheme {
                if (visible) {
                    ConfirmDialog(
                        title = "Reset",
                        text = "Confirm reset body",
                        confirm = "Confirm",
                        dismiss = "Cancel",
                        onConfirm = {
                            confirmed = true
                            visible = false
                        },
                        onDismiss = {
                            dismissed = true
                            visible = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithText("Confirm reset body").performClick()
        compose.waitForIdle()
        assertTrue(visible)
        assertFalse(dismissed)

        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()
        assertFalse(visible)
        assertTrue(confirmed)
        assertFalse(dismissed)
    }

    @Test
    fun `template field picker uses 144dp width and inserts selected variable`() {
        var parts by mutableStateOf<List<TemplatePart>>(listOf(TemplatePart.Text("Hi ")))
        compose.setContent {
            AppTheme {
                TemplateField(
                    value = parts,
                    onValueChange = { parts = it },
                    label = "Body",
                    kind = EventKind.Sms,
                    language = Language.En,
                    readiness = allReady,
                    onMissingPermission = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Body").performClick()
        compose.onNodeWithContentDescription("Body").performTextInput("/")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose variable").assertWidthIsEqualTo(144.dp)
        compose.onNodeWithText("Sender").performClick()
        compose.waitForIdle()
        assertTrue(parts.any { it == TemplatePart.Var(TemplateVariable.SmsFrom) })
    }

    private fun sheetTop(): Float? =
        compose.onAllNodes(hasText("Exit Sheet"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInWindow?.top

    @androidx.compose.runtime.Composable
    private fun ExitSheet(onDismiss: () -> Unit) {
        AppSheet(
            title = "Exit Sheet",
            subtitle = "Subtitle",
            onDismiss = onDismiss,
            actions = { close -> SheetButton("Close", filled = true) { close {} } },
        ) {}
    }
}
