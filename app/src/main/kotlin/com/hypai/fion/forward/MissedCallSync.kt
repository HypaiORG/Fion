package com.hypai.fion.forward

import com.hypai.fion.store.ForwardStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LoggedMissedCall(val id: Long, val event: ForwardEvent.MissedCall)

data class CallLogSnapshot(val maxId: Long, val missed: List<LoggedMissedCall>)

fun interface CallLogReader {
    suspend fun read(afterId: Long?): CallLogSnapshot?
}

class MissedCallSync(
    private val reader: CallLogReader,
    private val stateStore: ForwardStateStore,
    private val forwarder: Forwarder,
) {
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        val watermark = stateStore.snapshot().callLogWatermark
        val snapshot = reader.read(watermark) ?: return@withLock
        var persistedWatermark = watermark
        if (watermark != null) {
            for (call in snapshot.missed) {
                forwarder.forward(call.event) { it.copy(callLogWatermark = call.id) }
                persistedWatermark = call.id
            }
        }
        if (persistedWatermark != snapshot.maxId) {
            stateStore.settle {
                if (it.callLogWatermark == snapshot.maxId) it else it.copy(callLogWatermark = snapshot.maxId)
            }
        }
    }

    suspend fun advanceWatermark() = lock.withLock {
        val snapshot = reader.read(null) ?: return@withLock
        stateStore.settle {
            val current = it.callLogWatermark
            if (current != null && current >= snapshot.maxId) it else it.copy(callLogWatermark = snapshot.maxId)
        }
    }
}
