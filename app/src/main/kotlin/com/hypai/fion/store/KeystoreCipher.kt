package com.hypai.fion.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface ByteCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

class KeystoreCipher(
    associatedData: String,
    private val alias: String = DEFAULT_ALIAS,
) : ByteCipher {
    private val aad = associatedData.encodeToByteArray()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad)
        val iv = cipher.iv
        require(iv.size in 1..MAX_IV_SIZE) { "Unexpected GCM IV length: ${iv.size}" }
        val body = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + body.size).apply {
            this[0] = iv.size.toByte()
            iv.copyInto(this, 1)
            body.copyInto(this, 1 + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < 2) throw GeneralSecurityException("Ciphertext too short")
        val ivLength = ciphertext[0].toInt() and 0xFF
        if (ivLength == 0 || ciphertext.size <= 1 + ivLength) {
            throw GeneralSecurityException("Malformed encrypted payload")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, ciphertext, 1, ivLength))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext, 1 + ivLength, ciphertext.size - 1 - ivLength)
    }

    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "fion_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val MAX_IV_SIZE = 255
        val KEY_LOCK = Any()
    }
}
