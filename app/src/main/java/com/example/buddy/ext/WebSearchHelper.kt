package com.example.buddy.ext

import com.example.buddy.data.EventLog
import com.example.buddy.data.LlmDefaults

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
            .take(1024)
            .ifBlank { userMessage.take(100) }

        EventLog.debug(TAG, "Search query input prepared", "Original: ${userMessage.take(LlmDefaults.logPreviewMaxChars)}\nCleaned: ${cleanInput.take(LlmDefaults.logPreviewMaxChars)}", correlationId = correlationId)
        return try {
            searchQuery = llmClient.generateSearchQuery(cleanInput)
            EventLog.info(TAG, "Query generated", "Query: `$searchQuery`\nFrom: ${cleanInput.take(LlmDefaults.logPreviewMaxChars)}", correlationId = correlationId)

            val results = webSearch.search(searchQuery)
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}\n${result.content}"
            }
            EventLog.info(
                TAG,
                "Search completed",
                "Results: ${results.size}\nPreview: ${resultsText.take(LlmDefaults.logPreviewMaxChars)}${if (resultsText.length > LlmDefaults.logPreviewMaxChars) "..." else ""}",
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
