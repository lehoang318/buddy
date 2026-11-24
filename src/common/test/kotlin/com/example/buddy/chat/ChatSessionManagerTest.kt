package com.example.buddy.chat

import com.example.buddy.data.Role
import com.example.buddy.data.SavedSession
import com.example.buddy.data.SessionMessage
import com.example.buddy.data.SessionRepository
import com.example.buddy.data.SessionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionManagerTest {

    private class FakeSessionStorage : SessionStorage {
        val sessionsJsonFlow = MutableStateFlow("")
        val autoDeleteFlow = MutableStateFlow(false)
        override val sessionsJson: Flow<String> = sessionsJsonFlow
        override val autoDeleteOldSetting: Flow<Boolean> = autoDeleteFlow
        override suspend fun writeSessions(json: String) { sessionsJsonFlow.value = json }
        override suspend fun setAutoDeleteOld(enabled: Boolean) { autoDeleteFlow.value = enabled }
    }

    @Test
    fun `normal save stores the session and tracks its id and title`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        val manager = ChatSessionManager(repository)

        manager.save(listOf(SessionMessage(role = Role.USER, content = "first question")), emptyList())

        assertNotNull(manager.activeSessionId)
        assertEquals("first question", manager.activeSessionTitle)
        val stored = repository.sessions.first().single()
        assertEquals(manager.activeSessionId, stored.id)
        assertEquals("first question", stored.title)
    }

    @Test
    fun `empty messages are not saved without allowEmpty`() = runBlocking {
        val manager = ChatSessionManager(SessionRepository(FakeSessionStorage()))

        assertTrue(manager.save(emptyList(), emptyList()).isEmpty())
        assertNull(manager.activeSessionId)
        assertNull(manager.buildSession(emptyList(), emptyList()))
    }

    @Test
    fun `allowEmpty without an active session creates nothing`() {
        val manager = ChatSessionManager(SessionRepository(FakeSessionStorage()))

        assertNull(manager.buildSession(emptyList(), emptyList(), allowEmpty = true))
    }

    @Test
    fun `allowEmpty keeps the active session id and original title`() = runBlocking {
        val storage = FakeSessionStorage()
        val repository = SessionRepository(storage)
        val manager = ChatSessionManager(repository)
        manager.save(listOf(SessionMessage(role = Role.USER, content = "first question")), emptyList())
        val id = manager.activeSessionId

        manager.markDirty()
        manager.save(emptyList(), emptyList(), allowEmpty = true)

        val stored = repository.sessions.first().single()
        assertEquals(id, stored.id)
        assertEquals("first question", stored.title)
        assertEquals(listOf(id), repository.sessions.first().map { it.id })
    }

    @Test
    fun `bind restores id title and createdAt`() = runBlocking {
        val manager = ChatSessionManager(SessionRepository(FakeSessionStorage()))
        val session = SavedSession(
            id = "bound",
            title = "Bound title",
            createdAt = 42,
            raw = listOf(SessionMessage(role = Role.USER, content = "hi")),
            summaries = emptyList()
        )

        manager.bind(session)

        assertEquals("bound", manager.activeSessionId)
        assertEquals("Bound title", manager.activeSessionTitle)
        assertEquals(42L, manager.activeSessionCreatedAt)
    }

    @Test
    fun `deleteActive removes the stored session and resets state`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        val manager = ChatSessionManager(repository)
        manager.save(listOf(SessionMessage(role = Role.USER, content = "first question")), emptyList())
        val id = manager.activeSessionId

        assertEquals(id, manager.deleteActive())

        assertNull(manager.activeSessionId)
        assertNull(manager.activeSessionTitle)
        assertEquals(0L, manager.activeSessionCreatedAt)
        assertTrue(repository.sessions.first().isEmpty())
        assertNull(manager.deleteActive())
    }

    @Test
    fun `save after delete creates a new session`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        val manager = ChatSessionManager(repository)
        manager.save(listOf(SessionMessage(role = Role.USER, content = "first question")), emptyList())
        val firstId = manager.activeSessionId
        manager.deleteActive()

        manager.save(listOf(SessionMessage(role = Role.USER, content = "second question")), emptyList())

        assertNotEquals(firstId, manager.activeSessionId)
        assertEquals("second question", manager.activeSessionTitle)
        assertEquals(manager.activeSessionId, repository.sessions.first().single().id)
    }

    @Test
    fun `deleting the first exchange keeps the original session title`() = runBlocking {
        val repository = SessionRepository(FakeSessionStorage())
        val manager = ChatSessionManager(repository)
        val firstExchange = listOf(
            SessionMessage(role = Role.USER, content = "first question"),
            SessionMessage(role = Role.ASSISTANT, content = "first answer")
        )
        val secondExchange = listOf(
            SessionMessage(role = Role.USER, content = "second question"),
            SessionMessage(role = Role.ASSISTANT, content = "second answer")
        )
        manager.save(firstExchange + secondExchange, emptyList())
        val id = manager.activeSessionId

        manager.markDirty()
        manager.save(secondExchange, emptyList())

        val stored = repository.sessions.first().single()
        assertEquals(id, stored.id)
        assertEquals("first question", stored.title)
        assertEquals(secondExchange, stored.raw)
    }
}
