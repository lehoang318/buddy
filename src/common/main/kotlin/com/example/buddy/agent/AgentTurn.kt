package com.example.buddy.agent

import com.example.buddy.chat.ConversationMessage
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.Role as BuddyRole
import com.example.buddy.data.Summary
import com.example.buddy.fetch.FetchedUrl
import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.llm.LlmClient
import com.example.buddy.search.WebSearch
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role as AdkRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/** Events produced by a single agentic turn, decoupled from the ADK event model. */
sealed interface AgentTurnEvent {
    data class Text(val text: String) : AgentTurnEvent
    data class ThoughtsDelta(val text: String) : AgentTurnEvent
    data class AnswerReset(val retractedText: String) : AgentTurnEvent
    data class ToolCallStarted(val name: String, val args: Map<String, Any?>) : AgentTurnEvent
    data class ToolCallFinished(val name: String, val result: Map<String, Any?>) : AgentTurnEvent
    data class Completed(val fullText: String) : AgentTurnEvent
    data class Failed(val error: Throwable) : AgentTurnEvent
}

/**
 * Runs one user turn through an ADK [com.google.adk.kt.agents.LlmAgent], letting the model decide
 * whether to call the `web_search` / `fetch_url` tools. The runner drives the tool loop itself
 * (model -> function call -> function response -> model) until a final text response is produced.
 *
 * Each turn uses a fresh in-memory session. Multi-turn context is supplied through the agent
 * instruction (summaries plus the last few Q&A pairs), matching the limits the legacy pipeline
 * applies, rather than persisting ADK session state across turns.
 */
class AgentTurn(
    private val client: LlmClient,
    private val webSearch: WebSearch?,
    private val urlFetcher: UrlFetcher?,
    private val webSearchEnabled: Boolean
) {

    fun run(
        userMessage: String,
        imageBase64: String?,
        history: List<ConversationMessage>,
        summaries: List<Summary>,
        fetchedUrls: List<FetchedUrl> = emptyList()
    ): Flow<AgentTurnEvent> = flow {
        val appConfig = AppConfigProvider.current
        val agentic = appConfig.agentic

        val tools = buildList {
            if (webSearchEnabled && webSearch != null) {
                add(WebSearchTool(webSearch, agentic.webSearchToolDescription))
            }
            if (urlFetcher != null) {
                add(FetchUrlTool(urlFetcher, agentic.fetchUrlToolDescription))
            }
        }
        val toolInstruction = when {
            tools.any { it.name == "web_search" } || tools.any { it.name == "fetch_url" } ->
                buildList {
                    if (tools.any { it.name == "web_search" }) add(agentic.webSearchInstruction)
                    if (tools.any { it.name == "fetch_url" }) add(agentic.fetchUrlInstruction)
                }.joinToString("\n\n")
            else -> agentic.noToolsInstruction
        }

        val instruction = BuddyAgentFactory.buildInstruction(
            config = agentic,
            systemMessage = appConfig.llm.defaultSystemMessage,
            outputLimit = appConfig.llm.maxTokens,
            summariesContext = appConfig.summaries.formatSummariesContext(summaries),
            recentConversation = formatRecentConversation(history, appConfig.summaries.maxQaPairs),
            fetchedUrlsContext = formatFetchedUrls(fetchedUrls),
            toolInstruction = toolInstruction
        )

        val model = OpenAICompatibleModel(client, client.activeModel)
        val agent = BuddyAgentFactory.buildRootAgent(model, instruction, tools, agentic)

        val parts = mutableListOf(Part(text = userMessage))
        if (!imageBase64.isNullOrBlank()) {
            dataUriToBlob(imageBase64)?.let { parts += Part(inlineData = it) }
        }
        val userContent = Content(role = AdkRole.USER, parts = parts)

        val runner = InMemoryRunner(agent, appName = agentic.agentName, sessionService = InMemorySessionService())
        val emitted = StringBuilder()
        val rawReceived = StringBuilder()
        val splitter = ThinkTagSplitter()
        var completed = false

        suspend fun emitSegments(segments: List<ThoughtSegment>) {
            segments.forEach { segment ->
                when (segment) {
                    is ThoughtSegment.Thought -> emit(AgentTurnEvent.ThoughtsDelta(segment.text))
                    is ThoughtSegment.Answer -> {
                        emitted.append(segment.text)
                        emit(AgentTurnEvent.Text(segment.text))
                    }
                }
            }
        }

        try {
            runner.runAsync(
                userId = USER_ID,
                sessionId = UUID.randomUUID().toString(),
                newMessage = userContent,
                runConfig = RunConfig(streamingMode = StreamingMode.SSE)
            ).collect { event ->
                if (event.author != agentic.agentName) return@collect

                event.content?.parts
                    ?.filter { it.thought == true }
                    ?.mapNotNull { it.text }
                    ?.joinToString("")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { emit(AgentTurnEvent.ThoughtsDelta(it)) }

                val calls = event.functionCalls()
                if (calls.isNotEmpty()) {
                    emitSegments(splitter.flush())
                    if (emitted.isNotEmpty()) {
                        emit(AgentTurnEvent.AnswerReset(emitted.toString()))
                        emitted.clear()
                    }
                    rawReceived.clear()
                }
                calls.forEach { call ->
                    emit(AgentTurnEvent.ToolCallStarted(call.name, call.args))
                }
                event.functionResponses().forEach { response ->
                    emit(AgentTurnEvent.ToolCallFinished(response.name, response.response))
                }

                val text = event.contentText()
                if (event.partial) {
                    if (text.isNotEmpty()) {
                        rawReceived.append(text)
                        emitSegments(splitter.feed(text))
                    }
                } else if (event.isFinalResponse && event.functionCalls().isEmpty() && event.functionResponses().isEmpty()) {
                    val delta = when {
                        text.isEmpty() -> ""
                        rawReceived.isEmpty() -> text
                        text.startsWith(rawReceived.toString()) -> text.substring(rawReceived.length)
                        else -> ""
                    }
                    if (delta.isNotEmpty()) {
                        rawReceived.append(delta)
                        emitSegments(splitter.feed(delta))
                    }
                    emitSegments(splitter.flush())
                    completed = true
                    emit(AgentTurnEvent.Completed(emitted.toString()))
                }
            }
            if (!completed) {
                emitSegments(splitter.flush())
                emit(AgentTurnEvent.Completed(emitted.toString()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AgentTurnEvent.Failed(e))
        }
    }

    private fun formatFetchedUrls(fetchedUrls: List<FetchedUrl>): String {
        if (fetchedUrls.isEmpty()) return ""
        val header = AppConfigProvider.current.summaries.webDataHeader
        return buildString {
            appendLine(header)
            fetchedUrls.forEach { fetched ->
                appendLine("### Fetched URL")
                appendLine("#### ${fetched.url}")
                appendLine(fetched.content)
            }
        }.trimEnd()
    }

    private fun formatRecentConversation(history: List<ConversationMessage>, maxPairs: Int): String {
        if (maxPairs <= 0) return ""
        val previous = if (history.lastOrNull()?.role == BuddyRole.USER) history.dropLast(1) else history
        val pairs = mutableListOf<Pair<ConversationMessage, ConversationMessage>>()
        var index = 0
        while (index < previous.size - 1) {
            if (previous[index].role == BuddyRole.USER && previous[index + 1].role == BuddyRole.ASSISTANT) {
                pairs += previous[index] to previous[index + 1]
                index += 2
            } else {
                index++
            }
        }
        if (pairs.isEmpty()) return ""
        return buildString {
            appendLine("## Recent Conversation")
            pairs.takeLast(maxPairs).forEach { (user, assistant) ->
                appendLine("User: ${user.content}")
                appendLine("Assistant: ${assistant.content}")
            }
        }.trimEnd()
    }

    private companion object {
        const val USER_ID = "buddy-user"
    }
}

private sealed interface ThoughtSegment {
    data class Thought(val text: String) : ThoughtSegment
    data class Answer(val text: String) : ThoughtSegment
}

private class ThinkTagSplitter {
    private val open = "<think>"
    private val close = "</think>"
    private var inThink = false
    private var pending = ""

    fun feed(text: String): List<ThoughtSegment> {
        pending += text
        val out = mutableListOf<ThoughtSegment>()
        while (true) {
            if (!inThink) {
                val index = pending.indexOf(open)
                if (index >= 0) {
                    if (index > 0) out += ThoughtSegment.Answer(pending.substring(0, index))
                    pending = pending.substring(index + open.length)
                    inThink = true
                    continue
                }
                val hold = tagPrefixLength(pending, open)
                val emit = pending.substring(0, pending.length - hold)
                if (emit.isNotEmpty()) out += ThoughtSegment.Answer(emit)
                pending = pending.substring(pending.length - hold)
                return out
            } else {
                val index = pending.indexOf(close)
                if (index >= 0) {
                    if (index > 0) out += ThoughtSegment.Thought(pending.substring(0, index))
                    pending = pending.substring(index + close.length)
                    inThink = false
                    continue
                }
                val hold = tagPrefixLength(pending, close)
                val emit = pending.substring(0, pending.length - hold)
                if (emit.isNotEmpty()) out += ThoughtSegment.Thought(emit)
                pending = pending.substring(pending.length - hold)
                return out
            }
        }
    }

    fun flush(): List<ThoughtSegment> {
        if (pending.isEmpty()) return emptyList()
        val segment = if (inThink) ThoughtSegment.Thought(pending) else ThoughtSegment.Answer(pending)
        pending = ""
        return listOf(segment)
    }

    private fun tagPrefixLength(text: String, tag: String): Int {
        for (length in minOf(text.length, tag.length - 1) downTo 1) {
            if (text.regionMatches(text.length - length, tag, 0, length)) return length
        }
        return 0
    }
}
