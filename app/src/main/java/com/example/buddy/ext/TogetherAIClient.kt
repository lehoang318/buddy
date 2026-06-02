package com.example.buddy.ext

import com.example.buddy.data.AppResources
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

    override fun addReasoningParameter(requestBody: JsonObject, effort: AppResources.ReasoningEffort?, forSearchQuery: Boolean) {
        val modelId = activeModel

        when {
            modelId in AppResources.togetherAi.adjustableEffortModels -> {
                if (forSearchQuery) {
                    requestBody.addProperty("reasoning_effort", AppResources.togetherAi.effortSearch)
                } else if (effort != null) {
                    val effortStr = when (effort) {
                        AppResources.ReasoningEffort.LOW -> AppResources.togetherAi.effortChatLow
                        AppResources.ReasoningEffort.HIGH -> AppResources.togetherAi.effortChatHigh
                    }
                    requestBody.addProperty("reasoning_effort", effortStr)
                }
            }
            modelId in AppResources.togetherAi.hybridModels -> {
                val enabled = when {
                    forSearchQuery -> AppResources.togetherAi.hybridSearch
                    effort == AppResources.ReasoningEffort.LOW -> AppResources.togetherAi.hybridChatLow
                    effort == AppResources.ReasoningEffort.HIGH -> AppResources.togetherAi.hybridChatHigh
                    else -> return
                }
                requestBody.add("reasoning", JsonObject().apply {
                    addProperty("enabled", enabled)
                })
            }
        }
    }
}
