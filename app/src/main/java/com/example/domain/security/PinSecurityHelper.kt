package com.example.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

object PinSecurityHelper {

    private const val SALT_BYTES = 16
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 6
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
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        digest.update(":juthoor_pin:".toByteArray(Charsets.UTF_8))
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return toHex(digest.digest())
    }

    fun verifyPin(candidatePin: String, saltHex: String?, storedHashHex: String?): Boolean {
        if (saltHex.isNullOrBlank() || storedHashHex.isNullOrBlank()) return false
        val computed = hashPin(candidatePin, saltHex)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            storedHashHex.toByteArray(Charsets.UTF_8),
        )
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
