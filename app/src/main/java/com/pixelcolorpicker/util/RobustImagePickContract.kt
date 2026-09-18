package com.pixelcolorpicker.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * 稳健的图片选择器（与旧版 PixelColorPicker 行为一致）。
 *
 * 首选 `ACTION_PICK`（系统相册选择器）：
 *  - 与旧版完全一致，用户习惯的相册界面；
 *  - 在 MIUI / HyperOS 上可正常回传结果。
 *
 * 注意：系统「照片选择器」(PickVisualMedia / PhotoPickerActivity) 在 MIUI 上
 * 存在框架 bug（`deliverResultsIfNeeded` NullPointerException），结果无法回传，
 * 因此不使用。
 *
 * 回退顺序：ACTION_PICK → ACTION_OPEN_DOCUMENT → ACTION_GET_CONTENT
 */
class RobustImagePickContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        // 1. 首选：相册选择器（旧版同款，MIUI 上工作正常）
        val pick = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        if (pick.resolveActivity(context.packageManager) != null) {
            DebugLog.log("picker: using ACTION_PICK (gallery)")
            return pick
        }

        // 2. 回退：文档选择器（标准 SAF）
        val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        if (openDocument.resolveActivity(context.packageManager) != null) {
            DebugLog.log("picker: using OPEN_DOCUMENT (fallback)")
            return openDocument
        }

        // 3. 最后手段
        DebugLog.log("picker: using GET_CONTENT (last resort)")
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        val uri = if (resultCode == Activity.RESULT_OK) intent?.data else null
        DebugLog.log("picker result: code=$resultCode uri=$uri")
        return uri
    }
}

/**
 * 轻量调试日志（写入应用外部目录，便于在设备上排查问题）。
 * 文件位置：/sdcard/Android/data/com.pixelcolorpicker/files/pcp_debug.log
 */
object DebugLog {

    @Volatile
    private var logFile: java.io.File? = null

    /** 在 Activity onCreate 中调用一次。 */
    fun init(context: Context) {
        runCatching {
            logFile = java.io.File(context.getExternalFilesDir(null), "pcp_debug.log")
        }
    }

    fun log(message: String) {
        val file = logFile ?: return
        runCatching {
            synchronized(this) {
                file.appendText("[${System.currentTimeMillis()}] $message\n")
            }
        }
    }
}