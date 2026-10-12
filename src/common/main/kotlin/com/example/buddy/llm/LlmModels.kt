package com.example.buddy.llm

import com.example.buddy.data.Role
import com.google.gson.JsonObject

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

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

data class LlmMessage(
    val role: Role,
    val content: String,
    val imageBase64: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
    val toolCallId: String? = null
)

sealed interface LlmStreamEvent {
    data class TextDelta(val text: String) : LlmStreamEvent
    data class ReasoningDelta(val text: String) : LlmStreamEvent
    data class ToolCalls(val calls: List<LlmToolCall>) : LlmStreamEvent
    data class Finished(val finishReason: String?) : LlmStreamEvent
}
