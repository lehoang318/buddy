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
    const val SEARCH_QUERY_MAX_TOKENS = 60

    val searchQueryPrompt: String
        get() = "You are a search query generator. Based on the user's message, generate a focused web search query of up to $SEARCH_QUERY_MAX_WORDS words. Extract the core question or topic. Return ONLY the query text, no quotes, no explanation."
}
