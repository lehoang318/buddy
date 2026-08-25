package com.example.buddy.data

import kotlinx.coroutines.flow.Flow

interface SessionStorage {
    val sessionsJson: Flow<String>
    val autoDeleteOldSetting: Flow<Boolean>

    suspend fun writeSessions(json: String)

    suspend fun setAutoDeleteOld(enabled: Boolean)
}