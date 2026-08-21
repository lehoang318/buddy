package com.example.buddy.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SearchRecencyTest {
    @Test
    fun mapsRecencyToExpectedDateFilters() {
        val today = LocalDate.now()
        assertEquals(today.minusDays(1).toString(), SearchRecency.DAY.sinceDateOrNull())
        assertEquals(today.minusDays(7).toString(), SearchRecency.WEEK.sinceDateOrNull())
        assertEquals(today.minusDays(31).toString(), SearchRecency.MONTH.sinceDateOrNull())
        assertNull(SearchRecency.ANY.sinceDateOrNull())
    }
}
