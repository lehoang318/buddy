package com.example.buddy.ext.search

import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Summary
import com.example.buddy.ext.llm.LlmClient
import kotlinx.coroutines.CancellationException

private const val TAG = "WebSearch"

class WebSearchHelper(
    private val llmClient: LlmClient,
    private val webSearch: WebSearch
) {

    data class WebSearchOutcome(
        val rawResults: List<SearchResult> = emptyList(),
        val resultsText: String? = null,
        val answer: String? = null,
        val query: String? = null,
        val errorMessage: String? = null,
        val skipped: Boolean = false
    )

    suspend fun search(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): WebSearchOutcome {
        val cleanInput = userMessage
            .replace(Regex("""https?://\S+"""), "")
            .trim()
            .ifBlank { userMessage.take(100) }

        EventLog.debug(TAG, "Search query input prepared", "Original: ${userMessage.take(AppResources.search.logPreviewMaxChars)}\nCleaned: ${cleanInput.take(AppResources.search.logPreviewMaxChars)}", correlationId = correlationId)
        return try {
            val searchQuery = llmClient.generateSearchQuery(cleanInput, summaries, correlationId)
            if (searchQuery == null) {
                EventLog.info(TAG, "Search skipped", "Query generation returned null (NO_QUERY or sanitization failed)", correlationId = correlationId)
                return WebSearchOutcome(skipped = true)
            }
            EventLog.info(TAG, "Query generated", "Query: `$searchQuery`\nFrom: ${cleanInput.take(AppResources.search.logPreviewMaxChars)}", correlationId = correlationId)

            val response = webSearch.search(searchQuery)
            val results = cleanResults(response.results)
            val answer = response.answer?.trim()?.ifBlank { null }
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}" +
                    (result.publishedDate?.let { "\nDate: $it" } ?: "") +
                    "\n${result.content}"
            }
            EventLog.info(
                TAG,
                "Search completed",
                "Results: ${results.size}\nAnswer: ${answer?.take(AppResources.search.logPreviewMaxChars) ?: "<none>"}\nPreview: ${resultsText.take(AppResources.search.logPreviewMaxChars)}${if (resultsText.length > AppResources.search.logPreviewMaxChars) "..." else ""}",
                correlationId = correlationId
            )

            if (results.isEmpty()) {
                EventLog.warning(TAG, "Search returned no results", "Query: $searchQuery")
                WebSearchOutcome(query = searchQuery, errorMessage = "Web search returned no results")
            } else {
                WebSearchOutcome(rawResults = results, resultsText = resultsText, answer = answer, query = searchQuery)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EventLog.error(TAG, "Search failed", e.message, correlationId = correlationId)
            WebSearchOutcome(errorMessage = e.message)
        }
    }

    // Drops blank/duplicate (same domain+title) results and caps content length before it reaches the model.
    private fun cleanResults(results: List<SearchResult>): List<SearchResult> {
        val maxChars = AppResources.search.resultContentMaxChars
        val seen = mutableSetOf<String>()
        val cleaned = mutableListOf<SearchResult>()
        for (result in results) {
            val trimmedContent = result.content.trim()
            if (trimmedContent.isBlank()) continue
            val host = try {
                java.net.URI(result.url).host?.removePrefix("www.") ?: result.url
            } catch (_: Exception) {
                result.url
            }
            val dedupeKey = "$host|${result.title.trim().lowercase()}"
            if (!seen.add(dedupeKey)) continue
            cleaned.add(result.copy(content = trimmedContent.take(maxChars)))
        }
        return cleaned
    }
}
