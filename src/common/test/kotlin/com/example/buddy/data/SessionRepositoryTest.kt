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
    fun `legacy json missing turnEvents is normalized`() = runBlocking {
        val legacyJson = """[
            {
              "id": "s3",
              "title": "Legacy timeline",
              "createdAt": 4000,
              "raw": [
                {"role": "ASSISTANT", "content": "hi", "webSearchQueries": ["q"]}
              ],
              "summaries": []
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = legacyJson }
        val repository = SessionRepository(storage)
        val message = repository.sessions.first().single().raw.single()

        assertNotNull(message.turnEvents)
        assertTrue(message.turnEvents.isEmpty())
    }

    @Test
    fun `turnEvents round-trip through the repository`() = runBlocking {
        val storage = FakeSessionStorage()
        val repository = SessionRepository(storage)
        val events = listOf(
            TurnEvent(TurnEventKind.THOUGHTS, text = "thinking"),
            TurnEvent(TurnEventKind.SEARCH, queries = listOf("a", "b")),
            TurnEvent(TurnEventKind.QUESTION, text = "Which?"),
            TurnEvent(TurnEventKind.ANSWER, text = "That")
        )
        repository.addSession(
            SavedSession(
                id = "timeline",
                title = "Timeline",
                createdAt = 5000,
                updatedAt = 5000,
                raw = listOf(SessionMessage(role = Role.ASSISTANT, content = "done", turnEvents = events)),
                summaries = emptyList()
            )
        )

        val restored = repository.sessions.first().single { it.id == "timeline" }
        assertEquals(events, restored.raw.single().turnEvents)
    }

    @Test
    fun `malformed nested turn events are sanitized on load`() = runBlocking {
        val json = """[
            {
              "id": "s4",
              "title": "Malformed timeline",
              "createdAt": 6000,
              "raw": [
                {"role": "ASSISTANT", "content": "hi", "turnEvents": [{"kind":"BOGUS"}, {"kind":"THOUGHTS","text":"ok"}, null]}
              ],
              "summaries": []
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = json }
        val repository = SessionRepository(storage)
        val message = repository.sessions.first().single().raw.single()

        assertEquals(listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "ok")), message.turnEvents)
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

    @Test
    fun `addSession returns evicted ids when cap exceeded`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        var lastEvicted = emptySet<String>()
        for (i in 0..SessionRepository.MAX_SESSIONS) {
            lastEvicted = repository.addSession(
                SavedSession(id = "s$i", title = "t$i", createdAt = i.toLong(), updatedAt = i.toLong(), raw = emptyList(), summaries = emptyList())
            )
        }

        assertEquals(setOf("s0"), lastEvicted)
        assertEquals(SessionRepository.MAX_SESSIONS, repository.sessions.first().size)
    }

    @Test
    fun `knownSessionIds returns null for unreadable json`() = runBlocking {
        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = "{ not valid json" }
        val repository = SessionRepository(storage)

        assertEquals(null, repository.knownSessionIds())
    }

    @Test
    fun `knownSessionIds returns empty for blank and ids when readable`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())

        assertEquals(emptySet<String>(), repository.knownSessionIds())

        repository.addSession(SavedSession(id = "abc", title = "t", raw = emptyList(), summaries = emptyList()))
        assertEquals(setOf("abc"), repository.knownSessionIds())
    }

    @Test
    fun `turn ids and summary provenance round-trip through the repository`() = runBlocking {
        val storage = FakeSessionStorage()
        val repository = SessionRepository(storage)
        val summary = Summary(
            question = "Earlier conversation",
            points = listOf(SummaryPoint("p", key = true)),
            tags = listOf("Science"),
            turnIds = listOf("t1", "t2"),
            sources = listOf(
                SummarySource(listOf(SummaryPoint("p", key = true)), listOf("Science"), listOf("t1")),
                SummarySource(listOf(SummaryPoint("q")), listOf("World"), listOf("t2"))
            )
        )
        repository.addSession(
            SavedSession(
                id = "provenance",
                title = "Provenance",
                raw = listOf(
                    SessionMessage(role = Role.USER, content = "q1", turnId = "t1"),
                    SessionMessage(role = Role.ASSISTANT, content = "a1", turnId = "t1")
                ),
                summaries = listOf(summary)
            )
        )

        val restored = repository.sessions.first().single { it.id == "provenance" }

        assertEquals(listOf("t1", "t1"), restored.raw.map { it.turnId })
        assertEquals(summary, restored.summaries.single())
    }

    @Test
    fun `legacy json with null raw and missing provenance is normalized`() = runBlocking {
        val legacyJson = """[
            {
              "id": "s6",
              "title": "Nulls",
              "createdAt": 7000,
              "raw": null,
              "summaries": [
                {"question": "q", "points": [{"text": "p"}]},
                {
                  "question": "Earlier conversation",
                  "points": [{"text": "m"}],
                  "turnIds": ["t1"],
                  "sources": [
                    {"points": [{"text": "a"}]},
                    {"turnIds": ["t2"], "tags": ["science"]}
                  ]
                }
              ]
            }
        ]"""

        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = legacyJson }
        val repository = SessionRepository(storage)
        val session = repository.sessions.first().single()

        assertTrue(session.raw.isEmpty())
        assertTrue(!session.hasUserMessage())
        assertEquals(emptyList<String>(), session.summaries[0].turnIds)
        assertTrue(session.summaries[0].sources.isEmpty())
        assertEquals(listOf("t1", "t2"), session.summaries[1].turnIds)
        assertEquals(listOf(SummaryPoint("a")), session.summaries[1].sources[0].points)
        assertTrue(session.summaries[1].sources[0].tags.isEmpty())
        assertTrue(session.summaries[1].sources[0].turnIds.isEmpty())
        assertEquals(listOf("Science"), session.summaries[1].sources[1].tags)
        assertTrue(session.summaries[1].sources[1].points.isEmpty())
        assertEquals(listOf("t2"), session.summaries[1].sources[1].turnIds)
    }

    @Test
    fun `deleteEmptySessions removes only sessions without user messages`() = runBlocking {
        val storage = FakeSessionStorage()
        val repository = SessionRepository(storage)
        repository.addSession(
            SavedSession(
                id = "kept",
                title = "Kept",
                raw = listOf(SessionMessage(role = Role.USER, content = "hi")),
                summaries = emptyList()
            )
        )
        repository.addSession(
            SavedSession(
                id = "empty",
                title = "Empty",
                raw = listOf(SessionMessage(role = Role.ASSISTANT, content = "hi")),
                summaries = emptyList()
            )
        )
        repository.addSession(
            SavedSession(id = "blank", title = "Blank", raw = emptyList(), summaries = emptyList())
        )

        assertEquals(setOf("empty", "blank"), repository.deleteEmptySessions())
        assertEquals(listOf("kept"), repository.sessions.first().map { it.id })
    }

    @Test
    fun `deleteEmptySessions leaves malformed json intact`() = runBlocking {
        val malformed = "[{ not valid json"
        val storage = FakeSessionStorage().apply { sessionsJsonFlow.value = malformed }
        val repository = SessionRepository(storage)

        assertEquals(emptySet<String>(), repository.deleteEmptySessions())
        assertEquals(malformed, storage.sessionsJsonFlow.value)
    }

    @Test
    fun `latestSession skips a newer session without user messages`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        repository.addSession(
            SavedSession(
                id = "real",
                title = "Real",
                createdAt = 1000,
                updatedAt = 1000,
                raw = listOf(SessionMessage(role = Role.USER, content = "hi")),
                summaries = emptyList()
            )
        )
        repository.addSession(
            SavedSession(
                id = "empty",
                title = "Empty",
                createdAt = 2000,
                updatedAt = 2000,
                raw = listOf(SessionMessage(role = Role.ASSISTANT, content = "hi")),
                summaries = emptyList()
            )
        )

        assertEquals("real", repository.latestSession()?.id)
    }
}