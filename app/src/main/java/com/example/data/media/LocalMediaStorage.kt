package com.example.data.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class LocalMediaStorage(private val context: Context) {

    private val mediaDir: File
        get() = File(context.filesDir, "juthoor_media").apply {
            if (!exists()) mkdirs()
        }

    suspend fun copyFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val extension = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(mediaDir, "media_${UUID.randomUUID()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("تعذر قراءة الملف المختار.")
            destination.absolutePath
        }
    }

    fun deleteFileIfManaged(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists() && file.parentFile?.absolutePath == mediaDir.absolutePath) {
            file.delete()
        }
    }

    fun saveBytesForRestore(safeFileName: String, bytes: ByteArray): String {
        val cleanName = File(safeFileName).name.ifBlank { "media_${UUID.randomUUID()}.jpg" }
        val target = File(mediaDir, cleanName)
        target.writeBytes(bytes)
        return target.absolutePath
    }

    fun readBytesIfExists(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun detectExtensionAndMime(path: String): Pair<String, String> {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "png" to "image/png"
            lower.endsWith(".webp") -> "webp" to "image/webp"
            else -> "jpg" to "image/jpeg"
        }
    }

    fun clearAllManagedMedia() {
        mediaDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }
}
