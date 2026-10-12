package com.example.buddy.data

import android.content.Context
import android.util.Base64
import com.example.buddy.logging.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionImageStore(private val context: Context) {

    private val rootDir: File
        get() = File(context.filesDir, SESSION_IMAGES_DIR)

    fun fileFor(sessionId: String, ref: String): File? {
        val safe = safeRef(ref) ?: return null
        return File(File(rootDir, sessionId), safe)
    }

    suspend fun detach(sessionId: String, messages: List<SessionMessage>): List<SessionMessage> =
        withContext(Dispatchers.IO) {
            if (messages.none { it.imageBase64 != null }) return@withContext messages
            val dir = File(rootDir, sessionId)
            dir.mkdirs()
            messages.map { message ->
                val base64 = message.imageBase64
                if (base64 == null) {
                    message
                } else {
                    val existing = message.imageRef?.let { safeRef(it) }
                    if (existing != null && File(dir, existing).exists()) {
                        message.copy(imageBase64 = null)
                    } else {
                        persist(dir, sessionId, message, base64)
                    }
                }
            }
        }

    private fun persist(dir: File, sessionId: String, message: SessionMessage, base64: String): SessionMessage {
        val bytes = decodeBase64(base64)
        if (bytes == null || bytes.isEmpty()) {
            Log.warning(TAG, "Failed to decode session image", "sessionId=$sessionId")
            return message
        }
        val name = reference(bytes)
        val file = File(dir, name)
        if (file.exists() || writeAtomically(file, bytes)) {
            return message.copy(imageBase64 = null, imageRef = name)
        }
        Log.warning(TAG, "Failed to persist session image", "sessionId=$sessionId")
        return message
    }

    suspend fun prepareForResume(session: SavedSession, keepBase64Trailing: Int): SavedSession =
        withContext(Dispatchers.IO) {
            val dir = File(rootDir, session.id)
            val messages = session.raw
            val threshold = (messages.size - keepBase64Trailing).coerceAtLeast(0)
            session.copy(raw = messages.mapIndexed { index, message ->
                val safe = message.imageRef?.let { safeRef(it) }
                if (safe == null) {
                    message
                } else {
                    val file = File(dir, safe)
                    when {
                        !file.exists() -> {
                            Log.warning(TAG, "Missing session image", "sessionId=${session.id}, ref=$safe")
                            message.copy(imageBase64 = null)
                        }
                        index < threshold -> message.copy(imageBase64 = null)
                        else -> message.copy(imageBase64 = encodeBase64(file.readBytes()))
                    }
                }
            })
        }

    suspend fun readBase64(sessionId: String, ref: String): String? = withContext(Dispatchers.IO) {
        fileFor(sessionId, ref)?.takeIf { it.exists() }?.let { encodeBase64(it.readBytes()) }
    }

    suspend fun delete(sessionIds: Set<String>) = withContext(Dispatchers.IO) {
        sessionIds.forEach { id ->
            runCatching { File(rootDir, id).deleteRecursively() }
        }
    }

    suspend fun reconcile(knownSessionIds: Set<String>?) = withContext(Dispatchers.IO) {
        if (knownSessionIds == null) {
            Log.warning(TAG, "Skipped image reconciliation", "Session list could not be read")
            return@withContext
        }
        val dirs = rootDir.listFiles()?.filter { it.isDirectory } ?: return@withContext
        dirs.filter { it.name !in knownSessionIds }.forEach { stale ->
            runCatching { stale.deleteRecursively() }
        }
    }

    suspend fun cleanupStaleCaptures() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - STALE_CAPTURE_AGE_MILLIS
        context.cacheDir
            .listFiles { file -> file.isFile && file.name.startsWith(CAPTURE_PREFIX) && file.name.endsWith(".jpg") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun safeRef(ref: String): String? =
        ref.takeIf { it.isNotBlank() && it != "." && it != ".." && !it.contains('/') && !it.contains('\\') }

    private fun reference(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(REF_HASH_CHARS) + ".jpg"

    private fun writeAtomically(file: File, bytes: ByteArray): Boolean = try {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (tmp.renameTo(file)) {
            true
        } else {
            tmp.delete()
            file.writeBytes(bytes)
            true
        }
    } catch (e: Exception) {
        Log.warning(TAG, "Atomic image write failed", e.message)
        runCatching { File(file.parentFile, "${file.name}.tmp").delete() }
        false
    }

    private fun decodeBase64(base64: String): ByteArray? = try {
        Base64.decode(base64.substringAfter(",", base64), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    private fun encodeBase64(bytes: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val SESSION_IMAGES_DIR = "session_images"
        private const val CAPTURE_PREFIX = "buddy_camera_"
        private const val REF_HASH_CHARS = 32
        private const val STALE_CAPTURE_AGE_MILLIS = 24L * 60L * 60L * 1000L
        private const val TAG = "Images"
    }
}
