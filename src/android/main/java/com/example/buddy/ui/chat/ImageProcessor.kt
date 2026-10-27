package com.example.buddy.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object ImageProcessor {
    private const val MAX_DIMENSION = 1440
    private const val JPEG_QUALITY = 85

    fun fromUri(contentResolver: ContentResolver, uri: Uri): String? =
        process { contentResolver.openInputStream(uri) }

    fun fromFile(file: File): String? =
        process { if (file.exists()) file.inputStream() else null }

    fun decodeBytes(base64: String): ByteArray? = try {
        val payload = base64.substringAfter(',', base64)
        Base64.decode(payload, Base64.NO_WRAP).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun process(open: () -> InputStream?): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (maxDim / (inSampleSize * 2) >= MAX_DIMENSION) inSampleSize *= 2
            val options = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) }
            if (decoded == null) {
                null
            } else {
                val oriented = applyOrientation(decoded, readOrientation(open))
                if (oriented !== decoded) decoded.recycle()
                val scaled = downscale(oriented, MAX_DIMENSION)
                if (scaled !== oriented) oriented.recycle()
                val encoded = encode(scaled)
                scaled.recycle()
                encoded
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun readOrientation(open: () -> InputStream?): Int = try {
        open()?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= maxDimension) return bitmap
        val factor = maxDimension.toFloat() / maxDim
        return bitmap.scale(
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1)
        )
    }

    private fun encode(bitmap: Bitmap): String? = try {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
