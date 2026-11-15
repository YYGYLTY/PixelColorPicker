package com.pixelcolorpicker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
)

/**
 * 主题（wasm 版）。
 *
 * 与 Android 版唯一的差别：没有 Window/系统栏，所以删掉了
 * statusBarColor / navigationBarColor / WindowCompat 那段。
 * 颜色方案、typography、PcpColors.isLight 同步逻辑完全一致。
 */
@Composable
fun PixelColorPickerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    // 中文字体：wasm 的 Skia 不带 CJK，必须在 Typography 里显式指定。
    val cjkFamily = CjkFontFamily
    val typography = remember(cjkFamily) { appTypographyWith(cjkFamily) }

    // 同步给 PcpColors，使旧版视觉规范色值随主题切换。
    // 必须在组合期赋值（理由同 Android 版注释）。
    PcpColors.isLight = !darkTheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}