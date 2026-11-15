package com.pixelcolorpicker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 应用字体。
 *
 * 注意：lineHeight 全部使用 Unspecified（跟随字体默认行高，≈1.17×字号），
 * 复刻旧版 TextView 的紧凑行高。若使用固定值（如 Material 默认的 1.5×），
 * 小字号文字会继承过大的行高，导致各卡片间距视觉上变宽。
 */
val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = TextUnit.Unspecified,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = TextUnit.Unspecified,
    ),
)

/** 等宽字体，用于显示 HEX / RGB / 坐标。 */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = TextUnit.Unspecified,
)


/**
 * 用指定 FontFamily 重建整套 Typography。
 *
 * wasm 端必须这么干：Compose Web 的 Skia 没有 CJK 字形，
 * 注入子集字体后所有字重统一用它（子集只有 Regular）。
 */
fun appTypographyWith(family: FontFamily): Typography = AppTypography.copy(
    headlineMedium = AppTypography.headlineMedium.copy(fontFamily = family),
    titleLarge = AppTypography.titleLarge.copy(fontFamily = family),
    titleMedium = AppTypography.titleMedium.copy(fontFamily = family),
    bodyLarge = AppTypography.bodyLarge.copy(fontFamily = family),
    bodyMedium = AppTypography.bodyMedium.copy(fontFamily = family),
    bodySmall = AppTypography.bodySmall.copy(fontFamily = family),
    labelLarge = AppTypography.labelLarge.copy(fontFamily = family),
    labelMedium = AppTypography.labelMedium.copy(fontFamily = family),
    labelSmall = AppTypography.labelSmall.copy(fontFamily = family),
    displayLarge = AppTypography.displayLarge.copy(fontFamily = family),
    displayMedium = AppTypography.displayMedium.copy(fontFamily = family),
    displaySmall = AppTypography.displaySmall.copy(fontFamily = family),
    headlineLarge = AppTypography.headlineLarge.copy(fontFamily = family),
    headlineSmall = AppTypography.headlineSmall.copy(fontFamily = family),
    titleSmall = AppTypography.titleSmall.copy(fontFamily = family),
)
