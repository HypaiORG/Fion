package com.hypai.fion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypai.fion.android.Readiness
import com.hypai.fion.forward.AppPermission
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.Language
import com.hypai.fion.forward.SimKey
import com.hypai.fion.forward.TemplatePart
import com.hypai.fion.forward.TemplateVariable
import com.hypai.fion.store.Message
import com.hypai.fion.store.SimTag
import com.hypai.fion.store.SqliteOutbox
import com.hypai.fion.ui.design.AppTheme
import com.hypai.fion.ui.design.ConfirmDialog
import com.hypai.fion.ui.screen.forward.TemplateField
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun outboxRoundTripsOnDevice() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "instrumented_outbox.db"
        try {
            val outbox = SqliteOutbox(context, dbName)
            val msg = Message("id-1", EventKind.Sms, SimKey.subscription(1), SimTag(0, "SIM1", "Test"), "Title", "Body 123", 1_000L)
            outbox.enqueue(listOf(msg), maxSize = 10)
            assertEquals(msg, outbox.next(setOf(EventKind.Sms), emptySet()))
            outbox.remove("id-1")
            assertFalse(outbox.hasAny())
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun templateFieldInsertsVariableViaSlashPickerAndDialogBlocksBodyClicks() {
        var parts by mutableStateOf<List<TemplatePart>>(listOf(TemplatePart.Text("Hi ")))
        var dismissed by mutableStateOf(false)
        var showDialog by mutableStateOf(false)
        val allReady = Readiness(AppPermission.entries.toSet(), batteryUnrestricted = true)

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
                if (showDialog) {
                    ConfirmDialog(
                        title = "Reset stats",
                        text = "Body message",
                        confirm = "Confirm",
                        dismiss = "Cancel",
                        onConfirm = { showDialog = false },
                        onDismiss = {
                            dismissed = true
                            showDialog = false
                        },
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Body").performClick()
        compose.onNodeWithContentDescription("Body").performTextInput("/")
        compose.onNodeWithText("Sender").performClick()
        compose.waitForIdle()
        assertTrue(parts.any { it == TemplatePart.Var(TemplateVariable.SmsFrom) })

        compose.runOnUiThread { showDialog = true }
        compose.waitForIdle()
        compose.onNodeWithText("Body message").performClick()
        compose.waitForIdle()
        assertFalse(dismissed)
        assertTrue(showDialog)

        compose.onNodeWithText("Confirm").performClick()
        compose.waitForIdle()
        assertFalse(dismissed)
        assertFalse(showDialog)
    }
}
