package com.hypai.fion.forward

import com.hypai.fion.store.ForwardStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LoggedMissedCall(val id: Long, val event: ForwardEvent.MissedCall)

data class CallLogSnapshot(val maxId: Long, val missed: List<LoggedMissedCall>)

fun interface CallLogReader {
    suspend fun read(afterId: Long?, usedVariables: Set<TemplateVariable>): CallLogSnapshot?
}

class MissedCallSync(
    private val reader: CallLogReader,
    private val stateStore: ForwardStateStore,
    private val forwarder: Forwarder,
) {
    private val lock = Mutex()

    suspend fun sync() = lock.withLock {
        val watermark = stateStore.snapshot().callLogWatermark
        val usedVariables = if (watermark == null) emptySet() else forwarder.usedVariables(EventKind.MissedCall)
        val snapshot = reader.read(watermark, usedVariables) ?: return@withLock
        if (watermark == null || snapshot.missed.isEmpty()) {
            if (watermark != snapshot.maxId) {
                stateStore.settle {
                    if (it.callLogWatermark == snapshot.maxId) it else it.copy(callLogWatermark = snapshot.maxId)
                }
            }
            return@withLock
        }
        forwarder.forwardAll(
            events = snapshot.missed.map { it.event },
            progress = { if (it.callLogWatermark == snapshot.maxId) it else it.copy(callLogWatermark = snapshot.maxId) },
        )
    }

    suspend fun advanceWatermark() = lock.withLock {
        val snapshot = reader.read(null, emptySet()) ?: return@withLock
        stateStore.settle {
            val current = it.callLogWatermark
            if (current != null && current >= snapshot.maxId) it else it.copy(callLogWatermark = snapshot.maxId)
        }
    }
}
