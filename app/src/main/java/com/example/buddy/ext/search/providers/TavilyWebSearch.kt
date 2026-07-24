package com.example.buddy.ext.search.providers

import com.example.buddy.crypto.SessionKeyCache
import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.ext.search.SearchResponse
import com.example.buddy.ext.search.SearchResult
import com.example.buddy.ext.search.WebSearch
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

private const val TAG = "WebSearch"

class TavilyWebSearch(
    private val keyCache: SessionKeyCache,
    private val providerId: String
) : WebSearch {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)  // Keep HTTP/2 alive
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(
            maxIdleConnections = 3,
            keepAliveDuration = 3,
            timeUnit = TimeUnit.MINUTES
        ))
        .build()

    private val gson = Gson()

    override fun isAvailable(): Boolean {
        val keyBytes = keyCache.getKey(providerId) ?: return false
        keyBytes.fill(0)
        return true
    }

    override suspend fun search(query: String): SearchResponse {
        return withContext(Dispatchers.IO) {
            val keyBytes = keyCache.getKey(providerId) ?: throw Exception("No API key for Tavily")
            val tavilyKey = String(keyBytes, Charsets.UTF_8)
            keyBytes.fill(0)

            val requestBody = JsonObject().apply {
                addProperty("query", query)
                addProperty("api_key", tavilyKey)
                addProperty("max_results", AppResources.search.maxResults)
                addProperty("search_depth", "advanced")
                addProperty("chunks_per_source", 3)
                addProperty("include_answer", "advanced")
            }

            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            var lastException: Exception? = null
            val maxRetries = 2
            var currentCall: Call? = null
            coroutineContext[Job]?.invokeOnCompletion { currentCall?.cancel() }
            
            for (attempt in 0..maxRetries) {
                try {
                    currentCall = client.newCall(request)
                    currentCall!!.execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            val errorMsg = when (response.code) {
                                401, 403 -> "Invalid Tavily API key"
                                429 -> "Tavily usage limit exceeded"
                                else -> "Web search failed: HTTP ${response.code}"
                            }
                            EventLog.error(TAG, "Search failed", "Query: $query\nCode: ${response.code}\nError: $errorMsg\nBody: $errorBody")
                            throw Exception("$errorMsg ($errorBody)")
                        }
                        val bodyString = response.body?.string() ?: ""
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val results = json.getAsJsonArray("results")
                        val searchResults = results.map { resultObj ->
                            val obj = resultObj.asJsonObject
                            SearchResult(
                                title = obj.get("title")?.asString ?: "",
                                url = obj.get("url")?.asString ?: "",
                                content = obj.get("content")?.asString ?: "",
                                publishedDate = obj.get("published_date")?.takeIf { !it.isJsonNull }?.asString
                            )
                        }
                        val answer = json.get("answer")?.takeIf { !it.isJsonNull }?.asString?.trim()?.ifBlank { null }
                        return@withContext SearchResponse(searchResults, answer)
                    }
                } catch (e: CancellationException) {
                    throw e
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
