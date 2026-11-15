package com.pixelcolorpicker.util

import com.pixelcolorpicker.res.Strings
import kotlinx.browser.localStorage
import org.w3c.dom.get

/**
 * 语言切换工具（wasm 版）。
 *
 * Android 版靠 SharedPreferences + Configuration 覆盖；
 * wasm 用 localStorage 存语言代码，直接改 [Strings.current]。
 */
object LocaleHelper {
    private const val LS_KEY = "pcp_lang"

    /** 支持的语言代码（与 Android 版一致）。 */
    val SUPPORTED = listOf("zh", "en", "ja")

    /** 读取已保存语言；无则返回 null。 */
    fun getSavedLanguage(): String? {
        val lang = localStorage[LS_KEY]
        return if (lang != null && lang in SUPPORTED) lang else null
    }

    /** 保存并立即生效。 */
    fun setLanguage(lang: String) {
        if (lang !in SUPPORTED) return
        try {
            localStorage[LS_KEY] = lang
        } catch (t: Throwable) {
            // 浏览器可能禁用 localStorage，忽略
        }
        Strings.current = when (lang) {
            "en" -> Strings.Lang.EN
            "ja" -> Strings.Lang.JA
            else -> Strings.Lang.ZH
        }
    }

    /** 启动时应用已保存语言。 */
    fun applySaved() {
        getSavedLanguage()?.let { setLanguage(it) }
    }
}