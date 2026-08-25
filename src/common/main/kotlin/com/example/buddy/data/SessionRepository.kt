package com.example.buddy.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRepository(private val storage: SessionStorage) {
    companion object {
        const val AUTO_DELETE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val MAX_SESSIONS = 100
    }

    private val gson = Gson()
    private val mutex = Mutex()

    val sessions: Flow<List<SavedSession>> = storage.sessionsJson.map { json ->
        deserialize(json)
    }

    val autoDeleteOld: Flow<Boolean> = storage.autoDeleteOldSetting

    suspend fun addSession(session: SavedSession) = mutex.withLock {
        val list = deserialize(storage.sessionsJson.first()).toMutableList()
        list.removeAll { it.id == session.id }
        list.add(0, session)
        while (list.size > MAX_SESSIONS) list.removeAt(list.size - 1)
        storage.writeSessions(serialize(list))
    }

    suspend fun deleteSessions(ids: Set<String>) = mutex.withLock {
        val list = deserialize(storage.sessionsJson.first()).toMutableList()
        list.removeAll { it.id in ids }
        storage.writeSessions(serialize(list))
    }

    suspend fun purgeOlderThan(ageMillis: Long) = mutex.withLock {
        val cutoff = System.currentTimeMillis() - ageMillis
        val list = deserialize(storage.sessionsJson.first()).toMutableList()
        list.removeAll { it.updatedAt < cutoff }
        storage.writeSessions(serialize(list))
    }

    suspend fun setAutoDeleteOld(enabled: Boolean) {
        storage.setAutoDeleteOld(enabled)
    }

    suspend fun autoDeleteEnabled(): Boolean = storage.autoDeleteOldSetting.first()

    suspend fun latestSession(): SavedSession? = sessions.first().firstOrNull()

    @Suppress("USELESS_ELVIS")
    private fun deserialize(json: String): List<SavedSession> =
        if (json.isBlank()) emptyList()
        else try {
            val sessions = gson.fromJson<List<SavedSession>>(json, object : TypeToken<List<SavedSession>>() {}.type) ?: emptyList()
            sessions.map { session ->
                session.copy(
                    updatedAt = if (session.updatedAt > 0) session.updatedAt else session.createdAt,
                    raw = session.raw.map { message -> message.copy(webSearchQueries = message.webSearchQueries ?: emptyList()) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun serialize(list: List<SavedSession>): String = gson.toJson(list)
}