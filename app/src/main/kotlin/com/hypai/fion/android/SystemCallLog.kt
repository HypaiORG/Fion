package com.hypai.fion.android

import android.Manifest
import android.content.Context
import android.provider.CallLog
import com.hypai.fion.forward.CallLogReader
import com.hypai.fion.forward.CallLogSnapshot
import com.hypai.fion.forward.ForwardEvent
import com.hypai.fion.forward.TemplateVariable
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SENTINEL_HIDDEN_NUMBERS = setOf("-1", "-2", "-3")

private data class RawMissedCall(
    val number: String,
    val matchKey: String,
    val dateMs: Long,
    val accountComponent: String?,
    val accountId: String?,
    val viaNumber: String?,
)

internal fun normalizeCallNumber(rawNumber: String?, presentation: Int?): String {
    if (presentation != null && presentation != CallLog.Calls.PRESENTATION_ALLOWED) return ""
    val number = rawNumber?.trim().orEmpty()
    return if (number in SENTINEL_HIDDEN_NUMBERS) "" else number
}

internal fun callerMatchKey(number: String, normalizedNumber: String?): String {
    if (number.isBlank()) return ""
    normalizedNumber?.trim()?.takeIf(String::isNotBlank)?.let { return it }
    val digits = buildString(number.length) {
        for (ch in number) if (ch.isDigit() || ch == '+') append(ch)
    }
    return digits.ifEmpty { number }
}

internal fun countMissedOnDay(
    matchKey: String,
    dateMs: Long,
    timestampsByKey: Map<String, List<Long>>,
    zone: ZoneId,
): Int {
    if (matchKey.isBlank()) return 1
    val dayStart = startOfDayMs(dateMs, zone)
    return timestampsByKey[matchKey].orEmpty().count { it in dayStart..dateMs }.coerceAtLeast(1)
}

internal fun startOfDayMs(epochMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

class SystemCallLog(
    private val context: Context,
    private val sims: SimDirectory,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
) : CallLogReader {
    override suspend fun read(afterId: Long?, usedVariables: Set<TemplateVariable>): CallLogSnapshot? =
        withContext(Dispatchers.IO) {
            if (!context.isGranted(Manifest.permission.READ_CALL_LOG)) return@withContext null
            try {
                if (afterId == null) return@withContext latestId()?.let { CallLogSnapshot(it, emptyList()) }
                val receivedAt = now()
                val rawMissed = mutableListOf<RawMissedCall>()
                var maxId: Long = afterId
                var sawRow = false
                val rows = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.DATE,
                        CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
                        CallLog.Calls.PHONE_ACCOUNT_ID,
                        CallLog.Calls.NUMBER_PRESENTATION,
                        CallLog.Calls.CACHED_NORMALIZED_NUMBER,
                        CallLog.Calls.VIA_NUMBER,
                    ),
                    "${CallLog.Calls._ID} > ?",
                    arrayOf(afterId.toString()),
                    "${CallLog.Calls._ID} ASC",
                ) ?: return@withContext null
                rows.use {
                    while (rows.moveToNext()) {
                        sawRow = true
                        maxId = rows.getLong(0)
                        if (rows.getInt(1) != CallLog.Calls.MISSED_TYPE) continue
                        val presentation = if (rows.isNull(6)) null else rows.getInt(6)
                        val number = normalizeCallNumber(rows.getString(2), presentation)
                        rawMissed += RawMissedCall(
                            number = number,
                            matchKey = callerMatchKey(number, rows.getString(7)),
                            dateMs = rows.getLong(3),
                            accountComponent = rows.getString(4),
                            accountId = rows.getString(5),
                            viaNumber = rows.getString(8),
                        )
                    }
                }
                if (rawMissed.isEmpty()) {
                    val resolvedMaxId = if (sawRow) maxId else minOf(afterId, latestId() ?: return@withContext null)
                    return@withContext CallLogSnapshot(resolvedMaxId, emptyList())
                }

                val currentZone = zone()
                val includeCount = TemplateVariable.CallCount in usedVariables
                val includeSimNumber = TemplateVariable.SimNumber in usedVariables
                val simResolver = sims.openCallResolver(includeSimNumber)
                val dayTimestamps = if (includeCount) {
                    queryMissedTimestampsByKey(rawMissed, currentZone)
                } else {
                    emptyMap()
                }
                val missed = rawMissed.map { row ->
                    val countToday = if (includeCount) {
                        countMissedOnDay(row.matchKey, row.dateMs, dayTimestamps, currentZone)
                    } else {
                        1
                    }
                    ForwardEvent.MissedCall(
                        from = row.number,
                        sim = simResolver(row.accountComponent, row.accountId, row.viaNumber),
                        occurredAtMs = row.dateMs,
                        receivedAtMs = receivedAt,
                        countToday = countToday,
                    )
                }
                CallLogSnapshot(maxId, missed)
            } catch (_: SecurityException) {
                null
            }
        }

    private fun latestId(): Long? =
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "1").build(),
            arrayOf(CallLog.Calls._ID),
            null,
            null,
            "${CallLog.Calls._ID} DESC",
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L }

    private fun queryMissedTimestampsByKey(rows: List<RawMissedCall>, zone: ZoneId): Map<String, List<Long>> {
        val keys = rows.mapNotNull { it.matchKey.takeIf(String::isNotBlank) }.toSet()
        if (keys.isEmpty()) return emptyMap()
        val minDayStart = rows.minOf { startOfDayMs(it.dateMs, zone) }
        val maxDate = rows.maxOf { it.dateMs }
        val grouped = HashMap<String, MutableList<Long>>(keys.size)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NORMALIZED_NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.NUMBER_PRESENTATION,
            ),
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ? " +
                "AND (${CallLog.Calls.NUMBER_PRESENTATION} IS NULL OR ${CallLog.Calls.NUMBER_PRESENTATION} = ?)",
            arrayOf(
                CallLog.Calls.MISSED_TYPE.toString(),
                minDayStart.toString(),
                maxDate.toString(),
                CallLog.Calls.PRESENTATION_ALLOWED.toString(),
            ),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val presentation = if (cursor.isNull(3)) null else cursor.getInt(3)
                val number = normalizeCallNumber(cursor.getString(0), presentation)
                val key = callerMatchKey(number, cursor.getString(1))
                if (key in keys) {
                    grouped.getOrPut(key) { mutableListOf() }.add(cursor.getLong(2))
                }
            }
        }
        return grouped
    }
}
