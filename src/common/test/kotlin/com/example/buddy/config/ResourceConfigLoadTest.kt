package com.example.buddy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceConfigLoadTest {
    private val config: AppConfig = ResourceAppConfig(ResourceValuesLoader.loadFromClasspath())

    @Test
    fun loadsKnownValues() {
        assertEquals(20, config.summaries.maxSummaries)
        assertEquals(3, config.summaries.maxSessionTags)
        assertEquals(0.7f, config.llm.temperature, 0f)
        assertEquals(0.95f, config.llm.topP, 0f)
        assertEquals(4096, config.llm.maxTokens)
        assertEquals("You are a helpful assistant.", config.llm.defaultSystemMessage)
        assertEquals(32, config.events.maxEntries)
        assertEquals(2048, config.events.maxDataLength)
        assertTrue(config.prompts.summarizerSystem.contains("%4\$s"))
    }

    @Test
    fun loadsProviders() {
        assertEquals(
            listOf("fireworks", "together", "ollama", "openrouter", "siliconflow"),
            config.providers.llm.map { it.id }
        )
        assertEquals(listOf("exa", "linkup", "tavily"), config.providers.webSearch.map { it.id })
    }

    @Test
    fun loadsSessionTagsFromResource() {
        assertEquals(
            listOf("Politics", "Business", "World", "Technology", "Science", "Health", "Environment", "Justice", "Entertainment", "Sports"),
            config.summaries.sessionTags
        )
    }

    @Test
    fun loadsAllConfigKeys() {
        config.llm.run { temperature; topP; topK; maxTokens; defaultSystemMessage }
        config.search.run { queryTemperature; queryMaxChars; maxResults; totalMaxResults; logPreviewMaxChars; resultContentMaxChars; queryPrompt; webDataInstructions }
        config.summaries.run { maxSummaries; maxQaPairs; minPoints; maxPoints; maxSessionTags; sessionTags; keyPrefix; pointIndent; contextHeader; webDataHeader; temperature; maxTokens; restrictivePatterns }
        config.prompts.run { summarizerSystem; summarizerUserTemplate; compressSummaries }
        config.togetherAi.run { adjustableEffortModels; hybridModels; effortChatLow; effortChatHigh; effortSearch; hybridChatLow; hybridChatHigh; hybridSearch }
        config.siliconflow.run { reasoningModels; hybridChatLow; hybridChatHigh; hybridSearch; reasoningChatLow; reasoningChatHigh; reasoningSearch }
        config.defaults.run { reasoningChatLow; reasoningChatHigh; reasoningSearch }
        config.providers.llm.isNotEmpty()
        config.providers.webSearch.isNotEmpty()
    }
}