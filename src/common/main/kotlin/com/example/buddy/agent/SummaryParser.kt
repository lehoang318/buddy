package com.example.buddy.agent

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.data.SessionTags
import com.example.buddy.data.Summary
import com.example.buddy.data.SummaryPoint
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Lenient parser for summarizer output. Accepts the canonical `{"points":[...], "tags":[...]}`
 * shape plus common small-model deviations (code fences, a bare points array, a bare string), then
 * sanitizes the points and normalizes tags against the configured category list.
 */
object SummaryParser {
    private val gson = Gson()

    fun parse(question: String, rawContent: String): Summary {
        val clean = rawContent.trim()
            .removePrefix("```json\n").removePrefix("```json").removePrefix("```\n").removePrefix("```")
            .removeSuffix("\n```").removeSuffix("```")
            .trim()
            .let { if (it.startsWith("\"") && it.endsWith("\"")) it.removeSurrounding("\"") else it }

        val parsed = try {
            gson.fromJson(clean, JsonObject::class.java)
        } catch (_: Exception) {
            try {
                val array = gson.fromJson(clean, JsonArray::class.java)
                JsonObject().apply { add("points", array) }
            } catch (_: Exception) {
                val text = try {
                    gson.fromJson(clean, String::class.java)
                } catch (_: Exception) {
                    clean.takeIf { it.isNotBlank() }
                }
                if (text != null && text.isNotBlank()) {
                    JsonObject().apply {
                        add("points", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", text)
                                addProperty("key", false)
                            })
                        })
                    }
                } else {
                    null
                }
            }
        }

        val pointsArray = parsed?.getAsJsonArray("points") ?: return Summary(question, emptyList())
        val points = pointsArray.mapNotNull { elem ->
            when {
                elem.isJsonObject -> {
                    val obj = elem.asJsonObject
                    val text = obj.get("text")?.asString
                    if (text.isNullOrBlank()) null else SummaryPoint(text = text, key = obj.get("key")?.asBoolean ?: false)
                }
                elem.isJsonPrimitive -> {
                    val text = elem.asString
                    if (text.isBlank()) null else SummaryPoint(text = text, key = false)
                }
                else -> null
            }
        }
        val sanitized = AppConfigProvider.current.summaries.sanitizeSummaryPoints(points)
        val tags = parseTags(parsed, AppConfigProvider.current.summaries.maxSessionTags)
        return Summary(question = question, points = sanitized, tags = tags)
    }

    private fun parseTags(parsed: JsonObject?, maxTags: Int): List<String> {
        if (parsed == null) return emptyList()
        val tagsArray = parsed.get("tags")
        if (tagsArray == null || !tagsArray.isJsonArray) return emptyList()
        val tags = tagsArray.asJsonArray.mapNotNull { elem ->
            if (elem.isJsonPrimitive && elem.asJsonPrimitive.isString) elem.asString.takeIf { it.isNotBlank() } else null
        }
        return SessionTags.normalize(tags).take(maxTags)
    }
}
