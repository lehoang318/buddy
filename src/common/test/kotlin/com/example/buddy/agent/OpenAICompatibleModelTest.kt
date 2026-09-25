package com.example.buddy.agent

import com.example.buddy.data.Role as BuddyRole
import com.example.buddy.data.Summary
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.LlmStreamEvent
import com.example.buddy.llm.LlmTool
import com.example.buddy.llm.LlmToolCall
import com.example.buddy.llm.RawSearchResponse
import com.example.buddy.llm.ReasoningEffort
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role as AdkRole
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Tool
import com.google.adk.kt.types.Type
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAICompatibleModelTest {

    private class CapturingClient : LlmClient {
        var lastMessages: List<LlmMessage> = emptyList()
        var lastTools: List<LlmTool>? = null
        var lastConfig: LlmGenerationConfig? = null
        var scripted: List<LlmStreamEvent> = emptyList()

        override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> =
            flow { emit("unused") }

        override fun streamEvents(
            messages: List<LlmMessage>,
            model: String,
            config: LlmGenerationConfig,
            tools: List<LlmTool>?
        ): Flow<LlmStreamEvent> = flow {
            lastMessages = messages
            lastTools = tools
            lastConfig = config
            scripted.forEach { emit(it) }
        }

        override suspend fun getModels(): List<LlmModel> = emptyList()
        override suspend fun testConnection(): Boolean = true
        override suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary>, correlationId: String?, imageBase64: String?): RawSearchResponse =
            RawSearchResponse(content = null, finishReason = null)
        override suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String?, imageBase64: String?): Summary =
            Summary(question = userQuestion, points = emptyList())
        override suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String?): Summary =
            Summary(question = "Earlier conversation", points = emptyList())

        override val defaultModel: String = "test-model"
        override var activeModel: String = "test-model"
        override val isReasoningSupported: Boolean = true
    }

    private fun request() = LlmRequest(
        contents = listOf(
            Content(role = AdkRole.USER, parts = listOf(Part(text = "hello"))),
            Content(
                role = AdkRole.MODEL,
                parts = listOf(
                    Part(functionCall = FunctionCall(name = "web_search", args = mapOf("queries" to listOf("kotlin")), id = "call_1"))
                )
            ),
            Content(
                role = AdkRole.USER,
                parts = listOf(
                    Part(functionResponse = FunctionResponse(name = "web_search", response = mapOf("results" to "R"), id = "call_1"))
                )
            )
        ),
        config = GenerateContentConfig(
            systemInstruction = Content(parts = listOf(Part(text = "be nice"))),
            temperature = 0.5f,
            tools = listOf(
                Tool(
                    functionDeclarations = listOf(
                        com.google.adk.kt.types.FunctionDeclaration(
                            name = "web_search",
                            description = "search the web",
                            parameters = Schema(type = Type.OBJECT)
                        )
                    )
                )
            )
        )
    )

    @Test
    fun convertsAdkRequestToOpenAiMessagesAndTools() = runBlocking {
        val client = CapturingClient()
        val model = OpenAICompatibleModel(client, "test-model")

        model.generateContent(request(), stream = true).toList()

        assertEquals("web_search", client.lastTools?.single()?.name)
        val messages = client.lastMessages
        assertEquals(BuddyRole.SYSTEM, messages[0].role)
        assertEquals("be nice", messages[0].content)
        assertEquals(BuddyRole.USER, messages[1].role)
        assertEquals("hello", messages[1].content)

        val assistant = messages[2]
        assertEquals(BuddyRole.ASSISTANT, assistant.role)
        assertEquals("web_search", assistant.toolCalls?.single()?.name)
        assertTrue(assistant.toolCalls?.single()?.arguments?.contains("kotlin") == true)

        val tool = messages[3]
        assertEquals(BuddyRole.TOOL, tool.role)
        assertEquals("call_1", tool.toolCallId)
        assertTrue(tool.content.contains("results"))
    }

    @Test
    fun mapsStreamingTextAndToolCallsToLlmResponses() = runBlocking {        val client = CapturingClient().apply {
            scripted = listOf(
                LlmStreamEvent.TextDelta("Hel"),
                LlmStreamEvent.TextDelta("lo"),
                LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c9", name = "web_search", arguments = "{}"))),
                LlmStreamEvent.Finished("tool_calls")
            )
        }
        val model = OpenAICompatibleModel(client, "test-model")

        val responses = model.generateContent(request(), stream = true).toList()

        val partialText = responses.filter { it.partial }.flatMap { it.content?.parts.orEmpty() }.mapNotNull { it.text }
        assertEquals(listOf("Hel", "lo"), partialText)

        val final = responses.last()
        assertEquals(false, final.partial)
        assertEquals("Hello", final.content?.parts?.firstOrNull { it.text != null }?.text)
        val call = final.content?.parts?.mapNotNull { it.functionCall }?.singleOrNull()
        assertNotNull(call)
        assertEquals("web_search", call?.name)
        assertEquals("c9", call?.id)
    }

    @Test
    fun passesReasoningEffortToClientConfig() = runBlocking {
        val client = CapturingClient().apply {
            scripted = listOf(LlmStreamEvent.TextDelta("hi"), LlmStreamEvent.Finished("stop"))
        }
        val model = OpenAICompatibleModel(client, "test-model", reasoningEffort = ReasoningEffort.DEEP)

        model.generateContent(request(), stream = true).toList()

        assertEquals(ReasoningEffort.DEEP, client.lastConfig?.reasoningEffort)
    }
}
