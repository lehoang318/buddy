package com.example.buddy.chat

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

        val activeId = activeSessionId
        if (activeId != null && !dirty) return null

        return if (activeId != null) {
            SavedSession(
                id = activeId,
                createdAt = activeSessionCreatedAt,
                updatedAt = System.currentTimeMillis(),
                title = title,
                raw = messages,
                summaries = summaries
            )
        } else {
            SavedSession(
                createdAt = System.currentTimeMillis(),
                title = title,
                raw = messages,
                summaries = summaries
            )
        }
    }

    suspend fun save(messages: List<SessionMessage>, summaries: List<Summary>) {
        val toSave = buildSession(messages, summaries) ?: return
        repository.addSession(toSave)
        activeSessionId = toSave.id
        activeSessionCreatedAt = toSave.createdAt
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