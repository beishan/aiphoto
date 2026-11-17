package com.aiphoto.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import java.io.File
import kotlin.math.roundToInt

/** Decode pixels (including EXIF orientation), then write a fresh opaque sRGB JPEG. */
object PrivacyCopy {
    const val MAX_EDGE = 2048
    fun write(source: File, target: File) {
        try {
            val pixels = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                require(!info.isAnimated) { "动图不能转换为静态分享副本，请使用原文件分享" }
                require(info.mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heif", "image/heic", "image/avif", "image/bmp")) { "此图片格式暂不支持分享副本" }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                val ratio = minOf(1.0, MAX_EDGE.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * ratio).roundToInt().coerceAtLeast(1), (info.size.height * ratio).roundToInt().coerceAtLeast(1))
            }
            try {
                val output = Bitmap.createBitmap(pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(output)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(pixels, 0f, 0f, null)
                    target.outputStream().use { require(output.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "图片副本编码失败" } }
                } finally { output.recycle() }
            } finally { pixels.recycle() }
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
    }
}

data class SharedAsset(val file: File, val title: String)
