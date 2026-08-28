package com.example.buddy.data

import com.example.buddy.config.AppConfigProvider

object SessionTags {
    val CATEGORIES get() = AppConfigProvider.current.summaries.sessionTags

    fun normalize(tags: List<String>): List<String> =
        tags.mapNotNull { raw ->
            CATEGORIES.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
        }.distinct()
}