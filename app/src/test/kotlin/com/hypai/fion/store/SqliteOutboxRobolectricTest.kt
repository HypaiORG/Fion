package com.hypai.fion.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.hypai.fion.forward.EventKind
import com.hypai.fion.forward.SimKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteOutboxRobolectricTest {
    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun msg(id: String, kind: EventKind = EventKind.Sms, body: String = "OTP 839201", receivedAt: Long = 1_000L) =
        Message(id, kind, SimKey.subscription(1), SimTag(0, "Main", "CMCC"), "Title", body, receivedAt)

    @Test
    fun `preserves FIFO, deduplicates, caps maxSize, and purges expired`() = runTest {
        val outbox = SqliteOutbox(context(), "outbox_fifo.db")

        assertFalse(outbox.hasAny())
        outbox.enqueue(listOf(msg("m1", body = "SECRET-OTP-1", receivedAt = 100L), msg("m1", body = "DUP", receivedAt = 100L)), maxSize = 3)
        outbox.enqueue(listOf(msg("m2", EventKind.MissedCall, body = "call", receivedAt = 200L)), maxSize = 3)
        outbox.enqueue(listOf(msg("m3", body = "msg3", receivedAt = 300L), msg("m4", body = "msg4", receivedAt = 400L)), maxSize = 3)

        assertTrue(outbox.hasAny())
        val listed = outbox.queued(EventKind.entries.toSet())
        assertEquals(listOf("m2", "m3", "m4"), listed.map { it.id })

        assertEquals(1, outbox.purgeExpired(250L))
        assertEquals(listOf("m3", "m4"), outbox.queued(EventKind.entries.toSet()).map { it.id })
        outbox.remove("m3")
        outbox.remove("m4")
        assertFalse(outbox.hasAny())
        assertTrue(outbox.queued(EventKind.entries.toSet()).isEmpty())
    }

    @Test
    fun `enqueue reports how many queued messages the cap evicted`() = runTest {
        val outbox = SqliteOutbox(context(), "outbox_cap.db")

        assertEquals(0, outbox.enqueue(listOf(msg("a"), msg("b")), maxSize = 2))
        assertEquals(1, outbox.enqueue(listOf(msg("c")), maxSize = 2))
        assertEquals(0, outbox.enqueue(listOf(msg("c")), maxSize = 2))
        assertEquals(listOf("b", "c"), outbox.queued(EventKind.entries.toSet()).map { it.id })
    }

    @Test
    fun `drops corrupt rows without losing healthy queued messages`() = runTest {
        val dbName = "outbox_corrupt.db"
        val outbox = SqliteOutbox(context(), dbName)
        outbox.enqueue(listOf(msg("good-1", body = "hello")), maxSize = 10)

        SQLiteDatabase.openDatabase(context().getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.insert("outbox", null, ContentValues().apply {
                put("id", "bad-row")
                put("kind", EventKind.Sms.name)
                put("received_at", 1_000L)
                put("payload", "{not json")
            })
        }

        val recovered = outbox.queued(setOf(EventKind.Sms))
        assertEquals(listOf("good-1"), recovered.map { it.id })

        SQLiteDatabase.openDatabase(context().getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT id FROM outbox", null).use { c ->
                val ids = mutableListOf<String>()
                while (c.moveToNext()) ids += c.getString(0)
                assertEquals(listOf("good-1"), ids)
            }
        }
    }

    private suspend fun SqliteOutbox.queued(kinds: Set<EventKind>): List<Message> {
        val found = mutableListOf<Message>()
        while (true) found += next(kinds, found.mapTo(HashSet()) { it.id }) ?: return found
    }
}
