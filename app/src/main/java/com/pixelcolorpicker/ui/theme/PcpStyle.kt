package com.pixelcolorpicker.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 旧版 DecelerateInterpolator 曲线：1 - (1 - t)²。
 *
 * 旧版所有动画（展示框展开、欢迎动画、算法卡片）都用这个曲线，
 * 与 Compose 内置 EaseOut 不同，必须自定义才能一致。
 */
val PcpDecelerate: Easing = Easing { t -> 1f - (1f - t) * (1f - t) }

/**
 * 旧版 PixelColorPicker 的视觉规范（复刻）。
 *
 * 发光细边框原理（与旧版 bg_card_stroke 一致）：
 *   底层铺一条 45° 渐变（#525777 → #474E68）作为"光"，
 *   内层深色卡片向内收缩 1dp，露出的 1dp 渐变即发光细边框。
 */
object PcpColors {
    /**
     * 当前是否浅色主题。由 [PixelColorPickerTheme] 在每次组合时更新，
     * 使 [PcpColors] 的色值能随主题切换（无需改动全项目引用点）。
     */
    @Volatile
    var isLight: Boolean = false

    private fun pick(dark: Color, light: Color): Color = if (isLight) light else dark

    // 背景层（浅色：冷白底 + 纯白卡片，形成清晰层次）
    val BgPage: Color get() = pick(Color(0xFF08090F), Color(0xFFF4F5FA))
    val BgCardPrimary: Color get() = pick(Color(0xFF0E1016), Color(0xFFFFFFFF))
    val BgCardSecondary: Color get() = pick(Color(0xFF0B0C12), Color(0xFFF7F8FC))
    val AlgorithmSelectedBg: Color get() = pick(Color(0xFF181523), Color(0xFFEEF0FF))
    // 发光边框渐变（浅色：淡紫 → 淡蓝柔光，呼应品牌色）
    val GlowStart: Color get() = pick(Color(0xFF525777), Color(0xFFC9C4F0))
    val GlowEnd: Color get() = pick(Color(0xFF474E68), Color(0xFFB8C8F5))

    // 边框（浅色：淡紫灰，不抢眼）
    val BorderInput: Color get() = pick(Color(0xFF282B38), Color(0xFFDCDDE8))
    val BorderSubtle: Color get() = pick(Color(0xFF1A1C24), Color(0xFFE8E9F2))
    // 文字（浅色：近黑主文字 + 中灰次文字，层次清晰）
    val TextPrimary: Color get() = pick(Color(0xFFFFFFFF), Color(0xFF1A1B22))
    val TextSecondary: Color get() = pick(Color(0xFF9FA0A5), Color(0xFF6E7080))
    val TextIcon: Color get() = pick(Color(0xFF9CA3AF), Color(0xFF5F6172))
    // 强调色（浅色：蓝=天依蓝 66CCFF，紫=淡紫加深版 #A99BFF）
    val AccentPurple: Color get() = pick(Color(0xFF66CCFF), Color(0xFF66CCFF))
    val TianyiBlueDark: Color get() = pick(Color(0xFF3399FF), Color(0xFF66CCFF))
    val AccentBlue: Color get() = pick(Color(0xFF5E89EC), Color(0xFF5E89EC))
    val AccentGreen: Color get() = pick(Color(0xFF7AB876), Color(0xFF7AB876))
    val AccentSwap: Color get() = pick(Color(0xFF8B5CF6), Color(0xFFA99BFF))
    val Primary: Color get() = pick(Color(0xFF8B5CF6), Color(0xFFA99BFF))
    val PrimaryDark: Color get() = pick(Color(0xFF6D28D9), Color(0xFF8F7DFF))
    // 导航
    val NavSelected: Color get() = pick(Color(0xFF66CCFF), Color(0xFF66CCFF))
    val NavNormal: Color get() = pick(Color(0xFF8A8C99), Color(0xFF8E90A0))
    // 输入框（浅色：白底 + 淡边框）
    val InputBackground: Color get() = pick(Color(0xFF0A0C12), Color(0xFFFFFFFF))
    val InputBorder: Color get() = pick(Color(0xFF2A2E40), Color(0xFFDCDDE8))
    // 图标圆底（浅色：淡紫底 + 淡紫边）
    val CircleIconBg: Color get() = pick(Color(0xFF1A1A22), Color(0xFFEDEEFB))
    val CircleIconStroke: Color get() = pick(Color(0xFF2E2E3A), Color(0xFFD5D7EA))

    // 按钮渐变
    val ButtonGradStart: Color get() = pick(Color(0xFF8B5CF6), Color(0xFF7C5CF6))
    val ButtonGradEnd: Color get() = pick(Color(0xFF5C6EFF), Color(0xFF4F7BFF))
}
/** 发光细边框（复刻旧版 bg_card_stroke）。
 *
 * 使用 drawWithCache 缓存渐变 Brush，避免动画期间每帧重建。
 */
fun Modifier.glowCardBorder(
    radius: Dp = 16.dp,
    glowStart: Color = PcpColors.GlowStart,
    glowEnd: Color = PcpColors.GlowEnd,
    inset: Dp = 1.dp,
    backgroundColor: Color = PcpColors.BgCardPrimary,
    /** 可选：内侧强调描边（主操作标识）。null 时不绘制。 */
    accentStroke: Color? = null,
    accentStrokeWidth: Dp = 1.5.dp,
): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val i = inset.toPx()
    val brush = Brush.linearGradient(
        colors = listOf(glowStart, glowEnd),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val accentW = accentStrokeWidth.toPx()
    val accentColor = accentStroke
    onDrawWithContent {
        // 底层：45° 渐变（露出部分即"光边"）
        drawRoundRect(
            brush = brush,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(r),
        )
        // 内层：卡片底色（向内收缩 inset）
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(i, i),
            size = Size(size.width - i * 2, size.height - i * 2),
            cornerRadius = CornerRadius(r - i),
        )
        // 内侧强调描边：紧贴卡片内边缘（只让出发光边 inset），圆角半径同步收缩，避免边角粗细不均
        if (accentColor != null) {
            val gap = i + accentW / 2f
            val strokeRadius = (r - gap).coerceAtLeast(0f)
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(gap, gap),
                size = Size(size.width - gap * 2, size.height - gap * 2),
                cornerRadius = CornerRadius(strokeRadius),
                style = Stroke(width = accentW),
            )
        }
        drawContent()
    }
}

/** 输入框描边（复刻旧版 bg_input_box）。 */
fun Modifier.inputBoxBorder(
    radius: Dp = 6.dp,
    fill: Color = PcpColors.InputBackground,
    stroke: Color = PcpColors.InputBorder,
    strokeWidth: Dp = 1.dp,
): Modifier = this.drawWithContent {
    val r = radius.toPx()
    val w = strokeWidth.toPx()
    drawRoundRect(
        color = fill,
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(
        color = stroke,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r),
        style = Stroke(width = w),
    )
    drawContent()
}

/** 圆形图标底（复刻旧版 circle_icon_bg）。 */
fun Modifier.circleIconBg(
    fill: Color = PcpColors.CircleIconBg,
    stroke: Color = PcpColors.CircleIconStroke,
): Modifier = this.drawWithContent {
    val r = size.minDimension / 2f
    val w = 1.dp.toPx()
    drawCircle(color = fill, radius = r)
    drawCircle(color = stroke, radius = r - w / 2, style = Stroke(width = w))
    drawContent()
}