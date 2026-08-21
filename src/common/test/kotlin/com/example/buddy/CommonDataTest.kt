package com.example.buddy

import com.example.buddy.data.LlmProvider
import com.example.buddy.data.Role
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.logging.DesktopLogger
import com.example.buddy.logging.debug
import com.example.buddy.logging.info
import org.junit.Assert.assertEquals
import org.junit.Test

class CommonDataTest {
    private val logger = DesktopLogger("BuddyTest")

    @Test
    fun `common data models work on the desktop JVM`() {
        val summary = Summary(
            question = "What is Buddy?",
            points = listOf(SummaryPoint("A Kotlin Android AI assistant", key = true))
        )
        val provider = LlmProvider("test", "Test provider", "https://example.test")

        logger.info("CommonDataTest", "Testing common models", data = summary.toString())
        logger.debug("CommonDataTest", "Provider created", data = provider.id)

        assertEquals(Role.ASSISTANT, Role.valueOf("ASSISTANT"))
        assertEquals("test", provider.id)
        assertEquals(true, summary.points.single().key)
    }
}
