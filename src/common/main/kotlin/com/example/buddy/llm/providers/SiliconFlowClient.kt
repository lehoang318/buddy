package com.example.buddy.llm.providers

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.llm.OpenAIClient
import com.example.buddy.llm.ReasoningEffort
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class SiliconFlowClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun addReasoningParameter(requestBody: JsonObject, effort: ReasoningEffort?, forSearchQuery: Boolean) {
        val modelId = activeModel

        if (modelId in AppConfigProvider.current.siliconflow.reasoningModels) {
            val budget = when {
                forSearchQuery -> AppConfigProvider.current.siliconflow.reasoningSearch
                effort == ReasoningEffort.LOW -> AppConfigProvider.current.siliconflow.reasoningChatLow
                effort == ReasoningEffort.HIGH -> AppConfigProvider.current.siliconflow.reasoningChatHigh
                else -> return
            }
            requestBody.addProperty("thinking_budget", budget)
        } else {
            when {
                forSearchQuery || effort == ReasoningEffort.LOW -> {
                    requestBody.addProperty("enable_thinking", AppConfigProvider.current.siliconflow.hybridChatLow)
                }
                effort == ReasoningEffort.HIGH -> {
                    requestBody.addProperty("thinking_budget", AppConfigProvider.current.siliconflow.hybridChatHigh)
                }
            }
        }
    }
}
