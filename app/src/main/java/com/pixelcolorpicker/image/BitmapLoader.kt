package com.pixelcolorpicker.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pixelcolorpicker.util.DebugLog
import java.io.InputStream
import kotlin.math.max

/**
 * 图片加载与处理工具。
 *
 * 关键点：
 *  - 内存安全：先读取图片尺寸，再按目标大小做 inSampleSize 采样，避免大图 OOM。
 *  - EXIF：根据方向信息旋转，保证显示方向正确。
 */
object BitmapLoader {

    /** 加载时的最大边长（用于采样）。 */
    private const val MAX_LOAD_EDGE = 2048

    /**
     * 从 Uri 加载图片为 Bitmap，自动处理 EXIF 方向与内存采样。
     */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            // 第一次：只读取尺寸（注意：decodeStream 此时按设计返回 null，不能用于判断失败）
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri)
            if (boundsStream == null) {
                DebugLog.log("loadBitmap: openInputStream(bounds) returned null")
                return null
            }
            boundsStream.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            DebugLog.log("loadBitmap: bounds=${options.outWidth}x${options.outHeight}")
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, MAX_LOAD_EDGE)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodeStream = context.contentResolver.openInputStream(uri)
            if (decodeStream == null) {
                DebugLog.log("loadBitmap: openInputStream(decode) returned null")
                return null
            }
            val decoded = decodeStream.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
            if (decoded == null) {
                DebugLog.log("loadBitmap: decodeStream returned null")
                return null
            }
            DebugLog.log("loadBitmap: decoded=${decoded.width}x${decoded.height} sample=$sampleSize")

            val rotation = readExifRotation(context, uri)
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        } catch (t: Throwable) {
            DebugLog.log("loadBitmap: exception: $t")
            null
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (t: Throwable) {
            0f
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sampleSize = 1
        while (max(width, height) / sampleSize > maxEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** 读取 Bitmap 的像素到数组（行优先）。 */
    fun extractPixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }

    /** 从像素数组创建 Bitmap。 */
    fun toBitmap(pixels: IntArray, width: Int, height: Int): Bitmap {
        require(pixels.size >= width * height)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}