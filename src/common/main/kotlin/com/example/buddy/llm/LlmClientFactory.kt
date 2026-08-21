package com.example.buddy.llm

import com.example.buddy.crypto.ApiKeyInterceptor
import com.example.buddy.crypto.KeyProvider
import com.example.buddy.data.LlmProvider
import com.example.buddy.llm.providers.FireworksAIClient
import com.example.buddy.llm.providers.TogetherAIClient
import com.example.buddy.llm.providers.OpenRouterClient
import com.example.buddy.llm.providers.OllamaCloudClient
import com.example.buddy.llm.providers.SiliconFlowClient
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object LlmClientFactory {
    fun createWithProvider(provider: LlmProvider, keyCache: KeyProvider, model: String): Result<LlmClient> {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(keyCache, provider.id))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(
                maxIdleConnections = 5,
                keepAliveDuration = 5,
                timeUnit = TimeUnit.MINUTES
            ))
            .build()
        val client: OpenAIClient = when (provider.id) {
            "together" -> TogetherAIClient(provider.baseUrl, model, httpClient)
            "fireworks" -> FireworksAIClient(provider.baseUrl, model, httpClient)
            "openrouter" -> OpenRouterClient(provider.baseUrl, model, httpClient)
            "ollama" -> OllamaCloudClient(provider.baseUrl, model, httpClient)
            "siliconflow" -> SiliconFlowClient(provider.baseUrl, model, httpClient)
            else -> OpenAIClient(provider.baseUrl, model, httpClient)
        }
        return Result.success(client)
    }

    fun createTempForModels(provider: LlmProvider, keyCache: KeyProvider): Result<LlmClient> {
        return createWithProvider(provider, keyCache, "temp")
    }
}
