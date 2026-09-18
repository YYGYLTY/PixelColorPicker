package com.pixelcolorpicker.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pixelcolorpicker.image.BitmapLoader
import java.io.OutputStream

/** PNG 导出工具。导出的必须是真正的 W×H PNG（不放大）。 */
object PngExporter {

    /**
     * 将像素数组导出为 PNG 文件。
     *
     * @param scale 整数倍放大（最近邻，默认 1 不放大）。放大后像素方块保持锐利，
     *              避免相册 / 查看器把小图放大时做平滑导致发糊。
     * @return 保存成功返回文件的 Uri，失败返回 null。
     */
    fun exportPng(
        context: Context,
        pixels: IntArray,
        width: Int,
        height: Int,
        fileName: String,
        scale: Int = 1,
    ): Uri? {
        var bitmap = BitmapLoader.toBitmap(pixels, width, height)
        if (scale > 1) {
            val scaled = try {
                Bitmap.createScaledBitmap(bitmap, width * scale, height * scale, false)
            } catch (t: Throwable) {
                null
            }
            if (scaled != null && scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }
        return try {
            saveBitmapToGallery(context, bitmap, fileName)
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixelColorPicker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (t: Throwable) {
            null
        } ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { output: OutputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("PNG 压缩失败")
                }
            } ?: throw IllegalStateException("无法打开输出流")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}