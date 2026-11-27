package com.hypai.fion.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hypai.fion.forward.EventKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface Outbox {
    suspend fun enqueue(messages: List<Message>, maxSize: Int): Int
    suspend fun purgeExpired(cutoffMs: Long): Int
    suspend fun next(kinds: Set<EventKind>, skip: Set<String>): Message?
    suspend fun remove(id: String): Boolean
    suspend fun hasAny(kinds: Set<EventKind> = ALL_KINDS): Boolean

    companion object {
        private val ALL_KINDS = EventKind.entries.toSet()
    }
}

class SqliteOutbox(context: Context, name: String = "outbox.db") : Outbox {
    private val dbHelper = object : SQLiteOpenHelper(context.applicationContext, name, null, 5) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.enableWriteAheadLogging()
            db.rawQuery("PRAGMA synchronous=NORMAL", null).close()
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE outbox (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    id TEXT NOT NULL UNIQUE,
                    kind TEXT NOT NULL,
                    received_at INTEGER NOT NULL,
                    payload TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS outbox")
            onCreate(db)
        }
    }

    private suspend fun <T> io(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO + NonCancellable) {
        block(dbHelper.writableDatabase)
    }

    override suspend fun enqueue(messages: List<Message>, maxSize: Int): Int {
        if (messages.isEmpty()) return 0
        return io { db ->
            db.beginTransaction()
            try {
                for (m in messages) {
                    val values = ContentValues(4).apply {
                        put("id", m.id)
                        put("kind", m.kind.name)
                        put("received_at", m.receivedAtMs)
                        put("payload", STORE_JSON.encodeToString(Message.serializer(), m))
                    }
                    db.insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
                val evicted = db.delete(
                    "outbox",
                    "seq NOT IN (SELECT seq FROM outbox ORDER BY seq DESC LIMIT ?)",
                    arrayOf(maxSize.toString()),
                )
                db.setTransactionSuccessful()
                evicted
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun purgeExpired(cutoffMs: Long): Int = io { db ->
        db.delete("outbox", "received_at < ?", arrayOf(cutoffMs.toString()))
    }

    override suspend fun next(kinds: Set<EventKind>, skip: Set<String>): Message? =
        if (kinds.isEmpty()) null else io { db -> db.oldestReadable(kinds, skip) }

    private tailrec fun SQLiteDatabase.oldestReadable(kinds: Set<EventKind>, skip: Set<String>): Message? {
        val kindMarks = kinds.joinToString(",") { "?" }
        val skipClause = if (skip.isEmpty()) "" else " AND id NOT IN (${skip.joinToString(",") { "?" }})"
        val (id, payload) = rawQuery(
            "SELECT id, payload FROM outbox WHERE kind IN ($kindMarks)$skipClause ORDER BY seq ASC LIMIT 1",
            (kinds.map { it.name } + skip).toTypedArray(),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null } ?: return null
        val message = try {
            STORE_JSON.decodeFromString(Message.serializer(), payload)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (message != null) return message
        delete("outbox", "id = ?", arrayOf(id))
        return oldestReadable(kinds, skip)
    }

    override suspend fun remove(id: String): Boolean =
        io { db -> db.delete("outbox", "id = ?", arrayOf(id)) > 0 }

    override suspend fun hasAny(kinds: Set<EventKind>): Boolean {
        if (kinds.isEmpty()) return false
        return io { db ->
            val placeholders = kinds.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT 1 FROM outbox WHERE kind IN ($placeholders) LIMIT 1",
                kinds.map { it.name }.toTypedArray(),
            ).use { it.moveToFirst() }
        }
    }
}
