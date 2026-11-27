package com.hypai.fion.forward

import androidx.datastore.core.DataStore
import com.hypai.fion.bark.HttpResult
import com.hypai.fion.bark.HttpTransport
import com.hypai.fion.store.Message
import com.hypai.fion.store.Outbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MemoryStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    val value: T get() = state.value
    val history = mutableListOf(initial)
    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        state.value = transform(state.value)
        history += state.value
        return state.value
    }
}

class MemoryOutbox : Outbox {
    val items = mutableListOf<Message>()

    override suspend fun enqueue(messages: List<Message>, maxSize: Int): Int {
        for (message in messages) {
            if (items.none { it.id == message.id }) items += message
        }
        val evicted = (items.size - maxSize).coerceAtLeast(0)
        repeat(evicted) { items.removeAt(0) }
        return evicted
    }

    override suspend fun purgeExpired(cutoffMs: Long): Int {
        val before = items.size
        items.removeAll { it.receivedAtMs < cutoffMs }
        return before - items.size
    }

    override suspend fun next(kinds: Set<EventKind>, skip: Set<String>): Message? =
        items.firstOrNull { it.kind in kinds && it.id !in skip }

    override suspend fun remove(id: String): Boolean =
        items.removeAll { it.id == id }

    override suspend fun hasAny(kinds: Set<EventKind>): Boolean =
        items.any { it.kind in kinds }
}

class ScriptedTransport(vararg results: HttpResult) : HttpTransport {
    private val remaining = ArrayDeque(results.toList())
    val requests = mutableListOf<Pair<String, String>>()
    val timeouts = mutableListOf<Long>()

    override suspend fun postJson(url: String, body: String, timeoutMs: Long): HttpResult {
        requests += url to body
        timeouts += timeoutMs
        return remaining.removeFirst()
    }
}

val ok = HttpResult.Response(200, """{"code":200,"message":"success","timestamp":1}""")
val badKey = HttpResult.Response(400, """{"code":400,"message":"failed to get device token"}""")
