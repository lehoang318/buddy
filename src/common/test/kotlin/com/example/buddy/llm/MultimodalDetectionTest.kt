package com.example.buddy.llm

import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalDetectionTest {
    @Test
    fun recognizesCommonVisionModelNames() {
        val client = OpenAIClient("https://example.com", "test", OkHttpClient())

        assertTrue(client.isModelMultimodal("provider/llava-1.6"))
        assertTrue(client.isModelMultimodal("gemini-2.5-pro"))
    }
}
