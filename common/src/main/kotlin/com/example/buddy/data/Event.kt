package com.example.buddy.data

enum class EventLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class AppEvent(
    val level: EventLevel,
    val tag: String,
    val timestamp: Long,
    val text: String,
    val data: String? = null,
    val correlationId: String? = null,
    val durationMs: Long? = null
)
