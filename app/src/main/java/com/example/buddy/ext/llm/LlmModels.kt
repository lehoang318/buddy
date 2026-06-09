package com.example.buddy.ext.llm

import com.example.buddy.data.Role
import com.example.buddy.data.AppResources

data class LlmModel(
    val id: String,
    val name: String,
    val isMultimodal: Boolean = false
)

data class LlmGenerationConfig(
    val temperature: Float = 0f,
    val topP: Float = 0f,
    val topK: Int = 0,
    val maxTokens: Int = 0,
    val reasoningEffort: AppResources.ReasoningEffort? = null
)

data class LlmMessage(
    val role: Role,
    val content: String,
    val imageBase64: String? = null
)
