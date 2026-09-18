package com.pixelcolorpicker.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.ui.MainViewModel
import com.pixelcolorpicker.ui.theme.CheckerAvg
import com.pixelcolorpicker.ui.theme.CheckerDark
import com.pixelcolorpicker.ui.theme.CheckerLight
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.ui.theme.PcpDecelerate
import com.pixelcolorpicker.ui.theme.glowCardBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页展示框（复刻旧版预览区）。
 *
 * 结构（与旧版 content_home 的 previewOuter 一致）：
 *   外层：发光细边框卡片（渐变光边 + 圆角 16dp）
 *   内层：深色卡片（#0B0C12，圆角 12dp，四周留 10dp）
 *   内容：图片 / 像素化预览 / 欢迎动画 / 网格覆盖
 *
 * 高度按画布比例做 400ms 动画（复刻旧版 animatePreviewAspect）。
 */
@Composable
fun ImageDisplayBox(
    viewModel: MainViewModel,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cropped = viewModel.croppedBitmap
    val preview = viewModel.pixelPreview
    val isPixelated = viewModel.isPixelated
    val gridEnabled = viewModel.gridEnabled

    val croppedBitmap = remember(cropped) { cropped?.asImageBitmap() }
    val previewBitmap = remember(preview) { preview?.asImageBitmap() }
    // 缓存底层 Bitmap 引用（避免每帧转换）
    val croppedAndroid = remember(croppedBitmap) { croppedBitmap?.asAndroidBitmap() }
    val previewAndroid = remember(previewBitmap) { previewBitmap?.asAndroidBitmap() }
    // 复用绘制对象（避免每帧分配，减少 GC 卡顿）
    val paintNearest = remember {
        Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
    }
    val paintSmooth = remember {
        Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
    }
    val drawMatrix = remember { Matrix() }

    // 目标高度：按画布宽高比计算（复刻旧版逻辑，限制 40dp ~ 400dp / 屏高 60%）
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val maxHeightByScreen = screenHeightDp * 0.6f
    val maxHeight = minOf(400.dp, maxHeightByScreen)
    val minHeight = 40.dp

    var containerWidthPx by remember {
        // 用屏幕宽度估算初始容器宽度（页面左右各 16dp padding），
        // 避免首帧从 40dp 到目标高度的"展开"过程（旧版 immediate 无动画）
        val estimated = (configuration.screenWidthDp.dp - 32.dp)
        mutableStateOf(with(density) { estimated.toPx() })
    }
    val containerWidthDp = with(density) { containerWidthPx.toDp() }
    val ratio = viewModel.canvasHeight.toFloat() / viewModel.canvasWidth
    val targetHeight: Dp = remember(containerWidthDp, ratio, maxHeight, minHeight) {
        if (containerWidthDp.value <= 0f) {
            minHeight
        } else {
            (containerWidthDp * ratio).coerceIn(minHeight, maxHeight)
        }
    }

    // 高度动画（复刻旧版 animatePreviewAspect）：
    // - 初始：从内容高度展开到目标高度（400ms 减速动画）—— 旧版初始就是展开动画
    // - 无图时改画布尺寸：400ms 减速动画
    // - 有图时改画布尺寸：高度保持不变（旧版 imageLoaded && !immediate → return）
    // - 换新图（裁剪回来）：立即设置新高度（旧版 resetPreviewOnNewImage immediate=true）
    // - 动画值在 Modifier.layout 中读取（布局阶段），避免每帧重组
    val heightAnim = remember { Animatable(INITIAL_PREVIEW_HEIGHT.value) }
    var heightInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetHeight) {
        if (!heightInitialized) {
            heightInitialized = true
            if (!viewModel.homeIntroPlayed) {
                // 首次进入：从内容高度展开（复刻旧版初始的 400ms 展开动画）
                viewModel.homeIntroPlayed = true
                heightAnim.animateTo(
                    targetValue = targetHeight.value,
                    animationSpec = tween(400, easing = PcpDecelerate),
                )
            } else {
                // 切 tab 回来（已播过）：直接显示（复刻旧版 Fragment 不重建行为）
                heightAnim.snapTo(targetHeight.value)
            }
        } else if (cropped == null) {
            // 无图：改画布尺寸 → 400ms 动画
            val diff = kotlin.math.abs(targetHeight.value - heightAnim.value)
            if (diff < 2f) {
                // 宽度测量修正（<2dp）：动画中不打断，否则直接设置
                if (!heightAnim.isRunning) {
                    heightAnim.snapTo(targetHeight.value)
                }
            } else {
                heightAnim.animateTo(
                    targetValue = targetHeight.value,
                    animationSpec = tween(400, easing = PcpDecelerate),
                )
            }
        }
        // 有图：高度保持不变（不处理）
    }

    // 换新图：立即设置高度（复刻旧版 resetPreviewOnNewImage）
    LaunchedEffect(cropped) {
        if (cropped != null && heightInitialized) {
            heightAnim.snapTo(targetHeight.value)
        }
    }

    // ---------- 首次进入的开场动效（淡入 + 边框光晕脉冲 + 斜向流光） ----------
    val isFirstEntry = remember { !viewModel.homeIntroPlayed }
    val appearAlpha = remember { Animatable(if (isFirstEntry) 0f else 1f) }
    val glowPulse = remember { Animatable(0f) }
    val shine = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (isFirstEntry) {
            // 1) 淡入
            launch { appearAlpha.animateTo(1f, tween(420, easing = PcpDecelerate)) }
            // 2) 边框光晕脉冲（展开完成后亮起再熄灭）
            launch {
                delay(320)
                glowPulse.animateTo(1f, tween(240, easing = PcpDecelerate))
                glowPulse.animateTo(0f, tween(640, easing = PcpDecelerate))
            }
            // 3) 斜向流光扫过
            launch {
                delay(430)
                shine.animateTo(1f, tween(820, easing = FastOutSlowInEasing))
            }
        }
    }

    val boxSizeHolder = remember { SizeHolder() }
    val outerShape = RoundedCornerShape(16.dp)
    val innerShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = appearAlpha.value }
            .layout { measurable, constraints ->
                // 在布局阶段读取动画值：只触发布局，不触发重组
                val hPx = heightAnim.value.dp.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(
                    constraints.copy(minHeight = hPx, maxHeight = hPx)
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .clip(outerShape)
            .glowCardBorder(
                radius = 16.dp,
                backgroundColor = PcpColors.BgCardPrimary,
                // 拖拽调整模式：内描边天依蓝提示
                accentStroke = if (viewModel.dragAdjustEnabled && cropped != null) PcpColors.AccentPurple else null,
            )
            .drawWithContent {
                drawContent()
                // 边框光晕脉冲（首次进入开场动效）
                if (glowPulse.value > 0.01f) {
                    drawRoundRect(
                        color = PcpColors.AccentPurple.copy(alpha = 0.55f * glowPulse.value),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                // 斜向流光（首次进入开场动效）
                if (shine.value > 0f && shine.value < 1f) {
                    val w = size.width
                    val cx = -0.35f * w + shine.value * 1.7f * w
                    clipRect {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.13f),
                                    Color.Transparent,
                                ),
                                start = Offset(cx - 0.3f * w, 0f),
                                end = Offset(cx + 0.3f * w, size.height),
                            ),
                        )
                    }
                }
            }
            .padding(10.dp)
            .clip(innerShape)
            .background(PcpColors.BgCardSecondary)
            .pointerInput(cropped, viewModel.dragAdjustEnabled) {
                when {
                    cropped == null -> detectTapGestures { onPick() }
                    viewModel.dragAdjustEnabled -> detectTransformGestures { _, pan, zoom, _ ->
                        // 拖拽调整模式：单指拖动 = 平移区域；双指捏合 = 缩放区域
                        if (zoom != 1f) viewModel.dragAdjustZoom(zoom)
                        if (pan != Offset.Zero) {
                            viewModel.dragAdjustBy(
                                pan.x,
                                pan.y,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                        }
                    }
                    // 有图片（未开拖拽调整）：不拦截任何手势（单指 / 双指都交给首页滚动）
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { boxSizeHolder.value = Size(it.width.toFloat(), it.height.toFloat()) }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            if (cropped != null) {
                // 内容区：按图片比例 contain 居中
                val imageAspect = cropped.width.toFloat() / cropped.height.toFloat()
                val contentRect = fitRect(w, h, imageAspect)

                if (isPixelated && previewAndroid != null) {
                    // 两段式变换预览：按「当前参数 vs 预览参数」的几何差实时绘制
                    // （拖动中零重采样、零额外计算，仅 GPU 变换）
                    val srcWf = cropped.width.toFloat()
                    val srcHf = cropped.height.toFloat()
                    val targetRatio = viewModel.canvasWidth.toFloat() / viewModel.canvasHeight
                    val maxWinW: Float
                    val maxWinH: Float
                    if (srcWf / srcHf > targetRatio) {
                        maxWinH = srcHf
                        maxWinW = srcHf * targetRatio
                    } else {
                        maxWinW = srcWf
                        maxWinH = srcWf / targetRatio
                    }
                    val sC = viewModel.scale
                    val sP = viewModel.previewScale
                    val wc = maxWinW / sC   // 当前窗口宽（源像素）
                    val hc = maxWinH / sC
                    val wp = maxWinW / sP   // 预览窗口宽（源像素）
                    val hp = maxWinH / sP
                    val kc = contentRect.width() / wc // 显示 px / 源像素
                    val cpX = srcWf / 2f + viewModel.previewOffsetX * srcWf
                    val cpY = srcHf / 2f + viewModel.previewOffsetY * srcHf
                    val ccX = srcWf / 2f + viewModel.offsetX * srcWf
                    val ccY = srcHf / 2f + viewModel.offsetY * srcHf
                    val left = contentRect.left + (cpX - wp / 2f - (ccX - wc / 2f)) * kc
                    val top = contentRect.top + (cpY - hp / 2f - (ccY - hc / 2f)) * kc
                    val dispRot = viewModel.previewRotation - viewModel.rotation

                    // 透明区域：棋盘格底纹（画布编辑保存的画作展示样式）
                    drawCheckerFill(contentRect, viewModel.canvasWidth, viewModel.canvasHeight)

                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        val same = dispRot == 0f && left == contentRect.left &&
                            top == contentRect.top && wp == wc
                        if (same) {
                            nc.drawBitmap(previewAndroid, null, contentRect, paintNearest)
                        } else {
                            nc.save()
                            nc.clipRect(contentRect)
                            if (dispRot != 0f) {
                                nc.rotate(dispRot, contentRect.centerX(), contentRect.centerY())
                            }
                            nc.drawBitmap(
                                previewAndroid,
                                null,
                                RectF(left, top, left + wp * kc, top + hp * kc),
                                paintNearest,
                            )
                            nc.restore()
                        }
                    }
                } else if (croppedAndroid != null) {
                    // 原图 + 变换矩阵
                    val cw = contentRect.width()
                    val k = cw / cropped.width
                    drawMatrix.reset()
                    drawMatrix.postTranslate(
                        -(cropped.width / 2f + viewModel.offsetX * cropped.width),
                        -(cropped.height / 2f + viewModel.offsetY * cropped.height),
                    )
                    drawMatrix.postScale(viewModel.scale * k, viewModel.scale * k)
                    drawMatrix.postRotate(viewModel.rotation)
                    drawMatrix.postTranslate(contentRect.centerX(), contentRect.centerY())

                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(
                            croppedAndroid,
                            drawMatrix,
                            paintSmooth,
                        )
                    }
                }

                // 网格覆盖
                if (gridEnabled) {
                    val gw = viewModel.canvasWidth
                    val gh = viewModel.canvasHeight
                    if (gw in 1..160 && gh in 1..160) {
                        val cellW = contentRect.width() / gw
                        val cellH = contentRect.height() / gh
                        if (cellW >= 2.5f && cellH >= 2.5f) {
                            val gridColor = Color(0x55000000)
                            for (i in 0..gw) {
                                val x = contentRect.left + i * cellW
                                drawLine(
                                    color = gridColor,
                                    start = Offset(x, contentRect.top),
                                    end = Offset(x, contentRect.bottom),
                                    strokeWidth = 1f,
                                )
                            }
                            for (j in 0..gh) {
                                val y = contentRect.top + j * cellH
                                drawLine(
                                    color = gridColor,
                                    start = Offset(contentRect.left, y),
                                    end = Offset(contentRect.right, y),
                                    strokeWidth = 1f,
                                )
                            }
                    }
                }
            } else {
                // 无图：网格显示开启时，绘制空画布网格（棋盘格 + 网格线）
                if (gridEnabled) {
                    val gw = viewModel.canvasWidth
                    val gh = viewModel.canvasHeight
                    if (gw in 1..160 && gh in 1..160) {
                        val aspect = gw.toFloat() / gh
                        val emptyRect = fitRect(w, h, aspect)
                        val cellW = emptyRect.width() / gw
                        val cellH = emptyRect.height() / gh
                        if (cellW >= 2.5f && cellH >= 2.5f) {
                            // 底色：棋盘平均色（格子太多时保持颜色一致 + 避免卡顿）
                            drawRect(
                                color = CheckerAvg,
                                topLeft = Offset(emptyRect.left, emptyRect.top),
                                size = Size(emptyRect.width(), emptyRect.height()),
                            )
                            if (gw.toLong() * gh <= 6000) {
                                val halfW = cellW / 2f
                                val halfH = cellH / 2f
                                for (y in 0 until gh) {
                                    for (x in 0 until gw) {
                                        val left = emptyRect.left + x * cellW
                                        val top = emptyRect.top + y * cellH
                                        for (dy in 0..1) {
                                            for (dx in 0..1) {
                                                val light = ((x * 2 + dx) + (y * 2 + dy)) % 2 == 0
                                                drawRect(
                                                    color = if (light) CheckerLight else CheckerDark,
                                                    topLeft = Offset(left + dx * halfW, top + dy * halfH),
                                                    size = Size(halfW, halfH),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val gridColor = Color(0x55000000)
                            for (i in 0..gw) {
                                val x = emptyRect.left + i * cellW
                                drawLine(gridColor, Offset(x, emptyRect.top), Offset(x, emptyRect.bottom), 1f)
                            }
                            for (j in 0..gh) {
                                val y = emptyRect.top + j * cellH
                                drawLine(gridColor, Offset(emptyRect.left, y), Offset(emptyRect.right, y), 1f)
                            }
                        }
                    }
                }
            }
        }
        }

        // 空状态：欢迎动画
        if (cropped == null) {
            WelcomeAnimation(
                played = viewModel.homeWelcomePlayed,
                startDelayMs = if (isFirstEntry) 360L else 0L,
                onPlayed = { viewModel.homeWelcomePlayed = true },
            )
        }
    }
}

/** 棋盘格底纹（透明区域指示；用于像素画预览）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerFill(rect: RectF, gw: Int, gh: Int) {
    val cellW = rect.width() / gw
    val cellH = rect.height() / gh
    if (cellW < 2.5f || cellH < 2.5f) return
    drawRect(
        color = CheckerAvg,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width(), rect.height()),
    )
    if (gw.toLong() * gh <= 6000) {
        val halfW = cellW / 2f
        val halfH = cellH / 2f
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                val left = rect.left + x * cellW
                val top = rect.top + y * cellH
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val light = ((x * 2 + dx) + (y * 2 + dy)) % 2 == 0
                        drawRect(
                            color = if (light) CheckerLight else CheckerDark,
                            topLeft = Offset(left + dx * halfW, top + dy * halfH),
                            size = Size(halfW, halfH),
                        )
                    }
                }
            }
        }
    }
}

/** 计算内容矩形（contain 适配，居中）。 */
private fun fitRect(w: Float, h: Float, aspect: Float): RectF {
    val containerAspect = w / h
    return if (aspect >= containerAspect) {
        val cw = w
        val ch = w / aspect
        RectF(0f, (h - ch) / 2f, cw, (h + ch) / 2f)
    } else {
        val ch = h
        val cw = h * aspect
        RectF((w - cw) / 2f, 0f, (w + cw) / 2f, ch)
    }
}

/** 非状态的尺寸持有者（避免动画期间的状态写入触发重组）。 */
private class SizeHolder {
    var value: Size = Size.Zero
}

/** 展示框初始内容高度（复刻旧版 wrap_content 内容高度≈84dp，用于初始展开动画起点）。 */
private val INITIAL_PREVIEW_HEIGHT = 84.dp