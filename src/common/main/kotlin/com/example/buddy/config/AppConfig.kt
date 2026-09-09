package com.example.buddy.config

import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.example.buddy.data.LlmProvider
import com.example.buddy.data.WebSearchProvider

interface LlmConfig {
    val temperature: Float
    val topP: Float
    val topK: Int
    val maxTokens: Int
    val responseLimitMultiplier: Int
    val minResponseTokens: Int
    val maxRequestChars: Int
    val defaultSystemMessage: String
}

interface SearchConfig {
    val queryTemperature: Float
    val queryMaxChars: Int
    val queryMaxTokens: Int
    val queryRejectChars: Int
    val maxResults: Int
    val totalMaxResults: Int
    val logPreviewMaxChars: Int
    val resultContentMaxChars: Int
    val queryPrompt: String
    val webDataInstructions: String
}

interface SummariesConfig {
    val maxSummaries: Int
    val maxQaPairs: Int
    val minPoints: Int
    val maxPoints: Int
    val maxSessionTags: Int
    val sessionTags: List<String>
    val keyPrefix: String
    val pointIndent: String
    val contextHeader: String
    val webDataHeader: String
    val temperature: Float
    val maxTokens: Int
    val restrictivePatterns: List<String>

    fun sanitizeSummaryPoints(points: List<SummaryPoint>): List<SummaryPoint> {
        if (restrictivePatterns.isEmpty()) return points
        return points.map { point ->
            var text = point.text
            restrictivePatterns.forEach { pattern -> text = text.replace(pattern, "") }
            point.copy(text = text.trim())
        }
    }

    fun formatSummaryAsText(summary: Summary): String = summary.points.joinToString("\n") { point ->
        val prefix = if (point.key) keyPrefix else ""
        "$pointIndent$prefix${point.text}"
    }

    fun formatSummariesContext(summaries: List<Summary>): String {
        if (summaries.isEmpty()) return ""
        return buildString {
            appendLine(contextHeader)
            summaries.forEach { summary ->
                appendLine("### ${summary.question}")
                appendLine(formatSummaryAsText(summary))
            }
        }.trimEnd()
    }
}

interface PromptsConfig {
    val summarizerSystem: String
    val summarizerUserTemplate: String
    val compressSummaries: String
}

interface TogetherAiConfig {
    val adjustableEffortModels: Set<String>
    val hybridModels: Set<String>
    val effortChatLow: String
    val effortChatHigh: String
    val effortSearch: String
    val hybridChatLow: Boolean
    val hybridChatHigh: Boolean
    val hybridSearch: Boolean
}

interface SiliconFlowConfig {
    val reasoningModels: Set<String>
    val hybridChatLow: Boolean
    val hybridChatHigh: Int
    val hybridSearch: Boolean
    val reasoningChatLow: Int
    val reasoningChatHigh: Int
    val reasoningSearch: Int
}

interface ReasoningDefaultsConfig {
    val reasoningChatLow: String
    val reasoningChatHigh: String
    val reasoningSearch: String
}

interface EventsConfig {
    val maxEntries: Int
    val maxDataLength: Int
}

interface ProvidersConfig {
    val llm: List<LlmProvider>
    val webSearch: List<WebSearchProvider>
}

interface AppConfig {
    val llm: LlmConfig
    val search: SearchConfig
    val summaries: SummariesConfig
    val prompts: PromptsConfig
    val togetherAi: TogetherAiConfig
    val siliconflow: SiliconFlowConfig
    val defaults: ReasoningDefaultsConfig
    val events: EventsConfig
    val providers: ProvidersConfig
    val debugLogging: Boolean
}

object AppConfigProvider {
    private var installed: AppConfig? = null

    var current: AppConfig
        get() = installed ?: ResourceAppConfig(ResourceValuesLoader.loadFromClasspath()).also { installed = it }
        set(value) { installed = value }
}

