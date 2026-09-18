package com.pixelcolorpicker.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 极简语言切换工具（不依赖 AppCompat）。
 *
 * 在 Activity.attachBaseContext 中调用 [wrap] 应用已保存的语言，
 * 切换语言时调用 [save] 并 recreate()。
 */
object LocaleHelper {

    private const val PREFS = "settings"
    private const val KEY_LANG = "app_language"

    /** 支持的语言代码。 */
    val SUPPORTED = listOf("zh", "en", "ja")

    fun getSavedLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANG, null)
        return if (lang != null && lang in SUPPORTED) lang else null
    }

    fun save(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, lang)
            .apply()
    }

    fun wrap(context: Context): Context {
        val lang = getSavedLanguage(context) ?: return context
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}