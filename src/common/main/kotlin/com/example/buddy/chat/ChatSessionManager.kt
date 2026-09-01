package com.example.buddy.chat

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.Role
import com.example.buddy.data.SavedSession
import com.example.buddy.data.SessionMessage
import com.example.buddy.data.SessionRepository
import com.example.buddy.data.Summary

class ChatSessionManager(private val repository: SessionRepository) {
    var activeSessionId: String? = null
        private set
    var activeSessionCreatedAt: Long = 0
        private set
    var dirty = false
        private set

    fun markDirty() {
        dirty = true
    }

    fun buildSession(messages: List<SessionMessage>, summaries: List<Summary>): SavedSession? {
        val hasRealMessage = messages.any { it.role == Role.USER }
        if (!hasRealMessage) return null

        val title = messages.firstOrNull { it.role == Role.USER }
            ?.content?.trim()?.take(50)?.ifBlank { "Untitled" } ?: "Untitled"

        val tags = summaries.flatMap { it.tags }.distinct().takeLast(AppConfigProvider.current.summaries.maxSessionTags)

        val activeId = activeSessionId
        if (activeId != null && !dirty) return null

        return if (activeId != null) {
            SavedSession(
                id = activeId,
                createdAt = activeSessionCreatedAt,
                updatedAt = System.currentTimeMillis(),
                title = title,
                raw = messages,
                summaries = summaries,
                tags = tags
            )
        } else {
            SavedSession(
                createdAt = System.currentTimeMillis(),
                title = title,
                raw = messages,
                summaries = summaries,
                tags = tags
            )
        }
    }

    suspend fun save(messages: List<SessionMessage>, summaries: List<Summary>, transform: suspend (SavedSession) -> SavedSession = { it }) {
        val toSave = buildSession(messages, summaries) ?: return
        val final = transform(toSave)
        repository.addSession(final)
        activeSessionId = final.id
        activeSessionCreatedAt = final.createdAt
        dirty = false
    }

    fun bind(session: SavedSession) {
        activeSessionId = session.id
        activeSessionCreatedAt = session.createdAt
        dirty = false
    }

    fun reset() {
        activeSessionId = null
        activeSessionCreatedAt = 0
        dirty = false
    }
}