package com.example.buddy.logging

import com.example.buddy.data.EventLevel

interface Logger {
    fun log(
        level: EventLevel,
        tag: String,
        text: String,
        data: String? = null,
        correlationId: String? = null,
        durationMs: Long? = null
    )
}

fun Logger.debug(
    tag: String,
    text: String,
    data: String? = null,
    correlationId: String? = null,
    durationMs: Long? = null
) = log(EventLevel.DEBUG, tag, text, data, correlationId, durationMs)

fun Logger.info(
    tag: String,
    text: String,
    data: String? = null,
    correlationId: String? = null,
    durationMs: Long? = null
) = log(EventLevel.INFO, tag, text, data, correlationId, durationMs)

fun Logger.warning(
    tag: String,
    text: String,
    data: String? = null,
    correlationId: String? = null,
    durationMs: Long? = null
) = log(EventLevel.WARNING, tag, text, data, correlationId, durationMs)

fun Logger.error(
    tag: String,
    text: String,
    data: String? = null,
    correlationId: String? = null,
    durationMs: Long? = null
) = log(EventLevel.ERROR, tag, text, data, correlationId, durationMs)
