package com.example.buddy.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryParserTest {

    @Test
    fun parsesCanonicalObjectAndNormalizesTags() {
        val summary = SummaryParser.parse(
            "q",
            """{"points":[{"text":"Fact one","key":true},{"text":"Fact two","key":false}],"tags":["technology","UNKNOWN"]}"""
        )
        assertEquals(2, summary.points.size)
        assertTrue(summary.points.first().key)
        assertEquals(listOf("Technology"), summary.tags)
    }

    @Test
    fun parsesBarePointsArray() {
        val summary = SummaryParser.parse("q", """[{"text":"Only point","key":false}]""")
        assertEquals(1, summary.points.size)
        assertEquals("Only point", summary.points.single().text)
    }

    @Test
    fun stripsCodeFences() {
        val summary = SummaryParser.parse(
            "q",
            "```json\n{\"points\":[{\"text\":\"Fenced fact\"}]}\n```"
        )
        assertEquals("Fenced fact", summary.points.single().text)
    }

    @Test
    fun returnsEmptyOnBlankOutput() {
        val summary = SummaryParser.parse("q", "   ")
        assertTrue(summary.points.isEmpty())
    }
}
