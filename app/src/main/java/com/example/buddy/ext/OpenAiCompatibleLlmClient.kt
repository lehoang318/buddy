package com.example.buddy.ext

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

class OpenAiCompatibleLlmClient private constructor(
    private val baseUrl: String,
    private val apiKey: String,
    override val currentModel: String
) : LlmClient {

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

    private val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')

    override fun streamCompletion(messages: List<LlmMessage>, model: String, config: LlmGenerationConfig): Flow<String> = flow {
        val apiMessages = messages.map { msg ->
            val messageObj = JsonObject()
            messageObj.addProperty("role", msg.role.toApiRole())
            if (msg.imageBase64 != null && msg.content.isNotBlank()) {
                val contentArray = JsonArray()
                val textPart = JsonObject()
                textPart.addProperty("type", "text")
                textPart.addProperty("text", msg.content)
                contentArray.add(textPart)
                val imagePart = JsonObject()
                imagePart.addProperty("type", "image_url")
                val imageUrl = JsonObject()
                imageUrl.addProperty("url", msg.imageBase64)
                imagePart.add("image_url", imageUrl)
                contentArray.add(imagePart)
                messageObj.add("content", contentArray)
            } else {
                messageObj.addProperty("content", msg.content)
            }
            messageObj
        }

        val requestBody = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply { apiMessages.forEach { add(it) } })
            addProperty("max_tokens", 4096)
            addProperty("temperature", config.temperature.toDouble())
            addProperty("top_p", config.topP.toDouble())
            addProperty("stream", true)
        }

        val request = Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
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
                            val choices = json.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                                if (delta != null && delta.has("content") && !delta.get("content").isJsonNull) {
                                    val content = delta.get("content").asString
                                    if (!content.isNullOrEmpty()) {
                                        emit(content)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            EventLog.add("W", "Failed to parse SSE chunk")
                        }
                    }
                }
                break
            } catch (e: Exception) {
                retryCount++
                EventLog.add("W", "Stream error (attempt $retryCount): ${e.message}")
                if (retryCount <= maxRetries) {
                    delay(1000L * retryCount)
                } else {
                    throw e
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun detectMultimodalFromApi(modelObj: JsonObject): Boolean {
        try {
            val architecture = modelObj.getAsJsonObject("architecture")
            if (architecture != null) {
                val modalityElement = architecture.get("modality")
                if (modalityElement != null && modalityElement.isJsonArray) {
                    val modality = modalityElement.asJsonArray
                    modality.forEach { element ->
                        if (element.isJsonPrimitive) {
                            val mod = element.asString.lowercase()
                            if (mod == "image" || mod == "vision" || mod == "audio" || mod == "video") {
                                return true
                            }
                        }
                    }
                }
            }
            val modalityElement = modelObj.get("modality")
            if (modalityElement != null && modalityElement.isJsonArray) {
                val modalityArray = modalityElement.asJsonArray
                modalityArray.forEach { element ->
                    if (element.isJsonPrimitive) {
                        val mod = element.asString.lowercase()
                        if (mod == "image" || mod == "vision" || mod == "audio" || mod == "video") {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            EventLog.add("W", "Error parsing modality from API")
        }
        return false
    }

    private fun detectMultimodalFromId(modelId: String): Boolean {
        val id = modelId.lowercase()
        val shortId = id.substringAfterLast('/')

        val multimodalPatterns = listOf(
            "gpt-4o", "gpt-4-turbo", "gpt-4-vision", "o1", "o3", "o4",
            "claude-3", "claude-4", "gemini", "vision", "-vl", "-vlm",
            "multimodal", "llava", "qwen-vl", "yi-vl", "internvl",
            "deepseek-vl", "minicpm-v", "cogvlm", "pixtral", "idefics",
            "phi-3-vision", "phi-4", "lucy"
        )

        multimodalPatterns.forEach { pattern ->
            if (shortId.contains(pattern)) return true
        }

        if (shortId.matches(Regex("gpt-4-[0-9].*"))) return true

        return false
    }

    override suspend fun getModels(): List<LlmModel> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/models")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyString = response.body?.string() ?: ""
                    val json = gson.fromJson(bodyString, JsonObject::class.java)
                    val data = json.getAsJsonArray("data")
                    data.map { modelObj ->
                        val modelJson = modelObj.asJsonObject
                        val id = modelJson.get("id").asString
                        val isMultimodal = detectMultimodalFromApi(modelJson) || detectMultimodalFromId(id)
                        LlmModel(id = id, name = id, isMultimodal = isMultimodal)
                    }.sortedWith(
                        compareByDescending<LlmModel> { it.isMultimodal }
                            .thenBy { it.name }
                    )
                }
            } catch (e: Exception) {
                EventLog.add("E", "getModels failed: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$normalizedBaseUrl/models"
                EventLog.add("I", "Testing connection: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string()
                    EventLog.add("I", "Connection test: code=$code")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                EventLog.add("E", "Connection test failed: ${e.message}")
                false
            }
        }
    }

    override suspend fun generateSearchQuery(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val systemMsg = JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "You are a search query generator. Generate a concise, focused web search query (max 5 words) based on the user's message. Return ONLY the query text, nothing else. Do not include quotes or explanations.")
                }
                val userMsg = JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userMessage)
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", currentModel)
                    add("messages", JsonArray().apply {
                        add(systemMsg)
                        add(userMsg)
                    })
                    addProperty("max_tokens", 20)
                    addProperty("temperature", 0.3)
                }

                val request = Request.Builder()
                    .url("$normalizedBaseUrl/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        EventLog.add("W", "generateSearchQuery failed: code=${response.code}")
                        return@withContext userMessage.take(50)
                    }
                    val bodyString = response.body?.string() ?: ""
                    val json = gson.fromJson(bodyString, JsonObject::class.java)
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val message = choices[0].asJsonObject.getAsJsonObject("message")
                        val content = message?.get("content")?.asString?.trim()
                        if (!content.isNullOrBlank()) {
                            content.removeSurrounding("\"").trim()
                        } else {
                            userMessage.take(50)
                        }
                    } else {
                        userMessage.take(50)
                    }
                }
            } catch (e: Exception) {
                EventLog.add("E", "generateSearchQuery failed: ${e.message}")
                userMessage.take(50)
            }
        }
    }

    private fun LlmRole.toApiRole(): String {
        return when (this) {
            LlmRole.USER -> "user"
            LlmRole.ASSISTANT -> "assistant"
            LlmRole.SYSTEM -> "system"
        }
    }

    companion object {
        fun create(baseUrl: String, apiKey: String, model: String): Result<LlmClient> {
            return try {
                Result.success(OpenAiCompatibleLlmClient(baseUrl, apiKey, model))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}