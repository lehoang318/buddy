package com.example.buddy.data

import android.content.Context
import com.example.buddy.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object BuiltInProviders {
    private val gson = Gson()

    fun loadLlmProviders(context: Context): List<LlmProvider> {
        val ids = context.resources.getStringArray(R.array.llm_provider_ids)
        val names = context.resources.getStringArray(R.array.llm_provider_names)
        val urls = context.resources.getStringArray(R.array.llm_provider_urls)
        return ids.indices.map { i ->
            LlmProvider(
                id = ids[i],
                name = names[i],
                baseUrl = urls[i],
            )
        }
    }

    fun loadWebSearchProviders(context: Context): List<WebSearchProvider> {
        val ids = context.resources.getStringArray(R.array.websearch_provider_ids)
        val names = context.resources.getStringArray(R.array.websearch_provider_names)
        val urls = context.resources.getStringArray(R.array.websearch_provider_urls)
        return ids.indices.map { i ->
            WebSearchProvider(
                id = ids[i],
                name = names[i],
                baseUrl = urls[i]
            )
        }
    }

    fun serializeProviderData(providers: List<ProviderData>): String = gson.toJson(providers)

    fun deserializeProviderData(json: String): List<ProviderData> {
        if (json.isBlank()) return emptyList()
        return try {
            gson.fromJson(json, TypeToken.getParameterized(List::class.java, ProviderData::class.java).type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
