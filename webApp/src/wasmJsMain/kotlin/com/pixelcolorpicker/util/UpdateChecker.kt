package com.pixelcolorpicker.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新检查：从远程 version.json 读取最新版本信息。
 *
 * version.json 格式示例（放到仓库根目录或任意静态托管）：
 * {
 *   "versionCode": 3,
 *   "versionName": "2.1",
 *   "url": "https://github.com/你的用户名/你的仓库/releases",
 *   "notes": "修复若干问题，新增减色功能"
 * }
 */
object UpdateChecker {

    // version.json 地址（仓库：YYGYLTY/PixelColorPicker，默认分支 main）
    private const val VERSION_URL =
        "https://raw.githubusercontent.com/YYGYLTY/PixelColorPicker/main/version.json"

    data class Info(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val notes: String,
    )

    /** 拉取最新版本信息；失败（无网络 / 超时 / 格式错误）返回 null。 */
    fun fetch(): Info? {
        return try {
            val conn = URL(VERSION_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            try {
                if (conn.responseCode !in 200..299) return null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(text)
                Info(
                    versionCode = obj.optInt("versionCode", 0),
                    versionName = obj.optString("versionName", ""),
                    url = obj.optString("url", ""),
                    notes = obj.optString("notes", ""),
                )
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            null
        }
    }
}
