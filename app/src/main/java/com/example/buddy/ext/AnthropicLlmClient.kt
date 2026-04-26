package com.example.buddy.ext

import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults
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

private const val TAG = "LLM"

class AnthropicLlmClient private constructor(
    private val apiKey: String,
    override val currentModel: String
) : LlmClient {
    override val isReasoningSupported: Boolean = false

    private val baseUrl = "https://api.anthropic.com/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        ))
        .build()

    private val gson = Gson()

    override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> = flow {
        val systemMessages = messages.filter { it.role == LlmRole.SYSTEM }
        val nonSystemMessages = messages.filter { it.role != LlmRole.SYSTEM }

        val apiMessages = nonSystemMessages.map { msg ->
            JsonObject().apply {
                addProperty("role", msg.role.toAnthropicRole())
                addProperty("content", msg.content)
            }
        }

        val requestBody = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                apiMessages.forEach { add(it) }
            })
            if (systemMessages.isNotEmpty()) {
                addProperty("system", systemMessages.joinToString("\n") { it.content })
            }
            addProperty("max_tokens", config.maxTokens.takeIf { it > 0 } ?: LlmDefaults.maxTokens)
            addProperty("temperature", config.temperature.takeIf { it > 0 } ?: LlmDefaults.temperature)
            addProperty("top_p", config.topP.takeIf { it > 0 } ?: LlmDefaults.topP)
            addProperty("stream", true)
        }

        val request = Request.Builder()
            .url("$baseUrl/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        var retryCount = 0
        val maxRetries = 2

        while (retryCount <= maxRetries) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("API error ${response.code}: ${response.body?.string()}")
                    }
                    val source: BufferedSource = response.body!!.source()
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()?.trim() ?: break
                        if (line.isEmpty() || !line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        try {
                            val json = gson.fromJson(data, JsonObject::class.java)
                            val type = json.get("type")?.asString
                            when (type) {
                                "content_block_delta" -> {
                                    val delta = json.getAsJsonObject("delta")
                                    if (delta != null) {
                                        val text = delta.get("text")?.asString
                                        if (!text.isNullOrEmpty()) {
                                            emit(text)
                                        }
                                    }
                                }
                                "message_delta" -> {
                                    val delta = json.getAsJsonObject("delta")
                                    if (delta != null) {
                                        val text = delta.get("text")?.asString
                                        if (!text.isNullOrEmpty()) {
                                            emit(text)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            EventLog.warning(TAG, "SSE parse failed", e.message)
                        }
                    }
                }
                break
            } catch (e: Exception) {
                retryCount++
                EventLog.warning(TAG, "Stream error (attempt $retryCount)", e.message)
                if (retryCount <= maxRetries) {
                    delay(1000L * retryCount)
                } else {
                    throw e
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getModels(): List<LlmModel> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://docs.anthropic.com/en/docs/about-claude/models")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    listOf(
                        LlmModel("claude-opus-4-20250514", "Claude Opus 4", false),
                        LlmModel("claude-sonnet-4-20250514", "Claude Sonnet 4", false),
                        LlmModel("claude-3-5-sonnet-20240620", "Claude 3.5 Sonnet", false),
                        LlmModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet (New)", false),
                        LlmModel("claude-3-opus-20240229", "Claude 3 Opus", false),
                        LlmModel("claude-3-sonnet-20240229", "Claude 3 Sonnet", false),
                        LlmModel("claude-3-haiku-20240307", "Claude 3 Haiku", false)
                    )
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to acquire model list", e.message)
                emptyList()
            }
        }
    }

    override suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = JsonObject().apply {
                    addProperty("model", currentModel)
                    addProperty("max_tokens", 1)
                    add("messages", JsonArray())
                }

                val request = Request.Builder()
                    .url("$baseUrl/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    EventLog.info(TAG, "Connection check completed (code: $code)")
                    response.isSuccessful || code == 400
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Connection check failed", e.message)
                false
            }
        }
    }

    override suspend fun generateSearchQuery(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                EventLog.debug(TAG, "Search query generation started", "Input: ${userMessage.take(200)}\nModel: $currentModel")
                val requestBody = JsonObject().apply {
                    addProperty("model", currentModel)
                    addProperty("max_tokens", LlmDefaults.SEARCH_QUERY_MAX_TOKENS)
                    addProperty("temperature", 0.3)
                    addProperty("system", LlmDefaults.searchQueryPrompt)
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", userMessage)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        EventLog.warning(TAG, "Failed to generate search query (code: ${response.code})")
                        return@withContext userMessage.take(50)
                    }
                    val bodyString = response.body?.string() ?: ""
                    val json = gson.fromJson(bodyString, JsonObject::class.java)
                    val content = json.getAsJsonArray("content")
                    if (content != null && content.size() > 0) {
                        val raw = content[0].asJsonObject.get("text")?.asString?.trim()
                        if (!raw.isNullOrBlank()) {
                            val query = raw.removeSurrounding("\"").trim()
                            EventLog.debug(TAG, "Search query raw response", "Raw: ${raw.take(500)}\nProcessed: $query")
                            return@withContext query
                        }
                    }
                    EventLog.debug(TAG, "Search query no content", "Body: ${bodyString.take(500)}")
                    userMessage.take(50)
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to generate search query", e.message)
                userMessage.take(50)
            }
        }
    }

    private fun LlmRole.toAnthropicRole(): String {
        return when (this) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
            LlmRole.SYSTEM -> "system"
        }
    }

    companion object {
        fun create(apiKey: String, model: String): Result<LlmClient> {
            return try {
                Result.success(AnthropicLlmClient(apiKey, model))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun create(providerId: String, apiKey: String, model: String): Result<LlmClient> {
            return try {
                val provider = com.example.buddy.data.LlmProviders.ALL.find { it.id == providerId }
                    ?: throw IllegalArgumentException("Unknown provider: $providerId")
                
                if (provider.apiType != com.example.buddy.data.ApiType.ANTHROPIC) {
                    throw IllegalArgumentException("Provider $providerId is not Anthropic")
                }
                
                Result.success(AnthropicLlmClient(apiKey, model))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}