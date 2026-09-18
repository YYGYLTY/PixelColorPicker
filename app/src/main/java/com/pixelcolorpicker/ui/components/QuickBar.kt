package com.pixelcolorpicker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 快速定位滑块（垂直 / 水平通用）：
 *  - 拖动或点击 → 按比例跳转到对应滚动位置
 *  - thumb 大小反映视口占内容的比例
 *  - 按下 / 拖动时 thumb 轻微放大（150ms），松开复原
 *  - fraction / thumbFraction 为 lambda，在绘制阶段读取（自动订阅滚动状态）
 */
@Composable
fun QuickBar(
    fraction: () -> Float,
    thumbFraction: () -> Float,
    onSeek: (Float) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    // 按下（拖动或长按）时 thumb 轻微放大
    var pressed by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.6f else 1f,
        animationSpec = tween(150),
        label = "thumbScale",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { /* 消费点击，避免穿透到网格 */ },
                )
            }
            .pointerInput(vertical) {
                detectDragGestures(
                    onDragStart = { pos ->
                        pressed = true
                        onSeek(seekRatio(pos, size, vertical))
                    },
                    onDragEnd = { pressed = false },
                    onDragCancel = { pressed = false },
                    onDrag = { change, _ -> onSeek(seekRatio(change.position, size, vertical)) },
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackW = 4.dp.toPx()
            // thumb 比轨道略粗（1.5×），按下再轻微放大
            val tw = trackW * 1.5f * thumbScale
            if (vertical) {
                val cx = size.width / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(cx - trackW / 2f, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2f),
                )
                val tf = thumbFraction().coerceIn(0.04f, 1f)
                val thumbH = (size.height * tf).coerceAtLeast(trackW)
                val sf = fraction().coerceIn(0f, 1f)
                val thumbTop = (size.height - thumbH) * sf
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(cx - tw / 2f, thumbTop),
                    size = Size(tw, thumbH),
                    cornerRadius = CornerRadius(tw / 2f),
                )
            } else {
                val cy = size.height / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, cy - trackW / 2f),
                    size = Size(size.width, trackW),
                    cornerRadius = CornerRadius(trackW / 2f),
                )
                val tf = thumbFraction().coerceIn(0.04f, 1f)
                val thumbW = (size.width * tf).coerceAtLeast(trackW)
                val sf = fraction().coerceIn(0f, 1f)
                val thumbLeft = (size.width - thumbW) * sf
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbLeft, cy - tw / 2f),
                    size = Size(thumbW, tw),
                    cornerRadius = CornerRadius(tw / 2f),
                )
            }
        }
    }
}

private fun seekRatio(pos: Offset, size: IntSize, vertical: Boolean): Float {
    return if (vertical) {
        if (size.height > 0) (pos.y / size.height).coerceIn(0f, 1f) else 0f
    } else {
        if (size.width > 0) (pos.x / size.width).coerceIn(0f, 1f) else 0f
    }
}