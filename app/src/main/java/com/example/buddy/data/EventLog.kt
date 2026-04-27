package com.example.buddy.data

import com.example.buddy.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

object EventLog {
    private const val MAX_EVENTS = 20
    private const val MAX_DATA_LENGTH = 2000

    private val _events = MutableStateFlow<List<AppEvent>>(emptyList())
    val events: StateFlow<List<AppEvent>> = _events

    private fun add(
        level: EventLevel,
        tag: String,
        text: String,
        data: String? = null,
        correlationId: String? = null,
        durationMs: Long? = null
    ) {
        if (!BuildConfig.DEBUG && (level == EventLevel.DEBUG || level == EventLevel.INFO)) return
        val cappedData = data?.take(MAX_DATA_LENGTH)
        val event = AppEvent(level, tag, System.currentTimeMillis(), text, cappedData, correlationId, durationMs)
        val current = _events.value.toMutableList()
        current.add(0, event)
        if (current.size > MAX_EVENTS) current.removeAt(MAX_EVENTS)
        _events.value = current
    }

    fun debug(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.DEBUG, tag, text, data, correlationId, durationMs)

    fun info(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.INFO, tag, text, data, correlationId, durationMs)

    fun warning(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.WARNING, tag, text, data, correlationId, durationMs)

    fun error(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.ERROR, tag, text, data, correlationId, durationMs)

    fun clear() { _events.value = emptyList() }
}
