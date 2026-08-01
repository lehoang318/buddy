package com.example.buddy.logging

import com.example.buddy.data.EventLevel
import org.slf4j.LoggerFactory

class DesktopLogger(name: String = "Buddy") : Logger {
    private val delegate = LoggerFactory.getLogger(name)

    override fun log(level: EventLevel, tag: String, text: String, data: String?, correlationId: String?, durationMs: Long?) {
        val message = format(tag, text, data, correlationId, durationMs)
        when (level) {
            EventLevel.DEBUG -> delegate.debug(message)
            EventLevel.INFO -> delegate.info(message)
            EventLevel.WARNING -> delegate.warn(message)
            EventLevel.ERROR -> delegate.error(message)
        }
    }

    private fun format(
        tag: String,
        text: String,
        data: String?,
        correlationId: String?,
        durationMs: Long?
    ): String = buildString {
        append('[').append(tag).append("] ").append(text)
        data?.let { append(" | data=").append(it) }
        correlationId?.let { append(" | correlationId=").append(it) }
        durationMs?.let { append(" | durationMs=").append(it) }
    }
}
