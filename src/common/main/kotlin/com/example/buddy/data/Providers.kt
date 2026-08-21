package com.example.buddy.data

import com.google.gson.annotations.SerializedName

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
    override val apiKey: String = ""
) : BaseProvider(id, name, baseUrl, apiKey)

data class WebSearchProvider(
    override val id: String,
    override val name: String,
    override val baseUrl: String,
    override val apiKey: String = ""
) : BaseProvider(id, name, baseUrl, apiKey)

data class ProviderData(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("baseUrl")
    val baseUrl: String,
    @SerializedName("apiKey")
    val apiKey: String
)

fun LlmProvider.toProviderData() = ProviderData(id, name, baseUrl, apiKey)
fun LlmProvider.toProviderDataWithoutKey() = ProviderData(id, name, baseUrl, apiKey = "")
fun ProviderData.toLlmProvider() = LlmProvider(id, name, baseUrl, apiKey)
fun ProviderData.toWebSearchProvider() = WebSearchProvider(id, name, baseUrl, apiKey)
