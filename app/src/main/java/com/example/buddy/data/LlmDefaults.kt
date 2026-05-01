package com.example.buddy.data

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.example.buddy.R
import java.lang.ref.WeakReference

object LlmDefaults {
    private var contextRef: WeakReference<Context>? = null

    fun init(ctx: Context) {
        contextRef = WeakReference(ctx.applicationContext)
    }

    private val res get() = contextRef?.get()?.resources

    val temperature: Float
        get() = res?.let { ResourcesCompat.getFloat(it, R.dimen.default_temperature) } ?: 0.7f

    val topP: Float
        get() = res?.let { ResourcesCompat.getFloat(it, R.dimen.default_top_p) } ?: 0.95f

    val topK: Int
        get() = res?.getInteger(R.integer.default_top_k) ?: 20

    val maxTokens: Int
        get() = res?.getInteger(R.integer.default_max_tokens) ?: 4096

    val defaultSystemMessage: String
        get() = res?.getString(R.string.default_system_message) ?: "You are a helpful assistant."

    enum class ReasoningEffort { LOW, HIGH }

    const val SEARCH_QUERY_MAX_WORDS = 20

    val searchQueryTemperature: Float
        get() = res?.let { ResourcesCompat.getFloat(it, R.dimen.search_query_temperature) } ?: 0.2f

    val searchQueryMaxTokens: Int
        get() = res?.getInteger(R.integer.search_query_max_tokens) ?: 150

    val searchMaxResults: Int
        get() = res?.getInteger(R.integer.search_max_results) ?: 5

    val searchQueryFallbackLength: Int
        get() = res?.getInteger(R.integer.search_query_fallback_length) ?: 50

    val logPreviewMaxChars: Int
        get() = res?.getInteger(R.integer.log_preview_max_chars) ?: 1024

    val searchQueryPrompt: String
        get() = res?.getString(R.string.search_query_prompt)
            ?: "You are a search query generator. Based on the user's message, generate a focused web search query. Return ONLY the query text, no quotes, no explanation."

    fun sanitizeSearchQueryResponse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw
        text = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        text = Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        text = Regex("```[\\w]*\\s*THOUGHT:[\\s\\S]*?```", RegexOption.IGNORE_CASE).replace(text, "")
        text = Regex("(?i)^\\s*THOUGHT:.*", RegexOption.MULTILINE).replace(text, "")
        text = Regex("(?i)^\\s*REASONING:.*", RegexOption.MULTILINE).replace(text, "")
        text = text.removeSurrounding("\"").trim()
        if (text.isBlank()) return null
        return text
    }
}
