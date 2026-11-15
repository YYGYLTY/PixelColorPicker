package com.pixelcolorpicker.util

import com.pixelcolorpicker.res.Strings

/**
 * 语言切换工具（wasm 版）。
 *
 * Android 版靠 SharedPreferences + Configuration 覆盖；wasm 没有这些，
 * 直接改 [Strings.current]，并用 localStorage 持久化。
 */
object LocaleHelper {
    private const val LS_KEY = "pcp_lang"

    /** 支持的语言代码（与 Android 版一致）。 */
    val SUPPORTED = listOf("zh", "en", "ja")

    /** localStorage 是否可用（浏览器可能禁用）。 */
    private var memSaved: String? = null

    private fun lsGet(key: String): String? = try {
        js("localStorage.getItem(key)") as String?
    } catch (t: Throwable) {
        memSaved
    }

    private fun lsSet(key: String, value: String) {
        memSaved = value
        try {
            js("localStorage.setItem(key, value)")
        } catch (t: Throwable) {
            // 忽略
        }
    }

    /** 读取已保存语言；无则返回 null（跟随默认）。 */
    fun getSavedLanguage(): String? {
        val lang = lsGet(LS_KEY)
        return if (lang != null && lang in SUPPORTED) lang else null
    }

    /** 保存并立即生效。 */
    fun setLanguage(lang: String) {
        if (lang !in SUPPORTED) return
        lsSet(LS_KEY, lang)
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
