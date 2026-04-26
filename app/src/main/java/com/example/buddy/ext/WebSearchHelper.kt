package com.example.buddy.ext

import com.example.buddy.data.EventLog

private const val TAG = "WebSearch"

class WebSearchHelper(
    private val llmClient: LlmClient,
    private val webSearch: WebSearch
) {

    data class SearchResult(
        val resultsText: String?,
        val errorMessage: String?
    )

    suspend fun search(userMessage: String, correlationId: String? = null): SearchResult {
        var searchQuery: String? = null
        val cleanInput = userMessage
            .replace(Regex("""https?://\S+"""), "")
            .trim()
            .take(300)
            .ifBlank { userMessage.take(100) }
        EventLog.debug(TAG, "Search query input prepared", "Original: ${userMessage.take(100)}\nCleaned: ${cleanInput.take(100)}", correlationId = correlationId)
        return try {
            searchQuery = llmClient.generateSearchQuery(cleanInput)
            EventLog.info(TAG, "Query generated", "Query: `$searchQuery`\nFrom: ${cleanInput.take(100)}", correlationId = correlationId)

            val results = webSearch.search(searchQuery)
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}\n${result.content}"
            }
            EventLog.info(
                TAG,
                "Search completed",
                "Results: ${results.size}\nPreview: ${resultsText.take(100)}${if (resultsText.length > 100) "..." else ""}",
                correlationId = correlationId
            )

            if (results.isEmpty()) {
                SearchResult(null, "Web search returned no results")
            } else {
                SearchResult(resultsText, null)
            }
        } catch (e: Exception) {
            EventLog.error(TAG, "Search failed", e.message, correlationId = correlationId)
            SearchResult(null, e.message)
        }
    }
}
