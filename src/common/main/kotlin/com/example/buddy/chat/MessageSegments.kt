package com.example.buddy.chat

sealed interface MessageSegment {
    data class Text(val content: String) : MessageSegment
    data class Code(val lang: String, val code: String, val isClosed: Boolean) : MessageSegment
}

private val CLOSE_FENCE = Regex("^`{3,}$")

fun splitIntoSegments(content: String): List<MessageSegment> {
    if (content.isBlank()) return emptyList()

    val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
    val lines = normalized.lines()
    val segments = mutableListOf<MessageSegment>()
    val textBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flushText(textBuffer, segments)
            val lang = line.trimStart().drop(3).trim().lowercase()
            val codeLines = mutableListOf<String>()
            var closed = false
            i++
            while (i < lines.size) {
                val codeLine = lines[i]
                if (CLOSE_FENCE.matches(codeLine.trim())) {
                    closed = true
                    i++
                    break
                }
                codeLines.add(codeLine)
                i++
            }
            segments.add(
                MessageSegment.Code(
                    lang = lang,
                    code = codeLines.joinToString("\n"),
                    isClosed = closed
                )
            )
        } else {
            textBuffer.append(line).append('\n')
            i++
        }
    }

    flushText(textBuffer, segments)
    return segments
}

private fun flushText(buffer: StringBuilder, out: MutableList<MessageSegment>) {
    if (buffer.isEmpty()) return
    val trimmed = buffer.toString().trim()
    if (trimmed.isNotEmpty()) {
        out.add(MessageSegment.Text(trimmed))
    }
    buffer.clear()
}