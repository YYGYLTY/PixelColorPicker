package com.pixelcolorpicker.res

import androidx.compose.runtime.Composable

/**
 * 替代 Android 的 stringResource(R.string.xxx)。
 * 用法：str("tab_home") 或 str("palette_copied", hex)
 */
@Composable
fun str(key: String, vararg args: Any?): String = Strings.get(key, *args)

/** 非 Composable 场景用。 */
fun strRaw(key: String, vararg args: Any?): String = Strings.get(key, *args)
