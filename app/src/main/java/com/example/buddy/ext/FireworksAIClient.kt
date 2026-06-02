package com.example.buddy.ext

import com.example.buddy.data.AppResources
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
