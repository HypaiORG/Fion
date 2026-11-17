package com.hypai.fion.core

import androidx.datastore.core.DataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LoggedMissedCall(val id: Long, val event: ForwardEvent.MissedCall)

data class CallLogSnapshot(val maxId: Long, val missed: List<LoggedMissedCall>)

fun interface CallLogReader {
    suspend fun read(afterId: Long?): CallLogSnapshot?
}

class MissedCallSync(
    private val reader: CallLogReader,
    private val runtime: DataStore<Runtime>,
    private val forwarder: Forwarder,
) {
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        val watermark = runtime.data.first().callLogWatermark
        val snapshot = reader.read(watermark) ?: return@withLock
        if (watermark != null) {
            for (call in snapshot.missed) {
                forwarder.forward(call.event) { it.copy(callLogWatermark = call.id) }
            }
        }
        withContext(NonCancellable) {
            runtime.updateData {
                it.copy(callLogWatermark = snapshot.maxId)
            }
        }
    }
}
