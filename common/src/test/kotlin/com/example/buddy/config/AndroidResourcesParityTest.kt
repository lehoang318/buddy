package com.example.buddy.config

import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidResourcesParityTest {
    private val values = loadValues()
    private val config: AppConfig = DefaultAppConfig

    @Test
    fun llmConfigMatchesAndroidResources() {
        assertMatches(
            "llm",
            listOf(
                match("default_temperature", values.dimensions["default_temperature"], config.llm.temperature),
                match("default_top_p", values.dimensions["default_top_p"], config.llm.topP),
                match("default_top_k", values.integers["default_top_k"], config.llm.topK),
                match("default_max_tokens", values.integers["default_max_tokens"], config.llm.maxTokens),
                match("default_system_message", values.strings["default_system_message"], config.llm.defaultSystemMessage)
            )
        )
    }

    @Test
    fun searchConfigMatchesAndroidResources() {
        assertMatches(
            "search",
            listOf(
                match("search_query_temperature", values.dimensions["search_query_temperature"], config.search.queryTemperature),
                match("search_query_max_chars", values.integers["search_query_max_chars"], config.search.queryMaxChars),
                match("search_max_results", values.integers["search_max_results"], config.search.maxResults),
                match("search_total_max_results", values.integers["search_total_max_results"], config.search.totalMaxResults),
                match("log_preview_max_chars", values.integers["log_preview_max_chars"], config.search.logPreviewMaxChars),
                match("search_result_content_max_chars", values.integers["search_result_content_max_chars"], config.search.resultContentMaxChars),
                match("search_query_prompt", values.strings["search_query_prompt"], config.search.queryPrompt),
                match("web_data_instructions", values.strings["web_data_instructions"], config.search.webDataInstructions)
            )
        )
    }

    @Test
    fun summariesConfigMatchesAndroidResources() {
        assertMatches(
            "summaries",
            listOf(
                match("max_summaries", values.integers["max_summaries"], config.summaries.maxSummaries),
                match("max_qa_pairs", values.integers["max_qa_pairs"], config.summaries.maxQaPairs),
                match("min_summary_points", values.integers["min_summary_points"], config.summaries.minPoints),
                match("max_summary_points", values.integers["max_summary_points"], config.summaries.maxPoints),
                match("key_prefix", values.strings["key_prefix"], config.summaries.keyPrefix),
                match("point_indent", values.strings["point_indent"], config.summaries.pointIndent),
                match("context_header", values.strings["context_header"], config.summaries.contextHeader),
                match("web_data_header", values.strings["web_data_header"], config.summaries.webDataHeader),
                match("summary_temperature", values.dimensions["summary_temperature"], config.summaries.temperature),
                match("summary_max_tokens", values.integers["summary_max_tokens"], config.summaries.maxTokens),
                match("restrictive_patterns", values.arrays["restrictive_patterns"], config.summaries.restrictivePatterns)
            )
        )
    }

    @Test
    fun promptsConfigMatchesAndroidResources() {
        assertMatches(
            "prompts",
            listOf(
                match("summarizer_system_prompt", values.strings["summarizer_system_prompt"], config.prompts.summarizerSystem),
                match("summarizer_user_template", values.strings["summarizer_user_template"], config.prompts.summarizerUserTemplate),
                match("compress_summaries_prompt", values.strings["compress_summaries_prompt"], config.prompts.compressSummaries)
            )
        )
    }

    @Test
    fun togetherAiConfigMatchesAndroidResources() {
        assertMatches(
            "togetherAi",
            listOf(
                match("together_reasoning_effort_models", values.arrays["together_reasoning_effort_models"]?.toSet(), config.togetherAi.adjustableEffortModels),
                match("together_reasoning_hybrid_models", values.arrays["together_reasoning_hybrid_models"]?.toSet(), config.togetherAi.hybridModels),
                match("together_effort_chat_low", values.strings["together_effort_chat_low"], config.togetherAi.effortChatLow),
                match("together_effort_chat_high", values.strings["together_effort_chat_high"], config.togetherAi.effortChatHigh),
                match("together_effort_search", values.strings["together_effort_search"], config.togetherAi.effortSearch),
                match("together_hybrid_chat_low", values.booleans["together_hybrid_chat_low"], config.togetherAi.hybridChatLow),
                match("together_hybrid_chat_high", values.booleans["together_hybrid_chat_high"], config.togetherAi.hybridChatHigh),
                match("together_hybrid_search", values.booleans["together_hybrid_search"], config.togetherAi.hybridSearch)
            )
        )
    }

    @Test
    fun siliconflowConfigMatchesAndroidResources() {
        assertMatches(
            "siliconflow",
            listOf(
                match("siliconflow_reasoning_models", values.arrays["siliconflow_reasoning_models"]?.toSet(), config.siliconflow.reasoningModels),
                match("siliconflow_hybrid_chat_low", values.booleans["siliconflow_hybrid_chat_low"], config.siliconflow.hybridChatLow),
                match("siliconflow_hybrid_chat_high", values.integers["siliconflow_hybrid_chat_high"], config.siliconflow.hybridChatHigh),
                match("siliconflow_hybrid_search", values.booleans["siliconflow_hybrid_search"], config.siliconflow.hybridSearch),
                match("siliconflow_reasoning_chat_low", values.integers["siliconflow_reasoning_chat_low"], config.siliconflow.reasoningChatLow),
                match("siliconflow_reasoning_chat_high", values.integers["siliconflow_reasoning_chat_high"], config.siliconflow.reasoningChatHigh),
                match("siliconflow_reasoning_search", values.integers["siliconflow_reasoning_search"], config.siliconflow.reasoningSearch)
            )
        )
    }

    @Test
    fun reasoningDefaultsConfigMatchesAndroidResources() {
        assertMatches(
            "defaults",
            listOf(
                match("default_reasoning_chat_low", values.strings["default_reasoning_chat_low"], config.defaults.reasoningChatLow),
                match("default_reasoning_chat_high", values.strings["default_reasoning_chat_high"], config.defaults.reasoningChatHigh),
                match("default_reasoning_search", values.strings["default_reasoning_search"], config.defaults.reasoningSearch)
            )
        )
    }

    @Test
    fun eventsConfigMatchesAndroidResources() {
        assertMatches(
            "events",
            listOf(
                match("event_log_max_events", values.integers["event_log_max_events"], config.events.maxEntries),
                match("event_log_max_data_length", values.integers["event_log_max_data_length"], config.events.maxDataLength)
            )
        )
    }

    private fun match(name: String, expected: Any?, actual: Any?): Comparison = Comparison(name, expected, actual)

    private fun assertMatches(section: String, comparisons: List<Comparison>) {
        val mismatches = comparisons.filter { it.expected != it.actual }
        if (mismatches.isNotEmpty()) {
            fail(
                "$section config differs from Android resources:\n" +
                    mismatches.joinToString("\n") { describe(it) }
            )
        }
    }

    private fun describe(comparison: Comparison): String {
        val expected = comparison.expected
        val actual = comparison.actual
        if (expected is String && actual is String) {
            val index = expected.indices.firstOrNull { it >= actual.length || expected[it] != actual[it] }
                ?: actual.indices.firstOrNull { it >= expected.length }
            val start = (index ?: 0).coerceAtLeast(20).let { it.coerceAtMost(expected.length) } - 20
            val end = ((index ?: 0) + 20).coerceAtMost(expected.length)
            return "${comparison.name}: resource length=${expected.length}, default length=${actual.length}, first difference=$index, resource context=${expected.substring(start, end)}"
        }
        return "${comparison.name}: resource=$expected, default=$actual"
    }

    private fun loadValues(): ResourceValues {
        val valuesDirectory = resourceDirectory()
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val result = ResourceValues()
        valuesDirectory.listFiles { file -> file.extension == "xml" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val document = factory.newDocumentBuilder().parse(file)
                val resources = document.documentElement
                for (index in 0 until resources.childNodes.length) {
                    val node = resources.childNodes.item(index)
                    if (node !is Element) continue
                    when (node.tagName) {
                        "integer" -> result.integers[node.getAttribute("name")] = node.textContent.trim().toInt()
                        "bool" -> result.booleans[node.getAttribute("name")] = node.textContent.trim().toBooleanStrict()
                        "string" -> result.strings[node.getAttribute("name")] = unescape(node.textContent)
                        "item" -> if (node.getAttribute("type") == "dimen") {
                            result.dimensions[node.getAttribute("name")] = node.textContent.trim().toFloat()
                        }
                        "string-array" -> {
                            result.arrays[node.getAttribute("name")] = (0 until node.childNodes.length)
                                .map { node.childNodes.item(it) }
                                .filterIsInstance<Element>()
                                .filter { it.tagName == "item" }
                                .map { unescape(it.textContent) }
                        }
                    }
                }
            }
        return result
    }

    private fun resourceDirectory(): File {
        val workingDirectory = File(System.getProperty("user.dir"))
        val candidates = listOf(
            workingDirectory.resolve("../app/src/main/res/values"),
            workingDirectory.resolve("app/src/main/res/values")
        ).map { it.canonicalFile }
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Android resource directory not found. Checked: ${candidates.joinToString()}")
    }

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index == value.lastIndex) {
                result.append(value[index])
                index++
                continue
            }
            when (val escaped = value[index + 1]) {
                '\\' -> result.append('\\')
                '\'' -> result.append('\'')
                '"' -> result.append('"')
                'n' -> result.append('\n')
                't' -> result.append('\t')
                else -> result.append('\\').append(escaped)
            }
            index += 2
        }
        return result.toString()
    }

    private data class Comparison(val name: String, val expected: Any?, val actual: Any?)

    private class ResourceValues {
        val integers = mutableMapOf<String, Int>()
        val booleans = mutableMapOf<String, Boolean>()
        val dimensions = mutableMapOf<String, Float>()
        val strings = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()
    }
}
