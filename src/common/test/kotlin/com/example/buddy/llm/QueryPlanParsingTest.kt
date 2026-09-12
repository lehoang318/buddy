package com.example.buddy.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryPlanParsingTest {
    @Test
    fun parsesCanonicalPlanAndCapsQueries() {
        val plan = parseQueryPlan("""{"search":true,"queries":["one","two","three","four"],"recency":"week"}""")

        assertEquals(listOf("one", "two", "three"), plan?.queries)
        assertEquals(com.example.buddy.search.SearchRecency.WEEK, plan?.recency)
    }

    @Test
    fun parsesLegacyShapesWithoutSearchField() {
        assertEquals(listOf("single"), parseQueryPlan("""{"queries":["single"]}""")?.queries)
        assertEquals(listOf("single"), parseQueryPlan("""{"query":"single"}""")?.queries)
        assertEquals(listOf("first", "second"), parseQueryPlan("""{"queries":["first","second"]}""")?.queries)
        assertEquals(listOf("fenced"), parseQueryPlan("```json\n{\"queries\":[\"fenced\"]}\n```")?.queries)
    }

    @Test
    fun skipsWhenSearchFieldIsFalseEvenWithQueriesPresent() {
        assertNull(parseQueryPlan("""{"search":false,"queries":["one","two"],"recency":"week"}"""))
        assertNull(parseQueryPlan("""{"search":false,"queries":[],"recency":"any"}"""))
    }

    @Test
    fun skipsBareBooleanFalseResponse() {
        assertNull(parseQueryPlan("false"))
        assertNull(parseQueryPlan("true"))
    }

    @Test
    fun skipsNoJsonProseInsteadOfUsingItAsQuery() {
        assertNull(parseQueryPlan("Gmail problems are usually caused by spam filters, forwarding rules, and account settings. Check your spam folder first."))
        assertNull(parseQueryPlan("Sure! Here is what you need to know about rotating access keys."))
        assertNull(parseQueryPlan("The answer is NO_QUERY"))
    }

    @Test
    fun rejectsAnswerShapedQueries() {
        val essay = "The quick brown fox jumps over the lazy dog. ".repeat(6).trim()
        assertNull(parseQueryPlan("""{"search":true,"queries":["$essay"],"recency":"any"}"""))
        assertNull(parseQueryPlan("""{"search":true,"queries":["Gmail messages can stop arriving because of spam filters or forwarding rules. Check the spam folder. Verify the forwarding settings and review account filters."],"recency":"any"}"""))
        assertNull(parseQueryPlan("""{"search":true,"queries":["first line\nsecond line"],"recency":"any"}"""))
        assertNull(parseQueryPlan("""{"search":true,"queries":["- a bullet item"],"recency":"any"}"""))
        assertNull(parseQueryPlan("""{"search":true,"queries":["### a heading"],"recency":"any"}"""))
    }

    @Test
    fun dropsRejectedQueriesButKeepsValidOnes() {
        val plan = parseQueryPlan("""{"search":true,"queries":["RTX 5090 price","Here is a detailed comparison of the two cards. The RTX 5090 costs more than the RX 9800 XT. Most reviewers recommend waiting for independent benchmarks.","RX 9800 XT price"],"recency":"week"}""")
        assertEquals(listOf("RTX 5090 price", "RX 9800 XT price"), plan?.queries)
    }

    @Test
    fun keepsRealisticQueriesWithDotsOperatorsAndHash() {
        assertEquals(listOf("Node.js 22 LTS end of life", "St. Louis weather", "Dr. Seuss books"),
            parseQueryPlan("""{"queries":["Node.js 22 LTS end of life","St. Louis weather","Dr. Seuss books"],"recency":"any"}""")?.queries)
        assertEquals(listOf("site:github.com -android", "RTX 5090 - release date", "C# generics"),
            parseQueryPlan("""{"queries":["site:github.com -android","RTX 5090 - release date","C# generics"],"recency":"any"}""")?.queries)
    }

    @Test
    fun truncatesQueriesAndRededupesAfterTruncation() {
        val long = "word ".repeat(40).trim()
        val plan = parseQueryPlan("""{"queries":["$long","$long and different"],"recency":"any"}""")
        assertEquals(1, plan?.queries?.size)
        assert(plan!!.queries.single().length <= 128)
    }

    @Test
    fun skipsWhenEverythingIsDroppedAfterValidation() {
        assertNull(parseQueryPlan("""{"queries":["   "]}"""))
    }
}
