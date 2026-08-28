package com.example.buddy.data

import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import java.util.UUID

data class SessionMessage(
    val role: Role,
    val content: String,
    val imageBase64: String? = null,
    val imageRef: String? = null,
    val attachedFileName: String? = null,
    val attachedFileText: String? = null,
    val webSearchUsed: Boolean = false,
    val webSearchSkipped: Boolean = false,
    val webSearchQueries: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class SavedSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val raw: List<SessionMessage>,
    val summaries: List<Summary>,
    val tags: List<String> = emptyList()
)