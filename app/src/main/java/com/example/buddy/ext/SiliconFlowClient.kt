package com.example.buddy.ext

import com.example.buddy.data.AppResources
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class SiliconFlowClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun addReasoningParameter(requestBody: JsonObject, effort: AppResources.ReasoningEffort?, forSearchQuery: Boolean) {
        val modelId = activeModel

        if (modelId in AppResources.siliconflow.reasoningModels) {
            val budget = when {
                forSearchQuery -> AppResources.siliconflow.reasoningSearch
                effort == AppResources.ReasoningEffort.LOW -> AppResources.siliconflow.reasoningChatLow
                effort == AppResources.ReasoningEffort.HIGH -> AppResources.siliconflow.reasoningChatHigh
                else -> return
            }
            requestBody.addProperty("thinking_budget", budget)
        } else {
            when {
                forSearchQuery || effort == AppResources.ReasoningEffort.LOW -> {
                    requestBody.addProperty("enable_thinking", AppResources.siliconflow.hybridChatLow)
                }
                effort == AppResources.ReasoningEffort.HIGH -> {
                    requestBody.addProperty("thinking_budget", AppResources.siliconflow.hybridChatHigh)
                }
            }
        }
    }
}
