package com.example.buddy.chat

import com.example.buddy.agent.AgentTurn
import com.example.buddy.agent.AgentTurnEvent
import com.example.buddy.agent.QuestionBridge
import com.example.buddy.agent.SummarizerAgent
import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.fetch.FetchedUrl
import com.example.buddy.fetch.UrlFetchResult
import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.fetch.extractUrls
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.ReasoningEffort
import com.example.buddy.logging.Log
import com.example.buddy.search.SearchResult
import com.example.buddy.search.WebSearch
import com.example.buddy.search.WebSearchHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ConversationMessage(
    val role: Role,
    val content: String,
    val imageBase64: String? = null,
    val attachment: TextAttachment? = null,
    val questionAsked: String? = null
)

sealed interface ConversationEvent {
    data object UrlFetchStarted : ConversationEvent
    data class UrlFetchFinished(val result: UrlFetchResult) : ConversationEvent
    data class UserMessageAccepted(val message: ConversationMessage) : ConversationEvent
    data object SearchStarted : ConversationEvent
    data class SearchQueriesPlanned(val queries: List<String>) : ConversationEvent
    data class SearchFinished(val outcome: WebSearchHelper.WebSearchOutcome) : ConversationEvent
    data class QuestionAsked(val question: String, val options: List<String>) : ConversationEvent
    data object ClarificationAnswered : ConversationEvent
    data class AssistantStarted(
        val id: String,
        val searchOutcome: WebSearchHelper.WebSearchOutcome?,
        val fetchedUrls: List<FetchedUrl>
    ) : ConversationEvent
    data class Token(val text: String) : ConversationEvent
    data class ThoughtsDelta(val text: String) : ConversationEvent
    data class AnswerReset(val retractedText: String) : ConversationEvent
    data class Completed(val assistantText: String, val summaries: List<Summary>) : ConversationEvent
    data class Failed(val error: Throwable) : ConversationEvent
}

class ConversationEngine(
    client: LlmClient? = null,
    webSearch: WebSearch? = null,
    urlFetcher: UrlFetcher? = null
) {
    private val processingLock = Mutex()
    private val history = mutableListOf<ConversationMessage>()
    private val _summaries = MutableStateFlow<List<Summary>>(emptyList())
    private var activeQuestionBridge: QuestionBridge? = null

    val summaries = _summaries.asStateFlow()
    var client: LlmClient? = client
        private set
    var webSearch: WebSearch? = webSearch
        private set
    var urlFetcher: UrlFetcher? = urlFetcher
        private set
    var webSearchEnabled: Boolean = true
    var agenticMode: Boolean = false
    var reasoningEffort: ReasoningEffort? = null

    fun updateDependencies(client: LlmClient?, webSearch: WebSearch?, urlFetcher: UrlFetcher?) {
        this.client = client
        this.webSearch = webSearch
        this.urlFetcher = urlFetcher
    }

    fun answerPendingQuestion(text: String): Boolean = activeQuestionBridge?.send(text) ?: false

    fun clear() {
        history.clear()
        _summaries.value = emptyList()
    }

    fun restore(messages: List<ConversationMessage>, summaries: List<Summary>) {
        history.clear()
        history.addAll(messages)
        _summaries.value = summaries
    }

    fun send(
        userText: String,
        attachment: TextAttachment? = null,
        imageBase64: String? = null,
        generationConfig: LlmGenerationConfig = LlmGenerationConfig(),
        correlationId: String = UUID.randomUUID().toString()
    ): Flow<ConversationEvent> = flow {
        val urls = extractUrls(userText)
        val fetcher = urlFetcher
        val searchProvider = webSearch
        var fetchedUrls = emptyList<FetchedUrl>()
        if (urls.isNotEmpty() && fetcher != null) {
            emit(ConversationEvent.UrlFetchStarted)
            val fetchResult = fetcher.fetchAll(urls, correlationId)
            fetchedUrls = fetchResult.urls
            emit(ConversationEvent.UrlFetchFinished(fetchResult))
        }

        processingLock.withLock {
            val userMessage = ConversationMessage(
                role = Role.USER,
                content = userText,
                imageBase64 = imageBase64,
                attachment = attachment
            )
            history += userMessage
            emit(ConversationEvent.UserMessageAccepted(userMessage))

            val activeClient = client
            if (activeClient == null) {
                emit(ConversationEvent.Failed(IllegalStateException("No LLM provider is configured")))
                return@withLock
            }

            if (agenticMode) {
                runAgenticTurn(activeClient, userMessage, fetchedUrls, generationConfig, correlationId)
            } else {
                runLegacyTurn(activeClient, searchProvider, userMessage, fetchedUrls, generationConfig, correlationId)
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ConversationEvent>.runLegacyTurn(
        activeClient: LlmClient,
        searchProvider: WebSearch?,
        userMessage: ConversationMessage,
        fetchedUrls: List<FetchedUrl>,
        generationConfig: LlmGenerationConfig,
        correlationId: String
    ) {
        val config = generationConfig.copy(reasoningEffort = reasoningEffort ?: generationConfig.reasoningEffort)
        val searchOutcome = if (webSearchEnabled && searchProvider != null && userMessage.content.isNotBlank()) {
            emit(ConversationEvent.SearchStarted)
            val outcome = WebSearchHelper(activeClient, searchProvider).search(
                userMessage.content,
                _summaries.value,
                correlationId,
                userMessage.imageBase64,
                onQueriesPlanned = { emit(ConversationEvent.SearchQueriesPlanned(it)) }
            )
            emit(ConversationEvent.SearchFinished(outcome))
            outcome
        } else {
            null
        }

        val assistantId = UUID.randomUUID().toString()
        emit(ConversationEvent.AssistantStarted(assistantId, searchOutcome, fetchedUrls))

        val messages = MessageBuilder.build(
            history = history,
            summaries = _summaries.value,
            searchResults = searchOutcome?.rawResults.orEmpty(),
            fetchedUrls = fetchedUrls,
            searchAnswer = searchOutcome?.answer,
            outputLimit = config.maxTokens
        )
        val response = StringBuilder()
        try {
            activeClient.streamCompletionWithLogging(
                messages,
                activeClient.activeModel,
                config,
                correlationId
            ).collect { token ->
                response.append(token)
                emit(ConversationEvent.Token(token))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ConversationEvent.Failed(e))
            return
        }

        val assistantText = response.toString()
        history += ConversationMessage(Role.ASSISTANT, assistantText)
        updateSummaries(activeClient, userMessage, assistantText)
        emit(ConversationEvent.Completed(assistantText, _summaries.value))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ConversationEvent>.runAgenticTurn(
        activeClient: LlmClient,
        userMessage: ConversationMessage,
        fetchedUrls: List<FetchedUrl>,
        generationConfig: LlmGenerationConfig,
        correlationId: String
    ) {
        val assistantId = UUID.randomUUID().toString()
        emit(ConversationEvent.AssistantStarted(assistantId, null, fetchedUrls))

        val effort = reasoningEffort ?: generationConfig.reasoningEffort
        val searchRoundCap = if (webSearchEnabled) {
            if (effort == ReasoningEffort.DEEP) AppConfigProvider.current.agentic.deepResearchSearchRounds
            else AppConfigProvider.current.agentic.standardSearchRounds
        } else {
            null
        }
        val response = StringBuilder()
        var failed = false
        var askedQuestion: String? = null
        val questionBridge = QuestionBridge()
        activeQuestionBridge = questionBridge
        val turn = AgentTurn(
            client = activeClient,
            webSearch = webSearch,
            urlFetcher = urlFetcher,
            webSearchEnabled = webSearchEnabled,
            questionBridge = questionBridge,
            reasoningEffort = effort,
            searchRoundCap = searchRoundCap
        )
        val agentUserText = buildString {
            userMessage.attachment?.let { append("[File: ${it.name}]\n${it.text}\n\n") }
            append(userMessage.content)
        }
        try {
            turn.run(
                userMessage = agentUserText,
                imageBase64 = userMessage.imageBase64,
                history = history,
                summaries = _summaries.value,
                fetchedUrls = fetchedUrls
            ).collect { event ->
                when (event) {
                    is AgentTurnEvent.Text -> {
                        response.append(event.text)
                        emit(ConversationEvent.Token(event.text))
                    }
                    is AgentTurnEvent.ThoughtsDelta -> emit(ConversationEvent.ThoughtsDelta(event.text))
                    is AgentTurnEvent.AnswerReset -> {
                        response.clear()
                        emit(ConversationEvent.AnswerReset(event.retractedText))
                    }
                    is AgentTurnEvent.ToolCallStarted -> {
                        when (event.name) {
                            "web_search" -> {
                                emit(ConversationEvent.SearchStarted)
                                val queries = toolCallQueries(event.args)
                                if (queries.isNotEmpty()) emit(ConversationEvent.SearchQueriesPlanned(queries))
                            }
                            "ask_user" -> {
                                val question = (event.args["question"] as? String)?.trim().orEmpty()
                                if (question.isNotBlank()) {
                                    askedQuestion = question
                                    emit(ConversationEvent.QuestionAsked(question, toolCallOptions(event.args)))
                                }
                            }
                        }
                    }
                    is AgentTurnEvent.ToolCallFinished -> {
                        when (event.name) {
                            "web_search" -> emit(ConversationEvent.SearchFinished(toSearchOutcome(event.result)))
                            "fetch_url" -> {
                                emit(ConversationEvent.UrlFetchStarted)
                                emit(ConversationEvent.UrlFetchFinished(toFetchResult(event.result)))
                            }
                            "ask_user" -> {
                                (event.result["answer"] as? String)?.let { answer ->
                                    history += ConversationMessage(Role.USER, answer)
                                    emit(ConversationEvent.ClarificationAnswered)
                                }
                            }
                        }
                    }
                    is AgentTurnEvent.Completed -> Unit
                    is AgentTurnEvent.Failed -> {
                        failed = true
                        emit(ConversationEvent.Failed(event.error))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ConversationEvent.Failed(e))
            return
        } finally {
            activeQuestionBridge = null
        }
        if (failed) return

        val assistantText = response.toString()
        history += ConversationMessage(Role.ASSISTANT, assistantText, questionAsked = askedQuestion)
        updateSummaries(activeClient, userMessage, assistantText)
        emit(ConversationEvent.Completed(assistantText, _summaries.value))
    }

    private suspend fun updateSummaries(
        activeClient: LlmClient,
        userMessage: ConversationMessage,
        assistantText: String
    ) {
        if (assistantText.isBlank()) return
        val summarizer = SummarizerAgent(activeClient)
        try {
            val summary = summarizer.summarize(
                userMessage.content,
                assistantText,
                userMessage.imageBase64
            )
            val updated = _summaries.value + summary
            _summaries.value = if (updated.size > AppConfigProvider.current.summaries.maxSummaries) {
                val count = AppConfigProvider.current.summaries.maxSummaries / 2
                try {
                    val compressed = summarizer.compress(updated.take(count))
                    val preservedKeys = updated.take(count)
                        .flatMap { it.points }
                        .filter { it.key }
                    val preservedTags = updated.take(count)
                        .flatMap { it.tags }
                        .distinct()
                        .takeLast(AppConfigProvider.current.summaries.maxSessionTags)
                    listOf(Summary("Earlier conversation", (preservedKeys + compressed.points).distinctBy { it.text }, preservedTags)) + updated.drop(count)
                } catch (e: Exception) {
                    Log.error("Chat", "Failed to compress summaries", e.message)
                    updated
                }
            } else {
                updated
            }
        } catch (e: Exception) {
            Log.error("Chat", "Failed to generate summary", e.message)
        }
    }

    private fun toSearchOutcome(result: Map<String, Any?>): WebSearchHelper.WebSearchOutcome {
        val resultsText = result["results"] as? String
        val answer = result["answer"] as? String
        val error = result["error"] as? String
        val queries = (result["queries"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return WebSearchHelper.WebSearchOutcome(
            resultsText = resultsText,
            answer = answer,
            queries = queries,
            errorMessage = error
        )
    }

    private fun toFetchResult(result: Map<String, Any?>): UrlFetchResult {
        val url = result["url"] as? String
        val content = result["content"] as? String
        val error = result["error"] as? String
        return if (url != null && content != null) {
            UrlFetchResult(urls = listOf(FetchedUrl(url, content)))
        } else {
            UrlFetchResult(warnings = listOfNotNull(error ?: "Failed to fetch URL"))
        }
    }

    private fun toolCallQueries(args: Map<String, Any?>): List<String> =
        (args["queries"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

    private fun toolCallOptions(args: Map<String, Any?>): List<String> =
        (args["options"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            .map { it.trim() }.filter { it.isNotBlank() }.take(4)
}

object MessageBuilder {
    fun build(
        history: List<ConversationMessage>,
        summaries: List<Summary> = emptyList(),
        searchResults: List<SearchResult> = emptyList(),
        fetchedUrls: List<FetchedUrl> = emptyList(),
        searchAnswer: String? = null,
        outputLimit: Int? = null
    ): List<LlmMessage> {
        val llmConfig = AppConfigProvider.current.llm
        val summariesConfig = AppConfigProvider.current.summaries
        val searchConfig = AppConfigProvider.current.search
        val maxChars = llmConfig.maxRequestChars
        val resolvedLimit = outputLimit ?: llmConfig.maxTokens

        val currentDate = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.US))

        val instructionsPart = "## Instructions\n${llmConfig.defaultSystemMessage}\n\nCurrent date: $currentDate"
        val outputLimitPart = "## Output Limit\nYour response may not exceed $resolvedLimit tokens."

        val currentUser = history.lastOrNull()?.takeIf { it.role == Role.USER }
        val previous = if (currentUser != null) history.dropLast(1) else history
        var pairs = mutableListOf<Pair<ConversationMessage, ConversationMessage>>()
        var index = 0
        while (index < previous.size - 1) {
            if (previous[index].role == Role.USER && previous[index + 1].role == Role.ASSISTANT) {
                pairs += previous[index] to previous[index + 1]
                index += 2
            } else {
                index++
            }
        }

        var retainedSummaries = summaries.toMutableList()
        var retainedResults = searchResults.toMutableList()
        var retainedUrls = fetchedUrls.toMutableList()
        var retainedAnswer = searchAnswer
        pairs = pairs.takeLast(summariesConfig.maxQaPairs).toMutableList()
        var currentUserText = currentUser?.let { buildMessageContent(it) } ?: ""

        var trimmed = false

        fun webSectionText(): String {
            if (retainedAnswer.isNullOrBlank() && retainedUrls.isEmpty() && retainedResults.isEmpty()) return ""
            val parts = mutableListOf<String>()
            parts.add(summariesConfig.webDataHeader)
            parts.add(searchConfig.webDataInstructions)
            retainedAnswer?.let { parts.add("### Search Engine Summary\n$it") }
            retainedUrls.forEach { parts.add("### Fetched URL\n#### ${it.url}\n${it.content}") }
            retainedResults.forEach {
                parts.add("### Web Search\n#### ${it.title}\nSource: ${it.url}" +
                    (it.publishedDate?.let { date -> " (Published: $date)" } ?: "") + "\n${it.content}")
            }
            return parts.joinToString("\n")
        }

        fun systemText(): String {
            val parts = mutableListOf(instructionsPart)
            summariesConfig.formatSummariesContext(retainedSummaries).takeIf { it.isNotBlank() }?.let { parts.add(it) }
            webSectionText().takeIf { it.isNotBlank() }?.let { parts.add(it) }
            parts.add(outputLimitPart)
            return parts.joinToString("\n\n")
        }

        fun totalLength(): Int = systemText().length +
            pairs.sumOf { it.first.content.length + it.second.content.length } +
            currentUserText.length

        fun truncateAtWordBoundary(text: String, maxChars: Int): String {
            if (text.length <= maxChars) return text
            val cut = text.take(maxChars)
            val lastSpace = cut.lastIndexOf(' ')
            return if (lastSpace > maxChars / 2) cut.take(lastSpace).trim() else cut.trim()
        }

        if (totalLength() > maxChars) {
            trimmed = true
            while (totalLength() > maxChars && retainedResults.isNotEmpty()) retainedResults.removeAt(retainedResults.size - 1)
            while (totalLength() > maxChars && retainedUrls.isNotEmpty()) retainedUrls.removeAt(retainedUrls.size - 1)
            if (totalLength() > maxChars && !retainedAnswer.isNullOrBlank()) { retainedAnswer = null }
            while (totalLength() > maxChars && retainedSummaries.isNotEmpty()) retainedSummaries.removeAt(0)
            while (totalLength() > maxChars && pairs.isNotEmpty()) pairs.removeAt(0)
            if (totalLength() > maxChars && currentUserText.isNotEmpty()) {
                val over = totalLength() - maxChars
                currentUserText = truncateAtWordBoundary(currentUserText, (currentUserText.length - over).coerceAtLeast(0)).trim()
                if (currentUserText.isBlank()) {
                    currentUserText = ""
                    trimmed = true
                }
            }
        }

        if (trimmed) {
            Log.warning("Chat", "Request trimmed to fit $maxChars character limit",
                "Summaries kept: ${retainedSummaries.size}, results kept: ${retainedResults.size}, " +
                    "urls kept: ${retainedUrls.size}, pairs kept: ${pairs.size}, answer kept: ${!retainedAnswer.isNullOrBlank()}")
        }

        val systemMessage = systemText().takeIf { it.isNotBlank() }?.let { LlmMessage(Role.SYSTEM, it) }
        val finalUserMessage = currentUser?.takeIf { currentUserText.isNotBlank() }
            ?.let { LlmMessage(Role.USER, currentUserText, it.imageBase64) }
        val result = mutableListOf<LlmMessage>()
        systemMessage?.let { result += it }
        pairs.forEach { (user, assistant) ->
            result += LlmMessage(Role.USER, buildMessageContent(user), user.imageBase64)
            val assistantContent = assistant.questionAsked
                ?.let { "You asked the user: \"$it\"\n\n${assistant.content}" }
                ?: assistant.content
            result += LlmMessage(Role.ASSISTANT, assistantContent)
        }
        finalUserMessage?.let { result += it }
        return result
    }

    private fun buildMessageContent(message: ConversationMessage): String = buildList {
        message.attachment?.let { add("[File: ${it.name}]\n${it.text}") }
        add(message.content)
    }.joinToString("\n\n")
}
