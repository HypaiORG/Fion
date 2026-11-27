package com.hypai.fion.forward

import com.hypai.fion.store.ForwardState
import com.hypai.fion.store.ForwardStateStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CallLogSnapshot(val maxId: Long, val missed: List<ForwardEvent.MissedCall>)

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
            if (watermark != snapshot.maxId) persistWatermark(snapshot.maxId)
            return@withLock
        }
        forwarder.forwardAll(
            events = snapshot.missed,
            progress = { it.withWatermark(snapshot.maxId) },
        )
    }

    suspend fun advanceWatermark() = lock.withLock {
        val snapshot = reader.read(null, emptySet()) ?: return@withLock
        persistWatermark(snapshot.maxId)
    }

    private suspend fun persistWatermark(maxId: Long) = stateStore.settle { it.withWatermark(maxId) }

    private fun ForwardState.withWatermark(maxId: Long) =
        if (callLogWatermark == maxId) this else copy(callLogWatermark = maxId)
}
