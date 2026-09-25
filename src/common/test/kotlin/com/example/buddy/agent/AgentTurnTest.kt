package com.example.buddy.agent

import com.example.buddy.chat.ConversationMessage
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.LlmStreamEvent
import com.example.buddy.llm.LlmTool
import com.example.buddy.llm.LlmToolCall
import com.example.buddy.llm.RawSearchResponse
import com.example.buddy.search.SearchRecency
import com.example.buddy.search.SearchResponse
import com.example.buddy.search.SearchResult
import com.example.buddy.search.WebSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTurnTest {

    private class ScriptedClient(private val scripts: List<List<LlmStreamEvent>>) : LlmClient {
        var calls = 0
        val toolsByCall = mutableListOf<List<LlmTool>?>()
        val messagesByCall = mutableListOf<List<LlmMessage>>()

        override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> =
            flow { emit("unused") }

        override fun streamEvents(
            messages: List<LlmMessage>,
            model: String,
            config: LlmGenerationConfig,
            tools: List<LlmTool>?
        ): Flow<LlmStreamEvent> = flow {
            toolsByCall += tools
            messagesByCall += messages
            val script = scripts.getOrElse(calls) { emptyList() }
            calls++
            script.forEach { emit(it) }
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

    private class FakeWebSearch : WebSearch {
        override suspend fun search(query: String, recency: SearchRecency): SearchResponse =
            SearchResponse(results = listOf(SearchResult(title = "Result $query", url = "https://example.com/$query", content = "content for $query")))
        override fun isAvailable(): Boolean = true
    }

    @Test
    fun runsToolLoopThenFinalAnswer() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c1", name = "web_search", arguments = "{\"queries\":[\"kotlin\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.TextDelta("The answer"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        val started = events.filterIsInstance<AgentTurnEvent.ToolCallStarted>()
        assertEquals("web_search", started.single().name)
        assertTrue(started.single().args.containsKey("queries"))

        val finished = events.filterIsInstance<AgentTurnEvent.ToolCallFinished>()
        assertEquals("web_search", finished.single().name)
        assertTrue((finished.single().result["results"] as? String)?.contains("content for kotlin") == true)

        assertEquals("The answer", (events.last() as AgentTurnEvent.Completed).fullText)
        assertEquals(2, client.calls)
        assertEquals(listOf("web_search", "ask_user"), client.toolsByCall.first()?.map { it.name })
    }

    @Test
    fun omitsWebSearchToolWhenDisabled() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(LlmStreamEvent.TextDelta("No tools"), LlmStreamEvent.Finished("stop"))
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = false).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        assertEquals("No tools", (events.last() as AgentTurnEvent.Completed).fullText)
        assertEquals(listOf("ask_user"), client.toolsByCall.first()?.map { it.name })
    }

    @Test
    fun enforcesSearchRoundCap() = runBlocking {
        var searchCalls = 0
        val countingSearch = object : WebSearch {
            override suspend fun search(query: String, recency: SearchRecency): SearchResponse {
                searchCalls++
                return SearchResponse(results = listOf(SearchResult(title = query, url = "https://example.com/$query", content = "content for $query")))
            }
            override fun isAvailable(): Boolean = true
        }
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c1", name = "web_search", arguments = "{\"queries\":[\"one\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c2", name = "web_search", arguments = "{\"queries\":[\"two\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c3", name = "web_search", arguments = "{\"queries\":[\"three\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(LlmStreamEvent.TextDelta("Done"), LlmStreamEvent.Finished("stop"))
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, countingSearch, urlFetcher = null, webSearchEnabled = true, searchRoundCap = 2).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        val finished = events.filterIsInstance<AgentTurnEvent.ToolCallFinished>()
        assertEquals(3, finished.size)
        assertTrue((finished[2].result["error"] as? String)?.contains("round cap") == true)
        assertEquals(2, searchCalls)
    }

    @Test
    fun includesSearchCapHintInInstruction() = runBlocking {
        val client = ScriptedClient(
            listOf(listOf(LlmStreamEvent.TextDelta("No tools"), LlmStreamEvent.Finished("stop")))
        )

        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true, searchRoundCap = 2).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { }

        val instruction = client.messagesByCall.first().first().content
        assertTrue(instruction.contains("at most 2 web_search calls"))
    }

    @Test
    fun waitsForUserAnswerThenFinishes() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.ToolCalls(
                        listOf(
                            LlmToolCall(
                                id = "c1",
                                name = "ask_user",
                                arguments = "{\"question\":\"Which city?\",\"options\":[\"Paris\",\"Rome\"]}"
                            )
                        )
                    ),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.TextDelta("Paris it is"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )
        val bridge = QuestionBridge()
        val events = mutableListOf<AgentTurnEvent>()

        AgentTurn(client, webSearch = null, urlFetcher = null, webSearchEnabled = false, questionBridge = bridge).run(
            userMessage = "book me a trip",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "book me a trip")),
            summaries = emptyList()
        ).collect { event ->
            events += event
            if (event is AgentTurnEvent.ToolCallStarted && event.name == "ask_user") {
                assertTrue(bridge.send("Paris"))
            }
        }

        val started = events.filterIsInstance<AgentTurnEvent.ToolCallStarted>().single()
        assertEquals("ask_user", started.name)
        assertEquals("Which city?", started.args["question"])

        val finished = events.filterIsInstance<AgentTurnEvent.ToolCallFinished>().single()
        assertEquals("ask_user", finished.name)
        assertEquals("Paris", finished.result["answer"])

        assertEquals("Paris it is", (events.last() as AgentTurnEvent.Completed).fullText)
        assertEquals(2, client.calls)
    }

    @Test
    fun replayIncludesClarifyingQuestionAndAnswer() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(LlmStreamEvent.TextDelta("Fresh answer"), LlmStreamEvent.Finished("stop"))
            )
        )
        val history = listOf(
            ConversationMessage(Role.USER, "book me a trip"),
            ConversationMessage(Role.USER, "Paris"),
            ConversationMessage(Role.ASSISTANT, "Paris it is", questionAsked = "Which city?")
        )

        AgentTurn(client, webSearch = null, urlFetcher = null, webSearchEnabled = false).run(
            userMessage = "and hotels?",
            imageBase64 = null,
            history = history,
            summaries = emptyList()
        ).collect { }

        val system = client.messagesByCall.first().joinToString("\n") { it.content }
        assertTrue(system.contains("User: book me a trip"))
        assertTrue(system.contains("Assistant asked: \"Which city?\""))
        assertTrue(system.contains("User answered: \"Paris\""))
        assertTrue(system.contains("Assistant: Paris it is"))
    }

    @Test
    fun blankAnswerUsesSkipSentinel() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c1", name = "ask_user", arguments = "{\"question\":\"Which city?\"}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.TextDelta("I'll pick for you"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )
        val bridge = QuestionBridge()
        val events = mutableListOf<AgentTurnEvent>()

        AgentTurn(client, webSearch = null, urlFetcher = null, webSearchEnabled = false, questionBridge = bridge).run(
            userMessage = "book me a trip",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "book me a trip")),
            summaries = emptyList()
        ).collect { event ->
            events += event
            if (event is AgentTurnEvent.ToolCallStarted && event.name == "ask_user") {
                bridge.send("")
            }
        }

        val finished = events.filterIsInstance<AgentTurnEvent.ToolCallFinished>().single()
        assertTrue((finished.result["answer"] as? String)?.contains("did not provide") == true)
        assertEquals("I'll pick for you", (events.last() as AgentTurnEvent.Completed).fullText)
    }

    @Test
    fun retractsPreambleBeforeToolCall() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.TextDelta("I will search for that"),
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c1", name = "web_search", arguments = "{\"queries\":[\"kotlin\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.TextDelta("# The answer"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        assertEquals(
            listOf("I will search for that", "# The answer"),
            events.filterIsInstance<AgentTurnEvent.Text>().map { it.text }
        )

        val reset = events.filterIsInstance<AgentTurnEvent.AnswerReset>().single()
        assertEquals("I will search for that", reset.retractedText)
        assertTrue(events.indexOf(reset) < events.indexOf(events.filterIsInstance<AgentTurnEvent.ToolCallStarted>().single()))

        assertEquals("# The answer", (events.last() as AgentTurnEvent.Completed).fullText)
    }

    @Test
    fun retractsOnEveryToolRound() = runBlocking {
        val searchCall = LlmStreamEvent.ToolCalls(
            listOf(LlmToolCall(id = "c1", name = "web_search", arguments = "{\"queries\":[\"kotlin\"]}"))
        )
        val client = ScriptedClient(
            listOf(
                listOf(LlmStreamEvent.TextDelta("first thought"), searchCall, LlmStreamEvent.Finished("tool_calls")),
                listOf(LlmStreamEvent.TextDelta("second thought"), searchCall, LlmStreamEvent.Finished("tool_calls")),
                listOf(LlmStreamEvent.TextDelta("final answer"), LlmStreamEvent.Finished("stop"))
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        assertEquals(
            listOf("first thought", "second thought"),
            events.filterIsInstance<AgentTurnEvent.AnswerReset>().map { it.retractedText }
        )
        assertEquals(2, events.filterIsInstance<AgentTurnEvent.ToolCallStarted>().size)
        assertEquals("final answer", (events.last() as AgentTurnEvent.Completed).fullText)
    }

    @Test
    fun capturesReasoningDeltasAsThoughts() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.ReasoningDelta("Let me check "),
                    LlmStreamEvent.ReasoningDelta("current facts"),
                    LlmStreamEvent.ToolCalls(listOf(LlmToolCall(id = "c1", name = "web_search", arguments = "{\"queries\":[\"kotlin\"]}"))),
                    LlmStreamEvent.Finished("tool_calls")
                ),
                listOf(
                    LlmStreamEvent.TextDelta("The answer"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        assertEquals(
            "Let me check current facts",
            events.filterIsInstance<AgentTurnEvent.ThoughtsDelta>().joinToString("") { it.text }
        )
        assertEquals(listOf("The answer"), events.filterIsInstance<AgentTurnEvent.Text>().map { it.text })
        assertTrue(events.filterIsInstance<AgentTurnEvent.AnswerReset>().isEmpty())
        assertEquals("The answer", (events.last() as AgentTurnEvent.Completed).fullText)
    }

    @Test
    fun splitsInlineThinkTagsIntoThoughts() = runBlocking {
        val client = ScriptedClient(
            listOf(
                listOf(
                    LlmStreamEvent.TextDelta("<thi"),
                    LlmStreamEvent.TextDelta("nk>reasoning "),
                    LlmStreamEvent.TextDelta("here</thi"),
                    LlmStreamEvent.TextDelta("nk>Final answer"),
                    LlmStreamEvent.Finished("stop")
                )
            )
        )

        val events = mutableListOf<AgentTurnEvent>()
        AgentTurn(client, FakeWebSearch(), urlFetcher = null, webSearchEnabled = true).run(
            userMessage = "hello",
            imageBase64 = null,
            history = listOf(ConversationMessage(Role.USER, "hello")),
            summaries = emptyList()
        ).collect { events += it }

        assertEquals(
            "reasoning here",
            events.filterIsInstance<AgentTurnEvent.ThoughtsDelta>().joinToString("") { it.text }
        )
        assertEquals("Final answer", events.filterIsInstance<AgentTurnEvent.Text>().joinToString("") { it.text })
        assertEquals("Final answer", (events.last() as AgentTurnEvent.Completed).fullText)
    }
}
