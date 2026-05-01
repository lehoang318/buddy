package com.example.buddy.data

import android.net.Uri

enum class Role { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val imageBase64: String? = null,
    val attachedFileUri: Uri? = null,
    val attachedFileName: String? = null,
    val attachedFileText: String? = null,
    val fetchedUrlText: String? = null,
    val isStreaming: Boolean = false,
    val isComplete: Boolean = false,
    val webSearchUsed: Boolean = false,
    val webSearchSkipped: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
