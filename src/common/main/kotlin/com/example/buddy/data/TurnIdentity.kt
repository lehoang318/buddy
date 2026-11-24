package com.example.buddy.data

import java.util.UUID

fun assignTurnIds(messages: List<SessionMessage>): List<SessionMessage> {
    var currentTurnId: String? = null
    return messages.map { message ->
        when (message.role) {
            Role.USER -> {
                val turnId = message.turnId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
                currentTurnId = turnId
                message.copy(turnId = turnId)
            }
            Role.ASSISTANT -> {
                val existing = message.turnId?.takeIf { it.isNotBlank() }
                val turnId = existing ?: currentTurnId
                if (existing != null) currentTurnId = existing
                message.copy(turnId = turnId)
            }
            else -> message
        }
    }
}

@Suppress("USELESS_ELVIS")
fun linkSummariesToTurns(messages: List<SessionMessage>, summaries: List<Summary>): List<Summary> {
    val candidates = messages.asSequence()
        .filter { it.role == Role.USER }
        .mapNotNull { message -> message.turnId?.takeIf { it.isNotBlank() }?.let { it to message.content } }
        .toList()
    val used = mutableSetOf<String>()
    return summaries.map { summary ->
        val existing = summary.turnIds ?: emptyList()
        if (existing.isNotEmpty()) return@map summary
        val match = candidates.firstOrNull { (turnId, content) -> turnId !in used && content == summary.question }
            ?: return@map summary
        used += match.first
        summary.copy(turnIds = listOf(match.first))
    }
}
