package com.example.buddy.search

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "WebSearch"

data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val publishedDate: String? = null
)

data class SearchResponse(
    val results: List<SearchResult>,
    val answer: String? = null
)

enum class SearchRecency { DAY, WEEK, MONTH, ANY }

// ISO-8601 date `recency` days before today; null for ANY (no date filter).
fun SearchRecency.sinceDateOrNull(): String? {
    val days = when (this) {
        SearchRecency.DAY -> 1
        SearchRecency.WEEK -> 7
        SearchRecency.MONTH -> 31
        SearchRecency.ANY -> return null
    }
    return LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
}

interface WebSearch {
    suspend fun search(query: String, recency: SearchRecency = SearchRecency.ANY): SearchResponse
    fun isAvailable(): Boolean
}
