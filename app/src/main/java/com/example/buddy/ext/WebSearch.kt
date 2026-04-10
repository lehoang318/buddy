package com.example.buddy.ext

import com.example.buddy.data.EventLog
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val url: String,
    val content: String
)

interface WebSearch {
    suspend fun search(query: String): List<SearchResult>
    fun isAvailable(): Boolean
}

class TavilyWebSearch(
    private val apiKey: String
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

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    override suspend fun search(query: String): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val requestBody = JsonObject().apply {
                addProperty("query", query)
                addProperty("api_key", apiKey)
                addProperty("max_results", 3)
                addProperty("search_depth", "basic")
            }

            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .build()

            var lastException: Exception? = null
            val maxRetries = 2
            
            for (attempt in 0..maxRetries) {
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: ""
                            val errorMsg = when (response.code) {
                                401, 403 -> "Invalid Tavily API key"
                                429 -> "Tavily usage limit exceeded"
                                else -> "Web search failed: HTTP ${response.code}"
                            }
                            EventLog.add("E", "web fetch: failed")
                            throw Exception("$errorMsg ($errorBody)")
                        }
                        val bodyString = response.body?.string() ?: ""
                        val json = gson.fromJson(bodyString, JsonObject::class.java)
                        val results = json.getAsJsonArray("results")
                        return@withContext results.map { resultObj ->
                            val obj = resultObj.asJsonObject
                            SearchResult(
                                title = obj.get("title")?.asString ?: "",
                                url = obj.get("url")?.asString ?: "",
                                content = obj.get("content")?.asString ?: ""
                            )
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    lastException = e
                    EventLog.add("W", "web fetch: timeout, retry ${attempt + 1}/$maxRetries")
                    if (attempt < maxRetries) delay(1000L * (attempt + 1))
                } catch (e: Exception) {
                    lastException = e
                    EventLog.add("E", "web fetch: failed, retry ${attempt + 1}/$maxRetries")
                    if (attempt < maxRetries) delay(1000L * (attempt + 1))
                }
            }
            throw lastException ?: Exception("Web search failed after $maxRetries retries")
        }
    }
}
