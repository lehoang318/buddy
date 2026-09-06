package com.example.buddy.chat

import com.example.buddy.config.AppConfig
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.config.LlmConfig
import com.example.buddy.config.ResourceAppConfig
import com.example.buddy.config.ResourceValuesLoader
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.llm.LlmMessage
import com.example.buddy.search.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBuilderCapTest {
    private val resourceConfig = ResourceAppConfig(ResourceValuesLoader.loadFromClasspath())

    private class TinyLlmConfig(maxRequestChars: Int) : LlmConfig {
        override val temperature: Float = 0f
        override val topP: Float = 0f
        override val topK: Int = 0
        override val maxTokens: Int = 100
        override val responseLimitMultiplier: Int = 2
        override val minResponseTokens: Int = 16
        override val maxRequestChars: Int = maxRequestChars
        override val defaultSystemMessage: String = "System"
    }

    private class TestingAppConfig(delegate: AppConfig, maxRequestChars: Int) : AppConfig by delegate {
        override val llm: LlmConfig = TinyLlmConfig(maxRequestChars)
    }

    private fun <T> withConfig(maxRequestChars: Int, block: () -> T): T {
        val previous = AppConfigProvider.current
        try {
            AppConfigProvider.current = TestingAppConfig(resourceConfig, maxRequestChars)
            return block()
        } finally {
            AppConfigProvider.current = previous
        }
    }

    private fun history(): List<ConversationMessage> = listOf(
        ConversationMessage(Role.USER, "p"),
        ConversationMessage(Role.ASSISTANT, "p"),
        ConversationMessage(Role.USER, "Final question text")
    )

    private fun build(
        cap: Int,
        outputLimit: Int? = null,
        summaries: List<Summary> = listOf(Summary("q", listOf(SummaryPoint("text")))),
        searchResults: List<SearchResult> = emptyList(),
        searchAnswer: String? = null
    ): List<LlmMessage> = withConfig(cap) {
        MessageBuilder.build(
            history = history(),
            summaries = summaries,
            searchResults = searchResults,
            searchAnswer = searchAnswer,
            outputLimit = outputLimit
        )
    }

    @Test
    fun `request fits within character cap when under budget`() = withConfig(500) {
        val messages = build(500)
        assertTrue(messages.sumOf { it.content.length } <= 500)
        assertEquals("Final question text", messages.last().content)
        assertTrue(messages.first().content.contains("## Memory"))
    }

    @Test
    fun `trims web data before memory pairs and current user`() = withConfig(300) {
        val messages = build(
            300,
            summaries = listOf(Summary("q", listOf(SummaryPoint("text")))),
            searchResults = (1..3).map { SearchResult("t$it", "https://example.test/$it", "c".repeat(400)) },
            searchAnswer = "A".repeat(600)
        )
        assertTrue(messages.sumOf { it.content.length } <= 300)
        val systemContent = messages.first().content
        assertTrue(systemContent.contains("## Memory"))
        assertFalse(systemContent.contains("## Web Data"))
        assertEquals("Final question text", messages.last().content)
    }

    @Test
    fun `drops memory but keeps pairs and current user when budget tightens`() = withConfig(160) {
        val messages = build(160)
        assertTrue(messages.sumOf { it.content.length } <= 160)
        val systemContent = messages.first().content
        assertFalse(systemContent.contains("## Memory"))
        assertTrue(messages.any { it.role == Role.USER && it.content == "p" })
        assertEquals("Final question text", messages.last().content)
    }

    @Test
    fun `drops pairs and truncates current user when budget is exhausted`() = withConfig(140) {
        val messages = build(140)
        assertTrue(messages.sumOf { it.content.length } <= 140)
        val systemContent = messages.first().content
        assertFalse(systemContent.contains("## Memory"))
        assertFalse(messages.any { it.role == Role.USER && it.content == "p" })
        assertTrue(messages.last().role == Role.USER)
        assertTrue(messages.last().content.startsWith("Final"))
    }

    @Test
    fun `output limit text reflects passed value`() = withConfig(500) {
        assertTrue(build(500, outputLimit = 42).first().content.contains("may not exceed 42 tokens"))
        assertTrue(build(500).first().content.contains("may not exceed 100 tokens"))
    }
}