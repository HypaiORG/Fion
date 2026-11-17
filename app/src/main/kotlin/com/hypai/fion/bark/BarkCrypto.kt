package com.hypai.fion.bark

import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable

private const val GCM_TAG_BITS = 128

@Serializable
enum class AesKeySize(val bytes: Int) {
    Aes128(16), Aes192(24), Aes256(32);

    val label: String get() = "AES-${bytes * 8}"
}

@Serializable
enum class AesMode(val nonceBytes: Int, val transformation: String) {
    Cbc(16, "AES/CBC/PKCS5Padding") {
        override fun parameterSpec(nonce: ByteArray): AlgorithmParameterSpec = IvParameterSpec(nonce)
    },
    Ecb(0, "AES/ECB/PKCS5Padding") {
        override fun parameterSpec(nonce: ByteArray): AlgorithmParameterSpec? = null
    },
    Gcm(12, "AES/GCM/NoPadding") {
        override fun parameterSpec(nonce: ByteArray): AlgorithmParameterSpec = GCMParameterSpec(GCM_TAG_BITS, nonce)
    },
    ;

    val label: String get() = name.uppercase()

    abstract fun parameterSpec(nonce: ByteArray): AlgorithmParameterSpec?
}

@Serializable
data class AesConfig(
    val keySize: AesKeySize = AesKeySize.Aes128,
    val mode: AesMode = AesMode.Cbc,
    val key: String = "",
) {
    val cipherLabel: String get() = "${keySize.label}-${mode.label}"
}

data class KeyLengthMismatch(val expected: Int, val actual: Int)

fun AesConfig.keyError(): KeyLengthMismatch? {
    val actual = key.toByteArray().size
    return KeyLengthMismatch(keySize.bytes, actual).takeUnless { actual == 0 || actual == keySize.bytes }
}

fun generateNonce(bytes: Int, random: SecureRandom): String {
    if (bytes <= 0) return ""
    require(bytes % 4 == 0) { "Nonce length must be a multiple of 4 ASCII bytes: $bytes" }
    val entropy = ByteArray((bytes * 3) / 4).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy)
}

fun aesEncrypt(plaintext: String, aes: AesConfig, iv: String): String {
    val key = SecretKeySpec(aes.key.toByteArray(), "AES")
    val spec = aes.mode.parameterSpec(iv.toByteArray())
    val cipher = Cipher.getInstance(aes.mode.transformation).apply {
        if (spec == null) init(Cipher.ENCRYPT_MODE, key) else init(Cipher.ENCRYPT_MODE, key, spec)
    }
    return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray()))
}
