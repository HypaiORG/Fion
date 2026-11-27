package com.hypai.fion.store

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal val STORE_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        STORE_JSON.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(STORE_JSON.encodeToString(serializer, t).encodeToByteArray())
    }
}

fun <T> Context.jsonStore(name: String, serializer: KSerializer<T>, default: T): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(serializer, default),
    corruptionHandler = ReplaceFileCorruptionHandler { default },
    produceFile = { dataStoreFile("$name.json") },
)
