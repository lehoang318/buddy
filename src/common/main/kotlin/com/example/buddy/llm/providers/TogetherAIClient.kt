package com.example.buddy.llm.providers

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.llm.OpenAIClient
import com.example.buddy.llm.ReasoningEffort
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class TogetherAIClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun shouldIncludeModel(modelJson: JsonObject): Boolean {
        val type = modelJson.get("type")?.asString
        if (type != "chat") return false
        val pricing = modelJson.getAsJsonObject("pricing")
        val inputPrice = pricing?.get("input")?.asDouble ?: 0.0
        return inputPrice > 0
    }

    override fun addReasoningParameter(requestBody: JsonObject, effort: ReasoningEffort?, forSearchQuery: Boolean) {
        val modelId = activeModel

        when {
            modelId in AppConfigProvider.current.togetherAi.adjustableEffortModels -> {
                if (forSearchQuery) {
                    requestBody.addProperty("reasoning_effort", AppConfigProvider.current.togetherAi.effortSearch)
                } else if (effort != null) {
                    val effortStr = when (effort) {
                        ReasoningEffort.LOW -> AppConfigProvider.current.togetherAi.effortChatLow
                        ReasoningEffort.HIGH -> AppConfigProvider.current.togetherAi.effortChatHigh
                    }
                    requestBody.addProperty("reasoning_effort", effortStr)
                }
            }
            modelId in AppConfigProvider.current.togetherAi.hybridModels -> {
                val enabled = when {
                    forSearchQuery -> AppConfigProvider.current.togetherAi.hybridSearch
                    effort == ReasoningEffort.LOW -> AppConfigProvider.current.togetherAi.hybridChatLow
                    effort == ReasoningEffort.HIGH -> AppConfigProvider.current.togetherAi.hybridChatHigh
                    else -> false
                }
                requestBody.add("reasoning", JsonObject().apply {
                    addProperty("enabled", enabled)
                })
            }
        }
    }
}
