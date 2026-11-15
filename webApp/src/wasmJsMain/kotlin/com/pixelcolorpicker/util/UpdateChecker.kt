package com.pixelcolorpicker.util

/**
 * 更新检查（wasm 版）。
 *
 * Android 版用 HttpURLConnection 请求 GitHub API；
 * 网页版没有版本号概念（每次刷新都是最新），因此这里留一个空实现，
 * 保持 API 形状一致，避免上层调用点需要改。
 */
object UpdateChecker {

    /** 检查结果（与 Android 版字段一致）。 */
    data class UpdateInfo(
        val version: String,
        val url: String,
        val notes: String,
    )

    /**
     * 网页版恒返回 null（永远是最新版），调用方无需处理弹窗。
     */
    fun check(currentVersion: String): UpdateInfo? = null
}