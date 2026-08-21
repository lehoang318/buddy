package com.example.buddy.llm.providers

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.llm.OpenAIClient
import com.example.buddy.llm.ReasoningEffort
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class OllamaCloudClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun addReasoningParameter(requestBody: JsonObject, effort: ReasoningEffort?, forSearchQuery: Boolean) {
        val effortStr = when {
            forSearchQuery -> AppConfigProvider.current.defaults.reasoningSearch
            effort == ReasoningEffort.LOW -> AppConfigProvider.current.defaults.reasoningChatLow
            effort == ReasoningEffort.HIGH -> AppConfigProvider.current.defaults.reasoningChatHigh
            else -> return
        }
        requestBody.addProperty("reasoning_effort", effortStr)
    }
}
