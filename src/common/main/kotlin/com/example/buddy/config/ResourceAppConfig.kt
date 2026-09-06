package com.example.buddy.config

import com.example.buddy.data.LlmProvider
import com.example.buddy.data.WebSearchProvider

class ResourceAppConfig(private val values: ResourceValues) : AppConfig {
    override val llm = object : LlmConfig {
        override val temperature get() = values.dimension("default_temperature")
        override val topP get() = values.dimension("default_top_p")
        override val topK get() = values.integer("default_top_k")
        override val maxTokens get() = values.integer("default_max_tokens")
        override val responseLimitMultiplier get() = values.integer("response_hard_limit_multiplier")
        override val minResponseTokens get() = values.integer("min_response_tokens")
        override val maxRequestChars get() = values.integer("request_max_chars")
        override val defaultSystemMessage get() = values.string("default_system_message")
    }

    override val search = object : SearchConfig {
        override val queryTemperature get() = values.dimension("search_query_temperature")
        override val queryMaxChars get() = values.integer("search_query_max_chars")
        override val queryMaxTokens get() = values.integer("search_query_max_tokens")
        override val maxResults get() = values.integer("search_max_results")
        override val totalMaxResults get() = values.integer("search_total_max_results")
        override val logPreviewMaxChars get() = values.integer("log_preview_max_chars")
        override val resultContentMaxChars get() = values.integer("search_result_content_max_chars")
        override val queryPrompt get() = values.string("search_query_prompt")
        override val webDataInstructions get() = values.string("web_data_instructions")
    }

    override val summaries = object : SummariesConfig {
        override val maxSummaries get() = values.integer("max_summaries")
        override val maxQaPairs get() = values.integer("max_qa_pairs")
        override val minPoints get() = values.integer("min_summary_points")
        override val maxPoints get() = values.integer("max_summary_points")
        override val maxSessionTags get() = values.integer("max_session_tags")
        override val sessionTags get() = values.array("session_tags")
        override val keyPrefix get() = values.string("key_prefix")
        override val pointIndent get() = values.string("point_indent")
        override val contextHeader get() = values.string("context_header")
        override val webDataHeader get() = values.string("web_data_header")
        override val temperature get() = values.dimension("summary_temperature")
        override val maxTokens get() = values.integer("summary_max_tokens")
        override val restrictivePatterns get() = values.array("restrictive_patterns")
    }

    override val prompts = object : PromptsConfig {
        override val summarizerSystem get() = values.string("summarizer_system_prompt")
        override val summarizerUserTemplate get() = values.string("summarizer_user_template")
        override val compressSummaries get() = values.string("compress_summaries_prompt")
    }

    override val togetherAi = object : TogetherAiConfig {
        override val adjustableEffortModels get() = values.array("together_reasoning_effort_models").toSet()
        override val hybridModels get() = values.array("together_reasoning_hybrid_models").toSet()
        override val effortChatLow get() = values.string("together_effort_chat_low")
        override val effortChatHigh get() = values.string("together_effort_chat_high")
        override val effortSearch get() = values.string("together_effort_search")
        override val hybridChatLow get() = values.bool("together_hybrid_chat_low")
        override val hybridChatHigh get() = values.bool("together_hybrid_chat_high")
        override val hybridSearch get() = values.bool("together_hybrid_search")
    }

    override val siliconflow = object : SiliconFlowConfig {
        override val reasoningModels get() = values.array("siliconflow_reasoning_models").toSet()
        override val hybridChatLow get() = values.bool("siliconflow_hybrid_chat_low")
        override val hybridChatHigh get() = values.integer("siliconflow_hybrid_chat_high")
        override val hybridSearch get() = values.bool("siliconflow_hybrid_search")
        override val reasoningChatLow get() = values.integer("siliconflow_reasoning_chat_low")
        override val reasoningChatHigh get() = values.integer("siliconflow_reasoning_chat_high")
        override val reasoningSearch get() = values.integer("siliconflow_reasoning_search")
    }

    override val defaults = object : ReasoningDefaultsConfig {
        override val reasoningChatLow get() = values.string("default_reasoning_chat_low")
        override val reasoningChatHigh get() = values.string("default_reasoning_chat_high")
        override val reasoningSearch get() = values.string("default_reasoning_search")
    }

    override val events = object : EventsConfig {
        override val maxEntries get() = values.integer("event_log_max_events")
        override val maxDataLength get() = values.integer("event_log_max_data_length")
    }

    override val providers = object : ProvidersConfig {
        override val llm: List<LlmProvider>
            get() {
                val ids = values.array("llm_provider_ids")
                val names = values.array("llm_provider_names")
                val urls = values.array("llm_provider_urls")
                return ids.indices.map { LlmProvider(ids[it], names[it], urls[it]) }
            }

        override val webSearch: List<WebSearchProvider>
            get() {
                val ids = values.array("websearch_provider_ids")
                val names = values.array("websearch_provider_names")
                val urls = values.array("websearch_provider_urls")
                return ids.indices.map { WebSearchProvider(ids[it], names[it], urls[it]) }
            }
    }

    override val debugLogging = true
}