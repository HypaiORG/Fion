package com.hypai.fion.core

import androidx.datastore.core.DataStore
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

class ScriptedTransport(vararg results: HttpResult) : HttpTransport {
    private val remaining = ArrayDeque(results.toList())
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun postJson(url: String, body: String): HttpResult {
        requests += url to body
        return remaining.removeFirst()
    }
}

val ok = HttpResult.Response(200, """{"code":200,"message":"success","timestamp":1}""")
val badKey = HttpResult.Response(400, """{"code":400,"message":"failed to get device token"}""")
