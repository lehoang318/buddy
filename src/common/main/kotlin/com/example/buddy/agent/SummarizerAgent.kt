package com.example.buddy.agent

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.SessionTags
import com.example.buddy.data.Summary
import com.example.buddy.llm.LlmClient
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role as AdkRole
import java.util.UUID

/**
 * Deterministic summarization sub-agent. Wraps the configured summarizer/compress prompts in
 * dedicated ADK [LlmAgent]s, run through an in-memory runner after each finished turn. Falls back to
 * the direct [LlmClient] calls when the agent produces no usable output, so the per-turn summary
 * contract (and the History screen's tags) is never silently broken.
 */
class SummarizerAgent(private val client: LlmClient) {

    suspend fun summarize(userQuestion: String, assistantResponse: String, imageBase64: String?): Summary {
        if (imageBase64 != null) {
            return client.generateSummary(userQuestion, assistantResponse, client.activeModel, imageBase64)
        }
        val summariesConfig = AppConfigProvider.current.summaries
        val system = AppConfigProvider.current.prompts.summarizerSystem.format(
            summariesConfig.minPoints,
            summariesConfig.maxPoints,
            summariesConfig.maxSessionTags,
            SessionTags.CATEGORIES.joinToString(", ")
        ) + "\n\nYour response must fit within ${summariesConfig.maxTokens} tokens maximum."
        val user = AppConfigProvider.current.prompts.summarizerUserTemplate.format(userQuestion, assistantResponse)

        val raw = runAgent(system, user)
        if (raw.isNullOrBlank()) {
            return client.generateSummary(userQuestion, assistantResponse, client.activeModel, null)
        }
        val parsed = SummaryParser.parse(userQuestion, raw)
        return if (parsed.points.isEmpty()) {
            client.generateSummary(userQuestion, assistantResponse, client.activeModel, null)
        } else {
            parsed
        }
    }

    suspend fun compress(summariesToCompress: List<Summary>): Summary {
        val keyPoints = summariesToCompress.flatMap { it.points }.filter { it.key }
        val formattedParts = mutableListOf<String>()
        for (summary in summariesToCompress) {
            val nonKeys = summary.points.filter { !it.key }
            formattedParts.add("- ${summary.question}")
            if (nonKeys.isNotEmpty()) {
                nonKeys.forEach { formattedParts.add("  + ${it.text}") }
            } else {
                formattedParts.add("  (key points retained separately)")
            }
        }
        val summariesConfig = AppConfigProvider.current.summaries
        val system = AppConfigProvider.current.prompts.compressSummaries.format(
            summariesConfig.minPoints,
            summariesConfig.maxPoints,
            formattedParts.joinToString("\n")
        ) + "\n\nYour response must fit within ${summariesConfig.maxTokens} tokens maximum."

        val raw = runAgent(system, "Merge the summaries above into a single compact summary now.")
        if (raw.isNullOrBlank()) return client.compressSummaries(summariesToCompress, client.activeModel)

        val parsed = SummaryParser.parse("Earlier conversation", raw)
        if (parsed.points.isEmpty()) return client.compressSummaries(summariesToCompress, client.activeModel)

        val combined = parsed.points + keyPoints
        val sanitized = summariesConfig.sanitizeSummaryPoints(combined)
        return Summary(question = "Earlier conversation", points = sanitized)
    }

    private suspend fun runAgent(systemInstruction: String, userMessage: String): String? {
        val agent = LlmAgent(
            name = "summarizer",
            model = OpenAICompatibleModel(client, client.activeModel),
            instruction = Instruction(systemInstruction)
        )
        val runner = InMemoryRunner(agent, appName = "buddy-summarizer")
        val content = Content(role = AdkRole.USER, parts = listOf(Part(text = userMessage)))
        val buffer = StringBuilder()
        runner.runAsync(
            userId = "buddy-user",
            sessionId = UUID.randomUUID().toString(),
            newMessage = content
        ).collect { event ->
            if (event.isFinalResponse) buffer.append(event.contentText())
        }
        return buffer.toString().takeIf { it.isNotBlank() }
    }
}
