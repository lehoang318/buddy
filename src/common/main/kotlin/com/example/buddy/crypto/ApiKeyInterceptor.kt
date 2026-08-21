package com.example.buddy.crypto

import okhttp3.Interceptor
import okhttp3.Response

class ApiKeyInterceptor(
    private val keyCache: KeyProvider,
    private val providerId: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val keyBytes = keyCache.getKey(providerId)
        if (keyBytes == null) return chain.proceed(chain.request())

        val keyString = String(keyBytes, Charsets.UTF_8)
        keyBytes.fill(0)

        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $keyString")
            .build()
        return chain.proceed(request)
    }
}
