package com.example.buddy.chat

import com.example.buddy.config.AppConfig
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.config.SummariesConfig
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmModel
import com.example.buddy.llm.RawSearchResponse
import com.example.buddy.llm.ReasoningEffort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationEngineTurnTest {

    private val image = "data:image/jpeg;base64,abc"

    private class SmallSummariesConfig(base: SummariesConfig, override val maxSummaries: Int) : SummariesConfig by base

    private class TestConfig(base: AppConfig, override val summaries: SummariesConfig) : AppConfig by base

    private class RecordingClient(private val failOn: String? = null) : LlmClient {
        val requests = mutableListOf<List<LlmMessage>>()

        override val defaultModel: String = "offline-model"
        override var activeModel: String = defaultModel
        override val isReasoningSupported: Boolean = false

        override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> {
            requests += messages
            val last = messages.lastOrNull()?.content.orEmpty()
            if (last.contains("Merge the summaries")) return flowOf("""{"points":[{"text":"merged","key":false}]}""")
            if (failOn != null && last.contains(failOn)) return flow { throw IllegalStateException("offline failure") }
            return flowOf("answer to $last")
        }

        override suspend fun getModels(): List<LlmModel> = emptyList()

        override suspend fun testConnection(): Boolean = true

        override suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary>, correlationId: String?, imageBase64: String?): RawSearchResponse =
            RawSearchResponse(null, null)

        override suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String?, imageBase64: String?): Summary =
            Summary(
                question = userQuestion,
                points = listOf(SummaryPoint("point $userQuestion", key = userQuestion == "q0")),
                tags = if (userQuestion == "q0") listOf("Science") else listOf("World")
            )

        override suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String?): Summary =
            Summary("Earlier conversation", listOf(SummaryPoint("merged")))

        override fun toggleReasoning(current: ReasoningEffort?, webSearchEnabled: Boolean, agenticMode: Boolean): ReasoningEffort =
            ReasoningEffort.HIGH
    }

    @Test
    fun `summaries carry the send correlation id as their turn id`() = runBlocking {
        val engine = ConversationEngine(RecordingClient())

        engine.send("q1", imageBase64 = image, correlationId = "turn-a").toList()
        engine.send("q2", imageBase64 = image, correlationId = "turn-b").toList()

        assertEquals(listOf(listOf("turn-a"), listOf("turn-b")), engine.summaries.value.map { it.turnIds })
    }

    @Test
    fun `blank turn id leaves the summaries alone`() = runBlocking {
        val engine = ConversationEngine(RecordingClient())
        engine.send("q1", imageBase64 = image, correlationId = "turn-a").toList()

        assertEquals(engine.summaries.value, engine.removeTurn("  "))
        assertEquals(1, engine.summaries.value.size)
    }

    @Test
    fun `removeTurn drops the summary and keeps it out of the next request`() = runBlocking {
        val client = RecordingClient()
        val engine = ConversationEngine(client)
        engine.send("secret question", imageBase64 = image, correlationId = "turn-1").toList()
        assertEquals(listOf("point secret question"), engine.summaries.value.single().points.map { it.text })

        val removed = engine.removeTurn("turn-1")

        assertTrue(removed.isEmpty())
        assertTrue(engine.summaries.value.isEmpty())

        client.requests.clear()
        engine.send("second question", imageBase64 = image, correlationId = "turn-2").toList()

        val request = client.requests.single { it.last().role == Role.USER && it.last().content == "second question" }
        assertTrue(request.none { it.content.contains("secret question") })
        assertTrue(request.none { it.content.contains("point secret question") })
        assertEquals(listOf("turn-2"), engine.summaries.value.single().turnIds)
    }

    @Test
    fun `a failed turn with only a user history entry can be removed`() = runBlocking {
        val client = RecordingClient(failOn = "broken question")
        val engine = ConversationEngine(client)

        val events = engine.send("broken question", imageBase64 = image, correlationId = "turn-fail").toList()

        assertTrue(events.last() is ConversationEvent.Failed)
        assertTrue(engine.summaries.value.isEmpty())
        assertTrue(engine.removeTurn("turn-fail").isEmpty())

        engine.send("healthy question", imageBase64 = image, correlationId = "turn-ok").toList()

        val request = client.requests.single { it.last().role == Role.USER && it.last().content == "healthy question" }
        assertTrue(request.none { it.content.contains("broken question") })
        assertEquals(listOf("turn-ok"), engine.summaries.value.single().turnIds)
    }

    @Test
    fun `compression keeps flat provenance and removal rebuilds or drops it`() = runBlocking {
        val original = AppConfigProvider.current
        val engine = ConversationEngine(RecordingClient())
        try {
            AppConfigProvider.current = TestConfig(original, SmallSummariesConfig(original.summaries, 4))
            repeat(5) { index ->
                engine.send("q$index", imageBase64 = image, correlationId = "turn$index").toList()
            }

            val summaries = engine.summaries.value
            assertEquals(4, summaries.size)
            val compressed = summaries.first()
            assertEquals("Earlier conversation", compressed.question)
            assertEquals(listOf("turn0", "turn1"), compressed.turnIds)
            assertEquals(listOf("Science", "World"), compressed.tags)
            assertEquals(listOf("point q0", "merged"), compressed.points.map { it.text })
            assertEquals(2, compressed.sources.size)
            assertTrue(compressed.sources.all { it.turnIds.size == 1 })
            assertEquals(listOf("q2", "q3", "q4"), summaries.drop(1).map { it.question })

            val rebuilt = engine.removeTurn("turn0")

            assertEquals(4, rebuilt.size)
            val rebuiltCompressed = rebuilt.first()
            assertEquals("Earlier conversation", rebuiltCompressed.question)
            assertEquals(listOf("point q1"), rebuiltCompressed.points.map { it.text })
            assertEquals(listOf("World"), rebuiltCompressed.tags)
            assertEquals(listOf("turn1"), rebuiltCompressed.turnIds)
            assertEquals(1, rebuiltCompressed.sources.size)

            val dropped = engine.removeTurn("turn1")

            assertEquals(listOf("q2", "q3", "q4"), dropped.map { it.question })
        } finally {
            AppConfigProvider.current = original
        }
    }

    @Test
    fun `removing an uncovered turn leaves compressed provenance intact`() = runBlocking {
        val original = AppConfigProvider.current
        val engine = ConversationEngine(RecordingClient())
        try {
            AppConfigProvider.current = TestConfig(original, SmallSummariesConfig(original.summaries, 4))
            repeat(5) { index ->
                engine.send("q$index", imageBase64 = image, correlationId = "turn$index").toList()
            }
            val before = engine.summaries.value

            val after = engine.removeTurn("turn3")

            assertEquals(before.first(), after.first())
            assertEquals(listOf("q2", "q4"), after.drop(1).map { it.question })
        } finally {
            AppConfigProvider.current = original
        }
    }

    @Test
    fun `turn id is carried on engine history after a send`() = runBlocking {
        val engine = ConversationEngine(RecordingClient())
        val events = engine.send("q1", imageBase64 = image, correlationId = "turn-a").toList()

        val accepted = events.filterIsInstance<ConversationEvent.UserMessageAccepted>().single().message
        assertNotNull(accepted.turnId)
        assertEquals("turn-a", accepted.turnId)
    }

    @Test
    fun `removeTurn is idempotent and leaves the engine usable`() = runBlocking {
        val engine = ConversationEngine(RecordingClient())
        engine.send("q0", imageBase64 = image, correlationId = "turn-1").toList()
        assertEquals(1, engine.summaries.value.size)

        val first = engine.removeTurn("turn-1")
        val second = engine.removeTurn("turn-1")

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertTrue(engine.summaries.value.isEmpty())

        engine.send("q1", imageBase64 = image, correlationId = "turn-2").toList()

        assertEquals(listOf("turn-2"), engine.summaries.value.single().turnIds)
    }

    @Test
    fun `provenance stays flat across chained compressions`() = runBlocking {
        val original = AppConfigProvider.current
        val engine = ConversationEngine(RecordingClient())
        try {
            AppConfigProvider.current = TestConfig(original, SmallSummariesConfig(original.summaries, 4))
            repeat(5) { index ->
                engine.send("q$index", imageBase64 = image, correlationId = "turn$index").toList()
            }
            engine.removeTurn("turn0")

            engine.send("q5", imageBase64 = image, correlationId = "turn5").toList()
            engine.send("q6", imageBase64 = image, correlationId = "turn6").toList()

            val summaries = engine.summaries.value
            val compressed = summaries.filter { it.sources.isNotEmpty() }
            assertTrue(compressed.isNotEmpty())
            assertTrue(compressed.all { summary -> summary.sources.all { it.turnIds.size == 1 && it.points.isNotEmpty() } })
            assertTrue(summaries.none { it.turnIds.contains("turn0") })
            assertTrue(summaries.none { summary -> summary.sources.any { it.turnIds.contains("turn0") } })

            val texts = summaries.flatMap { it.points }.map { it.text } +
                compressed.flatMap { it.sources }.flatMap { it.points }.map { it.text }
            (1..6).forEach { index -> assertTrue("point q$index missing", texts.contains("point q$index")) }
            assertTrue(texts.none { it == "point q0" })
        } finally {
            AppConfigProvider.current = original
        }
    }
}
