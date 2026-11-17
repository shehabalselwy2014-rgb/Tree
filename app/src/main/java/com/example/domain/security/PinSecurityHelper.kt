package com.example.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurityHelper {

    private const val SALT_BYTES = 16
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 6
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256
    private const val PREFIX_PBKDF2_SHA256 = "pbkdf2_sha256"
    private const val PREFIX_PBKDF2_SHA1 = "pbkdf2_sha1"
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun validatePinFormat(pin: String): String? {
        if (pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
            return "يجب أن يتكون رمز القفل من $MIN_PIN_LENGTH إلى $MAX_PIN_LENGTH أرقام."
        }
        if (!pin.all { it.isDigit() }) {
            return "يجب أن يحتوي رمز القفل على أرقام فقط."
        }
        return null
    }

    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return toHex(bytes)
    }

    fun hashPin(pin: String, saltHex: String): String {
        val (algoName, tag) = selectPbkdf2Algorithm()
        val derived = derivePbkdf2(
            algorithm = algoName,
            pin = pin,
            salt = "$saltHex:juthoor_pin".toByteArray(Charsets.UTF_8),
            iterations = PBKDF2_ITERATIONS,
            keyBits = PBKDF2_KEY_BITS,
        )
        return "$tag$$PBKDF2_ITERATIONS$${toHex(derived)}"
    }

    fun verifyPin(candidatePin: String, saltHex: String?, storedHash: String?): Boolean {
        if (saltHex.isNullOrBlank() || storedHash.isNullOrBlank()) return false
        val parts = storedHash.split('$')
        if (parts.size == 3) {
            val tag = parts[0]
            val iterations = parts[1].toIntOrNull() ?: return false
            val expectedHex = parts[2]
            val algorithm = when (tag) {
                PREFIX_PBKDF2_SHA256 -> "PBKDF2WithHmacSHA256"
                PREFIX_PBKDF2_SHA1 -> "PBKDF2WithHmacSHA1"
                else -> return false
            }
            val computedHex = runCatching {
                val derived = derivePbkdf2(
                    algorithm = algorithm,
                    pin = candidatePin,
                    salt = "$saltHex:juthoor_pin".toByteArray(Charsets.UTF_8),
                    iterations = iterations,
                    keyBits = (expectedHex.length / 2) * 8,
                )
                toHex(derived)
            }.getOrNull() ?: return false
            return MessageDigest.isEqual(
                computedHex.toByteArray(Charsets.UTF_8),
                expectedHex.toByteArray(Charsets.UTF_8),
            )
        }

        // Backward-compatibility check for legacy SHA-256 PIN hashes
        val legacyComputed = hashLegacySha256(candidatePin, saltHex)
        return MessageDigest.isEqual(
            legacyComputed.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
    }

    fun needsRehash(storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return true
        return !storedHash.startsWith("$PREFIX_PBKDF2_SHA256$$PBKDF2_ITERATIONS$")
    }

    private fun selectPbkdf2Algorithm(): Pair<String, String> {
        return runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "PBKDF2WithHmacSHA256" to PREFIX_PBKDF2_SHA256
        }.getOrElse {
            "PBKDF2WithHmacSHA1" to PREFIX_PBKDF2_SHA1
        }
    }

    private fun derivePbkdf2(
        algorithm: String,
        pin: String,
        salt: ByteArray,
        iterations: Int,
        keyBits: Int,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyBits)
        return try {
            val factory = SecretKeyFactory.getInstance(algorithm)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hashLegacySha256(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        digest.update(":juthoor_pin:".toByteArray(Charsets.UTF_8))
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return toHex(digest.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }
}
