package com.example.buddy.ext

import com.example.buddy.BuildConfig
import com.example.buddy.data.ApiType
import com.example.buddy.data.LlmDefaults
import com.example.buddy.data.LlmProvider
import com.example.buddy.data.EventLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

private const val TAG_LLM = "LLM"

data class LlmModel(
    val id: String,
    val name: String,
    val isMultimodal: Boolean = false
)

data class LlmGenerationConfig(
    val temperature: Float = 0f,
    val topP: Float = 0f,
    val topK: Int = 0,
    val maxTokens: Int = 0,
    val reasoningEffort: LlmDefaults.ReasoningEffort? = null
)

interface LlmClient {
    fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
    suspend fun getModels(): List<LlmModel>
    suspend fun testConnection(): Boolean
    suspend fun generateSearchQueryRaw(userMessage: String): String?

    suspend fun generateSearchQuery(userMessage: String): String {
        val input = userMessage.take(1024)
        EventLog.debug(TAG_LLM, "Search query generation started", "Input: ${input.take(LlmDefaults.logPreviewMaxChars)}\nModel: $currentModel")
        val raw = generateSearchQueryRaw(input)
        val processed = LlmDefaults.sanitizeSearchQueryResponse(raw)
        EventLog.debug(TAG_LLM, "Search query raw response", "Raw: ${raw?.take(LlmDefaults.logPreviewMaxChars)}\nProcessed: ${processed ?: "<fallback>"}")
        return processed ?: userMessage.take(50)
    }

    val currentModel: String
    val isReasoningSupported: Boolean

    fun streamCompletionWithLogging(
        messages: List<LlmMessage>,
        model: String,
        config: LlmGenerationConfig = LlmGenerationConfig(),
        correlationId: String? = null
    ): Flow<String> {
        val startTime = System.currentTimeMillis()
        val resolvedTemp = config.temperature.takeIf { it > 0 } ?: LlmDefaults.temperature
        val resolvedTopP = config.topP.takeIf { it > 0 } ?: LlmDefaults.topP
        val resolvedTopK = config.topK.takeIf { it > 0 } ?: LlmDefaults.topK
        val resolvedMaxTokens = config.maxTokens.takeIf { it > 0 } ?: LlmDefaults.maxTokens
        val paramDetail = "model=$model, temp=$resolvedTemp, topP=$resolvedTopP, topK=$resolvedTopK, maxTokens=$resolvedMaxTokens, reasoning=${config.reasoningEffort}"
        EventLog.info(TAG_LLM, "Request sent: ${messages.size} messages", data = paramDetail, correlationId = correlationId)
        if (BuildConfig.DEBUG) {
            val systemMsg = messages.find { it.role == LlmRole.SYSTEM }?.content
            val debugData = buildString {
                appendLine("Config: $paramDetail")
                if (systemMsg != null) appendLine("System: $systemMsg")
                appendLine("Messages:")
                messages.forEach {
                    val preview = it.content.take(LlmDefaults.logPreviewMaxChars) + if (it.content.length > LlmDefaults.logPreviewMaxChars) "..." else ""
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

    fun toggleReasoning(current: LlmDefaults.ReasoningEffort?): LlmDefaults.ReasoningEffort {
        val next = when (current) {
            LlmDefaults.ReasoningEffort.LOW -> LlmDefaults.ReasoningEffort.HIGH
            LlmDefaults.ReasoningEffort.HIGH -> LlmDefaults.ReasoningEffort.LOW
            else -> LlmDefaults.ReasoningEffort.HIGH
        }
        val effortStr = when (next) {
            LlmDefaults.ReasoningEffort.LOW -> "low"
            LlmDefaults.ReasoningEffort.HIGH -> "high"
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

data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val imageBase64: String? = null
)

enum class LlmRole {
    USER,
    ASSISTANT,
    SYSTEM
}

object LlmClientFactory {
    fun createWithProvider(provider: LlmProvider, apiKey: String, model: String): Result<LlmClient> {
        val key = apiKey.ifBlank { provider.apiKey }
        return OpenAiCompatibleLlmClient.create(provider.baseUrl, key, model)
    }

    fun createWithBaseUrl(baseUrl: String, apiKey: String, model: String): Result<LlmClient> {
        return OpenAiCompatibleLlmClient.create(baseUrl, apiKey, model)
    }

    suspend fun getModels(provider: LlmProvider, apiKey: String): List<LlmModel> {
        val key = apiKey.ifBlank { provider.apiKey }
        val client = OpenAiCompatibleLlmClient.create(provider.baseUrl, key, "temp").getOrNull()
        return client?.getModels() ?: emptyList()
    }
}
