package com.hypai.fion.android

import android.Manifest
import android.app.Application
import android.app.job.JobScheduler
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import androidx.test.core.app.ApplicationProvider
import com.hypai.fion.forward.SimKey
import com.hypai.fion.forward.TemplateVariable
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private class FakeCallLogProvider : ContentProvider() {
    data class Row(val id: Long, val type: Int, val number: String, val dateMs: Long, val presentation: Int = CallLog.Calls.PRESENTATION_ALLOWED)

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (unavailable) return null
        val cols = projection ?: arrayOf(CallLog.Calls._ID)
        val cursor = MatrixCursor(cols)
        val filtered = when {
            selection == "${CallLog.Calls._ID} > ?" -> {
                val afterId = selectionArgs?.firstOrNull()?.toLong() ?: 0L
                rows.filter { it.id > afterId }.sortedBy { it.id }
            }
            sortOrder?.contains("DESC") == true -> rows.sortedByDescending { it.id }.take(1)
            else -> rows.filter { it.type == CallLog.Calls.MISSED_TYPE }
        }
        for (row in filtered) {
            cursor.addRow(cols.map { col ->
                when (col) {
                    CallLog.Calls._ID -> row.id
                    CallLog.Calls.TYPE -> row.type
                    CallLog.Calls.NUMBER -> row.number
                    CallLog.Calls.DATE -> row.dateMs
                    CallLog.Calls.NUMBER_PRESENTATION -> row.presentation
                    else -> null
                }
            })
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        var rows: List<Row> = emptyList()
        var unavailable = false
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidComponentsRobolectricTest {
    private fun app(): Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `sim directory handles null telephony services safely on wifi-only tablets`() = runTest {
        val noTelephony = object : ContextWrapper(app()) {
            override fun getSystemService(name: String): Any? = when (name) {
                Context.TELEPHONY_SUBSCRIPTION_SERVICE, Context.TELEPHONY_SERVICE -> null
                else -> super.getSystemService(name)
            }
        }
        shadowOf(app()).grantPermissions(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
        val sims = SimDirectory(noTelephony)

        val sim = sims.sim(subscriptionId = null, slotIndex = null, includeNumber = true)
        assertEquals(SimKey.Unknown, sim.key)
        assertEquals("", sim.number)

        val resolver = sims.openCallResolver(includeNumber = true)
        assertEquals(SimKey.subscription(1), resolver("com.test/.Svc", "1", null).key)
        assertEquals("", resolver("com.test/.Svc", "1", null).number)
        assertEquals(SimKey.Unknown, resolver("com.test/.Svc", "account", null).key)
    }

    @Test
    fun `system call log reads missed calls, skips non-missed, and resets downward when cleared`() = runTest {
        Robolectric.buildContentProvider(FakeCallLogProvider::class.java).create(CallLog.AUTHORITY)
        shadowOf(app()).grantPermissions(Manifest.permission.READ_CALL_LOG)
        FakeCallLogProvider.rows = listOf(
            FakeCallLogProvider.Row(10, CallLog.Calls.OUTGOING_TYPE, "10010", 1_000L),
            FakeCallLogProvider.Row(11, CallLog.Calls.MISSED_TYPE, "+8613800138000", 2_000L),
            FakeCallLogProvider.Row(12, CallLog.Calls.MISSED_TYPE, "+8613800138000", 3_000L),
        )
        val reader = SystemCallLog(app(), SimDirectory(app()), now = { 5_000L }, zone = { ZoneOffset.UTC })

        val initial = reader.read(afterId = null, usedVariables = emptySet())!!
        assertEquals(12L, initial.maxId)
        assertTrue(initial.missed.isEmpty())

        val incremental = reader.read(afterId = 10L, usedVariables = setOf(TemplateVariable.CallCount))!!
        assertEquals(12L, incremental.maxId)
        assertEquals(2, incremental.missed.size)
        assertEquals(1, incremental.missed[0].countToday)
        assertEquals(2, incremental.missed[1].countToday)

        FakeCallLogProvider.rows = emptyList()
        val afterClear = reader.read(afterId = 12L, usedVariables = emptySet())!!
        assertEquals(0L, afterClear.maxId)
        assertTrue(afterClear.missed.isEmpty())
    }

    @Test
    fun `system call log keeps the watermark when the provider returns no cursor`() = runTest {
        Robolectric.buildContentProvider(FakeCallLogProvider::class.java).create(CallLog.AUTHORITY)
        shadowOf(app()).grantPermissions(Manifest.permission.READ_CALL_LOG)
        FakeCallLogProvider.rows = listOf(FakeCallLogProvider.Row(12, CallLog.Calls.MISSED_TYPE, "10086", 1_000L))
        FakeCallLogProvider.unavailable = true
        val reader = SystemCallLog(app(), SimDirectory(app()), now = { 5_000L }, zone = { ZoneOffset.UTC })
        try {
            assertNull(reader.read(afterId = 12L, usedVariables = emptySet()))
            assertNull(reader.read(afterId = null, usedVariables = emptySet()))
        } finally {
            FakeCallLogProvider.unavailable = false
        }
    }

    @Test
    fun `forward jobs rotate call log watch slots and cancel cleanly`() {
        val context = app()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        shadowOf(app()).grantPermissions(Manifest.permission.READ_CALL_LOG)

        ForwardJobs.ensureCallLogWatch(context, runningJobId = null)
        assertNotNull(scheduler.getPendingJob(1))

        ForwardJobs.ensureCallLogWatch(context, runningJobId = 1)
        assertNotNull(scheduler.getPendingJob(4))

        ForwardJobs.syncCallLogAsync(context)
        assertNotNull(scheduler.getPendingJob(3))

        ForwardJobs.cancelCallLogWatch(context)
        assertNull(scheduler.getPendingJob(1))
        assertNull(scheduler.getPendingJob(3))
        assertNull(scheduler.getPendingJob(4))

        ForwardJobs.scheduleRetry(context, replace = false)
        assertNotNull(scheduler.getPendingJob(2))
        ForwardJobs.cancelRetry(context)
        assertNull(scheduler.getPendingJob(2))
    }

    @Test
    fun `keep alive service reconciles running state and call log observer flag`() {
        val context = app()
        shadowOf(app()).grantPermissions(Manifest.permission.READ_CALL_LOG)

        val controller = Robolectric.buildService(
            KeepAliveService::class.java,
            Intent(context, KeepAliveService::class.java),
        ).create()
        KeepAliveService.reconcile(context, shouldRun = true, watchCallLog = true)
        val resolver = shadowOf(context.contentResolver)
        controller.startCommand(0, 1)
        assertEquals(1, resolver.getContentObservers(CallLog.Calls.CONTENT_URI).size)

        KeepAliveService.reconcile(context, shouldRun = false, watchCallLog = false)
        controller.destroy()
        assertTrue(resolver.getContentObservers(CallLog.Calls.CONTENT_URI).isEmpty())
    }

    @Test
    fun `restart receiver and incoming sms receiver ignore unrelated actions safely`() {
        val context = app()
        IncomingSmsReceiver().onReceive(context, Intent("com.example.UNRELATED"))
        IncomingSmsReceiver().onReceive(context, Intent())
        RestartReceiver().onReceive(context, Intent("com.example.UNRELATED"))
        RestartReceiver().onReceive(context, Intent())
        assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
        assertNull(shadowOf(app()).nextStartedService)
    }
}
