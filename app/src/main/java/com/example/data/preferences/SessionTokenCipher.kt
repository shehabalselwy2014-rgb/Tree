package com.example.data.preferences

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SessionTokenCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "juthoor_auth_session_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PREFIX = "enc:v1:"

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        val key = getOrCreateAndroidKeyStoreKey() ?: return plainText
        return runCatching { encryptWithKey(plainText, key) }.getOrDefault(plainText)
    }

    fun decrypt(storedValue: String): String {
        if (storedValue.isBlank()) return storedValue
        if (!storedValue.startsWith(PREFIX)) return storedValue
        val key = getOrCreateAndroidKeyStoreKey() ?: return ""
        return runCatching { decryptWithKey(storedValue, key) }.getOrDefault("")
    }

    fun isEncryptedFormat(storedValue: String): Boolean = storedValue.startsWith(PREFIX)

    fun encryptWithKey(plainText: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$PREFIX$ivB64:$cipherB64"
    }

    fun decryptWithKey(storedValue: String, secretKey: SecretKey): String {
        require(storedValue.startsWith(PREFIX)) { "Invalid encrypted token header" }
        val body = storedValue.removePrefix(PREFIX)
        val parts = body.split(':', limit = 2)
        require(parts.size == 2) { "Malformed encrypted token payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun getOrCreateAndroidKeyStoreKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }
}
