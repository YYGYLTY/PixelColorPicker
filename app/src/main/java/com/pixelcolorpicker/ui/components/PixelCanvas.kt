package com.pixelcolorpicker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.model.PixelArt
import com.pixelcolorpicker.ui.theme.CheckerAvg
import com.pixelcolorpicker.ui.theme.CheckerDark
import com.pixelcolorpicker.ui.theme.CheckerLight
import com.pixelcolorpicker.ui.theme.SelectionRing
import kotlin.math.abs

/**
 * 可交互的像素画布（支持任意 W×H）：
 *  - 单指：绘制（拖动自动插值不漏格；点击即画一格）
 *  - 双指：缩放（1~16x）+ 平移
 *  - 撤销按整笔合并（onStrokeStart / onStrokeEnd）
 */
@Composable
fun PixelCanvas(
    pixelArt: PixelArt,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    contentDescription: String,
    onPixelTouched: (x: Int, y: Int) -> Unit,
    onPixelSelected: (x: Int, y: Int) -> Unit,
    onStrokeStart: () -> Unit = {},
    onStrokeEnd: () -> Unit = {},
    /** 外部缩放控制（滑块）：非 null 时由外部驱动视图缩放（1~16x）。 */
    externalScale: Float? = null,
    /** 外部平移控制（滑块）：非 null 时由外部驱动视图平移（-1~1，相对画布尺寸的比例）。 */
    externalOffset: Offset? = null,
    /** true 时画布撑满可用高度（极端比例画布用：避免被 aspectRatio 压成一条线）。 */
    fillHeight: Boolean = false,
) {
    val width = pixelArt.width
    val height = pixelArt.height
    val version = pixelArt.version
    val instanceId = pixelArt.instanceId

    // 全分辨率位图（缩放时清晰；画一格重建成本低）
    // key 含 instanceId：不同画布实例（即使宽高相同）也必须重建，避免复用旧位图导致显示空白/旧内容
    val fullBitmap = remember(instanceId, version, width, height) {
        try {
            android.graphics.Bitmap.createBitmap(
                pixelArt.snapshot(), width, height, android.graphics.Bitmap.Config.ARGB_8888,
            )
        } catch (t: Throwable) {
            null
        }
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current

    // 视图缩放 / 平移（双指手势）
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }

    /** 极端比例（长边/短边 > 6）：格子按较长边铺满，靠滑块滚动浏览。 */
    val extremeRatio = minOf(width, height) > 0 &&
        maxOf(width, height).toFloat() / minOf(width, height).toFloat() > 6f

    // 画布是否含透明像素（决定是否需要棋盘底）：不透明画布跳过棋盘绘制，避免缩放卡顿
    val hasTransparent = remember(instanceId, version) {
        pixelArt.snapshot().any { (it ushr 24) and 0xFF == 0 }
    }

    /**
     * 计算基础格子尺寸（px）：
     * - 常规比例：让整幅画布「完整适配」容器（contain）
     * - 极端比例（1×999 等）：按**较长边**计算，使格子足够大、可看清可点，超出部分靠滑块滚动浏览
     */
    fun computeBaseCell(container: Size): Float {
        if (container.width <= 0f || container.height <= 0f) return 0f
        val fit = if (extremeRatio) {
            // 极端比例：按较长边铺满容器较短边，保证每格可见
            minOf(container.width, container.height) / maxOf(width, height).toFloat()
        } else {
            // 常规比例：完整适配容器（contain），铺满可用空间
            minOf(container.width / width, container.height / height)
        }
        // 下限 1px（大画布也能完整适配显示、与预览框一致）；上限 512px（避免小画布格子过大）
        return if (extremeRatio) fit.coerceIn(12f, 512f) else fit.coerceIn(1f, 512f)
    }

    val baseCell: Float = computeBaseCell(canvasSize)

    /**
     * 统一计算「最终格子边长（px）」：基础格 × 缩放倍数，并限制单边总尺寸。
     * 绘制与偏移计算必须共用此函数，否则两者不一致会导致画布错位/消失。
     */
    fun computeCell(container: Size, scale: Float): Float {
        val base = computeBaseCell(container)
        if (base <= 0f) return 0f
        // 单格最大 200px（足够看清细节）；不限制内容总尺寸，因为绘制只画可见区域。
        return (base * scale).coerceIn(base, 200f)
    }

    /**
     * 根据当前 scale/offset 计算 viewOffset（基于容器尺寸）：
     * - 内容小于容器 → 居中
     * - 内容大于容器 → 按 offset（0~1）滚动，0=顶部/最左，1=底部/最右
     */
    fun computeViewOffset(container: Size, scale: Float, off: Offset): Offset {
        if (container.width <= 0f || container.height <= 0f) return Offset.Zero
        val cell = computeCell(container, scale)
        if (cell <= 0f) return Offset.Zero
        val contentW = width * cell
        val contentH = height * cell
        val maxScrollX = (contentW - container.width).coerceAtLeast(0f)
        val maxScrollY = (contentH - container.height).coerceAtLeast(0f)
        val x = if (maxScrollX <= 0f) (container.width - contentW) / 2f else -off.x * maxScrollX
        val y = if (maxScrollY <= 0f) (container.height - contentH) / 2f else -off.y * maxScrollY
        return Offset(x, y)
    }

    // 外部滑块控制：scale 直接作为「格子大小倍数」（1~16），offset 作为「滚动位置比例」
    LaunchedEffect(externalScale) {
        if (externalScale != null) {
            viewScale = externalScale.coerceIn(1f, 16f)
        }
    }
    // scale / offset / 容器尺寸任一变化都重算位置（缩放时也要保持滚动位置正确）
    LaunchedEffect(externalScale, externalOffset, canvasSize, baseCell) {
        if (externalOffset != null) {
            viewOffset = computeViewOffset(canvasSize, viewScale, externalOffset)
        } else {
            // 手势模式：保持当前偏移，仅收敛到合法范围
            val cell = computeCell(canvasSize, viewScale)
            viewOffset = clampViewOffset(viewOffset, canvasSize, width * cell, height * cell)
        }
        com.pixelcolorpicker.util.DebugLog.log(
            "canvas: size=${canvasSize.width.toInt()}x${canvasSize.height.toInt()} " +
                "grid=${width}x$height baseCell=${baseCell.toInt()} scale=$viewScale " +
                "viewOffset=${viewOffset.x.toInt()},${viewOffset.y.toInt()}",
        )
    }

    val currentTouch by rememberUpdatedState(onPixelTouched)
    val currentSelect by rememberUpdatedState(onPixelSelected)
    val currentStrokeStart by rememberUpdatedState(onStrokeStart)
    val currentStrokeEnd by rememberUpdatedState(onStrokeEnd)

    val gridStroke = with(density) { 0.5.dp.toPx() }
    val selectionStroke = with(density) { 2.dp.toPx() }

    fun toPixel(offset: Offset): Pair<Int, Int>? {
        if (canvasSize.width <= 0f || canvasSize.height <= 0f || baseCell <= 0f) return null
        // 与绘制共用 computeCell，保证「显示」与「触摸映射」完全一致
        val cell = computeCell(canvasSize, viewScale)
        if (cell <= 0f) return null
        val local = offset - viewOffset
        val x = (local.x / cell).toInt()
        val y = (local.y / cell).toInt()
        if (x !in 0 until width || y !in 0 until height) return null
        return x to y
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fillHeight) {
                    // 极端比例：撑满可用高度（内部按 viewScale/offset 自行缩放绘制）
                    Modifier.fillMaxHeight()
                } else {
                    // 比例夹取：防止极端画布（如 1×999）在固定宽度下产生超限高度，导致 Constraints 溢出崩溃
                    Modifier.aspectRatio((width.toFloat() / height.toFloat()).coerceIn(1f / 20f, 20f))
                },
            )
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(width, height) {
                awaitEachGesture {
                    var isTransform = false
                    var lastPixel: Pair<Int, Int>? = null
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentStrokeStart()
                    try {
                        // 起点：立即应用工具（点击即画一格）+ 选中
                        val p0 = toPixel(down.position)
                        if (p0 != null) {
                            currentSelect(p0.first, p0.second)
                            currentTouch(p0.first, p0.second)
                            lastPixel = p0
                        }

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) isTransform = true
                            if (isTransform) {
                                // 双指：缩放 + 平移
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val oldScale = viewScale
                                    val newScale = (viewScale * zoom).coerceIn(1f, 16f)
                                    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                                    val ratio = if (oldScale > 0f) newScale / oldScale else 1f
                                    val moved = center + (viewOffset - center) * ratio + pan
                                    viewScale = newScale
                                    val cell = computeCell(canvasSize, newScale)
                                    viewOffset = clampViewOffset(moved, canvasSize, width * cell, height * cell)
                                }
                                event.changes.forEach { if (it.pressed) it.consume() }
                            } else if (pressedCount == 1) {
                                // 单指：绘制（插值补格，避免快速拖动漏格）
                                val change = event.changes.firstOrNull { it.pressed } ?: continue
                                val pixel = toPixel(change.position)
                                if (pixel != null) {
                                    val from = lastPixel
                                    if (from == null) {
                                        currentTouch(pixel.first, pixel.second)
                                    } else if (from.first != pixel.first || from.second != pixel.second) {
                                        forEachLine(from.first, from.second, pixel.first, pixel.second) { x, y ->
                                            currentTouch(x, y)
                                        }
                                    }
                                    lastPixel = pixel
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        currentStrokeEnd()
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        version

        // 绘制偏移：外部模式按滑块重算；手势模式直接使用 viewOffset（与 toPixel 一致，保证显示与触摸对齐）
        val drawOffset = try {
            val drawSize = Size(size.width, size.height)
            if (externalOffset != null) {
                computeViewOffset(drawSize, viewScale, externalOffset)
            } else {
                val cell = computeCell(drawSize, viewScale)
                clampViewOffset(viewOffset, drawSize, width * cell, height * cell)
            }
        } catch (t: Throwable) {
            com.pixelcolorpicker.util.DebugLog.log("draw-err: ${t.message}")
            Offset.Zero
        }

        withTransform({
            translate(drawOffset.x, drawOffset.y)
        }) {
            // 格子尺寸：与 computeViewOffset 共用 computeCell，保证两者一致
            val cell = computeCell(Size(size.width, size.height), viewScale)

            // 可见格子范围（画布坐标，含 1 格边距）
            val visX0 = (-drawOffset.x / cell).toInt() - 1
            val visY0 = (-drawOffset.y / cell).toInt() - 1
            val visX1 = ((this.size.width - drawOffset.x) / cell).toInt() + 1
            val visY1 = ((this.size.height - drawOffset.y) / cell).toInt() + 1
            val x0 = visX0.coerceIn(0, width - 1)
            val y0 = visY0.coerceIn(0, height - 1)
            val x1 = visX1.coerceIn(0, width - 1)
            val y1 = visY1.coerceIn(0, height - 1)
            val visibleCount = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)

            // 1) 底色：棋盘平均色（只画可见区域，避免极端比例放大后绘制尺寸超限导致空白）
            drawRect(
                color = CheckerAvg,
                topLeft = Offset(x0 * cell, y0 * cell),
                size = Size(
                    (x1 - x0 + 1) * cell,
                    (y1 - y0 + 1) * cell,
                ),
            )

            // 1b) 棋盘格（逐格精确对齐；仅可见格数少 + 有透明像素时叠加，且随缩放渐显，避免明暗突变）
            if (hasTransparent && visibleCount <= 6000) {
                val appear = ((cell - 16f) / 4f).coerceIn(0f, 1f)
                if (appear > 0.02f) {
                    val lightC = lerp(CheckerAvg, CheckerLight, appear)
                    val darkC = lerp(CheckerAvg, CheckerDark, appear)
                    val half = cell / 2f
                    for (y in y0..y1) {
                        for (x in x0..x1) {
                            val left = x * cell
                            val top = y * cell
                            for (dy in 0..1) {
                                for (dx in 0..1) {
                                    val light = ((x * 2 + dx) + (y * 2 + dy)) % 2 == 0
                                    drawRect(
                                        color = if (light) lightC else darkC,
                                        topLeft = Offset(left + dx * half, top + dy * half),
                                        size = Size(half, half),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2) 像素层（只绘制可见区域对应的位图切片，避免极端比例放大后目标尺寸超限导致空白）
            if (fullBitmap != null && x1 >= x0 && y1 >= y0) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        isFilterBitmap = false
                        isAntiAlias = false
                    }
                    val src = android.graphics.Rect(
                        x0, y0, x1 + 1, y1 + 1,
                    )
                    val dst = android.graphics.RectF(
                        x0 * cell, y0 * cell,
                        (x1 + 1) * cell, (y1 + 1) * cell,
                    )
                    canvas.nativeCanvas.drawBitmap(fullBitmap, src, dst, paint)
                }
            }

            // 3) 网格线（线宽自适应；中灰配色；覆盖当前可见区域，缩放后不会消失）
            if (cell >= 3f) {
                val strokeW = minOf(gridStroke, cell / 8f)
                val lineColor = Color(0x50808080)
                // 可见区域在「内容坐标」中的范围（否则放大平移后线会画到看不见的地方）
                val visTop = -drawOffset.y
                val visBottom = visTop + this.size.height
                val visLeft = -drawOffset.x
                val visRight = visLeft + this.size.width
                for (i in x0..x1 + 1) {
                    val pos = i * cell
                    drawLine(
                        color = lineColor,
                        start = Offset(pos, visTop),
                        end = Offset(pos, visBottom),
                        strokeWidth = strokeW,
                    )
                }
                for (i in y0..y1 + 1) {
                    val pos = i * cell
                    drawLine(
                        color = lineColor,
                        start = Offset(visLeft, pos),
                        end = Offset(visRight, pos),
                        strokeWidth = strokeW,
                    )
                }
            }

            // 4) 选中高亮（描边宽度自适应，视觉恒定）
            if (selectedIndex in 0 until width * height) {
                val sx = selectedIndex % width
                val sy = selectedIndex / width
                drawRect(
                    color = SelectionRing,
                    topLeft = Offset(sx * cell, sy * cell),
                    size = Size(cell, cell),
                    style = Stroke(width = selectionStroke),
                )
            }
        }
    }
}

/** 限制平移范围：内容小于容器 → 居中；大于容器 → 边缘不超出容器。 */
private fun clampViewOffset(offset: Offset, canvas: Size, contentW: Float, contentH: Float): Offset {
    val x = if (contentW <= canvas.width) {
        (canvas.width - contentW) / 2f
    } else {
        offset.x.coerceIn(canvas.width - contentW, 0f)
    }
    val y = if (contentH <= canvas.height) {
        (canvas.height - contentH) / 2f
    } else {
        offset.y.coerceIn(canvas.height - contentH, 0f)
    }
    return Offset(x, y)
}

/** Bresenham 直线遍历（拖动插值，避免漏格）。 */
private fun forEachLine(x0: Int, y0: Int, x1: Int, y1: Int, action: (Int, Int) -> Unit) {
    var cx = x0
    var cy = y0
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    while (true) {
        action(cx, cy)
        if (cx == x1 && cy == y1) return
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            cx += sx
        }
        if (e2 <= dx) {
            err += dx
            cy += sy
        }
    }
}

/** 棋盘格背景（平铺位图，O(1) 绘制，任意大画布都不卡）。 */
internal fun DrawScope.drawCheckerboard(cell: Float, width: Int, height: Int) {
    val tile = checkerTileFor(cell) ?: return
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.BitmapShader(
            tile,
            android.graphics.Shader.TileMode.REPEAT,
            android.graphics.Shader.TileMode.REPEAT,
        )
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

private val checkerTiles = HashMap<Int, android.graphics.Bitmap>()

/** 按当前格子尺寸取（或生成）棋盘格平铺位图；格子太小返回 null（跳过绘制）。 */
private fun checkerTileFor(cell: Float): android.graphics.Bitmap? {
    val sub = (cell / 2f).toInt()
    if (sub < 2) return null
    val s = sub.coerceIn(2, 64)
    return checkerTiles.getOrPut(s) {
        val light = CheckerLight.toArgb()
        val dark = CheckerDark.toArgb()
        val t = android.graphics.Bitmap.createBitmap(s * 2, s * 2, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until s * 2) {
            for (x in 0 until s * 2) {
                val isLight = ((x / s) + (y / s)) % 2 == 0
                t.setPixel(x, y, if (isLight) light else dark)
            }
        }
        t
    }
}

/** 逐像素绘制阈值（超过则使用降采样位图）。 */
private const val LARGE_CANVAS_THRESHOLD = 65536

/** 将大画布降采样为位图（用于编辑器预览）。 */
private fun buildDownsampled(art: PixelArt, maxDim: Int): android.graphics.Bitmap? {
    return try {
        val dw = maxOf(1, minOf(maxDim, art.width))
        val dh = maxOf(1, minOf(maxDim, art.height))
        val pixels = IntArray(dw * dh)
        for (y in 0 until dh) {
            val sy = y * art.height / dh
            for (x in 0 until dw) {
                val sx = x * art.width / dw
                pixels[y * dw + x] = art.get(sx, sy)
            }
        }
        android.graphics.Bitmap.createBitmap(pixels, dw, dh, android.graphics.Bitmap.Config.ARGB_8888)
    } catch (t: Throwable) {
        null
    }
}