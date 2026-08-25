package com.example.buddy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private class FakeSessionStorage : SessionStorage {
        val sessionsJsonFlow = MutableStateFlow("")
        val autoDeleteFlow = MutableStateFlow(false)
        override val sessionsJson: Flow<String> = sessionsJsonFlow
        override val autoDeleteOldSetting: Flow<Boolean> = autoDeleteFlow
        override suspend fun writeSessions(json: String) { sessionsJsonFlow.value = json }
        override suspend fun setAutoDeleteOld(enabled: Boolean) { autoDeleteFlow.value = enabled }
    }

    @Test
    fun `legacy json missing updatedAt and webSearchQueries is normalized`() = runBlocking {
        val legacyJson = """[
            {
              "id": "s1",
              "title": "Legacy",
              "createdAt": 1000,
              "raw": [
                {"role": "USER", "content": "hi"}
              ],
              "summaries": []
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = legacyJson }
        val repository = SessionRepository(storage)
        val session = repository.sessions.first().single()

        assertEquals(1000L, session.updatedAt)
        assertNotNull(session.raw.single().webSearchQueries)
        assertTrue(session.raw.single().webSearchQueries.isEmpty())
    }
}