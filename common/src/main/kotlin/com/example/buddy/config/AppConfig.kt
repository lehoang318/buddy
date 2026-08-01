package com.example.buddy.config

import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint

interface LlmConfig {
    val temperature: Float
    val topP: Float
    val topK: Int
    val maxTokens: Int
    val defaultSystemMessage: String
}

interface SearchConfig {
    val queryTemperature: Float
    val queryMaxChars: Int
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

interface AppConfig {
    val llm: LlmConfig
    val search: SearchConfig
    val summaries: SummariesConfig
    val prompts: PromptsConfig
    val togetherAi: TogetherAiConfig
    val siliconflow: SiliconFlowConfig
    val defaults: ReasoningDefaultsConfig
    val events: EventsConfig
    val debugLogging: Boolean
}

object AppConfigProvider {
    var current: AppConfig = DefaultAppConfig
}

object DefaultAppConfig : AppConfig {
    override val llm = object : LlmConfig {
        override val temperature = 0.7f
        override val topP = 0.95f
        override val topK = 20
        override val maxTokens = 4096
        override val defaultSystemMessage = "You are a helpful assistant."
    }

    override val search = object : SearchConfig {
        override val queryTemperature = 0.2f
        override val queryMaxChars = 150
        override val maxResults = 6
        override val totalMaxResults = 10
        override val logPreviewMaxChars = 1024
        override val resultContentMaxChars = 2000
        override val queryPrompt = "You are a search query generator. Based on the user's message, respond with ONLY a JSON object {\"queries\": [\"...\"], \"recency\": \"day|week|month|any\"}, or exactly NO_QUERY if no search is needed."
        override val webDataInstructions = "The web results below were retrieved for this specific question and may be more current than your training data. Prefer them for time-sensitive or factual claims, cite sources using their URLs, note when results conflict, and say explicitly if they don't answer the question rather than guessing. If a \"Search Engine Summary\" is present, it was machine-synthesized from the sources below it — treat it as a helpful starting point, not ground truth, and verify its claims against the individual sources before citing them."
    }

    override val summaries = object : SummariesConfig {
        override val maxSummaries = 20
        override val maxQaPairs = 2
        override val minPoints = 2
        override val maxPoints = 3
        override val keyPrefix = "[KEY] "
        override val pointIndent = "  + "
        override val contextHeader = "## Use the context below when relevant:"
        override val webDataHeader = "## Web Data"
        override val temperature = 0.2f
        override val maxTokens = 512
        override val restrictivePatterns = emptyList<String>()
    }

    override val prompts = object : PromptsConfig {
        override val summarizerSystem = "You are a summarizer. Given a user/assistant exchange, you must produce a JSON object with %1\$d-%2\$d points summarizing the key information discussed."
        override val summarizerUserTemplate = "User: %1\$s\nAssistant: %2\$s"
        override val compressSummaries = "You are a summarizer. Below are multiple conversation summaries that need to be merged into a single compact summary.\n\nProduce a JSON object with %1\$d-%2\$d points summarizing the combined information from all entries.\n\nSummaries to merge:\n%3\$s"
    }

    override val togetherAi = object : TogetherAiConfig {
        override val adjustableEffortModels = emptySet<String>()
        override val hybridModels = emptySet<String>()
        override val effortChatLow = "low"
        override val effortChatHigh = "high"
        override val effortSearch = "low"
        override val hybridChatLow = false
        override val hybridChatHigh = true
        override val hybridSearch = false
    }

    override val siliconflow = object : SiliconFlowConfig {
        override val reasoningModels = emptySet<String>()
        override val hybridChatLow = false
        override val hybridChatHigh = 8192
        override val hybridSearch = false
        override val reasoningChatLow = 2048
        override val reasoningChatHigh = 8192
        override val reasoningSearch = 2048
    }

    override val defaults = object : ReasoningDefaultsConfig {
        override val reasoningChatLow = "low"
        override val reasoningChatHigh = "high"
        override val reasoningSearch = "low"
    }

    override val events = object : EventsConfig {
        override val maxEntries = 20
        override val maxDataLength = 2000
    }

    override val debugLogging = true
}
