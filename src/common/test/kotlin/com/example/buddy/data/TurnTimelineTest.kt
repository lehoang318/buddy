package com.example.buddy.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnTimelineTest {

    @Test
    fun `sanitizeTurnEvents returns empty for a null list`() {
        assertEquals(emptyList<TurnEvent>(), sanitizeTurnEvents(null))
    }

    @Test
    fun `sanitizeTurnEvents keeps valid and drops null elements`() {
        val valid = TurnEvent(TurnEventKind.THOUGHTS, text = "ok")
        assertEquals(listOf(valid), sanitizeTurnEvents(listOf(valid, null)))
    }

    @Test
    fun `sanitizeTurnEvents normalizes null text and queries`() {
        val malformed = Gson().fromJson("""{"kind":"THOUGHTS"}""", TurnEvent::class.java)
        assertEquals(
            listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "", queries = emptyList())),
            sanitizeTurnEvents(listOf(malformed))
        )
    }


    @Test
    fun `appendThoughtsEvent starts a new event on empty history`() {
        assertEquals(
            listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "first")),
            appendThoughtsEvent(emptyList(), "first")
        )
    }

    @Test
    fun `appendThoughtsEvent coalesces consecutive thoughts`() {
        val start = listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "a"))
        assertEquals(
            listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "ab")),
            appendThoughtsEvent(start, "b")
        )
    }

    @Test
    fun `appendThoughtsEvent starts a new thoughts event after a non-thoughts event`() {
        val withSearch = listOf(
            TurnEvent(TurnEventKind.THOUGHTS, text = "a"),
            TurnEvent(TurnEventKind.SEARCH, queries = listOf("q"))
        )
        assertEquals(
            listOf(
                TurnEvent(TurnEventKind.THOUGHTS, text = "a"),
                TurnEvent(TurnEventKind.SEARCH, queries = listOf("q")),
                TurnEvent(TurnEventKind.THOUGHTS, text = "b")
            ),
            appendThoughtsEvent(withSearch, "b")
        )
    }

    @Test
    fun `appendThoughtsEvent inserts separator between coalesced thoughts`() {
        val start = listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "a"))
        assertEquals(
            listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "a\n\nb")),
            appendThoughtsEvent(start, "b", separator = "\n\n")
        )
    }

    @Test
    fun `appendThoughtsEvent does not prepend separator to a new event`() {
        assertEquals(
            listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "b")),
            appendThoughtsEvent(emptyList(), "b", separator = "\n\n")
        )
    }

    @Test
    fun `appendThoughtsEvent ignores empty text`() {
        val events = listOf(TurnEvent(TurnEventKind.THOUGHTS, text = "a"))
        assertEquals(events, appendThoughtsEvent(events, ""))
    }

    @Test
    fun `reconstructTurnEvents orders search question answer`() {
        assertEquals(
            listOf(
                TurnEvent(TurnEventKind.SEARCH, queries = listOf("a", "b")),
                TurnEvent(TurnEventKind.QUESTION, text = "Which one?"),
                TurnEvent(TurnEventKind.ANSWER, text = "This one")
            ),
            reconstructTurnEvents(
                webSearchUsed = true,
                webSearchSkipped = false,
                webSearchQueries = listOf("a", "b"),
                questionAsked = "Which one?",
                questionAnswer = "This one"
            )
        )
    }

    @Test
    fun `reconstructTurnEvents yields skipped-only search`() {
        assertEquals(
            listOf(TurnEvent(TurnEventKind.SEARCH, skipped = true)),
            reconstructTurnEvents(
                webSearchUsed = false,
                webSearchSkipped = true,
                webSearchQueries = emptyList(),
                questionAsked = null,
                questionAnswer = null
            )
        )
    }

    @Test
    fun `reconstructTurnEvents empty for a plain message`() {
        assertEquals(
            emptyList<TurnEvent>(),
            reconstructTurnEvents(false, false, emptyList(), null, null)
        )
    }
}
