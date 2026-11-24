package com.example.buddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnIdentityTest {

    @Test
    fun `user and assistant pairs share a turn id and the greeting stays null`() {
        val messages = listOf(
            SessionMessage(Role.ASSISTANT, "greeting"),
            SessionMessage(Role.USER, "q1"),
            SessionMessage(Role.ASSISTANT, "a1")
        )

        val result = assignTurnIds(messages)

        assertNull(result[0].turnId)
        assertNotNull(result[1].turnId)
        assertEquals(result[1].turnId, result[2].turnId)
    }

    @Test
    fun `existing turn ids are preserved`() {
        val messages = listOf(
            SessionMessage(Role.USER, "q1", turnId = "keep"),
            SessionMessage(Role.ASSISTANT, "a1", turnId = "keep")
        )

        assertEquals(listOf("keep", "keep"), assignTurnIds(messages).map { it.turnId })
    }

    @Test
    fun `consecutive user messages get distinct ids and the assistant inherits the last`() {
        val messages = listOf(
            SessionMessage(Role.USER, "q1"),
            SessionMessage(Role.USER, "q2"),
            SessionMessage(Role.ASSISTANT, "a2")
        )

        val result = assignTurnIds(messages)

        assertNotNull(result[0].turnId)
        assertNotNull(result[1].turnId)
        assertTrue(result[0].turnId != result[1].turnId)
        assertEquals(result[1].turnId, result[2].turnId)
    }

    @Test
    fun `other roles are left unchanged`() {
        val messages = listOf(
            SessionMessage(Role.SYSTEM, "sys"),
            SessionMessage(Role.TOOL, "tool")
        )

        assertEquals(messages, assignTurnIds(messages))
    }

    @Test
    fun `summaries link to matching user turns without reusing a turn`() {
        val messages = listOf(
            SessionMessage(Role.USER, "same", turnId = "t1"),
            SessionMessage(Role.ASSISTANT, "a1", turnId = "t1"),
            SessionMessage(Role.USER, "same", turnId = "t2"),
            SessionMessage(Role.ASSISTANT, "a2", turnId = "t2"),
            SessionMessage(Role.USER, "other", turnId = "t3")
        )
        val summaries = listOf(
            Summary("same", listOf(SummaryPoint("p"))),
            Summary("same", listOf(SummaryPoint("p"))),
            Summary("missing", listOf(SummaryPoint("p"))),
            Summary("other", listOf(SummaryPoint("p")), turnIds = listOf("existing"))
        )

        val linked = linkSummariesToTurns(messages, summaries)

        assertEquals(listOf("t1"), linked[0].turnIds)
        assertEquals(listOf("t2"), linked[1].turnIds)
        assertTrue(linked[2].turnIds.isEmpty())
        assertEquals(listOf("existing"), linked[3].turnIds)
    }

    @Test
    fun `user messages without a turn id are not linked`() {
        val messages = listOf(SessionMessage(Role.USER, "q1"))
        val summaries = listOf(Summary("q1", listOf(SummaryPoint("p"))))

        assertTrue(linkSummariesToTurns(messages, summaries).single().turnIds.isEmpty())
    }
}
