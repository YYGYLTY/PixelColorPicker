package com.cnrtflm.pixelpicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE = "language";

    /**
     * 获取保存的语言代码，如果没有保存则返回空字符串
     */
    public static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "");
    }

    /**
     * 保存用户选择的语言
     */
    public static void saveLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    /**
     * 应用语言设置，需要在 Activity.onCreate 之前调用
     */
    public static Context applyLanguage(Context context) {
        String language = getSavedLanguage(context);
        if (language.isEmpty()) {
            return context;
        }

        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        return context.createConfigurationContext(config);
    }
}