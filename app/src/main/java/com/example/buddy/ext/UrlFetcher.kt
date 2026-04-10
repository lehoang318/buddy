package com.example.buddy.ext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

interface UrlFetcher {
    suspend fun fetchTextContent(url: String): String?
    suspend fun testConnection(): Boolean
}

class JsoupUrlFetcher : UrlFetcher {
    
    override suspend fun fetchTextContent(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null

        var lastException: Exception? = null
        val maxRetries = 2
        
        for (attempt in 0..maxRetries) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    .timeout(10000)
                    .followRedirects(true)
                    .maxBodySize(1024 * 1024)  // 1MB max
                    .get()

                val title = doc.title()
                val bodyText = doc.body().wholeText()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(8192)

                if (bodyText.isBlank()) return@withContext null
                return@withContext "[$title]\n$bodyText"
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(500L * (attempt + 1))  // Exponential backoff
                }
            }
        }
        null
    }
    
    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Test by fetching a simple page
            Jsoup.connect("https://www.google.com")
                .timeout(5000)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .execute()
            true
        } catch (e: Exception) {
            false
        }
    }
}
