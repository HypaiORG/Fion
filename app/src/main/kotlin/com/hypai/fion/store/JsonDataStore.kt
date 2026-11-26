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
import java.security.GeneralSecurityException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal val STORE_JSON = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
    private val cipher: ByteCipher? = null,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        val raw = input.readBytes()
        val jsonBytes = cipher?.decrypt(raw) ?: raw
        STORE_JSON.decodeFromString(serializer, jsonBytes.decodeToString())
    } catch (e: GeneralSecurityException) {
        throw CorruptionException("Undecryptable ${serializer.descriptor.serialName}", e)
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plain = STORE_JSON.encodeToString(serializer, t).encodeToByteArray()
        output.write(cipher?.encrypt(plain) ?: plain)
    }
}

fun <T> Context.jsonStore(
    name: String,
    serializer: KSerializer<T>,
    default: T,
    cipher: ByteCipher? = null,
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(serializer, default, cipher),
    corruptionHandler = ReplaceFileCorruptionHandler { default },
    produceFile = { dataStoreFile("$name.json") },
)
