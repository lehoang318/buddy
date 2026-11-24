package com.example.buddy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryRemovalTest {

    @Test
    fun `blank turn id leaves summaries unchanged`() {
        val summaries = listOf(Summary("q", listOf(SummaryPoint("p")), turnIds = listOf("t1")))

        assertEquals(summaries, removeTurnFromSummaries(summaries, "  ", 3))
    }

    @Test
    fun `standalone summary covering the turn is dropped`() {
        val summaries = listOf(
            Summary("q1", listOf(SummaryPoint("a1")), listOf("Science"), listOf("t1")),
            Summary("q2", listOf(SummaryPoint("a2")), turnIds = listOf("t2"))
        )

        assertEquals(listOf(summaries[1]), removeTurnFromSummaries(summaries, "t1", 3))
    }

    @Test
    fun `standalone summary not covering the turn is untouched`() {
        val summaries = listOf(Summary("q", listOf(SummaryPoint("a")), turnIds = listOf("other")))

        assertEquals(summaries, removeTurnFromSummaries(summaries, "t1", 3))
    }

    @Test
    fun `legacy summary without provenance is untouched`() {
        val summaries = listOf(Summary("Earlier conversation", listOf(SummaryPoint("a"))))

        assertEquals(summaries, removeTurnFromSummaries(summaries, "t1", 3))
    }

    @Test
    fun `compressed summary rebuilds from the remaining sources`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = listOf(SummaryPoint("q1 point", key = true), SummaryPoint("q2 point")),
                tags = listOf("Science", "World"),
                turnIds = listOf("t1", "t2"),
                sources = listOf(
                    SummarySource(listOf(SummaryPoint("q1 point", key = true)), listOf("Science"), listOf("t1")),
                    SummarySource(listOf(SummaryPoint("q2 point")), listOf("World"), listOf("t2"))
                )
            )
        )

        assertEquals(
            listOf(
                Summary(
                    question = "Earlier conversation",
                    points = listOf(SummaryPoint("q2 point")),
                    tags = listOf("World"),
                    turnIds = listOf("t2"),
                    sources = listOf(SummarySource(listOf(SummaryPoint("q2 point")), listOf("World"), listOf("t2")))
                )
            ),
            removeTurnFromSummaries(summaries, "t1", 3)
        )
    }

    @Test
    fun `compressed summary merges points keeping key flags and unions provenance`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = emptyList(),
                turnIds = listOf("t1", "t2", "t3"),
                sources = listOf(
                    SummarySource(listOf(SummaryPoint("dup", key = true)), listOf("Science"), listOf("t1")),
                    SummarySource(
                        listOf(SummaryPoint("dup"), SummaryPoint("shared")),
                        listOf("World", "Science"),
                        listOf("t2", "t4")
                    ),
                    SummarySource(listOf(SummaryPoint("shared", key = true)), listOf("Health"), listOf("t3", "t4"))
                )
            )
        )

        val result = removeTurnFromSummaries(summaries, "t1", 3).single()

        assertEquals(listOf(SummaryPoint("dup", key = false), SummaryPoint("shared", key = true)), result.points)
        assertEquals(listOf("World", "Science", "Health"), result.tags)
        assertEquals(listOf("t2", "t4", "t3"), result.turnIds)
        assertEquals("Earlier conversation", result.question)
        assertEquals(2, result.sources.size)
        assertTrue(result.sources.all { it.turnIds.isNotEmpty() })
    }

    @Test
    fun `compressed summary with a single remaining source keeps it`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = listOf(SummaryPoint("a")),
                sources = listOf(
                    SummarySource(listOf(SummaryPoint("a")), emptyList(), listOf("t1")),
                    SummarySource(listOf(SummaryPoint("b")), emptyList(), listOf("t2"))
                ),
                turnIds = listOf("t1", "t2")
            )
        )

        val result = removeTurnFromSummaries(summaries, "t1", 3).single()

        assertEquals(listOf(SummaryPoint("b")), result.points)
        assertEquals(listOf("t2"), result.turnIds)
        assertEquals(listOf(SummarySource(listOf(SummaryPoint("b")), emptyList(), listOf("t2"))), result.sources)
    }

    @Test
    fun `compressed summary covering only the removed turn is dropped`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = listOf(SummaryPoint("a")),
                sources = listOf(SummarySource(listOf(SummaryPoint("a")), emptyList(), listOf("t1"))),
                turnIds = listOf("t1")
            )
        )

        assertTrue(removeTurnFromSummaries(summaries, "t1", 3).isEmpty())
    }

    @Test
    fun `sanitize is applied to the rebuilt points`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = emptyList(),
                sources = listOf(
                    SummarySource(listOf(SummaryPoint("SECRET a")), emptyList(), listOf("t1")),
                    SummarySource(listOf(SummaryPoint("SECRET b")), emptyList(), listOf("t2"))
                ),
                turnIds = listOf("t1", "t2")
            )
        )

        val result = removeTurnFromSummaries(summaries, "t1", 3) { points ->
            points.map { it.copy(text = it.text.replace("SECRET ", "")) }
        }.single()

        assertEquals(listOf(SummaryPoint("b")), result.points)
    }

    @Test
    fun `unknown turn id leaves compressed summaries untouched`() {
        val summaries = listOf(
            Summary(
                question = "Earlier conversation",
                points = listOf(SummaryPoint("a")),
                sources = listOf(SummarySource(listOf(SummaryPoint("a")), emptyList(), listOf("t1"))),
                turnIds = listOf("t1")
            )
        )

        assertEquals(summaries, removeTurnFromSummaries(summaries, "missing", 3))
    }
}
