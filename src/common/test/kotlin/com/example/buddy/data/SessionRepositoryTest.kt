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
        assertTrue(session.tags.isEmpty())
    }

    @Test
    fun `legacy summary missing tags is normalized`() = runBlocking {
        val legacyJson = """[
            {
              "id": "s2",
              "title": "Legacy summary",
              "createdAt": 2000,
              "raw": [
                {"role": "USER", "content": "q"}
              ],
              "summaries": [
                {"question": "q", "points": [{"text": "p", "key": false}]}
              ]
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = legacyJson }
        val repository = SessionRepository(storage)
        val summary = repository.sessions.first().single().summaries.single()

        assertNotNull(summary.tags)
        assertTrue(summary.tags.isEmpty())
    }

    @Test
    fun `legacy free-form tags are normalized to fixed categories`() = runBlocking {
        val legacyJson = """[
            {
              "id": "s1",
              "title": "free-form",
              "createdAt": 3000,
              "raw": [
                {"role": "USER", "content": "q"}
              ],
              "tags": ["kotlin", "POLITICS", "technology", "henlo"],
              "summaries": [
                {"question": "q", "points": [{"text": "p", "key": false}], "tags": ["World", "unknown", "SCIENCE"]}
              ]
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = legacyJson }
        val repository = SessionRepository(storage)
        val session = repository.sessions.first().single()

        assertEquals(listOf("Politics", "Technology"), session.tags)
        assertEquals(listOf("World", "Science"), session.summaries.single().tags)
    }

    @Test
    fun `purgeOlderThan returns removed session ids`() = runBlocking {
        val storage = FakeSessionStorage()
        val repository = SessionRepository(storage)
        repository.addSession(
            SavedSession(id = "old", title = "Old", createdAt = 1000, updatedAt = 1000, raw = emptyList(), summaries = emptyList())
        )
        repository.addSession(
            SavedSession(id = "new", title = "New", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), raw = emptyList(), summaries = emptyList())
        )

        val removed = repository.purgeOlderThan(30L * 24L * 60L * 60L * 1000L)

        assertEquals(setOf("old"), removed)
        assertEquals(listOf("new"), repository.sessions.first().map { it.id })
    }

    @Test
    fun `normalize filters unknowns dedups and is case insensitive`() {
        assertEquals(listOf("Health", "World"), SessionTags.normalize(listOf("health", "HEALTH", "World")))
        assertEquals(emptyList<String>(), SessionTags.normalize(listOf("henlo", "")))
    }
}