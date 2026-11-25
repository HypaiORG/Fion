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
    suspend fun enqueue(messages: List<Message>, maxSize: Int)
    suspend fun purgeExpired(cutoffMs: Long): Int
    suspend fun list(kinds: Set<EventKind>): List<Message>
    suspend fun remove(id: String)
    suspend fun hasAny(kinds: Set<EventKind> = ALL_KINDS): Boolean

    companion object {
        private val ALL_KINDS = EventKind.entries.toSet()
    }
}

class SqliteOutbox(context: Context, name: String = "outbox.db") : Outbox {
    private val dbHelper = object : SQLiteOpenHelper(context.applicationContext, name, null, 3) {
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

    override suspend fun enqueue(messages: List<Message>, maxSize: Int) {
        if (messages.isEmpty()) return
        io { db ->
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
                db.execSQL(
                    "DELETE FROM outbox WHERE seq NOT IN (SELECT seq FROM outbox ORDER BY seq DESC LIMIT ?)",
                    arrayOf(maxSize),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun purgeExpired(cutoffMs: Long): Int = io { db ->
        db.delete("outbox", "received_at < ?", arrayOf(cutoffMs.toString()))
    }

    override suspend fun list(kinds: Set<EventKind>): List<Message> {
        if (kinds.isEmpty()) return emptyList()
        return io { db ->
            val placeholders = kinds.joinToString(",") { "?" }
            val result = mutableListOf<Message>()
            db.rawQuery(
                "SELECT payload FROM outbox WHERE kind IN ($placeholders) ORDER BY seq ASC",
                kinds.map { it.name }.toTypedArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result += STORE_JSON.decodeFromString(Message.serializer(), cursor.getString(0))
                }
            }
            result
        }
    }

    override suspend fun remove(id: String) {
        io { db -> db.delete("outbox", "id = ?", arrayOf(id)) }
    }

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

    fun close() = dbHelper.close()
}
