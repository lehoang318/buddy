package com.example.buddy.ext

import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Summary

private const val TAG = "WebSearch"

class WebSearchHelper(
    private val llmClient: LlmClient,
    private val webSearch: WebSearch
) {

    data class SearchResult(
        val rawResults: List<com.example.buddy.ext.SearchResult> = emptyList(),
        val resultsText: String? = null,
        val errorMessage: String? = null,
        val skipped: Boolean = false
    )

    suspend fun search(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): SearchResult {
        val cleanInput = userMessage
            .replace(Regex("""https?://\S+"""), "")
            .trim()
            .ifBlank { userMessage.take(100) }

        EventLog.debug(TAG, "Search query input prepared", "Original: ${userMessage.take(AppResources.search.logPreviewMaxChars)}\nCleaned: ${cleanInput.take(AppResources.search.logPreviewMaxChars)}", correlationId = correlationId)
        return try {
            val searchQuery = llmClient.generateSearchQuery(cleanInput, summaries, correlationId)
            if (searchQuery == null) {
                EventLog.info(TAG, "Search skipped", "Query generation returned null (NO_QUERY or sanitization failed)", correlationId = correlationId)
                return SearchResult(skipped = true)
            }
            EventLog.info(TAG, "Query generated", "Query: `$searchQuery`\nFrom: ${cleanInput.take(AppResources.search.logPreviewMaxChars)}", correlationId = correlationId)

            val results = webSearch.search(searchQuery)
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}\n${result.content}"
            }
            EventLog.info(
                TAG,
                "Search completed",
                "Results: ${results.size}\nPreview: ${resultsText.take(AppResources.search.logPreviewMaxChars)}${if (resultsText.length > AppResources.search.logPreviewMaxChars) "..." else ""}",
                correlationId = correlationId
            )

            if (results.isEmpty()) {
                EventLog.warning(TAG, "Search returned no results", "Query: $searchQuery")
                SearchResult(errorMessage = "Web search returned no results")
            } else {
                SearchResult(rawResults = results, resultsText = resultsText)
            }
        } catch (e: Exception) {
            EventLog.error(TAG, "Search failed", e.message, correlationId = correlationId)
            SearchResult(errorMessage = e.message)
        }
    }
}
