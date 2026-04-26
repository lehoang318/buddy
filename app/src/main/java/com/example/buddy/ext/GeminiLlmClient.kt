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

class GeminiLlmClient private constructor(
    private val apiKey: String,
    override val currentModel: String
) : LlmClient {
    override val isReasoningSupported: Boolean = false

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

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
        val conversationMessages = messages.filter { it.role != LlmRole.SYSTEM }

        val contents = conversationMessages.map { msg ->
            JsonObject().apply {
                addProperty("role", msg.role.toGeminiRole())
                val parts = JsonArray()
                val part = JsonObject()
                part.addProperty("text", msg.content)
                parts.add(part)
                add("parts", parts)
            }
        }

        val requestBody = JsonObject().apply {
            if (systemMessages.isNotEmpty()) {
                val systemText = systemMessages.joinToString("\n") { it.content }
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemText) })
                    })
                })
            }
            add("contents", JsonArray().apply { contents.forEach { add(it) } })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", config.temperature.takeIf { it > 0 } ?: LlmDefaults.temperature)
                addProperty("topP", config.topP.takeIf { it > 0 } ?: LlmDefaults.topP)
                addProperty("topK", config.topK.takeIf { it > 0 } ?: LlmDefaults.topK)
                addProperty("maxOutputTokens", config.maxTokens.takeIf { it > 0 } ?: LlmDefaults.maxTokens)
            })
        }

        val request = Request.Builder()
            .url("$baseUrl/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
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
                        if (data == "[DONE]") break
                        try {
                            val json = gson.fromJson(data, JsonObject::class.java)
                            val candidates = json.getAsJsonArray("candidates")
                            if (candidates != null && candidates.size() > 0) {
                                val content = candidates[0].asJsonObject.getAsJsonObject("content")
                                val parts = content?.getAsJsonArray("parts")
                                if (parts != null) {
                                    for (partEl in parts) {
                                        val part = partEl.asJsonObject
                                        // skip thought/reasoning blocks (Gemini 2.5+ thinking)
                                        if (part.get("thought")?.asBoolean == true) continue
                                        val text = part.get("text")?.asString
                                        if (!text.isNullOrEmpty()) emit(text)
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
                    .url("$baseUrl/models")
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyString = response.body?.string() ?: ""
                    val json = gson.fromJson(bodyString, JsonObject::class.java)
                    val models = json.getAsJsonArray("models")
                    models?.mapNotNull { modelObj ->
                        val obj = modelObj.asJsonObject
                        val supportedMethods = obj.getAsJsonArray("supportedGenerationMethods")
                        val supportsGenerate = supportedMethods?.any {
                            it.asString == "generateContent" || it.asString == "streamGenerateContent"
                        } ?: false
                        if (!supportsGenerate) return@mapNotNull null
                        val id = obj.get("name")?.asString?.substringAfterLast("/")
                            ?: return@mapNotNull null
                        LlmModel(id = id, name = id, isMultimodal = id.contains("vision") || id.contains("gemini"))
                    } ?: emptyList()
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
                    add("contents", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            add("parts", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("text", "test")
                                })
                            })
                        })
                    })
                    add("generationConfig", JsonObject().apply {
                        addProperty("maxOutputTokens", 1)
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/models/$currentModel:generateContent")
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string()
                    EventLog.info(TAG, "Connection check completed (code: $code)", body)
                    response.isSuccessful
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
                    add("systemInstruction", JsonObject().apply {
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", LlmDefaults.searchQueryPrompt)
                            })
                        })
                    })
                    add("contents", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            add("parts", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("text", userMessage)
                                })
                            })
                        })
                    })
                    add("generationConfig", JsonObject().apply {
                        addProperty("maxOutputTokens", LlmDefaults.SEARCH_QUERY_MAX_TOKENS)
                        addProperty("temperature", 0.3)
                        add("thinkingConfig", JsonObject().apply {
                            addProperty("thinkingBudget", 0)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/models/$currentModel:generateContent")
                    .header("x-goog-api-key", apiKey)
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
                    val candidates = json.getAsJsonArray("candidates")
                    if (candidates != null && candidates.size() > 0) {
                        val content = candidates[0].asJsonObject.getAsJsonObject("content")
                        if (content != null) {
                            val parts = content.getAsJsonArray("parts")
                            if (parts != null && parts.size() > 0) {
                                val raw = parts[0].asJsonObject.get("text")?.asString?.trim()
                                val text = raw
                                    ?.replace(Regex("```[\\w]*\\s*THOUGHT:[\\s\\S]*?```", RegexOption.IGNORE_CASE), "")
                                    ?.replace(Regex("(?i)THOUGHT:.*", RegexOption.DOT_MATCHES_ALL), "")
                                    ?.trim()
                                if (!text.isNullOrBlank()) {
                                    val query = text.removeSurrounding("\"").trim()
                                    EventLog.debug(TAG, "Search query raw response", "Raw: ${raw.take(500)}\nProcessed: $query")
                                    return@withContext query
                                }
                            }
                        }
                    }
                    EventLog.debug(TAG, "Search query no candidates", "Body: ${bodyString.take(500)}")
                    userMessage.take(50)
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to generate search query", e.message)
                userMessage.take(50)
            }
        }
    }

    private fun LlmRole.toGeminiRole(): String {
        return when (this) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "model"
            LlmRole.SYSTEM -> "user" // filtered out before contents[] is built; fallback only
        }
    }

    companion object {
        fun create(apiKey: String, model: String): Result<LlmClient> {
            return try {
                Result.success(GeminiLlmClient(apiKey, model))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun create(providerId: String, apiKey: String, model: String): Result<LlmClient> {
            return try {
                val provider = com.example.buddy.data.LlmProviders.ALL.find { it.id == providerId }
                    ?: throw IllegalArgumentException("Unknown provider: $providerId")
                
                if (provider.apiType != com.example.buddy.data.ApiType.GEMINI) {
                    throw IllegalArgumentException("Provider $providerId is not Gemini")
                }
                
                Result.success(GeminiLlmClient(apiKey, model))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}