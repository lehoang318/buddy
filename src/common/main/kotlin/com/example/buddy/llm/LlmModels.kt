package com.example.buddy.llm

import com.example.buddy.data.Role

data class LlmModel(
    val id: String,
    val name: String,
    val isMultimodal: Boolean = false,
    val contextLength: Int? = null
)

data class LlmGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val reasoningEffort: ReasoningEffort? = null
)

data class LlmMessage(
    val role: Role,
    val content: String,
    val imageBase64: String? = null
)
