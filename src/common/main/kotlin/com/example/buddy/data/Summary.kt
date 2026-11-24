package com.example.buddy.data

data class SummaryPoint(
    val text: String,
    val key: Boolean = false
)

data class SummarySource(
    val points: List<SummaryPoint> = emptyList(),
    val tags: List<String> = emptyList(),
    val turnIds: List<String> = emptyList()
)

data class Summary(
    val question: String,
    val points: List<SummaryPoint>,
    val tags: List<String> = emptyList(),
    val turnIds: List<String> = emptyList(),
    val sources: List<SummarySource> = emptyList()
)

@Suppress("USELESS_ELVIS")
fun removeTurnFromSummaries(
    summaries: List<Summary>,
    turnId: String,
    maxSessionTags: Int,
    sanitize: (List<SummaryPoint>) -> List<SummaryPoint> = { it }
): List<Summary> {
    if (turnId.isBlank()) return summaries
    return summaries.mapNotNull { summary ->
        val sources = summary.sources ?: emptyList()
        val directTurnIds = summary.turnIds ?: emptyList()
        val covered = directTurnIds.contains(turnId) || sources.any { (it.turnIds ?: emptyList()).contains(turnId) }
        if (!covered) return@mapNotNull summary
        if (sources.isEmpty()) return@mapNotNull null
        val remaining = sources
            .filterNot { (it.turnIds ?: emptyList()).contains(turnId) }
            .map { source ->
                SummarySource(
                    points = source.points ?: emptyList(),
                    tags = source.tags ?: emptyList(),
                    turnIds = source.turnIds ?: emptyList()
                )
            }
        if (remaining.isEmpty()) return@mapNotNull null
        val points = sanitize(remaining.flatMap { it.points }).mergedByText()
        val tags = remaining.flatMap { it.tags }.distinct().takeLast(maxSessionTags)
        val turnIds = remaining.flatMap { it.turnIds }.distinct()
        summary.copy(points = points, tags = tags, turnIds = turnIds, sources = remaining)
    }
}

private fun List<SummaryPoint>.mergedByText(): List<SummaryPoint> {
    val merged = LinkedHashMap<String, SummaryPoint>()
    forEach { point ->
        val existing = merged[point.text]
        merged[point.text] = if (existing == null) point else existing.copy(key = existing.key || point.key)
    }
    return merged.values.toList()
}
