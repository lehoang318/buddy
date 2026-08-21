package com.example.buddy.logging

object Log {
    var logger: Logger = DesktopLogger("Buddy")

    fun debug(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        logger.debug(tag, text, data, correlationId, durationMs)

    fun info(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        logger.info(tag, text, data, correlationId, durationMs)

    fun warning(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        logger.warning(tag, text, data, correlationId, durationMs)

    fun error(tag: String, text: String, data: String? = null, correlationId: String? = null, durationMs: Long? = null) =
        logger.error(tag, text, data, correlationId, durationMs)
}
