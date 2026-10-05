package com.example.buddy.data

enum class TurnEventKind { THOUGHTS, SEARCH, QUESTION, ANSWER }

data class TurnEvent(
    val kind: TurnEventKind,
    val text: String = "",
    val queries: List<String> = emptyList(),
    val skipped: Boolean = false
)

fun sanitizeTurnEvents(events: List<TurnEvent?>?): List<TurnEvent> =
    events.orEmpty().mapNotNull { event ->
        if (event == null || event.kind !in TurnEventKind.entries) return@mapNotNull null
        val text: String? = event.text
        val queries: List<String?>? = event.queries
        event.copy(text = text ?: "", queries = queries.orEmpty().filterNotNull())
    }

fun appendThoughtsEvent(events: List<TurnEvent>, text: String, separator: String = ""): List<TurnEvent> {
    if (text.isEmpty()) return events
    val last = events.lastOrNull()
    return if (last != null && last.kind == TurnEventKind.THOUGHTS) {
        events.dropLast(1) + last.copy(text = last.text + separator + text)
    } else {
        events + TurnEvent(TurnEventKind.THOUGHTS, text = text)
    }
}

fun reconstructTurnEvents(
    webSearchUsed: Boolean,
    webSearchSkipped: Boolean,
    webSearchQueries: List<String>,
    questionAsked: String?,
    questionAnswer: String?
): List<TurnEvent> = buildList {
    if (webSearchUsed) add(TurnEvent(TurnEventKind.SEARCH, queries = webSearchQueries))
    else if (webSearchSkipped) add(TurnEvent(TurnEventKind.SEARCH, skipped = true))
    if (!questionAsked.isNullOrBlank()) add(TurnEvent(TurnEventKind.QUESTION, text = questionAsked))
    if (!questionAnswer.isNullOrBlank()) add(TurnEvent(TurnEventKind.ANSWER, text = questionAnswer))
}
