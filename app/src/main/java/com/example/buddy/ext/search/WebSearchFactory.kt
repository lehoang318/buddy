package com.example.buddy.ext.search

import com.example.buddy.crypto.ApiKeyInterceptor
import com.example.buddy.crypto.SessionKeyCache
import com.example.buddy.ext.search.providers.ExaApiKeyInterceptor
import com.example.buddy.ext.search.providers.ExaWebSearch
import com.example.buddy.ext.search.providers.LinkUpWebSearch
import com.example.buddy.ext.search.providers.TavilyWebSearch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object WebSearchFactory {
    fun create(keyCache: SessionKeyCache, providerId: String): WebSearch? {
        return when (providerId) {
            "linkup" -> {
                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(ApiKeyInterceptor(keyCache, "ws_$providerId"))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
                    .build()
                LinkUpWebSearch(httpClient, keyCache, "ws_$providerId")
            }
            "exa" -> {
                val httpClient = OkHttpClient.Builder()
                    .addInterceptor(ExaApiKeyInterceptor(keyCache, "ws_$providerId"))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .pingInterval(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .connectionPool(ConnectionPool(3, 3, TimeUnit.MINUTES))
                    .build()
                ExaWebSearch(httpClient, keyCache, "ws_$providerId")
            }
            "tavily" -> TavilyWebSearch(keyCache, "ws_$providerId")
            else -> null
        }
    }
}
