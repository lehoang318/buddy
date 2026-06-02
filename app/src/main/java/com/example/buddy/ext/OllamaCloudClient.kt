package com.example.buddy.ext

import com.example.buddy.data.AppResources
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class OllamaCloudClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun addReasoningParameter(requestBody: JsonObject, effort: AppResources.ReasoningEffort?, forSearchQuery: Boolean) {
        val effortStr = when {
            forSearchQuery -> AppResources.defaults.reasoningSearch
            effort == AppResources.ReasoningEffort.LOW -> AppResources.defaults.reasoningChatLow
            effort == AppResources.ReasoningEffort.HIGH -> AppResources.defaults.reasoningChatHigh
            else -> return
        }
        requestBody.addProperty("reasoning_effort", effortStr)
    }
}
