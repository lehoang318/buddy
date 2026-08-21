package com.example.buddy.llm.providers

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.llm.OpenAIClient
import com.example.buddy.llm.ReasoningEffort
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class FireworksAIClient(
    baseUrl: String,
    defaultModel: String,
    httpClient: OkHttpClient
) : OpenAIClient(baseUrl, defaultModel, httpClient) {

    override fun shouldIncludeModel(modelJson: JsonObject): Boolean {
        return modelJson.get("supports_chat")?.asBoolean ?: false
    }

    override fun detectMultimodalFromApi(modelObj: JsonObject): Boolean {
        return modelObj.get("supports_image_input")?.asBoolean ?: false
    }

    override fun getModelDisplayName(modelId: String): String =
        modelId.removePrefix("accounts/fireworks/models/")

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
