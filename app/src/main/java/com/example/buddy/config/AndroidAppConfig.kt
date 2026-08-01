package com.example.buddy.config

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.example.buddy.BuildConfig
import com.example.buddy.R

class AndroidAppConfig(context: Context) : AppConfig {
    private val res = context.applicationContext.resources

    override val llm = object : LlmConfig {
        override val temperature get() = ResourcesCompat.getFloat(res, R.dimen.default_temperature)
        override val topP get() = ResourcesCompat.getFloat(res, R.dimen.default_top_p)
        override val topK get() = res.getInteger(R.integer.default_top_k)
        override val maxTokens get() = res.getInteger(R.integer.default_max_tokens)
        override val defaultSystemMessage get() = res.getString(R.string.default_system_message)
    }

    override val search = object : SearchConfig {
        override val queryTemperature get() = ResourcesCompat.getFloat(res, R.dimen.search_query_temperature)
        override val queryMaxChars get() = res.getInteger(R.integer.search_query_max_chars)
        override val maxResults get() = res.getInteger(R.integer.search_max_results)
        override val totalMaxResults get() = res.getInteger(R.integer.search_total_max_results)
        override val logPreviewMaxChars get() = res.getInteger(R.integer.log_preview_max_chars)
        override val resultContentMaxChars get() = res.getInteger(R.integer.search_result_content_max_chars)
        override val queryPrompt get() = res.getString(R.string.search_query_prompt)
        override val webDataInstructions get() = res.getString(R.string.web_data_instructions)
    }

    override val summaries = object : SummariesConfig {
        override val maxSummaries get() = res.getInteger(R.integer.max_summaries)
        override val maxQaPairs get() = res.getInteger(R.integer.max_qa_pairs)
        override val minPoints get() = res.getInteger(R.integer.min_summary_points)
        override val maxPoints get() = res.getInteger(R.integer.max_summary_points)
        override val keyPrefix get() = res.getString(R.string.key_prefix)
        override val pointIndent get() = res.getString(R.string.point_indent)
        override val contextHeader get() = res.getString(R.string.context_header)
        override val webDataHeader get() = res.getString(R.string.web_data_header)
        override val temperature get() = ResourcesCompat.getFloat(res, R.dimen.summary_temperature)
        override val maxTokens get() = res.getInteger(R.integer.summary_max_tokens)
        override val restrictivePatterns get() = res.getStringArray(R.array.restrictive_patterns).toList()
    }

    override val prompts = object : PromptsConfig {
        override val summarizerSystem get() = res.getString(R.string.summarizer_system_prompt)
        override val summarizerUserTemplate get() = res.getString(R.string.summarizer_user_template)
        override val compressSummaries get() = res.getString(R.string.compress_summaries_prompt)
    }

    override val togetherAi = object : TogetherAiConfig {
        override val adjustableEffortModels get() = res.getStringArray(R.array.together_reasoning_effort_models).toSet()
        override val hybridModels get() = res.getStringArray(R.array.together_reasoning_hybrid_models).toSet()
        override val effortChatLow get() = res.getString(R.string.together_effort_chat_low)
        override val effortChatHigh get() = res.getString(R.string.together_effort_chat_high)
        override val effortSearch get() = res.getString(R.string.together_effort_search)
        override val hybridChatLow get() = res.getBoolean(R.bool.together_hybrid_chat_low)
        override val hybridChatHigh get() = res.getBoolean(R.bool.together_hybrid_chat_high)
        override val hybridSearch get() = res.getBoolean(R.bool.together_hybrid_search)
    }

    override val siliconflow = object : SiliconFlowConfig {
        override val reasoningModels get() = res.getStringArray(R.array.siliconflow_reasoning_models).toSet()
        override val hybridChatLow get() = res.getBoolean(R.bool.siliconflow_hybrid_chat_low)
        override val hybridChatHigh get() = res.getInteger(R.integer.siliconflow_hybrid_chat_high)
        override val hybridSearch get() = res.getBoolean(R.bool.siliconflow_hybrid_search)
        override val reasoningChatLow get() = res.getInteger(R.integer.siliconflow_reasoning_chat_low)
        override val reasoningChatHigh get() = res.getInteger(R.integer.siliconflow_reasoning_chat_high)
        override val reasoningSearch get() = res.getInteger(R.integer.siliconflow_reasoning_search)
    }

    override val defaults = object : ReasoningDefaultsConfig {
        override val reasoningChatLow get() = res.getString(R.string.default_reasoning_chat_low)
        override val reasoningChatHigh get() = res.getString(R.string.default_reasoning_chat_high)
        override val reasoningSearch get() = res.getString(R.string.default_reasoning_search)
    }

    override val events = object : EventsConfig {
        override val maxEntries get() = res.getInteger(R.integer.event_log_max_events)
        override val maxDataLength get() = res.getInteger(R.integer.event_log_max_data_length)
    }

    override val debugLogging = BuildConfig.DEBUG
}
