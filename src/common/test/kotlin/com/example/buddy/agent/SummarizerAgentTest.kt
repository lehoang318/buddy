package com.example.buddy.agent

import com.example.buddy.data.Summary
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.LlmStreamEvent
import com.example.buddy.llm.LlmTool
import com.example.buddy.llm.RawSearchResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SummarizerAgentTest {

    private class JsonClient(private val json: String) : LlmClient {
        var directSummaryCalls = 0

        override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> =
            flow { emit(json) }

        override fun streamEvents(
            messages: List<LlmMessage>,
            model: String,
            config: LlmGenerationConfig,
            tools: List<LlmTool>?
        ): Flow<LlmStreamEvent> = flow {
            emit(LlmStreamEvent.TextDelta(json))
            emit(LlmStreamEvent.Finished("stop"))
        }

        override suspend fun getModels(): List<LlmModel> = emptyList()
        override suspend fun testConnection(): Boolean = true
        override suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary>, correlationId: String?, imageBase64: String?): RawSearchResponse =
            RawSearchResponse(content = null, finishReason = null)
        override suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String?, imageBase64: String?): Summary {
            directSummaryCalls++
            return Summary(question = userQuestion, points = emptyList())
        }
        override suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String?): Summary =
            Summary(question = "Earlier conversation", points = emptyList())

        override val defaultModel: String = "test-model"
        override var activeModel: String = "test-model"
        override val isReasoningSupported: Boolean = true
    }

    @Test
    fun summarizesThroughAgentWithoutFallingBack() = runBlocking {
        val client = JsonClient("""{"points":[{"text":"Agent fact","key":false}],"tags":[]}""")

        val summary = SummarizerAgent(client).summarize("q", "a", imageBase64 = null)

        assertEquals(1, summary.points.size)
        assertEquals("Agent fact", summary.points.single().text)
        assertFalse("Should not fall back to the direct client call", client.directSummaryCalls > 0)
    }
}
