package com.example.buddy.config

import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class SummariesConfigTest {
    @Test
    fun formatsAndSanitizesSummaryPoints() {
        val config = object : SummariesConfig {
            override val maxSummaries = 20
            override val maxQaPairs = 2
            override val minPoints = 2
            override val maxPoints = 3
            override val keyPrefix = "[KEY] "
            override val pointIndent = "  + "
            override val contextHeader = "## Context"
            override val webDataHeader = "## Web Data"
            override val temperature = 0.2f
            override val maxTokens = 512
            override val restrictivePatterns = listOf("SECRET")
        }
        val summary = Summary("Question", listOf(SummaryPoint("SECRET answer", key = true)))

        assertEquals("  + [KEY] answer", config.formatSummaryAsText(config.sanitizeSummaryPoints(summary.points).let { summary.copy(points = it) }))
        assertEquals("## Context\n### Question\n  + [KEY] answer", config.formatSummariesContext(listOf(summary.copy(points = config.sanitizeSummaryPoints(summary.points)))))
    }
}
