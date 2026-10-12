package com.example.buddy.llm

import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningDeltaParsingTest {

    @Test
    fun `reads reasoning content field`() {
        val delta = JsonObject().apply { addProperty("reasoning_content", "thinking") }
        assertEquals("thinking", reasoningTextFromDelta(delta))
    }

    @Test
    fun `reads openrouter reasoning field`() {
        val delta = JsonObject().apply { addProperty("reasoning", "thinking") }
        assertEquals("thinking", reasoningTextFromDelta(delta))
    }

    @Test
    fun `prefers reasoning content when both present`() {
        val delta = JsonObject().apply {
            addProperty("reasoning_content", "first")
            addProperty("reasoning", "second")
        }
        assertEquals("first", reasoningTextFromDelta(delta))
    }

    @Test
    fun `returns null when absent`() {
        assertNull(reasoningTextFromDelta(JsonObject()))
        assertNull(reasoningTextFromDelta(JsonObject().apply { addProperty("content", "answer") }))
    }

    @Test
    fun `ignores blank and non-string values`() {
        assertNull(reasoningTextFromDelta(JsonObject().apply { addProperty("reasoning_content", "") }))
        assertNull(reasoningTextFromDelta(JsonObject().apply { addProperty("reasoning_content", 42) }))
        assertNull(reasoningTextFromDelta(JsonObject().apply { addProperty("reasoning_content", true) }))
    }
}
