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

private const val TAG_LLM = "LLM"

data class SearchQueryPlan(
    val queries: List<String>,
    val recency: SearchRecency = SearchRecency.ANY
)

private fun isNoQuerySentinel(text: String): Boolean =
    text.trim().trim('"', '\'', '.', '`').equals("NO_QUERY", ignoreCase = true)

private fun stripCodeFences(text: String): String = text.trim()
    .removePrefix("```json\n").removePrefix("```json").removePrefix("```\n").removePrefix("```")
    .removeSuffix("\n```").removeSuffix("```")
    .trim()

private fun tryParseJson(text: String): JsonElement? = try {
    JsonParser.parseString(text).takeIf { !it.isJsonNull }
} catch (_: Exception) {
    null
}

// Accepts the canonical {"queries": [...]} shape plus common small-model deviations:
// singular {"query": "..."}, a string where the array should be, a bare array of strings,
// or a bare quoted string.
private fun extractQueries(element: JsonElement): List<String> = when {
    element.isJsonObject -> {
        val obj = element.asJsonObject
        val queries = obj.get("queries")
        when {
            queries?.isJsonArray == true ->
                queries.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
            queries?.isJsonPrimitive == true -> listOf(queries.asString)
            else -> obj.get("query")?.takeIf { it.isJsonPrimitive }?.asString?.let { listOf(it) } ?: emptyList()
        }
    }
    element.isJsonArray -> element.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
    element.isJsonPrimitive -> listOf(element.asString)
    else -> emptyList()
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

// Never rejects: worst case (no JSON found at all) treats the whole cleaned text as one
// plain query, matching the pre-multi-query behavior. Only a null/blank raw response upstream
// still surfaces as a failure.
internal fun parseQueryPlan(cleaned: String, correlationId: String? = null): SearchQueryPlan? {
    if (isNoQuerySentinel(cleaned)) return null

    val fenceStripped = stripCodeFences(cleaned)
    if (isNoQuerySentinel(fenceStripped)) return null

    val jsonElement = tryParseJson(fenceStripped)
        ?: Regex("(?s)\\{.*\\}").find(fenceStripped)?.value?.let { tryParseJson(it) }

    val rawQueries: List<String>
    val recency: SearchRecency
    if (jsonElement != null) {
        rawQueries = extractQueries(jsonElement)
        recency = extractRecency(jsonElement)
    } else {
        Log.warning(TAG_LLM, "Query plan not JSON, using raw text as single query",
            "Raw: ${fenceStripped.take(AppConfigProvider.current.search.logPreviewMaxChars)}", correlationId = correlationId)
        rawQueries = listOf(fenceStripped)
        recency = SearchRecency.ANY
    }

    val queries = rawQueries
        .map { it.trim() }
        .filter { it.isNotBlank() && !isNoQuerySentinel(it) }
        .distinctBy { it.lowercase() }
        .take(3)
        .map { truncateAtWordBoundary(it, AppConfigProvider.current.search.queryMaxChars) }

    if (queries.isEmpty()) return null
    return SearchQueryPlan(queries, recency)
}

interface LlmClient {
    fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
    suspend fun getModels(): List<LlmModel>
    suspend fun testConnection(): Boolean
    suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): String?
    suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String? = null): Summary
    suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String? = null): Summary

    suspend fun generateSearchQuery(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): SearchQueryPlan? {
        val input = userMessage.take(1024)
        Log.debug(TAG_LLM, "Search query generation started", "Input: ${input.take(AppConfigProvider.current.search.logPreviewMaxChars)}\nModel: $activeModel", correlationId = correlationId)
        val raw = generateSearchQueryRaw(input, summaries, correlationId)

        // Some hybrid-reasoning models leak <think> blocks even when instructed to be terse;
        // strip complete and dangling (truncated) blocks before inspecting the result.
        val cleaned = raw
            ?.replace(Regex("(?is)<think>.*?</think>"), "")
            ?.replace(Regex("(?is)<think>.*"), "")
            ?.trim()

        Log.debug(TAG_LLM, "Search query raw response", "Raw: ${cleaned?.take(AppConfigProvider.current.search.logPreviewMaxChars) ?: "<null>"}", correlationId = correlationId)

        if (cleaned.isNullOrBlank()) {
            Log.warning(TAG_LLM, "Search query generation failed",
                "API call returned null or blank (network error or no response). Raw: ${raw?.take(AppConfigProvider.current.search.logPreviewMaxChars)}",
                correlationId = correlationId)
            throw Exception("Unable to generate search query")
        }

        val plan = parseQueryPlan(cleaned, correlationId)
        if (plan == null) {
            Log.info(TAG_LLM, "Search query skipped (NO_QUERY)",
                "LLM indicated no web search is needed for this input", correlationId = correlationId)
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
        val resolvedTemp = config.temperature.takeIf { it > 0 } ?: AppConfigProvider.current.llm.temperature
        val resolvedTopP = config.topP.takeIf { it > 0 } ?: AppConfigProvider.current.llm.topP
        val resolvedTopK = config.topK.takeIf { it > 0 } ?: AppConfigProvider.current.llm.topK
        val resolvedMaxTokens = config.maxTokens.takeIf { it > 0 } ?: AppConfigProvider.current.llm.maxTokens
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

    fun toggleReasoning(current: ReasoningEffort?): ReasoningEffort {
        val next = when (current) {
            ReasoningEffort.LOW -> ReasoningEffort.HIGH
            ReasoningEffort.HIGH -> ReasoningEffort.LOW
            else -> ReasoningEffort.HIGH
        }
        val effortStr = when (next) {
            ReasoningEffort.LOW -> "low"
            ReasoningEffort.HIGH -> "high"
        }
        val message = if (isReasoningSupported) {
            "Reasoning effort set: $effortStr"
        } else {
            "Reasoning effort set: $effortStr (not supported)"
        }
        Log.info(TAG_LLM, message)
        return next
    }
}
