package com.example.buddy.ext.llm

import com.example.buddy.BuildConfig
import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Summary
import com.example.buddy.data.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

private const val TAG_LLM = "LLM"

interface LlmClient {
    fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
    suspend fun getModels(): List<LlmModel>
    suspend fun testConnection(): Boolean
    suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): String?
    suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String? = null): Summary
    suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String? = null): Summary

    suspend fun generateSearchQuery(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): String? {
        val input = userMessage.take(1024)
        EventLog.debug(TAG_LLM, "Search query generation started", "Input: ${input.take(AppResources.search.logPreviewMaxChars)}\nModel: $activeModel", correlationId = correlationId)
        val raw = generateSearchQueryRaw(input, summaries, correlationId)

        // Some hybrid-reasoning models leak <think> blocks even when instructed to be terse;
        // strip complete and dangling (truncated) blocks before inspecting the result.
        val cleaned = raw
            ?.replace(Regex("(?is)<think>.*?</think>"), "")
            ?.replace(Regex("(?is)<think>.*"), "")
            ?.trim()

        if (cleaned != null && cleaned.equals("NO_QUERY", ignoreCase = true)) {
            EventLog.info(TAG_LLM, "Search query skipped (NO_QUERY)",
                "LLM indicated no web search is needed for this input", correlationId = correlationId)
            return null
        }

        EventLog.debug(TAG_LLM, "Search query raw response", "Raw: ${cleaned?.take(AppResources.search.logPreviewMaxChars) ?: "<null>"}", correlationId = correlationId)

        if (!cleaned.isNullOrBlank()) return cleaned

        EventLog.warning(TAG_LLM, "Search query generation failed",
            "API call returned null or blank (network error or no response). Raw: ${raw?.take(AppResources.search.logPreviewMaxChars)}",
            correlationId = correlationId)
        throw Exception("Unable to generate search query")
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
        val resolvedTemp = config.temperature.takeIf { it > 0 } ?: AppResources.llm.temperature
        val resolvedTopP = config.topP.takeIf { it > 0 } ?: AppResources.llm.topP
        val resolvedTopK = config.topK.takeIf { it > 0 } ?: AppResources.llm.topK
        val resolvedMaxTokens = config.maxTokens.takeIf { it > 0 } ?: AppResources.llm.maxTokens
        val paramDetail = "model=$model, temp=$resolvedTemp, topP=$resolvedTopP, topK=$resolvedTopK, maxTokens=$resolvedMaxTokens, reasoning=${config.reasoningEffort}"
        EventLog.info(TAG_LLM, "Request sent: ${messages.size} messages", data = paramDetail, correlationId = correlationId)
        if (BuildConfig.DEBUG) {
            val systemMsg = messages.find { it.role == Role.SYSTEM }?.content
            val debugData = buildString {
                appendLine("Config: $paramDetail")
                if (systemMsg != null) appendLine("System: $systemMsg")
                appendLine("Messages:")
                messages.forEach {
                    val preview = it.content.take(AppResources.search.logPreviewMaxChars) + if (it.content.length > AppResources.search.logPreviewMaxChars) "..." else ""
                    appendLine("${it.role}: $preview")
                }
            }
            EventLog.debug(TAG_LLM, "Request details", data = debugData, correlationId = correlationId)
        }
        var chunkCount = 0
        return streamCompletion(messages, model, config)
            .onEach { chunkCount++ }
            .onCompletion { throwable ->
                val durationMs = System.currentTimeMillis() - startTime
                if (throwable == null) {
                    EventLog.info(TAG_LLM, "Response received ($chunkCount chunks)", correlationId = correlationId, durationMs = durationMs)
                } else {
                    EventLog.error(TAG_LLM, "Response failed", throwable.message, correlationId = correlationId, durationMs = durationMs)
                }
            }
    }

    fun toggleReasoning(current: AppResources.ReasoningEffort?): AppResources.ReasoningEffort {
        val next = when (current) {
            AppResources.ReasoningEffort.LOW -> AppResources.ReasoningEffort.HIGH
            AppResources.ReasoningEffort.HIGH -> AppResources.ReasoningEffort.LOW
            else -> AppResources.ReasoningEffort.HIGH
        }
        val effortStr = when (next) {
            AppResources.ReasoningEffort.LOW -> "low"
            AppResources.ReasoningEffort.HIGH -> "high"
        }
        val message = if (isReasoningSupported) {
            "Reasoning effort set: $effortStr"
        } else {
            "Reasoning effort set: $effortStr (not supported)"
        }
        EventLog.info(TAG_LLM, message)
        return next
    }
}
