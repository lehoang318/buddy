package com.example.buddy.data

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.example.buddy.R
import java.lang.ref.WeakReference

object AppResources {
    private var contextRef: WeakReference<Context>? = null

    fun init(ctx: Context) {
        contextRef = WeakReference(ctx.applicationContext)
    }

    private val res get() = contextRef?.get()?.resources

    enum class ReasoningEffort { LOW, HIGH }

    object llm {
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
    }

    object search {
        val queryTemperature: Float
            get() = res?.let { ResourcesCompat.getFloat(it, R.dimen.search_query_temperature) } ?: 0.2f

        val queryMaxTokens: Int
            get() = res?.getInteger(R.integer.search_query_max_tokens) ?: 150

        val maxResults: Int
            get() = res?.getInteger(R.integer.search_max_results) ?: 5

        val queryFallbackLength: Int
            get() = res?.getInteger(R.integer.search_query_fallback_length) ?: 50

        val logPreviewMaxChars: Int
            get() = res?.getInteger(R.integer.log_preview_max_chars) ?: 1024

        val queryPrompt: String
            get() = res?.getString(R.string.search_query_prompt)
                ?: "You are a search query generator. Based on the user's message, generate a focused web search query. Return ONLY the query text, no quotes, no explanation."
    }

    object summaries {
        val maxSummaries: Int
            get() = res?.getInteger(R.integer.max_summaries) ?: 20

        val maxQaPairs: Int
            get() = res?.getInteger(R.integer.max_qa_pairs) ?: 2

        val minPoints: Int
            get() = res?.getInteger(R.integer.min_summary_points) ?: 2

        val maxPoints: Int
            get() = res?.getInteger(R.integer.max_summary_points) ?: 3

        val keyPrefix: String
            get() = res?.getString(R.string.key_prefix) ?: "[KEY] "

        val pointIndent: String
            get() = res?.getString(R.string.point_indent) ?: "  + "

        val contextHeader: String
            get() = res?.getString(R.string.context_header) ?: "## Use the context below when relevant:"

        val webDataHeader: String
            get() = res?.getString(R.string.web_data_header) ?: "## Web Data"

        val temperature: Float
            get() = res?.let { ResourcesCompat.getFloat(it, R.dimen.summary_temperature) } ?: 0.2f

        val maxTokens: Int
            get() = res?.getInteger(R.integer.summary_max_tokens) ?: 512

        val restrictivePatterns: List<String>
            get() = res?.getStringArray(R.array.restrictive_patterns)?.toList() ?: emptyList()

        fun sanitizeSummaryPoints(points: List<SummaryPoint>): List<SummaryPoint> {
            val patterns = restrictivePatterns
            if (patterns.isEmpty()) return points
            return points.map { point ->
                var text = point.text
                for (pattern in patterns) {
                    text = text.replace(pattern, "")
                }
                point.copy(text = text.trim())
            }
        }

        fun formatSummaryAsText(summary: Summary): String {
            return summary.points.joinToString("\n") { point ->
                val prefix = if (point.key) keyPrefix else ""
                "$pointIndent$prefix${point.text}"
            }
        }

        fun formatSummariesContext(summaries: List<Summary>): String {
            if (summaries.isEmpty()) return ""
            return buildString {
                appendLine(contextHeader)
                summaries.forEach { summary ->
                    appendLine("- ${summary.question}")
                    appendLine(formatSummaryAsText(summary))
                }
            }.trimEnd()
        }
    }

    object prompts {
        val summarizerSystem: String
            get() = res?.getString(R.string.summarizer_system_prompt)
                ?: "You are a summarizer. Given a user/assistant exchange, you must produce a JSON object with %1\$d-%2\$d points summarizing the key information discussed."

        val summarizerUserTemplate: String
            get() = res?.getString(R.string.summarizer_user_template) ?: "User: %1\$s\nAssistant: %2\$s"

        val compressSummaries: String
            get() = res?.getString(R.string.compress_summaries_prompt)
                ?: "You are a summarizer. Below are multiple conversation summaries that need to be merged into a single compact summary.\n\nProduce a JSON object with %1\$d-%2\$d points summarizing the combined information from all entries.\n\nSummaries to merge:\n%3\$s"
    }

    object events {
        val maxEntries: Int
            get() = res?.getInteger(R.integer.event_log_max_events) ?: 20

        val maxDataLength: Int
            get() = res?.getInteger(R.integer.event_log_max_data_length) ?: 2000
    }
}
