package com.example.buddy.ext.search

private const val TAG = "WebSearch"

data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val publishedDate: String? = null
)

interface WebSearch {
    suspend fun search(query: String): List<SearchResult>
    fun isAvailable(): Boolean
}
