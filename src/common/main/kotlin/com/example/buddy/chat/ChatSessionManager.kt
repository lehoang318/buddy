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
    var activeSessionTitle: String? = null
        private set
    var dirty = false
        private set

    fun markDirty() {
        dirty = true
    }

    fun buildSession(messages: List<SessionMessage>, summaries: List<Summary>, allowEmpty: Boolean = false): SavedSession? {
        val hasUserMessage = messages.any { it.role == Role.USER }
        val activeId = activeSessionId
        if (!hasUserMessage && (!allowEmpty || activeId == null)) return null

        val derivedTitle = messages.firstOrNull { it.role == Role.USER }
            ?.content?.trim()?.take(50)?.ifBlank { null }
        val title = if (activeId != null) {
            activeSessionTitle ?: derivedTitle ?: "Untitled"
        } else {
            derivedTitle ?: "Untitled"
        }

        val tags = summaries.flatMap { it.tags }.distinct().takeLast(AppConfigProvider.current.summaries.maxSessionTags)

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

    suspend fun save(
        messages: List<SessionMessage>,
        summaries: List<Summary>,
        allowEmpty: Boolean = false,
        transform: suspend (SavedSession) -> SavedSession = { it }
    ): Set<String> {
        val toSave = buildSession(messages, summaries, allowEmpty) ?: return emptySet()
        val final = transform(toSave)
        val evicted = repository.addSession(final)
        activeSessionId = final.id
        activeSessionCreatedAt = final.createdAt
        activeSessionTitle = final.title
        dirty = false
        return evicted
    }

    suspend fun deleteActive(): String? {
        val id = activeSessionId ?: return null
        repository.deleteSessions(setOf(id))
        reset()
        return id
    }

    fun bind(session: SavedSession) {
        activeSessionId = session.id
        activeSessionCreatedAt = session.createdAt
        activeSessionTitle = session.title
        dirty = false
    }

    fun reset() {
        activeSessionId = null
        activeSessionCreatedAt = 0
        activeSessionTitle = null
        dirty = false
    }
}
