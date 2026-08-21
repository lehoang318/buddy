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
        override val queryMaxChars = 128
        override val maxResults = 6
        override val totalMaxResults = 10
        override val logPreviewMaxChars = 1024
        override val resultContentMaxChars = 2000
        override val queryPrompt = """## Search Query Generator
You are a search query generator. Your job is to convert user questions into one or more concise, effective web search queries.

### Rules
- If the user's message does not require a web search (e.g., casual conversation, greetings, simple acknowledgments, or questions about the assistant itself), respond with exactly NO_QUERY and nothing else.
- Otherwise, respond with ONLY a JSON object of this exact shape — no explanation, no preamble, no code fences:
  {"queries": ["..."], "recency": "day|week|month|any"}
- "queries": use exactly 1 query for the common case of a single, simple question. Use 2-3 queries only when the question genuinely has multiple independent parts (comparisons, multiple unrelated topics in one message).
- "recency": "day" for breaking news or live/current data (prices, scores, weather, "today"); "week" or "month" for recent-but-not-urgent developments; "any" for timeless facts. Default to "any" when unsure.
- Strip all personal data from every query: names, emails, API keys, account IDs, phone numbers, URLs with credentials, or any other identifying information. Replace them with generic placeholders if needed (e.g. "my account", "a specific API", "this service").
- Keep each query short, specific, and optimized for a search engine. Focus on intent, not literal wording.
- If conversation history provides context (e.g., pronouns, references to earlier topics), use it to make queries specific instead of vague.

### Examples
- "Why is my Gmail account john.doe@gmail.com not receiving emails?" → {"queries": ["Gmail not receiving emails fix"], "recency": "any"}
- "Hello" → NO_QUERY
- "How do I rotate my AWS key AKIAIOSFODNN7EXAMPLE?" → {"queries": ["rotate AWS access key"], "recency": "any"}
- "What's the weather like today?" → {"queries": ["current weather forecast"], "recency": "day"}
- "thank you" → NO_QUERY
- "who are you?" → NO_QUERY
- "Compare the price of the RTX 5090 and the RX 9800 XT" → {"queries": ["RTX 5090 price", "RX 9800 XT price"], "recency": "week"}
- "What time is it in Tokyo?" → {"queries": ["Tokyo current time"], "recency": "any"}"""
        override val webDataInstructions = """The results below were retrieved just now for this specific question and may be more current than your training data. Prefer them for time-sensitive or factual claims, cite sources by their URL, note explicitly when sources conflict, and say so plainly if the results don't actually answer the question rather than guessing. If a "Search Engine Summary" is present, it was machine-synthesized from the sources below it — treat it as a helpful starting point, not ground truth, and verify its claims against the individual sources before citing them."""
    }

    override val summaries = object : SummariesConfig {
        override val maxSummaries = 20
        override val maxQaPairs = 2
        override val minPoints = 2
        override val maxPoints = 3
        override val keyPrefix = "[KEY] "
        override val pointIndent = "  + "
        override val contextHeader = "## Memory"
        override val webDataHeader = "## Web Data"
        override val temperature = 0.2f
        override val maxTokens = 512
        override val restrictivePatterns = listOf(
            "strictly prohibited",
            "not permitted",
            "no terminal commands",
            "external tools not",
            "only permitted",
            "terminal commands and external tools are strictly prohibited",
            "pre-approved sources",
            "approved source materials",
            "no external tools",
            "no terminal",
            "not allowed",
            "sources are not permitted"
        )
    }

    override val prompts = object : PromptsConfig {
        override val summarizerSystem = """You are a summarizer. Given a user/assistant exchange, you must produce a JSON object with %1${'$'}d-%2${'$'}d points summarizing the key information discussed.

Each point in the "points" array must have:
  "text" — A self-contained fact about the exchange. Write it as a plain declarative sentence. Do NOT include bold markers, [KEY] tags, or topic labels like "Deployment target:". Just state the fact directly.
  "key" — Use this field sparingly. Set to true ONLY if:
    • The user made an explicit, hard-to-reverse decision
    • The user stated an absolute, unambiguous constraint
    • The user expressed a strong, clear preference that materially shapes the project direction
    Do NOT set key=true for facts discovered during the exchange, suggestions the assistant offered, general context, or mild interest. Default to false. Most summaries should have at most 1 key=true point.

IMPORTANT: Do NOT include any self-imposed rules about what tools, sources, or methods are "permitted" or "prohibited". Only summarize factual information exchanged between the user and assistant."""
        override val summarizerUserTemplate = "User: %1\$s\nAssistant: %2\$s"
        override val compressSummaries = """You are a summarizer. Below are multiple conversation summaries that need to be merged into a single compact summary.

Produce a JSON object with %1${'$'}d-%2${'$'}d points summarizing the combined information from all entries.

Each point in the "points" array must have:
  "text" — A self-contained fact. Write it as a plain declarative sentence. Do NOT include bold markers, [KEY] tags, or topic labels. Just state the fact directly.
  "key" — Always set to false.

Summaries to merge:
%3${'$'}s"""
    }

    override val togetherAi = object : TogetherAiConfig {
        override val adjustableEffortModels = setOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b"
        )
        override val hybridModels = setOf(
            "deepseek-ai/DeepSeek-V4-Pro",
            "zai-org/GLM-5.1",
            "zai-org/GLM-5",
            "moonshotai/Kimi-K2.6",
            "moonshotai/Kimi-K2.5",
            "Qwen/Qwen3.6-Plus",
            "Qwen/Qwen3.5-397B-A17B",
            "Qwen/Qwen3.5-9B",
            "deepcogito/cogito-v2-1-671b"
        )
        override val effortChatLow = "low"
        override val effortChatHigh = "high"
        override val effortSearch = "low"
        override val hybridChatLow = false
        override val hybridChatHigh = true
        override val hybridSearch = false
    }

    override val siliconflow = object : SiliconFlowConfig {
        override val reasoningModels = setOf(
            "deepseek-ai/DeepSeek-R1",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-14B",
            "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
            "Qwen/QwQ-32B",
            "THUDM/GLM-Z1-32B-0414",
            "THUDM/GLM-Z1-9B-0414",
            "MiniMaxAI/MiniMax-M2.1",
            "MiniMaxAI/MiniMax-M2.5",
            "moonshotai/Kimi-K2-Thinking",
            "Qwen/Qwen3-235B-A22B-Thinking-2507",
            "Qwen/Qwen3-30B-A3B-Thinking-2507",
            "Qwen/Qwen3-Next-80B-A3B-Thinking",
            "Qwen/Qwen3-Omni-30B-A3B-Thinking",
            "Qwen/Qwen3-VL-32B-Thinking",
            "Qwen/Qwen3-VL-8B-Thinking",
            "Qwen/Qwen3-VL-235B-A22B-Thinking",
            "Qwen/Qwen3-VL-30B-A3B-Thinking"
        )
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
        override val maxEntries = 32
        override val maxDataLength = 2048
    }

    override val providers = object : ProvidersConfig {
        override val llm = listOf(
            LlmProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1"),
            LlmProvider("together", "Together AI", "https://api.together.ai/v1"),
            LlmProvider("ollama", "Ollama Cloud", "https://ollama.com/v1/"),
            LlmProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
            LlmProvider("siliconflow", "SiliconFlow", "https://api.siliconflow.com/v1")
        )
        override val webSearch = listOf(
            WebSearchProvider("exa", "Exa", "https://api.exa.ai"),
            WebSearchProvider("linkup", "LinkUp", "https://api.linkup.so"),
            WebSearchProvider("tavily", "Tavily", "https://api.tavily.com")
        )
    }

    override val debugLogging = true
}
