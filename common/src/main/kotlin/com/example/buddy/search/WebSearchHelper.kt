package com.example.buddy.search

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.Summary
import com.example.buddy.llm.LlmClient
import com.example.buddy.logging.Log
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
        val queries: List<String> = emptyList(),
        val errorMessage: String? = null,
        val skipped: Boolean = false
    )

    suspend fun search(userMessage: String, summaries: List<Summary> = emptyList(), correlationId: String? = null): WebSearchOutcome {
        val cleanInput = userMessage
            .replace(Regex("""https?://\S+"""), "")
            .trim()
            .ifBlank { userMessage.take(100) }

        Log.debug(TAG, "Search query input prepared", "Original: ${userMessage.take(AppConfigProvider.current.search.logPreviewMaxChars)}\nCleaned: ${cleanInput.take(AppConfigProvider.current.search.logPreviewMaxChars)}", correlationId = correlationId)
        return try {
            val plan = llmClient.generateSearchQuery(cleanInput, summaries, correlationId)
            if (plan == null) {
                Log.info(TAG, "Search skipped", "Query generation returned null (NO_QUERY or sanitization failed)", correlationId = correlationId)
                return WebSearchOutcome(skipped = true)
            }
            Log.info(TAG, "Query plan generated", "Queries: ${plan.queries.joinToString(" | ") { "`$it`" }}\nRecency: ${plan.recency}\nFrom: ${cleanInput.take(AppConfigProvider.current.search.logPreviewMaxChars)}", correlationId = correlationId)

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
                Log.warning(TAG, "Some queries failed", "Failed: ${failedQueries.joinToString(", ")}\nSucceeded: ${succeeded.size}/${attempts.size}", correlationId = correlationId)
            }

            val perQuery = succeeded.map { (q, result) -> q to result.getOrThrow() }
            val merged = interleave(perQuery.map { it.second.results })
            val results = cleanResults(merged).take(AppConfigProvider.current.search.totalMaxResults)

            val answer = if (perQuery.size == 1) {
                perQuery.first().second.answer?.trim()?.ifBlank { null }
            } else {
                perQuery.mapNotNull { (q, resp) ->
                    resp.answer?.trim()?.ifBlank { null }?.let { "**$q:** $it" }
                }.joinToString("\n\n").ifBlank { null }
            }

            val queries = perQuery.map { it.first }
            val resultsText = results.joinToString("\n\n") { result ->
                "Source: ${result.title}\nURL: ${result.url}" +
                    (result.publishedDate?.let { "\nDate: $it" } ?: "") +
                    "\n${result.content}"
            }
            Log.info(
                TAG,
                "Search completed",
                "Queries succeeded: ${perQuery.size}/${plan.queries.size}\nResults: ${results.size}\nAnswer: ${answer?.take(AppConfigProvider.current.search.logPreviewMaxChars) ?: "<none>"}\nPreview: ${resultsText.take(AppConfigProvider.current.search.logPreviewMaxChars)}${if (resultsText.length > AppConfigProvider.current.search.logPreviewMaxChars) "..." else ""}",
                correlationId = correlationId
            )

            if (results.isEmpty()) {
                Log.warning(TAG, "Search returned no results", "Queries: ${queries.joinToString(" · ")}")
                WebSearchOutcome(queries = queries, errorMessage = "Web search returned no results")
            } else {
                WebSearchOutcome(rawResults = results, resultsText = resultsText, answer = answer, queries = queries)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.error(TAG, "Search failed", e.message, correlationId = correlationId)
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
        val maxChars = AppConfigProvider.current.search.resultContentMaxChars
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
