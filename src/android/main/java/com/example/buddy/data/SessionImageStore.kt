package com.example.buddy.data

import android.content.Context
import android.util.Base64
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionImageStore(private val context: Context) {

    private val rootDir: File
        get() = File(context.filesDir, SESSION_IMAGES_DIR)

    suspend fun detach(sessionId: String, messages: List<SessionMessage>): List<SessionMessage> = withContext(Dispatchers.IO) {
        if (messages.none { it.imageBase64 != null }) return@withContext messages
        val dir = File(rootDir, sessionId)
        dir.mkdirs()
        messages.mapIndexed { index, message ->
            val base64 = message.imageBase64
            if (base64 == null) {
                message
            } else {
                val file = File(dir, "$index.jpg")
                if (!file.exists()) {
                    file.writeBytes(decodeBase64(base64))
                }
                message.copy(imageBase64 = null, imageRef = "$index.jpg")
            }
        }
    }

    suspend fun hydrate(session: SavedSession): SavedSession = withContext(Dispatchers.IO) {
        val dir = File(rootDir, session.id)
        val hydrated = session.raw.map { message ->
            val ref = message.imageRef
            if (ref == null) {
                message
            } else {
                val file = File(dir, ref)
                message.copy(imageBase64 = if (file.exists()) encodeBase64(file.readBytes()) else null)
            }
        }
        session.copy(raw = hydrated)
    }

    suspend fun delete(sessionIds: Set<String>) = withContext(Dispatchers.IO) {
        sessionIds.forEach { id ->
            File(rootDir, id).deleteRecursively()
        }
    }

    private fun decodeBase64(base64: String): ByteArray {
        val payload = base64.substringAfter(",", base64)
        return Base64.decode(payload, Base64.NO_WRAP)
    }

    private fun encodeBase64(bytes: ByteArray): String =
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    companion object {
        private const val SESSION_IMAGES_DIR = "session_images"
    }
}