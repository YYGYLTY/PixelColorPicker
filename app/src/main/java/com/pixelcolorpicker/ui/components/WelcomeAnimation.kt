package com.pixelcolorpicker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.ui.theme.PcpDecelerate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 欢迎动画（重新设计版）。
 *
 * 「欢迎使用」逐字从左滑入，随后「PixelColorPicker」逐字弹性弹出，
 * 最后淡入「点击选择图片」提示并持续轻微呼吸。
 *
 * @param played 是否已播放过（复刻旧版 Fragment 行为：切 tab 回来不重播）
 * @param startDelayMs 开场延迟（与展示框展开动效错开，0 = 立即）
 * @param onPlayed 播放完成回调
 */
@Composable
fun WelcomeAnimation(
    modifier: Modifier = Modifier,
    played: Boolean = false,
    startDelayMs: Long = 0,
    onPlayed: () -> Unit = {},
) {
    val welcomeText = stringResource(R.string.welcome_prefix)   // 欢迎使用
    val appName = "PixelColorPicker"

    // 每个字符的显示进度 0..1
    var welcomeProgress by remember { mutableStateOf(0) }
    var appProgress by remember { mutableStateOf(0) }
    var hintVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (played) {
            // 已播过（切 tab 回来）：直接显示完整状态
            welcomeProgress = welcomeText.length
            appProgress = appName.length
            hintVisible = true
            return@LaunchedEffect
        }
        // 与展示框展开动效错开
        if (startDelayMs > 0) delay(startDelayMs)
        // 「欢迎使用」逐字
        for (i in welcomeText.indices) {
            delay(80)
            welcomeProgress = i + 1
        }
        delay(280)
        // 「PixelColorPicker」逐字（快，弹性弹出）
        for (i in appName.indices) {
            delay(30)
            appProgress = i + 1
        }
        delay(200)
        hintVisible = true
        onPlayed()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 欢迎行：逐字滑入
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            welcomeText.forEachIndexed { index, ch ->
                AnimatedChar(
                    char = ch.toString(),
                    shown = index < welcomeProgress,
                    durationMs = 400,
                    offsetPx = -30f,
                    fontSizeSp = 20,
                    instant = played,
                )
            }
            appName.forEachIndexed { index, ch ->
                AnimatedChar(
                    char = ch.toString(),
                    shown = index < appProgress,
                    durationMs = 30,
                    offsetPx = -30f,
                    fontSizeSp = 20,
                    instant = played,
                    pop = true,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 点击提示（淡入 + 轻微呼吸，引导点击）
        val hintAlpha by animateFloatAsState(
            targetValue = if (hintVisible) 1f else 0f,
            animationSpec = tween(500, easing = PcpDecelerate),
            label = "hintAlpha",
        )
        val breath = rememberInfiniteTransition(label = "hintBreath")
        val breathAlpha by breath.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathAlpha",
        )
        Text(
            text = stringResource(R.string.display_tap),
            style = MaterialTheme.typography.bodyMedium,
            color = PcpColors.TextSecondary,
            modifier = Modifier.alpha(hintAlpha * breathAlpha),
        )
    }
}

/** 单个字符的滑入/弹出动画（Animatable + graphicsLayer：动画期间只重绘、不重组）。 */
@Composable
private fun AnimatedChar(
    char: String,
    shown: Boolean,
    durationMs: Int,
    offsetPx: Float,
    fontSizeSp: Int,
    instant: Boolean = false,
    pop: Boolean = false,
) {
    val alphaAnim = remember { Animatable(0f) }
    val offsetAnim = remember { Animatable(offsetPx) }
    val scaleAnim = remember { Animatable(if (pop && !instant) 0.4f else 1f) }
    LaunchedEffect(shown) {
        if (shown) {
            if (instant) {
                // 已播放过（切 tab 回来）：直接显示，不播动画
                alphaAnim.snapTo(1f)
                offsetAnim.snapTo(0f)
                scaleAnim.snapTo(1f)
            } else {
                launch { alphaAnim.animateTo(1f, tween(durationMs, easing = PcpDecelerate)) }
                launch { offsetAnim.animateTo(0f, tween(durationMs, easing = PcpDecelerate)) }
                if (pop) {
                    launch {
                        // 弹性弹出（轻微过冲，活泼但不夸张）
                        scaleAnim.animateTo(
                            1f,
                            spring(dampingRatio = 0.45f, stiffness = 600f),
                        )
                    }
                }
            }
        }
    }
    Text(
        text = char,
        color = PcpColors.TextPrimary,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.graphicsLayer {
            this.alpha = alphaAnim.value
            translationX = offsetAnim.value
            scaleX = scaleAnim.value
            scaleY = scaleAnim.value
        },
    )
}