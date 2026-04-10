package com.example.buddy.ext

import com.example.buddy.data.ApiType
import com.example.buddy.data.LlmProviders
import com.example.buddy.data.EventLog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.util.concurrent.TimeUnit

data class LlmModel(
    val id: String,
    val name: String,
    val isMultimodal: Boolean = false
)

data class LlmGenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 20
)

interface LlmClient {
    fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig = LlmGenerationConfig()): Flow<String>
    suspend fun getModels(): List<LlmModel>
    suspend fun testConnection(): Boolean
    suspend fun generateSearchQuery(userMessage: String): String
    val currentModel: String
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
    fun createWithBaseUrl(baseUrl: String, apiKey: String, model: String): Result<LlmClient> {
        return OpenAiCompatibleLlmClient.create(baseUrl, apiKey, model)
    }

    fun createWithProviderId(providerId: String, apiKey: String, model: String): Result<LlmClient> {
        return try {
            val provider = LlmProviders.ALL.find { it.id == providerId }
                ?: throw IllegalArgumentException("Unknown provider: $providerId")
            
            val client = when (provider.apiType) {
                ApiType.OPENAI_COMPATIBLE -> OpenAiCompatibleLlmClient.create(provider.baseUrl, apiKey, model).getOrThrow()
                ApiType.ANTHROPIC -> AnthropicLlmClient.create(apiKey, model).getOrThrow()
                ApiType.GEMINI -> GeminiLlmClient.create(apiKey, model).getOrThrow()
            }
            Result.success(client)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModels(providerId: String, apiKey: String): List<LlmModel> {
        return try {
            val provider = LlmProviders.ALL.find { it.id == providerId }
                ?: return emptyList()
            
            when (provider.apiType) {
                ApiType.OPENAI_COMPATIBLE -> {
                    val client = OpenAiCompatibleLlmClient.create(provider.baseUrl, apiKey, "temp").getOrNull()
                    client?.getModels() ?: emptyList()
                }
                ApiType.ANTHROPIC -> {
                    val client = AnthropicLlmClient.create(apiKey, "temp").getOrNull()
                    client?.getModels() ?: emptyList()
                }
                ApiType.GEMINI -> {
                    val client = GeminiLlmClient.create(apiKey, "temp").getOrNull()
                    client?.getModels() ?: emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}