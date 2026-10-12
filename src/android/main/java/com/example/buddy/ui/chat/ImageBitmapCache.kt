package com.example.buddy.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache

object ImageBitmapCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String, base64: String): Bitmap? {
        cache.get(key)?.let { return it }
        val bitmap = decodeBase64ToBitmap(base64) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

fun decodeBase64ToBitmap(base64: String): android.graphics.Bitmap? {
    return try {
        val base64Data = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
