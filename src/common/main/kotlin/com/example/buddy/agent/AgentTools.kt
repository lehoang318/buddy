package com.example.buddy.agent

import com.example.buddy.fetch.UrlFetcher
import com.example.buddy.search.SearchRecency
import com.example.buddy.search.WebSearch
import com.example.buddy.search.WebSearchHelper
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type

/**
 * ADK function tool that runs the web-search fan-out/merge for an explicit query list chosen by the
 * model. Unlike the prompt-plan path, no query-generation LLM call is made here — the model has
 * already decided what to search for.
 */
class WebSearchTool(
    private val webSearch: WebSearch,
    description: String
) : FunctionTool(name = "web_search", description = description) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = "web_search",
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "queries" to Schema(
                    type = Type.ARRAY,
                    items = Schema(type = Type.STRING),
                    description = "1-3 short keyword search queries."
                ),
                "recency" to Schema(
                    type = Type.STRING,
                    enum = listOf("day", "week", "month", "any"),
                    description = "How recent results should be. Defaults to any."
                )
            ),
            required = listOf("queries")
        )
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val queries = (args["queries"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }.orEmpty()
        if (queries.isEmpty()) return mapOf("error" to "No queries provided")

        val outcome = WebSearchHelper.searchQueries(webSearch, queries, parseRecency(args["recency"] as? String))
        val result = linkedMapOf<String, Any?>()
        outcome.resultsText?.let { result["results"] = it }
        outcome.answer?.let { result["answer"] = it }
        if (outcome.queries.isNotEmpty()) result["queries"] = outcome.queries
        outcome.errorMessage?.let { result["error"] = it }
        if (result.isEmpty()) result["error"] = "Web search returned no results"
        return result
    }

    private fun parseRecency(raw: String?): SearchRecency = when (raw?.trim()?.lowercase()) {
        "day" -> SearchRecency.DAY
        "week" -> SearchRecency.WEEK
        "month" -> SearchRecency.MONTH
        else -> SearchRecency.ANY
    }
}

/** ADK function tool that fetches and extracts the text of a single http(s) URL. */
class FetchUrlTool(
    private val urlFetcher: UrlFetcher,
    description: String
) : FunctionTool(name = "fetch_url", description = description) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = "fetch_url",
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "url" to Schema(type = Type.STRING, description = "Absolute http(s) URL to fetch.")
            ),
            required = listOf("url")
        )
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val url = (args["url"] as? String)?.trim()
        if (url.isNullOrBlank()) return mapOf("error" to "No URL provided")

        val result = urlFetcher.fetchAll(listOf(url))
        val fetched = result.urls.firstOrNull()
            ?: return mapOf("error" to (result.warnings.firstOrNull() ?: "Failed to fetch URL"))
        return mapOf("url" to fetched.url, "content" to fetched.content)
    }
}
