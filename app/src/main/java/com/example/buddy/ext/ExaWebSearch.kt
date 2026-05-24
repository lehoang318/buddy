package com.example.buddy.ext

import com.example.buddy.crypto.SessionKeyCache
import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException

private const val TAG = "WebSearch"

class ExaWebSearch(
    private val httpClient: OkHttpClient,
    private val keyCache: SessionKeyCache,
    private val providerId: String
) : WebSearch {

    private val gson = Gson()

    override fun isAvailable(): Boolean {
        val keyBytes = keyCache.getKey(providerId) ?: return false
        keyBytes.fill(0)
        return true
    }

    override suspend fun search(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val requestBody = JsonObject().apply {
                addProperty("query", query)
                addProperty("numResults", AppResources.search.maxResults)
                addProperty("type", "auto")
                add("contents", JsonObject().apply {
                    addProperty("text", true)
                })
            }

            val request = Request.Builder()
                .url("https://api.exa.ai/search")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            var lastException: Exception? = null
            val maxRetries = 2

            for (attempt in 0..maxRetries) {
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            val errorMsg = when (response.code) {
                                401, 403 -> "Invalid Exa API key"
                                429 -> "Exa usage limit exceeded"
                                else -> "Web search failed: HTTP ${response.code}"
                            }
                            EventLog.error(TAG, "Search failed", "Query: $query\nCode: ${response.code}\nError: $errorMsg\nBody: $errorBody")
                            throw Exception("$errorMsg ($errorBody)")
                        }
                        val bodyString = response.body?.string() ?: ""
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val results = json.getAsJsonArray("results")
                            ?: throw Exception("Missing 'results' array in response")
                        return@withContext results.map { resultObj ->
                            val obj = resultObj.asJsonObject
                            val textElement = obj.get("text")
                            val content = when {
                                textElement?.isJsonObject == true -> textElement.asJsonObject.get("text")?.asString ?: ""
                                textElement?.isJsonPrimitive == true -> textElement.asString
                                else -> ""
                            }
                            SearchResult(
                                title = obj.get("title")?.asString ?: "",
                                url = obj.get("url")?.asString ?: "",
                                content = content
                            )
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    EventLog.warning(TAG, "Timeout (attempt ${attempt + 1}/$maxRetries)", "Query: $query\nAttempt: ${attempt + 1}/$maxRetries\nException: SocketTimeoutException")
                    if (attempt < maxRetries) delay(1000L * (attempt + 1))
                } catch (e: Exception) {
                    lastException = e
                    EventLog.error(TAG, "Search failed (attempt ${attempt + 1}/$maxRetries)", "Query: $query\nAttempt: ${attempt + 1}/$maxRetries\nException: ${e.message}")
                    if (attempt < maxRetries) delay(1000L * (attempt + 1))
                }
            }
            throw lastException ?: Exception("Web search failed after $maxRetries retries")
        }
    }
}
