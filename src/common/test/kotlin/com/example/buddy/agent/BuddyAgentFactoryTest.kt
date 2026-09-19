package com.example.buddy.agent

import com.example.buddy.config.AppConfig
import com.example.buddy.config.ResourceAppConfig
import com.example.buddy.config.ResourceValuesLoader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyAgentFactoryTest {
    private val config: AppConfig = ResourceAppConfig(ResourceValuesLoader.loadFromClasspath())
    private val statePlaceholderPattern = Regex("""\{+[^\{\}]*\}+""")

    @Test
    fun neutralizesBracesInDynamicInstructionContent() {
        val instruction = BuddyAgentFactory.buildInstruction(
            config = config.agentic,
            systemMessage = "never emit {style_block}",
            outputLimit = 1000,
            summariesContext = "chose colors {red, blue}",
            recentConversation = "user: show me {this}",
            fetchedUrlsContext = "body { font-size: 12px }",
            toolInstruction = null
        )
        assertFalse(statePlaceholderPattern.containsMatchIn(instruction))
        assertFalse(instruction.contains('{'))
        assertFalse(instruction.contains('}'))
        assertTrue(instruction.contains("style_block"))
        assertTrue(instruction.contains('\uFF5B'))
    }

    @Test
    fun staticInstructionResourcesContainNoBraces() {
        with(config.agentic) {
            listOf(
                baseInstruction,
                webSearchInstruction,
                fetchUrlInstruction,
                noToolsInstruction,
                webSearchToolDescription,
                fetchUrlToolDescription
            ).forEach { value ->
                assertFalse(value.contains('{'))
                assertFalse(value.contains('}'))
            }
        }
    }
}
