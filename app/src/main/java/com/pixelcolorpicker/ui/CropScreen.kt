package com.pixelcolorpicker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.ui.icons.AppIcons
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 相册式裁剪页。
 *
 * 交互模型（与系统相册一致）：
 *  - 四角手柄：拖动等比缩放裁剪框（锁定画布比例）
 *  - 四边中点手柄：拖动单边缩放（同样锁定比例，内部换算）
 *  - 框内拖动：移动整个裁剪框
 *  - 框外拖动：平移图片
 *  - 双指：缩放图片
 *
 * 关键设计（修复上一版"抓不到手柄"）：
 *  - 触摸判定 **手柄优先**，且触摸区远大于视觉手柄（视觉 22dp / 触摸 52dp）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val bitmap = viewModel.sourceBitmap
    val canvasAspect = viewModel.canvasWidth.toFloat() / viewModel.canvasHeight

    var areaSize by remember { mutableStateOf(Size.Zero) }

    // 图片变换（相对「铺满裁剪框」的额外倍率 + 像素平移）
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // 裁剪框：四条边（left/top/right/bottom），拖角只动对应两条边
    var cropLeft by remember { mutableFloatStateOf(Float.NaN) }
    var cropTop by remember { mutableFloatStateOf(Float.NaN) }
    var cropRight by remember { mutableFloatStateOf(Float.NaN) }
    var cropBottom by remember { mutableFloatStateOf(Float.NaN) }

    fun maxCropW(): Float {
        if (areaSize.width <= 0f) return 0f
        return min(areaSize.width, areaSize.height * canvasAspect) * 0.94f
    }

    // 图片基准缩放：让图片「完整显示」（scale=1 时整张图都在可视区域内）
    val baseScale = remember(bitmap, areaSize.width, areaSize.height) {
        if (bitmap == null || areaSize.width <= 0f || areaSize.height <= 0f) 1f
        else min(areaSize.width / bitmap.width, areaSize.height / bitmap.height)
    }

    fun initCropIfNeeded() {
        if (areaSize.width <= 0f || areaSize.height <= 0f) return
        if (!cropLeft.isNaN() && cropRight > cropLeft) return
        val bmp = bitmap ?: return
        // 按「完整显示」后的图片范围取初始框（图片可能小于区域，框不会超到图外）
        val bs = min(areaSize.width / bmp.width, areaSize.height / bmp.height)
        val imgW = bmp.width * bs
        val imgH = bmp.height * bs
        val il = max(0f, (areaSize.width - imgW) / 2f)
        val it = max(0f, (areaSize.height - imgH) / 2f)
        val ir = min(areaSize.width, (areaSize.width + imgW) / 2f)
        val ib = min(areaSize.height, (areaSize.height + imgH) / 2f)
        var w = min(ir - il, (ib - it) * canvasAspect) * 0.94f
        var h = w / canvasAspect
        val cx = (il + ir) / 2f
        val cy = (it + ib) / 2f
        cropLeft = cx - w / 2f
        cropRight = cx + w / 2f
        cropTop = cy - h / 2f
        cropBottom = cy + h / 2f
        com.pixelcolorpicker.util.DebugLog.log(
            "initCrop area=${areaSize.width}x${areaSize.height} img=${imgW}x${imgH} " +
                "aspect=$canvasAspect w=$w h=$h rect=($cropLeft,$cropTop,$cropRight,$cropBottom)"
        )
    }

    fun currentCropRect(): RectF {
        if (areaSize.width <= 0f || cropLeft.isNaN() || cropRight <= cropLeft) return RectF()
        return RectF(cropLeft, cropTop, cropRight, cropBottom)
    }

    val cropRect = currentCropRect()

    val k = baseScale * scale

    /** 图片在屏幕上的显示区域（用于限制框不超出图片）。 */
    fun imageDisplayRect(): RectF {
        val bmp = bitmap ?: return RectF()
        if (areaSize.width <= 0f) return RectF()
        val w = bmp.width * k
        val h = bmp.height * k
        val cx = areaSize.width / 2f + panX
        val cy = areaSize.height / 2f + panY
        return RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    fun clampPan() {
        val bmp = bitmap ?: return
        if (areaSize.width <= 0f || areaSize.height <= 0f) return
        val imgW = bmp.width * k
        val imgH = bmp.height * k
        // 比区域大：不能露边（始终盖满区域）；比区域小：不能跑出区域
        val maxPanX = abs(imgW - areaSize.width) / 2f
        val maxPanY = abs(imgH - areaSize.height) / 2f
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    /** 把裁剪框限制在「图片 ∩ 可视区域」内（图片完整显示时框不会跑到图外）。 */
    fun clampFrameIntoImage() {
        if (areaSize.width <= 0f || areaSize.height <= 0f) return
        if (cropLeft.isNaN() || cropRight <= cropLeft) return
        val img = imageDisplayRect()
        val il = max(0f, img.left)
        val it = max(0f, img.top)
        val ir = min(areaSize.width, img.right)
        val ib = min(areaSize.height, img.bottom)
        if (ir - il <= 1f || ib - it <= 1f) return
        val maxW = min(ir - il, (ib - it) * canvasAspect)
        var w = cropRight - cropLeft
        var h = cropBottom - cropTop
        if (w > maxW) {
            w = maxW
            h = w / canvasAspect
        }
        val cx = (cropLeft + cropRight) / 2f
        val cy = (cropTop + cropBottom) / 2f
        val ncx = cx.coerceIn(il + w / 2f, max(il + w / 2f, ir - w / 2f))
        val ncy = cy.coerceIn(it + h / 2f, max(it + h / 2f, ib - h / 2f))
        cropLeft = ncx - w / 2f
        cropRight = ncx + w / 2f
        cropTop = ncy - h / 2f
        cropBottom = ncy + h / 2f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (bitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.toast_load_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            CropArea(
                bitmap = bitmap,
                cropRect = cropRect,
                canvasAspect = canvasAspect,
                k = k,
                panX = panX,
                panY = panY,
                onSizeChanged = {
                    areaSize = it
                    initCropIfNeeded()
                },
                onPan = { dx, dy ->
                    panX += dx
                    panY += dy
                    clampPan()
                    clampFrameIntoImage()
                },
                onZoom = { factor ->
                    scale = (scale * factor).coerceIn(1f, 12f)
                    clampPan()
                    clampFrameIntoImage()
                },
                onMoveCrop = { dx, dy ->
                    if (cropRight > cropLeft && areaSize.width > 0f) {
                        // 限制：框不能超出「图片 ∩ 可视区域」（保证 min <= max，避免 coerceIn 崩溃）
                        val img = imageDisplayRect()
                        val il = max(0f, img.left)
                        val it = max(0f, img.top)
                        val ir = min(areaSize.width, img.right)
                        val ib = min(areaSize.height, img.bottom)
                        val w = cropRight - cropLeft
                        val h = cropBottom - cropTop
                        val maxL = max(il, ir - w)
                        val maxT = max(it, ib - h)
                        val nl = (cropLeft + dx).coerceIn(min(il, maxL), maxL)
                        val nt = (cropTop + dy).coerceIn(min(it, maxT), maxT)
                        val sx = nl - cropLeft
                        val sy = nt - cropTop
                        cropLeft = nl
                        cropRight += sx
                        cropTop = nt
                        cropBottom += sy
                    }
                },
                onResizeCorner = { corner, dx, dy ->
                    // 拖角：只动该角对应的两条边，对角固定；锁定画布比例；限制在「图片∩可视区域」内
                    val cw = areaSize.width
                    val ch = areaSize.height
                    if (cw > 0f && ch > 0f) {
                        val img = imageDisplayRect()
                        val il = max(0f, img.left)
                        val it = max(0f, img.top)
                        val ir = min(cw, img.right)
                        val ib = min(ch, img.bottom)
                        if (ir > il && ib > it) {
                            val minW = min(max(48f, 48f * canvasAspect), min(ir - il, (ib - it) * canvasAspect) * 0.2f)
                            val xSign = if (corner == Corner.TL || corner == Corner.BL) -1f else 1f
                            val ySign = if (corner == Corner.TL || corner == Corner.TR) -1f else 1f
                            // 极端比例（如 1×999）时只按长边方向驱动，避免横向微动被放大导致框跳变
                            val dw = when {
                                canvasAspect < 0.5f -> ySign * dy * canvasAspect
                                canvasAspect > 2f -> xSign * dx
                                else -> if (abs(dx) >= abs(dy)) xSign * dx else ySign * dy * canvasAspect
                            }
                            when (corner) {
                                Corner.TL -> {
                                    val maxW = min(cropRight - il, (cropBottom - it) * canvasAspect)
                                    val w = (cropRight - cropLeft + dw).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    cropLeft = cropRight - w
                                    cropTop = cropBottom - h
                                }
                                Corner.TR -> {
                                    val maxW = min(ir - cropLeft, (cropBottom - it) * canvasAspect)
                                    val w = (cropRight - cropLeft + dw).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    cropRight = cropLeft + w
                                    cropTop = cropBottom - h
                                }
                                Corner.BL -> {
                                    val maxW = min(cropRight - il, (ib - cropTop) * canvasAspect)
                                    val w = (cropRight - cropLeft + dw).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    cropLeft = cropRight - w
                                    cropBottom = cropTop + h
                                }
                                Corner.BR -> {
                                    val maxW = min(ir - cropLeft, (ib - cropTop) * canvasAspect)
                                    val w = (cropRight - cropLeft + dw).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    cropRight = cropLeft + w
                                    cropBottom = cropTop + h
                                }
                            }
                        }
                    }
                },
                onResizeEdge = { edge, dx, dy ->
                    // 拖边：只动该边，对边固定；锁定画布比例；限制在「图片∩可视区域」内
                    val cw = areaSize.width
                    val ch = areaSize.height
                    if (cw > 0f && ch > 0f) {
                        val img = imageDisplayRect()
                        val il = max(0f, img.left)
                        val it = max(0f, img.top)
                        val ir = min(cw, img.right)
                        val ib = min(ch, img.bottom)
                        if (ir > il && ib > it) {
                            val minW = min(max(48f, 48f * canvasAspect), min(ir - il, (ib - it) * canvasAspect) * 0.2f)
                            val minH = minW / canvasAspect
                            when (edge) {
                                Edge.TOP -> {
                                    val maxH = min(cropBottom - it, min(ib - it, (ir - il) / canvasAspect))
                                    val h = (cropBottom - (cropTop + dy)).coerceIn(minH, max(minH, maxH))
                                    val w = h * canvasAspect
                                    val cx = (cropLeft + cropRight) / 2f
                                    val ncx = cx.coerceIn(il + w / 2f, max(il + w / 2f, ir - w / 2f))
                                    cropTop = cropBottom - h
                                    cropLeft = ncx - w / 2f
                                    cropRight = ncx + w / 2f
                                }
                                Edge.BOTTOM -> {
                                    val maxH = min(ib - cropTop, min(ib - it, (ir - il) / canvasAspect))
                                    val h = ((cropBottom + dy) - cropTop).coerceIn(minH, max(minH, maxH))
                                    val w = h * canvasAspect
                                    val cx = (cropLeft + cropRight) / 2f
                                    val ncx = cx.coerceIn(il + w / 2f, max(il + w / 2f, ir - w / 2f))
                                    cropBottom = cropTop + h
                                    cropLeft = ncx - w / 2f
                                    cropRight = ncx + w / 2f
                                }
                                Edge.LEFT -> {
                                    val maxW = min(cropRight - il, min(ir - il, (ib - it) * canvasAspect))
                                    val w = (cropRight - (cropLeft + dx)).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    val cy = (cropTop + cropBottom) / 2f
                                    val ncy = cy.coerceIn(it + h / 2f, max(it + h / 2f, ib - h / 2f))
                                    cropLeft = cropRight - w
                                    cropTop = ncy - h / 2f
                                    cropBottom = ncy + h / 2f
                                }
                                Edge.RIGHT -> {
                                    val maxW = min(ir - cropLeft, min(ir - il, (ib - it) * canvasAspect))
                                    val w = ((cropRight + dx) - cropLeft).coerceIn(minW, max(minW, maxW))
                                    val h = w / canvasAspect
                                    val cy = (cropTop + cropBottom) / 2f
                                    val ncy = cy.coerceIn(it + h / 2f, max(it + h / 2f, ib - h / 2f))
                                    cropRight = cropLeft + w
                                    cropTop = ncy - h / 2f
                                    cropBottom = ncy + h / 2f
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        val r = currentCropRect()
                        if (r.width() > 0f) {
                            val out = renderCrop(bitmap, r, areaSize, k, panX, panY)
                            if (out != null) viewModel.applyCrop(out)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.crop_confirm))
                }
            }
        }
    }
}

private enum class DragMode {
    NONE, IMAGE, MOVE_CROP,
    RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR,
    RESIZE_TOP, RESIZE_BOTTOM, RESIZE_LEFT, RESIZE_RIGHT,
}

enum class Corner { TL, TR, BL, BR }

enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

@Composable
private fun CropArea(
    bitmap: Bitmap,
    cropRect: RectF,
    canvasAspect: Float,
    k: Float,
    panX: Float,
    panY: Float,
    onSizeChanged: (Size) -> Unit,
    onPan: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onMoveCrop: (Float, Float) -> Unit,
    onResizeCorner: (Corner, Float, Float) -> Unit,
    onResizeEdge: (Edge, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current

    // 用 rememberUpdatedState 拿最新 cropRect（避免 pointerInput 因 cropRect 变化重启）
    val rectState = rememberUpdatedState(cropRect)
    val onPanState = rememberUpdatedState(onPan)
    val onZoomState = rememberUpdatedState(onZoom)
    val onMoveCropState = rememberUpdatedState(onMoveCrop)
    val onResizeCornerState = rememberUpdatedState(onResizeCorner)
    val onResizeEdgeState = rememberUpdatedState(onResizeEdge)

    // 视觉手柄长度 / 触摸区半径（px）
    val handleVisual = with(density) { 22.dp.toPx() }
    val handleTouch = with(density) { 52.dp.toPx() }
    val handleEdge = with(density) { 20.dp.toPx() }

    var mode by remember { mutableStateOf(DragMode.NONE) }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF0A0A0F))
            .onSizeChanged { onSizeChanged(Size(it.width.toFloat(), it.height.toFloat())) }
            .pointerInput(bitmap) {
                awaitPointerEventScope {
                    while (true) {
                        // 等待第一根手指按下
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 按下时判定模式（角 → 边 → 框内 → 图片）
                        val currentRect = rectState.value
                        val cornerX = min(handleTouch, max(48f, currentRect.width() * 0.3f))
                        val cornerY = min(handleTouch, max(48f, currentRect.height() * 0.3f))
                        val edgeLR = min(handleEdge, currentRect.width() * 0.35f)
                        val edgeTB = min(handleEdge, currentRect.height() * 0.35f)
                        // 细框也能抓住：内部拖动判定区自动放宽到至少 80px
                        val movePadX = max(0f, (80f - currentRect.width()) / 2f)
                        val movePadY = max(0f, (80f - currentRect.height()) / 2f)
                        mode = when {
                            currentRect.width() <= 0f -> DragMode.IMAGE
                            near(down.position, currentRect.left, currentRect.top, cornerX, cornerY) -> DragMode.RESIZE_TL
                            near(down.position, currentRect.right, currentRect.top, cornerX, cornerY) -> DragMode.RESIZE_TR
                            near(down.position, currentRect.left, currentRect.bottom, cornerX, cornerY) -> DragMode.RESIZE_BL
                            near(down.position, currentRect.right, currentRect.bottom, cornerX, cornerY) -> DragMode.RESIZE_BR
                            abs(down.position.y - currentRect.top) <= edgeTB &&
                                down.position.x >= currentRect.left - edgeLR &&
                                down.position.x <= currentRect.right + edgeLR -> DragMode.RESIZE_TOP
                            abs(down.position.y - currentRect.bottom) <= edgeTB &&
                                down.position.x >= currentRect.left - edgeLR &&
                                down.position.x <= currentRect.right + edgeLR -> DragMode.RESIZE_BOTTOM
                            abs(down.position.x - currentRect.left) <= edgeLR &&
                                down.position.y >= currentRect.top - edgeTB &&
                                down.position.y <= currentRect.bottom + edgeTB -> DragMode.RESIZE_LEFT
                            abs(down.position.x - currentRect.right) <= edgeLR &&
                                down.position.y >= currentRect.top - edgeTB &&
                                down.position.y <= currentRect.bottom + edgeTB -> DragMode.RESIZE_RIGHT
                            // 框内拖动 → 移动框；框外拖动 → 移动图片
                            down.position.x >= currentRect.left - movePadX &&
                                down.position.x <= currentRect.right + movePadX &&
                                down.position.y >= currentRect.top - movePadY &&
                                down.position.y <= currentRect.bottom + movePadY -> DragMode.MOVE_CROP
                            else -> DragMode.IMAGE
                        }

                        // 处理这一轮手势
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                // 双指缩放图片
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) onZoomState.value(zoom)
                            } else {
                                // 单指：按模式处理
                                val change = pressed.first()
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    when (mode) {
                                        DragMode.IMAGE -> onPanState.value(delta.x, delta.y)
                                        DragMode.MOVE_CROP -> onMoveCropState.value(delta.x, delta.y)
                                        DragMode.RESIZE_TL -> onResizeCornerState.value(Corner.TL, delta.x, delta.y)
                                        DragMode.RESIZE_TR -> onResizeCornerState.value(Corner.TR, delta.x, delta.y)
                                        DragMode.RESIZE_BL -> onResizeCornerState.value(Corner.BL, delta.x, delta.y)
                                        DragMode.RESIZE_BR -> onResizeCornerState.value(Corner.BR, delta.x, delta.y)
                                        DragMode.RESIZE_TOP -> onResizeEdgeState.value(Edge.TOP, delta.x, delta.y)
                                        DragMode.RESIZE_BOTTOM -> onResizeEdgeState.value(Edge.BOTTOM, delta.x, delta.y)
                                        DragMode.RESIZE_LEFT -> onResizeEdgeState.value(Edge.LEFT, delta.x, delta.y)
                                        DragMode.RESIZE_RIGHT -> onResizeEdgeState.value(Edge.RIGHT, delta.x, delta.y)
                                        DragMode.NONE -> {}
                                    }
                                    change.consume()
                                }
                            }
                        }
                        mode = DragMode.NONE
                    }
                }
            },
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            if (cropRect.width() <= 0f) return@ComposeCanvas

            // 图片
            drawIntoCanvas { canvas ->
                val m = Matrix()
                m.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                m.postScale(k, k)
                // 图片中心固定在「区域中心」+ 用户平移（不随裁剪框移动）
                m.postTranslate(size.width / 2f + panX, size.height / 2f + panY)
                val paint = Paint().apply {
                    isFilterBitmap = true
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawBitmap(imageBitmap.asAndroidBitmap(), m, paint)
            }

            // 框外遮罩（变暗）—— 用「整块暗色 + 框内挖空」的方式，避免框越界时算错
            val mask = Color.Black.copy(alpha = 0.45f)
            // 把框限制在画布内（越界部分按边界算），保证遮罩一定能盖满框外
            val l = cropRect.left.coerceIn(0f, size.width)
            val t = cropRect.top.coerceIn(0f, size.height)
            val r = cropRect.right.coerceIn(0f, size.width)
            val b = cropRect.bottom.coerceIn(0f, size.height)
            // 上
            drawRect(mask, topLeft = Offset(0f, 0f), size = Size(size.width, t))
            // 下
            drawRect(mask, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
            // 左（在框的上下范围内）
            drawRect(mask, topLeft = Offset(0f, t), size = Size(l, b - t))
            // 右（在框的上下范围内）
            drawRect(mask, topLeft = Offset(r, t), size = Size(size.width - r, b - t))

            // 裁剪框
            drawRect(
                color = Color.White,
                topLeft = Offset(cropRect.left, cropRect.top),
                size = Size(cropRect.width(), cropRect.height()),
                style = Stroke(width = 2.5f),
            )

            // 三分线
            val thirdW = cropRect.width() / 3f
            val thirdH = cropRect.height() / 3f
            for (i in 1..2) {
                val x = cropRect.left + i * thirdW
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(x, cropRect.top),
                    end = Offset(x, cropRect.bottom),
                    strokeWidth = 1.2f,
                )
                val y = cropRect.top + i * thirdH
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(cropRect.left, y),
                    end = Offset(cropRect.right, y),
                    strokeWidth = 1.2f,
                )
            }

            // 四角 L 形手柄（视觉粗、触摸区大）
            val hW = 9f
            val hLenX = min(handleVisual, cropRect.width() * 0.35f)
            val hLenY = min(handleVisual, cropRect.height() * 0.35f)
            // 左上
            drawLine(Color.White, Offset(cropRect.left, cropRect.top + hW / 2), Offset(cropRect.left + hLenX, cropRect.top + hW / 2), hW)
            drawLine(Color.White, Offset(cropRect.left + hW / 2, cropRect.top), Offset(cropRect.left + hW / 2, cropRect.top + hLenY), hW)
            // 右上
            drawLine(Color.White, Offset(cropRect.right - hLenX, cropRect.top + hW / 2), Offset(cropRect.right, cropRect.top + hW / 2), hW)
            drawLine(Color.White, Offset(cropRect.right - hW / 2, cropRect.top), Offset(cropRect.right - hW / 2, cropRect.top + hLenY), hW)
            // 左下
            drawLine(Color.White, Offset(cropRect.left + hW / 2, cropRect.bottom - hLenY), Offset(cropRect.left + hW / 2, cropRect.bottom), hW)
            drawLine(Color.White, Offset(cropRect.left, cropRect.bottom - hW / 2), Offset(cropRect.left + hLenX, cropRect.bottom - hW / 2), hW)
            // 右下
            drawLine(Color.White, Offset(cropRect.right - hW / 2, cropRect.bottom - hLenY), Offset(cropRect.right - hW / 2, cropRect.bottom), hW)
            drawLine(Color.White, Offset(cropRect.right - hLenX, cropRect.bottom - hW / 2), Offset(cropRect.right, cropRect.bottom - hW / 2), hW)

            // 四边中点手柄（短横条）——提示「边也能拖」（细框时保底长度）
            val midX = (cropRect.left + cropRect.right) / 2f
            val midY = (cropRect.top + cropRect.bottom) / 2f
            val barX = max(min(handleEdge, cropRect.width() * 0.25f), 24f)
            val barY = max(min(handleEdge, cropRect.height() * 0.15f), 24f)
            drawLine(Color.White, Offset(midX - barX, cropRect.top), Offset(midX + barX, cropRect.top), 6f)
            drawLine(Color.White, Offset(midX - barX, cropRect.bottom), Offset(midX + barX, cropRect.bottom), 6f)
            drawLine(Color.White, Offset(cropRect.left, midY - barY), Offset(cropRect.left, midY + barY), 6f)
            drawLine(Color.White, Offset(cropRect.right, midY - barY), Offset(cropRect.right, midY + barY), 6f)

            // 四角圆点（细框时也能看清抓手）
            val dotR = 12f
            drawCircle(Color.White, radius = dotR, center = Offset(cropRect.left, cropRect.top))
            drawCircle(Color.White, radius = dotR, center = Offset(cropRect.right, cropRect.top))
            drawCircle(Color.White, radius = dotR, center = Offset(cropRect.left, cropRect.bottom))
            drawCircle(Color.White, radius = dotR, center = Offset(cropRect.right, cropRect.bottom))
        }
    }
}

private fun near(p: Offset, x: Float, y: Float, rx: Float, ry: Float): Boolean =
    abs(p.x - x) <= rx && abs(p.y - y) <= ry

/** 将裁剪框区域渲染为输出 Bitmap（分辨率上限 2048）。 */
private fun renderCrop(
    bitmap: Bitmap,
    cropRect: RectF,
    areaSize: Size,
    k: Float,
    panX: Float,
    panY: Float,
): Bitmap? {
    return try {
        val srcRegionW = cropRect.width() / k
        val srcRegionH = cropRect.height() / k
        val maxEdge = 2048f
        val outScale = min(1f, maxEdge / max(srcRegionW, srcRegionH))
        val outW = max(1, (cropRect.width() * outScale).toInt())
        val outH = max(1, (cropRect.height() * outScale).toInt())

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val m = Matrix()
        m.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        m.postScale(k, k)
        // 图片中心固定在「区域中心」+ 用户平移（与显示一致）
        m.postTranslate(areaSize.width / 2f + panX, areaSize.height / 2f + panY)
        m.postTranslate(-cropRect.left, -cropRect.top)
        m.postScale(outScale, outScale)

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(bitmap, m, paint)
        out
    } catch (t: Throwable) {
        null
    }
}