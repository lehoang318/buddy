package com.example.buddy.llm

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.config.ResourceAppConfig
import com.example.buddy.config.ResourceValuesLoader
import com.example.buddy.data.Role
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OpenAIClientRequestTest {
    private val client = OpenAIClient("https://example.test", "test-model", OkHttpClient())

    @Before
    fun setUp() {
        AppConfigProvider.current = ResourceAppConfig(ResourceValuesLoader.loadFromClasspath())
    }

    private fun body(config: LlmGenerationConfig = LlmGenerationConfig(), messages: List<LlmMessage> = listOf(LlmMessage(Role.USER, "Hello"))): JsonObject =
        client.buildChatRequestBody(messages, client.activeModel, config)

    @Test
    fun `max tokens is stated limit times multiplier`() {
        assertEquals(2000, body(LlmGenerationConfig(maxTokens = 1000)).get("max_tokens").asInt)
    }

    @Test
    fun `null config resolves max tokens from resource default`() {
        assertEquals(8192, body().get("max_tokens").asInt)
    }

    @Test
    fun `explicit zero temperature is preserved`() {
        assertEquals(0.0, body(LlmGenerationConfig(temperature = 0f)).get("temperature").asDouble, 0.0)
    }

    @Test
    fun `top k is sent when resolved`() {
        assertEquals(42, body(LlmGenerationConfig(topK = 42)).get("top_k").asInt)
    }

    @Test
    fun `top k falls back to resource default`() {
        assertEquals(20, body().get("top_k").asInt)
    }

    @Test
    fun `response budget is clamped to context window with floor`() {
        client.setModelContextLength("test-model", 5000)
        val messages = listOf(LlmMessage(Role.USER, "X".repeat(20000)))
        assertEquals(256, body(LlmGenerationConfig(maxTokens = 1000), messages).get("max_tokens").asInt)
    }

    @Test
    fun `response budget is not clamped when prompt fits`() {
        client.setModelContextLength("test-model", 10000)
        val messages = listOf(LlmMessage(Role.USER, "X".repeat(8000)))
        assertEquals(2000, body(LlmGenerationConfig(maxTokens = 1000), messages).get("max_tokens").asInt)
    }

    @Test
    fun `response budget is full when context length is unknown`() {
        client.setModelContextLength("test-model", null)
        assertEquals(2000, body(LlmGenerationConfig(maxTokens = 1000)).get("max_tokens").asInt)
    }
}