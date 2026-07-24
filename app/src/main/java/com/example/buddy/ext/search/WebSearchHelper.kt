package com.example.buddy.ext.search

import com.example.buddy.data.EventLog
import com.example.buddy.data.AppResources
import com.example.buddy.data.Summary
import com.example.buddy.ext.llm.LlmClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
            val plan = llmClient.generateSearchQuery(cleanInput, summaries, correlationId)
            if (plan == null) {
                EventLog.info(TAG, "Search skipped", "Query generation returned null (NO_QUERY or sanitization failed)", correlationId = correlationId)
                return WebSearchOutcome(skipped = true)
            }
            EventLog.info(TAG, "Query plan generated", "Queries: ${plan.queries.joinToString(" | ") { "`$it`" }}\nRecency: ${plan.recency}\nFrom: ${cleanInput.take(AppResources.search.logPreviewMaxChars)}", correlationId = correlationId)

            // Fan out one search per query; a query that fails on its own doesn't sink the others.
            val attempts: List<Pair<String, Result<SearchResponse>>> = coroutineScope {
                plan.queries.map { q ->
                    async {
                        q to try {
                            Result.success(webSearch.search(q, plan.recency))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }.awaitAll()
            }

            val succeeded = attempts.filter { it.second.isSuccess }
            if (succeeded.isEmpty()) {
                throw attempts.last().second.exceptionOrNull() ?: Exception("Web search failed for all queries")
            }
            if (succeeded.size < attempts.size) {
                val failedQueries = attempts.filter { it.second.isFailure }.map { it.first }
                EventLog.warning(TAG, "Some queries failed", "Failed: ${failedQueries.joinToString(", ")}\nSucceeded: ${succeeded.size}/${attempts.size}", correlationId = correlationId)
            }

            val perQuery = succeeded.map { (q, result) -> q to result.getOrThrow() }
            val merged = interleave(perQuery.map { it.second.results })
            val results = cleanResults(merged).take(AppResources.search.totalMaxResults)

            val answer = if (perQuery.size == 1) {
                perQuery.first().second.answer?.trim()?.ifBlank { null }
            } else {
                perQuery.mapNotNull { (q, resp) ->
                    resp.answer?.trim()?.ifBlank { null }?.let { "**$q:** $it" }
                }.joinToString("\n\n").ifBlank { null }
            }

            val queryLabel = perQuery.joinToString(" · ") { it.first }
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}" +
                    (result.publishedDate?.let { "\nDate: $it" } ?: "") +
                    "\n${result.content}"
            }
            EventLog.info(
                TAG,
                "Search completed",
                "Queries succeeded: ${perQuery.size}/${plan.queries.size}\nResults: ${results.size}\nAnswer: ${answer?.take(AppResources.search.logPreviewMaxChars) ?: "<none>"}\nPreview: ${resultsText.take(AppResources.search.logPreviewMaxChars)}${if (resultsText.length > AppResources.search.logPreviewMaxChars) "..." else ""}",
                correlationId = correlationId
            )

            if (results.isEmpty()) {
                EventLog.warning(TAG, "Search returned no results", "Queries: $queryLabel")
                WebSearchOutcome(query = queryLabel, errorMessage = "Web search returned no results")
            } else {
                WebSearchOutcome(rawResults = results, resultsText = resultsText, answer = answer, query = queryLabel)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EventLog.error(TAG, "Search failed", e.message, correlationId = correlationId)
            WebSearchOutcome(errorMessage = e.message)
        }
    }

    // Keeps every query's top results represented near the front of the merged list
    // instead of exhausting the first query's results before showing the second's.
    private fun interleave(lists: List<List<SearchResult>>): List<SearchResult> {
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        val merged = mutableListOf<SearchResult>()
        for (i in 0 until maxLen) {
            for (list in lists) {
                if (i < list.size) merged.add(list[i])
            }
        }
        return merged
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
