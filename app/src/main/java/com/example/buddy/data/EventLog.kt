package com.example.buddy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppEvent(
    val level: String,
    val timestamp: Long,
    val text: String
)

object EventLog {
    private val _events = MutableStateFlow<List<AppEvent>>(emptyList())
    val events: StateFlow<List<AppEvent>> = _events

    fun add(level: String, text: String) {
        val event = AppEvent(level, System.currentTimeMillis(), text)
        val current = _events.value.toMutableList()
        current.add(0, event)
        if (current.size > 10) current.removeAt(10)
        _events.value = current
    }
}
