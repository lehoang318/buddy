package com.example.buddy.data

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object EventLog : Logger {
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
        if (!AppConfigProvider.current.debugLogging && (level == EventLevel.DEBUG || level == EventLevel.INFO)) return
        val cappedData = data?.take(AppConfigProvider.current.events.maxDataLength)
        val event = AppEvent(level, tag, System.currentTimeMillis(), text, cappedData, correlationId, durationMs)
        val current = _events.value.toMutableList()
        current.add(0, event)
        if (current.size > AppConfigProvider.current.events.maxEntries) current.removeAt(AppConfigProvider.current.events.maxEntries)
        _events.value = current
    }

    override fun log(
        level: EventLevel,
        tag: String,
        text: String,
        data: String?,
        correlationId: String?,
        durationMs: Long?
    ) = add(level, tag, text, data, correlationId, durationMs)

    fun debug(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.DEBUG, tag, text, data, correlationId, durationMs)

    fun info(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.INFO, tag, text, data, correlationId, durationMs)

    fun warning(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.WARNING, tag, text, data, correlationId, durationMs)

    fun error(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        add(EventLevel.ERROR, tag, text, data, correlationId, durationMs)

}
