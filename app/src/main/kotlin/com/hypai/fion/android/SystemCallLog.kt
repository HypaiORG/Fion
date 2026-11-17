package com.hypai.fion.android

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.hypai.fion.forward.CallLogReader
import com.hypai.fion.forward.CallLogSnapshot
import com.hypai.fion.forward.ForwardEvent
import com.hypai.fion.forward.LoggedMissedCall
import com.hypai.fion.forward.SimIdentity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RawMissedCall(
    val id: Long,
    val number: String,
    val dateMs: Long,
    val accountComponent: String?,
    val accountId: String?,
)

class SystemCallLog(
    private val context: Context,
    private val sims: SimDirectory,
    private val now: () -> Long,
) : CallLogReader {
    override suspend fun read(afterId: Long?): CallLogSnapshot? = withContext(Dispatchers.IO) {
        if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return@withContext null
        try {
            if (afterId == null) return@withContext CallLogSnapshot(latestId(), emptyList())
            val receivedAt = now()
            val rawMissed = mutableListOf<RawMissedCall>()
            var maxId: Long = afterId
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
                    CallLog.Calls.PHONE_ACCOUNT_ID,
                ),
                "${CallLog.Calls._ID} > ?",
                arrayOf(afterId.toString()),
                "${CallLog.Calls._ID} ASC",
            )?.use { rows ->
                while (rows.moveToNext()) {
                    maxId = rows.getLong(0)
                    if (rows.getInt(1) != CallLog.Calls.MISSED_TYPE) continue
                    rawMissed += RawMissedCall(
                        id = maxId,
                        number = rows.getString(2).orEmpty(),
                        dateMs = rows.getLong(3),
                        accountComponent = rows.getString(4),
                        accountId = rows.getString(5),
                    )
                }
            }
            if (rawMissed.isEmpty()) return@withContext CallLogSnapshot(maxId, emptyList())

            val zone = ZoneId.systemDefault()
            val activeSims = sims.activeSims()
            val dayTimestamps = queryMissedTimestampsByNumber(rawMissed, zone)
            val missed = rawMissed.map { row ->
                val countToday = if (row.number.isBlank()) {
                    1
                } else {
                    val dayStart = startOfDayMs(row.dateMs, zone)
                    dayTimestamps[row.number].orEmpty().count { it in dayStart..row.dateMs }.coerceAtLeast(1)
                }
                LoggedMissedCall(
                    id = row.id,
                    event = ForwardEvent.MissedCall(
                        from = row.number,
                        sim = SimIdentity.resolve(activeSims, sims.subscriptionId(row.accountComponent, row.accountId), slotIndex = null),
                        occurredAtMs = row.dateMs,
                        receivedAtMs = receivedAt,
                        countToday = countToday,
                    ),
                )
            }
            CallLogSnapshot(maxId, missed)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun latestId(): Long =
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1").build(),
            arrayOf(CallLog.Calls._ID),
            null,
            null,
            "${CallLog.Calls._ID} DESC",
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L

    private fun queryMissedTimestampsByNumber(rows: List<RawMissedCall>, zone: ZoneId): Map<String, List<Long>> {
        val numbers = rows.mapNotNull { it.number.takeIf(String::isNotBlank) }.toSet()
        if (numbers.isEmpty()) return emptyMap()
        val minDayStart = rows.minOf { startOfDayMs(it.dateMs, zone) }
        val maxDate = rows.maxOf { it.dateMs }
        val grouped = HashMap<String, MutableList<Long>>(numbers.size)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString(), minDayStart.toString(), maxDate.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0).orEmpty()
                if (number in numbers) {
                    grouped.getOrPut(number) { mutableListOf() }.add(cursor.getLong(1))
                }
            }
        }
        return grouped
    }

    private fun startOfDayMs(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
}
