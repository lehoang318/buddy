package com.example.buddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

object SessionKeys {
    val SESSIONS = stringPreferencesKey("sessions_list")
    val AUTO_DELETE_OLD = booleanPreferencesKey("auto_delete_old")
}

class DataStoreSessionStorage(private val context: Context) : SessionStorage {
    private val dataStore = context.sessionDataStore

    override val sessionsJson: Flow<String> = dataStore.data.map { prefs ->
        prefs[SessionKeys.SESSIONS] ?: ""
    }

    override val autoDeleteOldSetting: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SessionKeys.AUTO_DELETE_OLD] ?: false
    }

    override suspend fun writeSessions(json: String) {
        dataStore.edit { prefs -> prefs[SessionKeys.SESSIONS] = json }
    }

    override suspend fun setAutoDeleteOld(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SessionKeys.AUTO_DELETE_OLD] = enabled }
    }
}

fun SessionRepository(context: Context): SessionRepository = SessionRepository(DataStoreSessionStorage(context))