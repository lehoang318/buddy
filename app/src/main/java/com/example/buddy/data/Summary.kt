package com.example.buddy.data

data class SummaryPoint(
    val text: String,
    val key: Boolean = false
)

data class Summary(
    val question: String,
    val points: List<SummaryPoint>
)
