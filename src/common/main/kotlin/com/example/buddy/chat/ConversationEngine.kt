package com.example.buddy.chat

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
    val attachment: TextAttachment? = null
)

sealed interface ConversationEvent {
    data object UrlFetchStarted : ConversationEvent
    data class UrlFetchFinished(val result: UrlFetchResult) : ConversationEvent
    data class UserMessageAccepted(val message: ConversationMessage) : ConversationEvent
    data object SearchStarted : ConversationEvent
    data class SearchFinished(val outcome: WebSearchHelper.WebSearchOutcome) : ConversationEvent
    data class AssistantStarted(
        val id: String,
        val searchOutcome: WebSearchHelper.WebSearchOutcome?,
        val fetchedUrls: List<FetchedUrl>
    ) : ConversationEvent
    data class Token(val text: String) : ConversationEvent
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

    val summaries = _summaries.asStateFlow()
    var client: LlmClient? = client
        private set
    var webSearch: WebSearch? = webSearch
        private set
    var urlFetcher: UrlFetcher? = urlFetcher
        private set
    var webSearchEnabled: Boolean = true

    fun updateDependencies(client: LlmClient?, webSearch: WebSearch?, urlFetcher: UrlFetcher?) {
        this.client = client
        this.webSearch = webSearch
        this.urlFetcher = urlFetcher
    }

    fun clear() {
        history.clear()
        _summaries.value = emptyList()
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

            val searchOutcome = if (webSearchEnabled && searchProvider != null && userText.isNotBlank()) {
                emit(ConversationEvent.SearchStarted)
                val outcome = WebSearchHelper(activeClient, searchProvider).search(
                    userText,
                    _summaries.value,
                    correlationId
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
                searchAnswer = searchOutcome?.answer
            )
            val response = StringBuilder()
            try {
                activeClient.streamCompletionWithLogging(
                    messages,
                    activeClient.activeModel,
                    generationConfig,
                    correlationId
                ).collect { token ->
                    response.append(token)
                    emit(ConversationEvent.Token(token))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(ConversationEvent.Failed(e))
                return@withLock
            }

            val assistantText = response.toString()
            history += ConversationMessage(Role.ASSISTANT, assistantText)
            if (assistantText.isNotBlank()) {
                try {
                    val summary = activeClient.generateSummary(
                        userMessage.content,
                        assistantText,
                        model = activeClient.activeModel
                    )
                    val updated = _summaries.value + summary
                    _summaries.value = if (updated.size > AppConfigProvider.current.summaries.maxSummaries) {
                        val count = AppConfigProvider.current.summaries.maxSummaries / 2
                        try {
                            val compressed = activeClient.compressSummaries(
                                updated.take(count),
                                model = activeClient.activeModel
                            )
                            val preservedKeys = updated.take(count)
                                .flatMap { it.points }
                                .filter { it.key }
                            listOf(Summary("Earlier conversation", (preservedKeys + compressed.points).distinctBy { it.text })) + updated.drop(count)
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
            emit(ConversationEvent.Completed(assistantText, _summaries.value))
        }
    }
}

object MessageBuilder {
    fun build(
        history: List<ConversationMessage>,
        summaries: List<Summary> = emptyList(),
        searchResults: List<SearchResult> = emptyList(),
        fetchedUrls: List<FetchedUrl> = emptyList(),
        searchAnswer: String? = null
    ): List<LlmMessage> {
        val currentDate = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", java.util.Locale.US))

        val systemParts = mutableListOf<String>()
        systemParts.add("## Instructions\n${AppConfigProvider.current.llm.defaultSystemMessage}\n\nCurrent date: $currentDate")

        if (summaries.isNotEmpty()) {
            systemParts.add(AppConfigProvider.current.summaries.formatSummariesContext(summaries))
        }

        if (fetchedUrls.isNotEmpty() || searchResults.isNotEmpty()) {
            val webParts = mutableListOf<String>()
            webParts.add(AppConfigProvider.current.summaries.webDataHeader)
            webParts.add(AppConfigProvider.current.search.webDataInstructions)

            if (fetchedUrls.isNotEmpty()) {
                webParts.add("### Fetched URL")
                fetchedUrls.forEach {
                    webParts.add("#### ${it.url}")
                    webParts.add(it.content)
                }
            }

            if (!searchAnswer.isNullOrBlank()) {
                webParts.add("### Search Engine Summary")
                webParts.add(searchAnswer)
            }

            if (searchResults.isNotEmpty()) {
                webParts.add("### Web Search")
                searchResults.forEach {
                    webParts.add("#### ${it.title}")
                    webParts.add("Source: ${it.url}" + (it.publishedDate?.let { date -> " (Published: $date)" } ?: ""))
                    webParts.add(it.content)
                }
            }
            systemParts.add(webParts.joinToString("\n"))
        }

        systemParts.add("## Output Limit\nYour response may not exceed ${AppConfigProvider.current.llm.maxTokens} tokens.")
        val result = mutableListOf(LlmMessage(Role.SYSTEM, systemParts.joinToString("\n\n")))

        val currentUser = history.lastOrNull()?.takeIf { it.role == Role.USER }
        val previous = if (currentUser != null) history.dropLast(1) else history
        val pairs = mutableListOf<Pair<ConversationMessage, ConversationMessage>>()
        var index = 0
        while (index < previous.size - 1) {
            if (previous[index].role == Role.USER && previous[index + 1].role == Role.ASSISTANT) {
                pairs += previous[index] to previous[index + 1]
                index += 2
            } else {
                index++
            }
        }

        pairs.takeLast(AppConfigProvider.current.summaries.maxQaPairs).forEach { (user, assistant) ->
            result += LlmMessage(Role.USER, buildMessageContent(user), user.imageBase64)
            result += LlmMessage(Role.ASSISTANT, assistant.content)
        }
        currentUser?.let { result += LlmMessage(Role.USER, buildMessageContent(it), it.imageBase64) }
        return result
    }

    private fun buildMessageContent(message: ConversationMessage): String = buildList {
        message.attachment?.let { add("[File: ${it.name}]\n${it.text}") }
        add(message.content)
    }.joinToString("\n\n")
}
