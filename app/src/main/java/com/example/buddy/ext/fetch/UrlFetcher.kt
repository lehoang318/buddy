package com.example.buddy.ext.fetch

import com.example.buddy.data.EventLog

private const val TAG = "UrlFetcher"

data class FetchedUrl(
    val url: String,
    val content: String
)

data class UrlFetchResult(
    val urls: List<FetchedUrl> = emptyList(),
    val warnings: List<String> = emptyList()
)

interface UrlFetcher {
    suspend fun fetchTextContent(url: String): String?
    suspend fun testConnection(): Boolean

    suspend fun fetchAll(urls: List<String>, correlationId: String? = null): UrlFetchResult {
        val warnings = mutableListOf<String>()
        val fetched = urls.mapNotNull { url ->
            try {
                EventLog.info(TAG, "Fetching URL", "URL: $url", correlationId = correlationId)
                val content = fetchTextContent(url)
                if (content != null) {
                    EventLog.info(TAG, "Fetch succeeded", "URL: $url\nLength: ${content.length}", correlationId = correlationId)
                    FetchedUrl(url = url, content = content)
                } else {
                    EventLog.error(TAG, "Fetch failed", "URL: $url", correlationId = correlationId)
                    warnings.add("Failed to fetch: $url")
                    null
                }
            } catch (e: Exception) {
                EventLog.error(TAG, "Fetch failed", "URL: $url\n${e.message}", correlationId = correlationId)
                warnings.add("Failed to fetch: $url")
                null
            }
        }
        return UrlFetchResult(urls = fetched, warnings = warnings)
    }
}
