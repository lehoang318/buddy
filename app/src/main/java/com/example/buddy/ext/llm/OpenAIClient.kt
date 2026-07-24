package com.example.buddy.ext.llm

import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Summary
import com.example.buddy.data.Role
import com.example.buddy.data.SummaryPoint
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "LLM"

private fun currentDateString(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))

private fun truncateAtWordBoundary(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text.trim()
    val cut = text.take(maxChars)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > maxChars / 2) cut.take(lastSpace).trim() else cut.trim()
}

open class OpenAIClient internal constructor(
    protected val baseUrl: String,
    override val defaultModel: String,
    protected val httpClient: OkHttpClient
) : LlmClient {
    override var activeModel: String = defaultModel
    override val isReasoningSupported: Boolean = true

    protected val gson = Gson()

    private val normalizedBaseUrl: String
        get() = baseUrl.trimEnd('/')

    private fun executeStreamingRequest(requestBody: JsonObject, cancellationJob: Job? = null): String? {
        requestBody.addProperty("stream", true)

        val request = Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val call = httpClient.newCall(request)
        cancellationJob?.invokeOnCompletion { call.cancel() }

        return call.execute().use { response ->
            if (!response.isSuccessful) return@use null
            val source: BufferedSource = response.body!!.source()
            val content = StringBuilder()
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
                            val chunk = delta.get("content").asString
                            if (!chunk.isNullOrEmpty()) content.append(chunk)
                        }
                    }
                } catch (_: Exception) { }
            }
            content.toString().takeIf { it.isNotBlank() }
        }
    }

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
            addProperty("max_tokens", config.maxTokens.takeIf { it > 0 } ?: AppResources.llm.maxTokens)
            addProperty("temperature", config.temperature.takeIf { it > 0 } ?: AppResources.llm.temperature)
            addProperty("top_p", config.topP.takeIf { it > 0 } ?: AppResources.llm.topP)
            addReasoningParameter(this, config.reasoningEffort, forSearchQuery = false)
            addProperty("stream", true)
        }

        val request = Request.Builder()
            .url("$normalizedBaseUrl/chat/completions")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        var retryCount = 0
        var currentCall: Call? = null
        currentCoroutineContext()[Job]?.invokeOnCompletion { currentCall?.cancel() }
        
        while (true) {
            try {
                currentCall = httpClient.newCall(request)
                currentCall!!.execute().use { response ->
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
                            EventLog.warning(TAG, "SSE parse failed", e.message)
                        }
                    }
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                retryCount++
                EventLog.warning(TAG, "Stream error (attempt $retryCount)", e.message)
                if (retryCount <= 2) {
                    delay(1000L * retryCount)
                } else {
                    throw e
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    protected open fun addReasoningParameter(requestBody: JsonObject, effort: AppResources.ReasoningEffort?, forSearchQuery: Boolean = false) {
    }

    protected open fun shouldIncludeModel(modelJson: JsonObject): Boolean {
        return true
    }

    protected open fun getModelDisplayName(modelId: String): String = modelId

    protected open fun detectMultimodalFromApi(modelObj: JsonObject): Boolean {
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
            EventLog.warning(TAG, "Error parsing modality from API", e.message)
        }
        return false
    }

    open fun isModelMultimodal(modelId: String): Boolean {
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

        return false
    }

    override suspend fun getModels(): List<LlmModel> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$normalizedBaseUrl/models")
                    .header("Content-Type", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyString = response.body?.string() ?: ""
                    val element = gson.fromJson(bodyString, JsonElement::class.java)
                    val data = when {
                        element.isJsonArray -> element.asJsonArray
                        element.isJsonObject && element.asJsonObject.has("data") ->
                            element.asJsonObject.getAsJsonArray("data")
                        else -> {
                            EventLog.warning(TAG, "Unexpected /models response format")
                            return@withContext emptyList()
                        }
                    }
                    data.mapNotNull { modelObj ->
                        val modelJson = modelObj.asJsonObject
                        if (!shouldIncludeModel(modelJson)) return@mapNotNull null
                        val id = modelJson.get("id").asString
                        val isMultimodal = detectMultimodalFromApi(modelJson) || isModelMultimodal(id)
                        LlmModel(id = id, name = getModelDisplayName(id), isMultimodal = isMultimodal)
                    }.sortedWith(
                        compareByDescending<LlmModel> { it.isMultimodal }
                            .thenBy { it.name }
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
                val url = "$normalizedBaseUrl/models"
                EventLog.info(TAG, "Connection check", data = url)
                val request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    EventLog.info(TAG, "Connection check completed (code: $code)")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Connection check failed", e.message)
                false
            }
        }
    }

    override suspend fun generateSearchQueryRaw(userMessage: String, summaries: List<Summary>, correlationId: String?): String? {
        return withContext(Dispatchers.IO) {
            try {
                val userMsg = JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userMessage)
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", activeModel)
                    add("messages", JsonArray().apply {
                        val dateNote = "Current date: ${currentDateString()}.\n\n"
                        val limitNote = "\n\nYour response must be under ${AppResources.search.queryMaxTokens} characters."
                        val systemContent = if (summaries.isNotEmpty()) {
                            dateNote + AppResources.search.queryPrompt + limitNote + "\n\n" + AppResources.summaries.formatSummariesContext(summaries)
                        } else {
                            dateNote + AppResources.search.queryPrompt + limitNote
                        }
                        add(JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemContent)
                        })
                        add(userMsg)
                    })
                    addProperty("max_tokens", 4096)
                    addProperty("temperature", AppResources.search.queryTemperature)
                    addReasoningParameter(this, AppResources.ReasoningEffort.LOW, forSearchQuery = true)
                }

                EventLog.debug(TAG, "Search query request", gson.toJson(requestBody), correlationId = correlationId)

                val content = executeStreamingRequest(requestBody, coroutineContext[Job])
                if (content == null) {
                    EventLog.warning(TAG, "Failed to generate search query", correlationId = correlationId)
                    return@withContext null
                }
                return@withContext truncateAtWordBoundary(content, AppResources.search.queryMaxTokens)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to generate search query", e.message, correlationId = correlationId)
                null
            }
        }
    }

    override suspend fun generateSummary(userQuestion: String, assistantResponse: String, model: String?): Summary {
        return withContext(Dispatchers.IO) {
            try {
                val userContent = AppResources.prompts.summarizerUserTemplate
                    .format(userQuestion, assistantResponse)

                val systemMsg = JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", AppResources.prompts.summarizerSystem.format(AppResources.summaries.minPoints, AppResources.summaries.maxPoints) +
                        "\n\nYour response must fit within ${AppResources.summaries.maxTokens} tokens maximum.")
                }
                val userMsg = JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userContent)
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", model ?: activeModel)
                    add("messages", JsonArray().apply {
                        add(systemMsg)
                        add(userMsg)
                    })
                    addProperty("temperature", AppResources.summaries.temperature)
                    addProperty("max_tokens", AppResources.summaries.maxTokens)
                    add("response_format", JsonObject().apply {
                        addProperty("type", "json_object")
                    })
                    addReasoningParameter(this, null, forSearchQuery = false)
                }

                EventLog.debug(TAG, "Summary generation request", correlationId = null)

                val content = executeStreamingRequest(requestBody, coroutineContext[Job])
                if (content == null) {
                    EventLog.warning(TAG, "Failed to generate summary")
                    return@withContext Summary(question = userQuestion, points = emptyList())
                }

                val cleanContent = content.trim()
                    .removePrefix("```json\n")
                    .removePrefix("```json")
                    .removePrefix("```\n")
                    .removePrefix("```")
                    .removeSuffix("\n```")
                    .removeSuffix("```")
                    .trim()
                    .let { if (it.startsWith("\"") && it.endsWith("\"")) it.removeSurrounding("\"") else it }

                try {
                    val parsed = try {
                        gson.fromJson(cleanContent, JsonObject::class.java)
                    } catch (_: Exception) {
                        try {
                            val array = gson.fromJson(cleanContent, JsonArray::class.java)
                            JsonObject().apply { add("points", array) }
                        } catch (_: Exception) {
                            val text = try {
                                gson.fromJson(cleanContent, String::class.java)
                            } catch (_: Exception) {
                                cleanContent.takeIf { it.isNotBlank() }
                            }
                            if (text != null && text.isNotBlank()) {
                                JsonObject().apply {
                                    add("points", JsonArray().apply {
                                        add(JsonObject().apply {
                                            addProperty("text", text)
                                            addProperty("key", false)
                                        })
                                    })
                                }
                            } else {
                                null
                            }
                        }
                    }
                    val pointsArray = parsed?.getAsJsonArray("points") ?: throw Exception("Missing points array")
                    val points = pointsArray.mapNotNull { elem ->
                        if (elem.isJsonObject) {
                            val obj = elem.asJsonObject
                            val text = obj.get("text")?.asString
                            if (text.isNullOrBlank()) return@mapNotNull null
                            SummaryPoint(text = text, key = obj.get("key")?.asBoolean ?: false)
                        } else if (elem.isJsonPrimitive) {
                            val text = elem.asString
                            if (text.isBlank()) return@mapNotNull null
                            SummaryPoint(text = text, key = false)
                        } else {
                            null
                        }
                    }
                    val sanitized = AppResources.summaries.sanitizeSummaryPoints(points)
                    EventLog.debug(TAG, "Summary generated",
                        "Question: ${userQuestion.take(200)}\nPoints: ${sanitized.size}\n" +
                        sanitized.joinToString("\n") { "${if (it.key) "[KEY] " else ""}${it.text}" })
                    return@withContext Summary(question = userQuestion, points = sanitized)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    EventLog.warning(TAG, "Failed to parse summary JSON", e.message)
                    return@withContext Summary(question = userQuestion, points = emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to generate summary", e.message)
                Summary(question = userQuestion, points = emptyList())
            }
        }
    }

    override suspend fun compressSummaries(summariesToCompress: List<Summary>, model: String?): Summary {
        return withContext(Dispatchers.IO) {
            try {
                val keyPoints = mutableListOf<SummaryPoint>()
                val formattedParts = mutableListOf<String>()

                for (s in summariesToCompress) {
                    val keys = s.points.filter { it.key }
                    val nonKeys = s.points.filter { !it.key }
                    keyPoints.addAll(keys)

                    formattedParts.add("- ${s.question}")
                    if (nonKeys.isNotEmpty()) {
                        for (p in nonKeys) {
                            formattedParts.add("  + ${p.text}")
                        }
                    } else {
                        formattedParts.add("  (key points retained separately)")
                    }
                }

                val summariesText = formattedParts.joinToString("\n")
                val prompt = AppResources.prompts.compressSummaries.format(AppResources.summaries.minPoints, AppResources.summaries.maxPoints, summariesText)

                val systemMsg = JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", prompt + "\n\nYour response must fit within ${AppResources.summaries.maxTokens} tokens maximum.")
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", model ?: activeModel)
                    add("messages", JsonArray().apply {
                        add(systemMsg)
                    })
                    addProperty("temperature", AppResources.summaries.temperature)
                    addProperty("max_tokens", AppResources.summaries.maxTokens)
                    add("response_format", JsonObject().apply {
                        addProperty("type", "json_object")
                    })
                    addReasoningParameter(this, null, forSearchQuery = false)
                }

                EventLog.debug(TAG, "Summary compression request", "Compressing ${summariesToCompress.size} summaries")

                val content = executeStreamingRequest(requestBody, coroutineContext[Job])
                if (content == null) {
                    EventLog.warning(TAG, "Failed to compress summaries")
                    return@withContext Summary(question = "Earlier conversation", points = keyPoints)
                }

                val cleanContent = content.trim()
                    .removePrefix("```json\n")
                    .removePrefix("```json")
                    .removePrefix("```\n")
                    .removePrefix("```")
                    .removeSuffix("\n```")
                    .removeSuffix("```")
                    .trim()
                    .let { if (it.startsWith("\"") && it.endsWith("\"")) it.removeSurrounding("\"") else it }

                try {
                    val parsed = try {
                        gson.fromJson(cleanContent, JsonObject::class.java)
                    } catch (_: Exception) {
                        try {
                            val array = gson.fromJson(cleanContent, JsonArray::class.java)
                            JsonObject().apply { add("points", array) }
                        } catch (_: Exception) {
                            val text = try {
                                gson.fromJson(cleanContent, String::class.java)
                            } catch (_: Exception) {
                                cleanContent.takeIf { it.isNotBlank() }
                            }
                            if (text != null && text.isNotBlank()) {
                                JsonObject().apply {
                                    add("points", JsonArray().apply {
                                        add(JsonObject().apply {
                                            addProperty("text", text)
                                            addProperty("key", false)
                                        })
                                    })
                                }
                            } else {
                                null
                            }
                        }
                    }
                    val pointsArray = parsed?.getAsJsonArray("points") ?: throw Exception("Missing points array")
                    val llmPoints = pointsArray.mapNotNull { elem ->
                        if (elem.isJsonObject) {
                            val obj = elem.asJsonObject
                            val text = obj.get("text")?.asString
                            if (text.isNullOrBlank()) return@mapNotNull null
                            SummaryPoint(text = text, key = false)
                        } else if (elem.isJsonPrimitive) {
                            val text = elem.asString
                            if (text.isBlank()) return@mapNotNull null
                            SummaryPoint(text = text, key = false)
                        } else {
                            null
                        }
                    }
                    val combined = llmPoints + keyPoints
                    val sanitized = AppResources.summaries.sanitizeSummaryPoints(combined)
                    EventLog.debug(TAG, "Summary compression completed",
                        "LLM points: ${llmPoints.size}, Key points retained: ${keyPoints.size}, Combined: ${combined.size}")
                    return@withContext Summary(question = "Earlier conversation", points = sanitized)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    EventLog.warning(TAG, "Failed to parse compressed summary JSON", e.message)
                    return@withContext Summary(question = "Earlier conversation", points = keyPoints)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EventLog.error(TAG, "Failed to compress summaries", e.message)
                Summary(question = "Earlier conversation", points = emptyList())
            }
        }
    }

    private fun Role.toApiRole(): String {
        return when (this) {
            Role.USER -> "user"
            Role.ASSISTANT -> "assistant"
            Role.SYSTEM -> "system"
        }
    }
}
