package com.pixelcolorpicker.util

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/** 颜色转换工具。 */
object ColorUtils {

    /** ARGB Int -> Compose Color。 */
    fun toCompose(argb: Int): Color = Color(argb)

    /** 提取 alpha 通道。 */
    fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF

    /** 提取红通道。 */
    fun red(argb: Int): Int = (argb ushr 16) and 0xFF

    /** 提取绿通道。 */
    fun green(argb: Int): Int = (argb ushr 8) and 0xFF

    /** 提取蓝通道。 */
    fun blue(argb: Int): Int = argb and 0xFF

    /** 组合 ARGB。 */
    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** 格式化为 #RRGGBB。 */
    fun toHexRgb(argb: Int): String =
        String.format("#%02X%02X%02X", red(argb), green(argb), blue(argb))

    /** 格式化为 #AARRGGBB。 */
    fun toHexArgb(argb: Int): String =
        String.format("#%08X", argb)

    /** 判断颜色是否透明（alpha == 0）。 */
    fun isTransparent(argb: Int): Boolean = alpha(argb) == 0

    /**
     * 计算适合放在给定背景上的文字颜色（用于对比度）。
     */
    fun contrastingText(argb: Int): Color {
        val luminance = (0.299 * red(argb) + 0.587 * green(argb) + 0.114 * blue(argb))
        return if (luminance > 140) Color.Black else Color.White
    }

    /** HSL 转 ARGB，用于色轮。 */
    fun hslToArgb(h: Float, s: Float, l: Float, alpha: Int = 255): Int {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val hp = h / 60f
        val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0f)
            hp < 2 -> Triple(x, c, 0f)
            hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c)
            hp < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return argb(
            alpha,
            ((r1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255).roundToInt().coerceIn(0, 255),
        )
    }
}