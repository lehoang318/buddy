package com.example.buddy.agent

import com.example.buddy.config.AgenticConfig
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.tools.BaseTool
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the root ADK agent and its instruction for a chat turn. */
object BuddyAgentFactory {

    private const val LEFT_BRACE = '\uFF5B'
    private const val RIGHT_BRACE = '\uFF5D'

    fun buildRootAgent(model: Model, instruction: String, tools: List<BaseTool>, config: AgenticConfig): LlmAgent =
        LlmAgent(
            name = config.agentName,
            description = config.agentDescription,
            model = model,
            instruction = Instruction(instruction),
            tools = tools,
            maxSteps = config.maxSteps
        )

    fun buildInstruction(
        config: AgenticConfig,
        systemMessage: String,
        outputLimit: Int,
        summariesContext: String,
        recentConversation: String,
        fetchedUrlsContext: String,
        toolInstruction: String?
    ): String {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
        val base = config.baseInstruction.format(escapeStatePlaceholders(systemMessage), date, outputLimit)
        return buildList {
            add(base)
            summariesContext.takeIf { it.isNotBlank() }?.let { add(escapeStatePlaceholders(it)) }
            fetchedUrlsContext.takeIf { it.isNotBlank() }?.let { add(escapeStatePlaceholders(it)) }
            recentConversation.takeIf { it.isNotBlank() }?.let { add(escapeStatePlaceholders(it)) }
            add(toolInstruction ?: config.noToolsInstruction)
        }.joinToString("\n\n")
    }

    private fun escapeStatePlaceholders(text: String): String =
        text.replace('{', LEFT_BRACE).replace('}', RIGHT_BRACE)
}
