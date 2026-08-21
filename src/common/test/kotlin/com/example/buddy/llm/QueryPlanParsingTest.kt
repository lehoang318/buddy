package com.example.buddy.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryPlanParsingTest {
    @Test
    fun parsesCanonicalPlanAndCapsQueries() {
        val plan = parseQueryPlan("""{"queries":["one","two","three","four"],"recency":"week"}""")

        assertEquals(listOf("one", "two", "three"), plan?.queries)
        assertEquals(com.example.buddy.search.SearchRecency.WEEK, plan?.recency)
    }

    @Test
    fun acceptsCommonSmallModelShapes() {
        assertEquals(listOf("single"), parseQueryPlan("""{"query":"single"}""")?.queries)
        assertEquals(listOf("first", "second"), parseQueryPlan("[\"first\",\"second\"]")?.queries)
        assertEquals(listOf("plain query"), parseQueryPlan("plain query")?.queries)
        assertEquals(listOf("fenced"), parseQueryPlan("```json\n{\"queries\":[\"fenced\"]}\n```")?.queries)
    }

    @Test
    fun skipsSentinelAndDuplicateQueries() {
        assertNull(parseQueryPlan("NO_QUERY"))
        assertEquals(listOf("same", "other"), parseQueryPlan("""{"queries":["same","SAME","other"]}""")?.queries)
    }
}
