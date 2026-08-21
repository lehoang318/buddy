package com.example.buddy.data

data class LlmSettings(
    val provider: String = "",
    val model: String = "",
    val temperature: Float = 0f,
    val topP: Float = 0f,
    val topK: Int = 0,
    val maxTokens: Int = 0,
    val reasoningEffort: String = "",
    val systemMessage: String = "",
    val webSearchProvider: String = "",
    val customLlmProvidersJson: String = "",
    val customWebSearchProvidersJson: String = ""
)
