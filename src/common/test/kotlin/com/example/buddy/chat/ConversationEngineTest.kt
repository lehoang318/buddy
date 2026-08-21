package com.example.buddy.chat

import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.fetch.FetchedUrl
import com.example.buddy.fetch.UrlFetchResult
import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.ReasoningEffort
import com.example.buddy.search.SearchResponse
import com.example.buddy.search.SearchRecency
import com.example.buddy.search.SearchResult
import com.example.buddy.search.WebSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineTest {
    @Test
    fun `engine streams response and records summary without network`() = runBlocking {
        val engine = ConversationEngine(FakeLlmClient())

        val events = engine.send("What is Buddy?", correlationId = "offline-test").toList()

        assertTrue(events.any { it is ConversationEvent.UserMessageAccepted })
        assertEquals("A helpful assistant.", events.filterIsInstance<ConversationEvent.Token>().joinToString("") { it.text })
        assertEquals(1, engine.summaries.value.size)
        assertTrue(events.last() is ConversationEvent.Completed)
    }

    @Test
    fun `message builder includes attachment and web data`() {
        val messages = MessageBuilder.build(
            history = listOf(
                ConversationMessage(Role.USER, "Read this", attachment = TextAttachment("notes.txt", "important"))
            ),
            searchResults = listOf(com.example.buddy.search.SearchResult("Title", "https://example.test", "Content")),
            searchAnswer = "Summary",
            fetchedUrls = listOf(com.example.buddy.fetch.FetchedUrl("https://example.test/page", "Fetched"))
        )

        assertTrue(messages.first().content.contains("## Web Data"))
        assertTrue(messages.first().content.contains("Fetched"))
        assertTrue(messages.last().content.contains("[File: notes.txt]\nimportant"))
    }

    @Test
    fun `engine orchestrates url fetching and web search offline`() = runBlocking {
        val engine = ConversationEngine(
            client = FakeLlmClient("""{"queries":["Buddy"],"recency":"any"}"""),
            webSearch = FakeWebSearch(),
            urlFetcher = FakeUrlFetcher()
        )

        val events = engine.send("Read https://example.test and search Buddy", correlationId = "offline-test").toList()

        assertTrue(events.any { it is ConversationEvent.UrlFetchFinished })
        assertTrue(events.filterIsInstance<ConversationEvent.SearchFinished>().single().outcome.rawResults.isNotEmpty())
    }

    private class FakeLlmClient(private val searchPlan: String? = null) : LlmClient {
        override val defaultModel: String = "offline-model"
        override var activeModel: String = defaultModel
        override val isReasoningSupported: Boolean = false

        override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> =
            flowOf("A helpful ", "assistant.")

        override suspend fun getModels(): List<LlmModel> = emptyList()

        override suspend fun testConnection(): Boolean = true

        override suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary>, correlationId: String?): String? = searchPlan

        override suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String?): Summary =
            Summary(userQuestion, listOf(SummaryPoint(assistantResponse)))

        override suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String?): Summary =
            Summary("Compressed", listOf(SummaryPoint(summariesToCompress.joinToString { it.question })))

        override fun toggleReasoning(current: ReasoningEffort?): ReasoningEffort = ReasoningEffort.HIGH
    }

    private class FakeUrlFetcher : UrlFetcher {
        override suspend fun fetchTextContent(url: String): String = "Fetched content"

        override suspend fun testConnection(): Boolean = true

        override suspend fun fetchAll(urls: List<String>, correlationId: String?): UrlFetchResult =
            UrlFetchResult(urls.map { FetchedUrl(it, "Fetched content") })
    }

    private class FakeWebSearch : WebSearch {
        override suspend fun search(query: String, recency: SearchRecency): SearchResponse =
            SearchResponse(listOf(SearchResult("Buddy", "https://example.test", "Search content")))

        override fun isAvailable(): Boolean = true
    }
}
