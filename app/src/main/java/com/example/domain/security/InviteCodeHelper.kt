package com.example.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object InviteCodeHelper {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generateCode(): String {
        val part1 = buildString(4) {
            repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        val part2 = buildString(4) {
            repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        return "JTH-$part1-$part2"
    }

    fun normalizeCode(raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.US)
        if (trimmed.isEmpty()) return ""
        val alnum = trimmed.filter { it.isLetterOrDigit() }
        return if (alnum.length == 11 && alnum.startsWith("JTH")) {
            "JTH-${alnum.substring(3, 7)}-${alnum.substring(7, 11)}"
        } else {
            trimmed.replace(Regex("\\s+"), "")
        }
    }

    fun sha256Hex(normalizedCode: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(normalizedCode.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildHint(normalizedCode: String): String {
        val tail = normalizedCode.takeLast(4).ifBlank { "****" }
        return if (normalizedCode.startsWith("JTH-")) {
            "JTH-••••-$tail"
        } else {
            "••••-$tail"
        }
    }
}
