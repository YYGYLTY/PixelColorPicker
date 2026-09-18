package com.pixelcolorpicker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 发光细边框：在控件外围绘制多层渐隐描边模拟光晕，中心是一条清晰细边框。
 * 不依赖 RenderEffect，兼容所有 API 级别。
 *
 * @param radius    圆角半径
 * @param color     边框/光晕颜色
 * @param pulse     发光强度 0..1（可动画）
 * @param borderWidth 中心边框宽度
 */
fun Modifier.glowBorder(
    radius: Dp,
    color: Color,
    pulse: Float = 1f,
    borderWidth: Dp = 1.2.dp,
): Modifier = this.drawWithContent {
    val r = radius.toPx()
    val w = borderWidth.toPx()
    val intensity = pulse.coerceIn(0f, 1f)

    // 光晕层（由外向内逐渐增强）
    for (i in 4 downTo 1) {
        val spread = i * 2.2f
        val alpha = (0.028f + 0.052f * (5 - i) / 4f) * (0.45f + 0.55f * intensity)
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(r + spread),
            style = Stroke(width = w + spread),
        )
    }

    // 内容
    drawContent()

    // 清晰细边框（最上层）
    drawRoundRect(
        color = color.copy(alpha = 0.55f + 0.45f * intensity),
        topLeft = Offset.Zero,
        size = size,
        cornerRadius = CornerRadius(r),
        style = Stroke(width = w),
    )
}

/** 常用形状。 */
val CardShape = RoundedCornerShape(20.dp)
val ButtonShape = RoundedCornerShape(16.dp)
val ChipShape = RoundedCornerShape(12.dp)