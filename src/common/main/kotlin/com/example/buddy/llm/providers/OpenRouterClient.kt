package com.example.buddy.llm.providers

import com.example.buddy.config.AppConfigProvider
import com.example.buddy.llm.OpenAIClient
import com.example.buddy.llm.ReasoningEffort
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient

class OpenRouterClient(
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
        requestBody.add("reasoning", JsonObject().apply {
            addProperty("effort", effortStr)
        })
    }

    override fun detectMultimodalFromApi(modelObj: JsonObject): Boolean {
        try {
            val architecture = modelObj.getAsJsonObject("architecture")
            val inputModalities = architecture?.getAsJsonArray("input_modalities")
            if (inputModalities != null) {
                inputModalities.forEach { element ->
                    if (element.isJsonPrimitive) {
                        val mod = element.asString.lowercase()
                        if (mod == "image" || mod == "video") return true
                    }
                }
            }
        } catch (e: Exception) {
        }
        return false
    }
}
