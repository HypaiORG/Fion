package com.hypai.fion.platform

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.hypai.fion.core.CallLogReader
import com.hypai.fion.core.CallLogSnapshot
import com.hypai.fion.core.ForwardEvent
import com.hypai.fion.core.LoggedMissedCall
import com.hypai.fion.core.SimIdentity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemCallLog(
    private val context: Context,
    private val telephony: Telephony,
    private val now: () -> Long,
) : CallLogReader {
    override suspend fun read(afterId: Long?): CallLogSnapshot? = withContext(Dispatchers.IO) {
        if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return@withContext null
        if (afterId == null) return@withContext CallLogSnapshot(latestId(), emptyList())
        val receivedAt = now()
        val sims by lazy { telephony.activeSims() }
        val missed = mutableListOf<LoggedMissedCall>()
        var maxId: Long = afterId
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID, CallLog.Calls.TYPE, CallLog.Calls.NUMBER,
                CallLog.Calls.DATE, CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME, CallLog.Calls.PHONE_ACCOUNT_ID,
            ),
            "${CallLog.Calls._ID} > ?",
            arrayOf(afterId.toString()),
            "${CallLog.Calls._ID} ASC",
        )?.use { rows ->
            while (rows.moveToNext()) {
                maxId = rows.getLong(0)
                if (rows.getInt(1) != CallLog.Calls.MISSED_TYPE) continue
                val number = rows.getString(2).orEmpty()
                val date = rows.getLong(3)
                missed += LoggedMissedCall(maxId, ForwardEvent.MissedCall(
                    from = number,
                    sim = SimIdentity.resolve(sims, telephony.subscriptionId(rows.getString(4), rows.getString(5)), slotIndex = null),
                    occurredAtMs = date,
                    receivedAtMs = receivedAt,
                    countToday = missedTodayCount(number, date),
                ))
            }
        }
        CallLogSnapshot(maxId, missed)
    }

    private fun latestId(): Long =
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1").build(),
            arrayOf(CallLog.Calls._ID),
            null,
            null,
            "${CallLog.Calls._ID} DESC",
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L

    private fun missedTodayCount(number: String, upToMs: Long): Int {
        val zone = ZoneId.systemDefault()
        val startOfDay = Instant.ofEpochMilli(upToMs).atZone(zone).toLocalDate().atStartOfDay(zone)
            .toInstant().toEpochMilli()
        return context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.TYPE} = ? AND " +
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?",
            arrayOf(number, CallLog.Calls.MISSED_TYPE.toString(), startOfDay.toString(), upToMs.toString()),
            null,
        )?.use { it.count } ?: 1
    }
}
