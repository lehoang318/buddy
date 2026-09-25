package com.example.buddy.llm

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.Summary
import com.example.buddy.data.Role
import com.example.buddy.logging.Log
import com.example.buddy.search.SearchRecency
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform

private const val TAG_LLM = "LLM"

data class SearchQueryPlan(
    val queries: List<String>,
    val recency: SearchRecency = SearchRecency.ANY
)

data class RawSearchResponse(
    val content: String?,
    val finishReason: String?
)

private fun stripCodeFences(text: String): String = text.trim()
    .removePrefix("```json\n").removePrefix("```json").removePrefix("```\n").removePrefix("```")
    .removeSuffix("\n```").removeSuffix("```")
    .trim()

private fun tryParseJson(text: String): JsonElement? = try {
    JsonParser.parseString(text).takeIf { !it.isJsonNull }
} catch (_: Exception) {
    null
}

// Accepts the canonical {"search": ..., "queries": [...]} shape plus common small-model
// deviations: singular {"query": "..."}, a string where the array should be, a bare array
// of strings, or a bare quoted string. Non-string primitives (bare booleans/numbers) yield
// no queries.
private fun extractQueries(element: JsonElement): List<String> = when {
    element.isJsonObject -> {
        val obj = element.asJsonObject
        val queries = obj.get("queries")
        when {
            queries?.isJsonArray == true ->
                queries.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString }
            queries?.isJsonPrimitive == true -> queries.asJsonPrimitive.takeIf { it.isString }?.asString?.let { listOf(it) } ?: emptyList()
            else -> obj.get("query")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let { listOf(it) } ?: emptyList()
        }
    }
    element.isJsonArray -> element.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString }
    element.isJsonPrimitive -> element.asJsonPrimitive.takeIf { it.isString }?.asString?.let { listOf(it) } ?: emptyList()
    else -> emptyList()
}

// "Do I need to search?" is a first-class field. A false value wins unconditionally over any
// queries the model also happened to emit. Tolerates a bare JSON boolean response.
private fun isSearchFalse(element: JsonElement): Boolean {
    if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) return !element.asJsonPrimitive.asBoolean
    if (!element.isJsonObject) return false
    val obj = element.asJsonObject
    for (key in listOf("search", "needed", "required", "should_search", "search_needed")) {
        val value = obj.get(key) ?: continue
        if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) return !value.asJsonPrimitive.asBoolean
    }
    return false
}

// Determines whether a single query string looks like a prose answer rather than a search
// query. Applied per extracted query, never to the whole response (pretty-printed JSON
// legitimately contains newlines). Deliberately narrow to avoid rejecting real queries that
// contain dots, operators or CJK text ("Node.js 22 LTS", "site:github.com -android",
// "C# generics", "St. Louis weather", "RTX 5090 - release date").
private fun looksLikeAnswer(query: String): Boolean {
    if (query.length > AppConfigProvider.current.search.queryRejectChars) return true
    if (query.contains('\n')) return true
    if (query.contains("```")) return true
    if (Regex("(?m)^(?:[-*]|#{1,6})\\s").containsMatchIn(query)) return true
    // Two or more sentence boundaries followed by an uppercase start: prose, not a query
    // fragment. A single occurrence stays safe (dots in "St. Louis", "Node.js 22").
    return Regex("[.!?]\\s+[A-Z]").findAll(query).count() >= 2
}

private fun extractRecency(element: JsonElement): SearchRecency {
    val raw = element.takeIf { it.isJsonObject }
        ?.asJsonObject?.get("recency")
        ?.takeIf { it.isJsonPrimitive }?.asString
        ?: return SearchRecency.ANY
    return when (raw.trim().lowercase()) {
        "day" -> SearchRecency.DAY
        "week" -> SearchRecency.WEEK
        "month" -> SearchRecency.MONTH
        else -> SearchRecency.ANY
    }
}

private fun truncateAtWordBoundary(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text.trim()
    val cut = text.take(maxChars)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > maxChars / 2) cut.take(lastSpace).trim() else cut.trim()
}

// Never invents a query from prose: if the model answered the user instead of producing a
// plan, the search is skipped rather than performed with the answer text as a query. A null
// return means "skip" (search=false, empty plan, or everything rejected); only an upstream
// null/blank raw response still surfaces as a hard failure.
internal fun parseQueryPlan(cleaned: String, correlationId: String? = null): SearchQueryPlan? {
    val fenceStripped = stripCodeFences(cleaned)

    val jsonElement = tryParseJson(fenceStripped)
        ?: Regex("(?s)\\{.*\\}").find(fenceStripped)?.value?.let { tryParseJson(it) }

    if (jsonElement == null) {
        Log.warning(TAG_LLM, "Search skipped (no JSON plan)",
            "Raw: ${fenceStripped.take(AppConfigProvider.current.search.logPreviewMaxChars)}", correlationId = correlationId)
        return null
    }

    if (isSearchFalse(jsonElement)) {
        Log.info(TAG_LLM, "Search skipped (search field false)", correlationId = correlationId)
        return null
    }

    val recency = extractRecency(jsonElement)
    val candidates = extractQueries(jsonElement)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    if (candidates.isEmpty()) {
        Log.info(TAG_LLM, "Search skipped (empty plan)", correlationId = correlationId)
        return null
    }

    val (accepted, rejected) = candidates.partition { !looksLikeAnswer(it) }
    if (rejected.isNotEmpty()) {
        Log.warning(TAG_LLM, "Dropped answer-shaped queries",
            "Rejected ${rejected.size}: ${rejected.joinToString(" | ") { it.take(60) }}", correlationId = correlationId)
    }
    if (accepted.isEmpty()) {
        Log.info(TAG_LLM, "Search skipped (query rejected as answer-shaped)", correlationId = correlationId)
        return null
    }

    val queries = accepted
        .map { truncateAtWordBoundary(it, AppConfigProvider.current.search.queryMaxChars) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(3)

    if (queries.isEmpty()) return null
    return SearchQueryPlan(queries, recency)
}

interface LlmClient {
    fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>

    fun streamEvents(
        messages: List<LlmMessage>,
        model: String,
        config: LlmGenerationConfig = LlmGenerationConfig(),
        tools: List<LlmTool>? = null
    ): Flow<LlmStreamEvent> = streamCompletion(messages, model, config).transform { emit(LlmStreamEvent.TextDelta(it)) }
    suspend fun getModels(): List<LlmModel>
    suspend fun testConnection(): Boolean
    suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null, imageBase64: String? = null): RawSearchResponse
    suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String? = null, imageBase64: String? = null): Summary
    suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String? = null): Summary

    suspend fun generateSearchQuery(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null, imageBase64: String? = null): SearchQueryPlan? {
        val input = userMessage.take(1024)
        Log.debug(TAG_LLM, "Search query generation started", "Input: ${input.take(AppConfigProvider.current.search.logPreviewMaxChars)}\nModel: $activeModel${if (imageBase64 != null) "\nImage attached" else ""}", correlationId = correlationId)

        val raw = generateSearchQueryRaw(input, summaries, correlationId, imageBase64)

        if (raw.finishReason == "length") {
            Log.warning(TAG_LLM, "Search skipped (output truncated)", "finish_reason=length: query plan would be incomplete", correlationId = correlationId)
            return null
        }

        // Some hybrid-reasoning models leak thinking blocks even when instructed to be terse;
        // strip complete and dangling (truncated) blocks before inspecting the result.
        val cleaned = raw.content
            ?.replace(Regex("(?is)<think>.*?</think>"), "")
            ?.replace(Regex("(?is)<think>.*"), "")
            ?.trim()

        Log.debug(TAG_LLM, "Search query raw response", "Raw: ${cleaned?.take(AppConfigProvider.current.search.logPreviewMaxChars) ?: "<null>"}", correlationId = correlationId)

        if (cleaned.isNullOrBlank()) {
            Log.warning(TAG_LLM, "Search query generation failed",
                "API call returned blank content. Raw: ${raw.content?.take(AppConfigProvider.current.search.logPreviewMaxChars)}",
                correlationId = correlationId)
            throw Exception("Unable to generate search query")
        }

        val plan = parseQueryPlan(cleaned, correlationId)
        if (plan == null) {
            Log.info(TAG_LLM, "Search skipped", "Query plan was rejected or declared unnecessary", correlationId = correlationId)
            return null
        }

        Log.debug(TAG_LLM, "Query plan parsed", "Queries: ${plan.queries.joinToString(" | ")}\nRecency: ${plan.recency}", correlationId = correlationId)
        return plan
    }

    val defaultModel: String
    var activeModel: String
    val isReasoningSupported: Boolean

    fun streamCompletionWithLogging(
        messages: List<LlmMessage>,
        model: String,
        config: LlmGenerationConfig = LlmGenerationConfig(),
        correlationId: String? = null
    ): Flow<String> {
        val startTime = System.currentTimeMillis()
        val resolvedTemp = config.temperature ?: AppConfigProvider.current.llm.temperature
        val resolvedTopP = config.topP ?: AppConfigProvider.current.llm.topP
        val resolvedTopK = config.topK ?: AppConfigProvider.current.llm.topK
        val resolvedMaxTokens = config.maxTokens ?: AppConfigProvider.current.llm.maxTokens
        val paramDetail = "model=$model, temp=$resolvedTemp, topP=$resolvedTopP, topK=$resolvedTopK, maxTokens=$resolvedMaxTokens, reasoning=${config.reasoningEffort}"
        Log.info(TAG_LLM, "Request sent: ${messages.size} messages", data = paramDetail, correlationId = correlationId)
        if (AppConfigProvider.current.debugLogging) {
            val systemMsg = messages.find { it.role == Role.SYSTEM }?.content
            val debugData = buildString {
                appendLine("Config: $paramDetail")
                if (systemMsg != null) appendLine("System: $systemMsg")
                appendLine("Messages:")
                messages.forEach {
                    val preview = it.content.take(AppConfigProvider.current.search.logPreviewMaxChars) + if (it.content.length > AppConfigProvider.current.search.logPreviewMaxChars) "..." else ""
                    appendLine("${it.role}: $preview")
                }
            }
            Log.debug(TAG_LLM, "Request details", data = debugData, correlationId = correlationId)
        }
        var chunkCount = 0
        return streamCompletion(messages, model, config)
            .onEach { chunkCount++ }
            .onCompletion { throwable ->
                val durationMs = System.currentTimeMillis() - startTime
                if (throwable == null) {
                    Log.info(TAG_LLM, "Response received ($chunkCount chunks)", correlationId = correlationId, durationMs = durationMs)
                } else {
                    Log.error(TAG_LLM, "Response failed", throwable.message, correlationId = correlationId, durationMs = durationMs)
                }
            }
    }

    fun toggleReasoning(current: ReasoningEffort?, webSearchEnabled: Boolean, agenticMode: Boolean): ReasoningEffort {
        val next = current.cycle(webSearchEnabled && agenticMode)
        val message = if (isReasoningSupported) {
            "Reasoning effort set: ${next.label()}"
        } else {
            "Reasoning effort set: ${next.label()} (not supported)"
        }
        Log.info(TAG_LLM, message)
        return next
    }
}
