package com.example.buddy.fetch

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlExtractorTest {
    @Test
    fun `extracts https urls and removes punctuation`() {
        assertEquals(
            listOf("https://example.com/a", "https://example.org"),
            extractUrls("Read https://example.com/a, then https://example.org.")
        )
    }
}
