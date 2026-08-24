package com.example.buddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

object SessionKeys {
    val SESSIONS = stringPreferencesKey("sessions_list")
    val AUTO_DELETE_OLD = booleanPreferencesKey("auto_delete_old")
}

class SessionRepository(private val context: Context) {
    companion object {
        const val AUTO_DELETE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val MAX_SESSIONS = 100
    }
    private val dataStore = context.sessionDataStore
    private val gson = Gson()

    val sessions: Flow<List<SavedSession>> = dataStore.data.map { prefs ->
        deserialize(prefs[SessionKeys.SESSIONS] ?: "")
    }

    val autoDeleteOld: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SessionKeys.AUTO_DELETE_OLD] ?: false
    }

    suspend fun addSession(session: SavedSession) {
        dataStore.edit { prefs ->
            val list = deserialize(prefs[SessionKeys.SESSIONS] ?: "").toMutableList()
            list.removeAll { it.id == session.id }
            list.add(0, session)
            while (list.size > MAX_SESSIONS) list.removeAt(list.size - 1)
            prefs[SessionKeys.SESSIONS] = serialize(list)
        }
    }

    suspend fun deleteSessions(ids: Set<String>) {
        dataStore.edit { prefs ->
            val list = deserialize(prefs[SessionKeys.SESSIONS] ?: "").toMutableList()
            list.removeAll { it.id in ids }
            prefs[SessionKeys.SESSIONS] = serialize(list)
        }
    }

    suspend fun purgeOlderThan(ageMillis: Long) {
        dataStore.edit { prefs ->
            val cutoff = System.currentTimeMillis() - ageMillis
            val list = deserialize(prefs[SessionKeys.SESSIONS] ?: "").toMutableList()
            list.removeAll { it.updatedAt < cutoff }
            prefs[SessionKeys.SESSIONS] = serialize(list)
        }
    }

    suspend fun setAutoDeleteOld(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SessionKeys.AUTO_DELETE_OLD] = enabled
        }
    }

    suspend fun autoDeleteEnabled(): Boolean = dataStore.data.first()[SessionKeys.AUTO_DELETE_OLD] ?: false

    private fun deserialize(json: String): List<SavedSession> =
        if (json.isBlank()) emptyList()
        else try {
            val sessions = gson.fromJson<List<SavedSession>>(json, object : TypeToken<List<SavedSession>>() {}.type) ?: emptyList()
            sessions.map { it.copy(updatedAt = if (it.updatedAt > 0) it.updatedAt else it.createdAt) }
        } catch (_: Exception) {
            emptyList()
        }

    private fun serialize(list: List<SavedSession>): String = gson.toJson(list)
}