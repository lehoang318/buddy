package com.example.buddy.agent

import com.example.buddy.data.Role as BuddyRole
import com.example.buddy.llm.LlmClient
import com.example.buddy.llm.LlmGenerationConfig
import com.example.buddy.llm.LlmMessage
import com.example.buddy.llm.LlmStreamEvent
import com.example.buddy.llm.LlmTool
import com.example.buddy.llm.LlmToolCall
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FinishReason
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role as AdkRole
import com.google.adk.kt.types.Schema
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Base64

private val MAP_TYPE = object : TypeToken<Map<String, Any?>>() {}.type

/** Converts an ADK [Schema] into the OpenAPI-subset JSON schema an OpenAI-compatible API expects. */
internal fun Schema.toJsonSchema(): JsonObject = JsonObject().apply {
    type?.let { addProperty("type", it.name.lowercase()) }
    description?.let { addProperty("description", it) }
    items?.let { add("items", it.toJsonSchema()) }
    properties?.takeIf { it.isNotEmpty() }?.let { props ->
        add("properties", JsonObject().apply { props.forEach { (name, schema) -> add(name, schema.toJsonSchema()) } })
    }
    required?.takeIf { it.isNotEmpty() }?.let { req ->
        add("required", JsonArray().apply { req.forEach { add(it) } })
    }
    enum?.takeIf { it.isNotEmpty() }?.let { values ->
        add("enum", JsonArray().apply { values.forEach { add(it) } })
    }
    format?.let { addProperty("format", it) }
    nullable?.let { addProperty("nullable", it) }
    title?.let { addProperty("title", it) }
    pattern?.let { addProperty("pattern", it) }
    minimum?.let { addProperty("minimum", it) }
    maximum?.let { addProperty("maximum", it) }
    minLength?.let { addProperty("minLength", it) }
    maxLength?.let { addProperty("maxLength", it) }
    minItems?.let { addProperty("minItems", it) }
    maxItems?.let { addProperty("maxItems", it) }
}

internal fun Blob.toDataUri(): String? {
    val bytes = data ?: return null
    return "data:${mimeType ?: "image/jpeg"};base64,${Base64.getEncoder().encodeToString(bytes)}"
}

/** Parses a `data:<mime>;base64,<payload>` string back into an ADK [Blob]; null when malformed. */
internal fun dataUriToBlob(dataUri: String): Blob? {
    val comma = dataUri.indexOf(',')
    if (comma <= 0) return null
    val meta = dataUri.substring(0, comma)
    val payload = dataUri.substring(comma + 1)
    if (!meta.contains("base64")) return null
    val mime = meta.removePrefix("data:").substringBefore(';').ifBlank { "image/jpeg" }
    return try {
        Blob(mimeType = mime, data = Base64.getDecoder().decode(payload))
    } catch (_: Exception) {
        null
    }
}

/**
 * An ADK [Model] backed by any OpenAI-compatible [LlmClient] (Fireworks, Together, Ollama Cloud,
 * OpenRouter, SiliconFlow, custom). Translates ADK request/response types to the OpenAI chat
 * format, including function/tool calling, and streams partial text responses so the UI can render
 * tokens as they arrive.
 */
class OpenAICompatibleModel(
    private val client: LlmClient,
    override val name: String
) : Model {
    private val gson = Gson()

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val messages = buildMessages(request)
        val tools = request.config.tools.orEmpty()
            .flatMap { it.functionDeclarations.orEmpty() }
            .map { declaration ->
                LlmTool(
                    name = declaration.name,
                    description = declaration.description,
                    parameters = declaration.parameters?.toJsonSchema() ?: JsonObject()
                )
            }

        val config = LlmGenerationConfig(
            temperature = request.config.temperature,
            topP = request.config.topP,
            topK = request.config.topK,
            maxTokens = request.config.maxOutputTokens
        )

        val text = StringBuilder()
        var calls: List<LlmToolCall> = emptyList()

        client.streamEvents(messages, name, config, tools.takeIf { it.isNotEmpty() }).collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    text.append(event.text)
                    emit(
                        LlmResponse(
                            content = Content(role = AdkRole.MODEL, parts = listOf(Part(text = event.text))),
                            partial = true
                        )
                    )
                }
                is LlmStreamEvent.ReasoningDelta -> {
                    emit(
                        LlmResponse(
                            content = Content(role = AdkRole.MODEL, parts = listOf(Part(text = event.text, thought = true))),
                            partial = true
                        )
                    )
                }
                is LlmStreamEvent.ToolCalls -> calls = event.calls
                is LlmStreamEvent.Finished -> Unit
            }
        }

        emit(finalResponse(text.toString(), calls))
    }

    private fun finalResponse(text: String, calls: List<LlmToolCall>): LlmResponse {
        val parts = mutableListOf<Part>()
        if (text.isNotBlank()) parts += Part(text = text)
        calls.forEach { call ->
            val args: Map<String, Any?> = try {
                gson.fromJson<Map<String, Any?>>(call.arguments, MAP_TYPE) ?: emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }
            parts += Part(functionCall = FunctionCall(name = call.name, args = args, id = call.id))
        }
        return LlmResponse(
            content = parts.takeIf { it.isNotEmpty() }?.let { Content(role = AdkRole.MODEL, parts = it) },
            finishReason = FinishReason.STOP,
            partial = false
        )
    }

    private fun buildMessages(request: LlmRequest): List<LlmMessage> {
        val out = mutableListOf<LlmMessage>()
        val systemText = request.config.systemInstruction?.parts?.mapNotNull { it.text }?.joinToString("\n\n")
        if (!systemText.isNullOrBlank()) {
            out += LlmMessage(BuddyRole.SYSTEM, systemText)
        }
        request.contents.forEach { content -> appendContent(out, content) }
        return out
    }

    private fun appendContent(out: MutableList<LlmMessage>, content: Content) {
        val parts = content.parts

        val functionResponses = parts.mapNotNull { it.functionResponse }
        if (functionResponses.isNotEmpty()) {
            functionResponses.forEach { response ->
                out += LlmMessage(
                    role = BuddyRole.TOOL,
                    content = gson.toJson(response.response),
                    toolCallId = response.id ?: response.name
                )
            }
            appendUserRemainder(out, parts.filter { it.functionResponse == null })
            return
        }

        val functionCalls = parts.mapNotNull { it.functionCall }
        if (content.role == AdkRole.MODEL || functionCalls.isNotEmpty()) {
            val text = parts.filter { it.thought != true }.mapNotNull { it.text }.joinToString("")
            val toolCalls = functionCalls.map { call ->
                LlmToolCall(
                    id = call.id ?: "call_${java.util.UUID.randomUUID()}",
                    name = call.name,
                    arguments = gson.toJson(call.args)
                )
            }
            out += LlmMessage(
                role = BuddyRole.ASSISTANT,
                content = text,
                toolCalls = toolCalls.takeIf { it.isNotEmpty() }
            )
            return
        }

        appendUserRemainder(out, parts)
    }

    private fun appendUserRemainder(out: MutableList<LlmMessage>, parts: List<Part>) {
        if (parts.isEmpty()) return
        val text = parts.mapNotNull { it.text }.joinToString("\n")
        val image = parts.firstNotNullOfOrNull { it.inlineData?.toDataUri() }
        if (text.isNotBlank() || image != null) {
            out += LlmMessage(BuddyRole.USER, text, imageBase64 = image)
        }
    }
}
