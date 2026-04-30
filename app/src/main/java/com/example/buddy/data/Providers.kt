package com.example.buddy.data

import android.content.Context
import androidx.annotation.Keep
import com.example.buddy.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class BaseProvider(
    open val id: String,
    open val name: String,
    open val baseUrl: String,
    open val apiKey: String = ""
)

data class LlmProvider(
    override val id: String,
    override val name: String,
    override val baseUrl: String,
    override val apiKey: String = "",
    val apiType: ApiType = ApiType.OPENAI_COMPATIBLE
) : BaseProvider(id, name, baseUrl, apiKey)

data class WebSearchProvider(
    override val id: String,
    override val name: String,
    override val baseUrl: String,
    override val apiKey: String = ""
) : BaseProvider(id, name, baseUrl, apiKey)

@Keep
data class ProviderData(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String
)

fun LlmProvider.toProviderData() = ProviderData(id, name, baseUrl, apiKey)
fun ProviderData.toLlmProvider() = LlmProvider(id, name, baseUrl, apiKey)
fun WebSearchProvider.toProviderData() = ProviderData(id, name, baseUrl, apiKey)
fun ProviderData.toWebSearchProvider() = WebSearchProvider(id, name, baseUrl, apiKey)

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
                apiType = ApiType.OPENAI_COMPATIBLE
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
