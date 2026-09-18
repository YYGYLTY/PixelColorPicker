package com.pixelcolorpicker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.model.PixelGrid
import com.pixelcolorpicker.ui.components.EmptyState
import com.pixelcolorpicker.ui.components.QuickBar
import com.pixelcolorpicker.ui.icons.AppIcons
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.util.ColorUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 色板页：以网格展示像素化结果每一格的颜色。
 *  - 双向自由滚动（自绘虚拟化，画布再大也不卡）
 *  - 顶部 / 左侧坐标标尺（随滚动同步）
 *  - 格子圆角 + 同色计数（×N）
 *  - 右侧 / 底部快速定位滑块、坐标搜索
 *  - 点击格子复制该格颜色的 HEX
 */
@Composable
fun PaletteScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 进入页面时确保完整网格已计算
    LaunchedEffect(viewModel.isPixelated, viewModel.gridDirty) {
        if (viewModel.isPixelated) {
            viewModel.ensurePixelGrid()
        }
    }

    val grid = viewModel.pixelGrid
    var showSearch by remember { mutableStateOf(false) }
    var searchTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (grid != null) {
                        stringResource(R.string.palette_title, "${grid.width}×${grid.height}")
                    } else {
                        stringResource(R.string.tab_palette)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (grid != null) {
                IconButton(onClick = { showSearch = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.palette_search_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.palette_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !viewModel.isPixelated -> {
                    EmptyState(
                        icon = AppIcons.Palette,
                        title = stringResource(R.string.empty_title),
                        hint = stringResource(R.string.empty_hint),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                viewModel.computingGrid || grid == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.generating))
                    }
                }
                else -> {
                    PaletteGrid(
                        grid = grid,
                        searchTarget = searchTarget,
                        onSearchConsumed = { searchTarget = null },
                        onCopy = { x, y ->
                            val color = grid.get(x, y)
                            val hex = ColorUtils.toHexRgb(color)
                            copyToClipboard(context, hex)
                            Toast.makeText(
                                context,
                                context.getString(R.string.palette_copied, hex),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }
    }

    if (showSearch && grid != null) {
        CoordinateSearchDialog(
            maxX = grid.width,
            maxY = grid.height,
            onDismiss = { showSearch = false },
            onConfirm = { x, y ->
                searchTarget = (x - 1) to (y - 1)
                showSearch = false
            },
        )
    }
}

/**
 * 虚拟化 2D 网格：
 *  - 顶部 X 标尺 + 左侧 Y 标尺（随滚动同步）
 *  - 用一个透明的"滚动占位"提供滚动范围
 *  - 覆盖层 Canvas 只绘制可视区域内的格子
 *  - 右侧 / 底部快速定位滑块
 */
@Composable
private fun PaletteGrid(
    grid: PixelGrid,
    searchTarget: Pair<Int, Int>?,
    onSearchConsumed: () -> Unit,
    onCopy: (x: Int, y: Int) -> Unit,
) {
    val density = LocalDensity.current
    // 格子尺寸统一为「999×999 时的尺寸」（不随画布比例变化）
    val cellDp = remember(density) {
        (210_000f / (999f * density.density)).coerceIn(24f, 76f)
    }
    val cellPx = with(density) { cellDp.dp.toPx() }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var highlight by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // 文字画笔
    val hexPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 10f.sp.toPx() }
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }
    val coordPaint = remember(density) {
        Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 9f.sp.toPx() }
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }

    // 标尺画笔
    val rulerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rulerPaint = remember(density, rulerColor) {
        Paint().apply {
            isAntiAlias = true
            textSize = with(density) { 8f.sp.toPx() }
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.argb(
                (rulerColor.alpha * 255).toInt(),
                (rulerColor.red * 255).toInt(),
                (rulerColor.green * 255).toInt(),
                (rulerColor.blue * 255).toInt(),
            )
        }
    }

    // 搜索跳转 + 短暂高亮
    LaunchedEffect(searchTarget) {
        val target = searchTarget ?: return@LaunchedEffect
        val vw = if (viewportSize.width > 0) viewportSize.width else 1200
        val vh = if (viewportSize.height > 0) viewportSize.height else 1600
        val cx = (target.first * cellPx + cellPx / 2f - vw / 2f).toInt()
        val cy = (target.second * cellPx + cellPx / 2f - vh / 2f).toInt()
        hScroll.animateScrollTo(cx.coerceIn(0, hScroll.maxValue))
        vScroll.animateScrollTo(cy.coerceIn(0, vScroll.maxValue))
        highlight = target
        onSearchConsumed()
        delay(2600)
        highlight = null
    }

    val rulerW = 28.dp
    val rulerH = 22.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // 水平快速滑块（X 标尺上方）
        QuickBar(
            fraction = {
                if (hScroll.maxValue > 0) hScroll.value.toFloat() / hScroll.maxValue else 0f
            },
            thumbFraction = {
                val content = grid.width * cellPx
                if (content > 0f) (viewportSize.width / content).coerceIn(0.05f, 1f) else 1f
            },
            onSeek = { ratio ->
                scope.launch { hScroll.scrollTo((ratio * hScroll.maxValue).toInt()) }
            },
            vertical = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = 28.dp), // 与网格区对齐（稍缩短）
        )
        // X 标尺行（与网格列对齐）
        Row(modifier = Modifier.fillMaxWidth().height(rulerH)) {
            Spacer(Modifier.width(rulerW))
            XRuler(
                scroll = hScroll,
                cellPx = cellPx,
                count = grid.width,
                paint = rulerPaint,
                modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
            )
            Spacer(Modifier.width(rulerW)) // 与右侧滑块列对称
        }
        // 主体：Y 标尺 + 网格区
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            YRuler(
                scroll = vScroll,
                cellPx = cellPx,
                count = grid.height,
                paint = rulerPaint,
                modifier = Modifier.width(rulerW).fillMaxHeight().clipToBounds(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds() // 防止滚动的边缘格子溢出画到标尺 / 标题区
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(grid) {
                        detectTapGestures { offset ->
                            val ox = hScroll.value + offset.x
                            val oy = vScroll.value + offset.y
                            val x = (ox / cellPx).toInt()
                            val y = (oy / cellPx).toInt()
                            if (x in 0 until grid.width && y in 0 until grid.height) {
                                onCopy(x, y)
                            }
                        }
                    }
            ) {
                // 滚动占位（虚拟尺寸：报告完整滚动范围，但不创建 Constraints，绕过 Compose 布局上限）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll)
                ) {
                    VirtualSpacer(
                        width = (grid.width * cellDp).dp,
                        height = (grid.height * cellDp).dp + 110.dp, // 底部预留悬浮导航栏空间，保证最后一行能滚上来
                    )
                }

                // 绘制层
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val viewportW = size.width
                    val viewportH = size.height

                    val ox = hScroll.value.toFloat()
                    val oy = vScroll.value.toFloat()

                    val firstX = (ox / cellPx).toInt().coerceIn(0, grid.width - 1)
                    val firstY = (oy / cellPx).toInt().coerceIn(0, grid.height - 1)
                    val lastX = ((ox + viewportW) / cellPx).toInt().coerceIn(0, grid.width - 1)
                    val lastY = ((oy + viewportH) / cellPx).toInt().coerceIn(0, grid.height - 1)

                    // 文字实际宽度放得下时才显示（大网格格子小，自动隐藏文字）
                    val textThreshold = maxOf(
                        hexPaint.measureText("#FFFFFF"),
                        coordPaint.measureText("(999,999)"),
                    ) + 16f
                    val showText = cellPx >= textThreshold

                    val pad = cellPx * 0.05f
                    val corner = CornerRadius(cellPx * 0.16f)

                    for (y in firstY..lastY) {
                        for (x in firstX..lastX) {
                            val left = x * cellPx - ox
                            val top = y * cellPx - oy
                            val color = grid.get(x, y)

                            // 色块（圆角）
                            drawRoundRect(
                                color = Color(color),
                                topLeft = Offset(left + pad, top + pad),
                                size = Size(cellPx - pad * 2, cellPx - pad * 2),
                                cornerRadius = corner,
                            )

                            if (showText) {
                                val hex = ColorUtils.toHexRgb(color)
                                val textColor = ColorUtils.contrastingText(color)
                                val textArgb = android.graphics.Color.argb(
                                    (textColor.alpha * 255).toInt(),
                                    (textColor.red * 255).toInt(),
                                    (textColor.green * 255).toInt(),
                                    (textColor.blue * 255).toInt(),
                                )
                                drawIntoCanvas { canvas ->
                                    val cx = left + cellPx / 2f
                                    hexPaint.color = textArgb
                                    coordPaint.color = textArgb
                                    canvas.nativeCanvas.drawText(
                                        hex,
                                        cx,
                                        top + cellPx * 0.36f,
                                        hexPaint,
                                    )
                                    canvas.nativeCanvas.drawText(
                                        "(${x + 1},${y + 1})",
                                        cx,
                                        top + cellPx * 0.56f,
                                        coordPaint,
                                    )
                                    // 同色计数（出现 ≥2 次时显示）
                                    val cnt = grid.countOf(color)
                                    if (cnt >= 2) {
                                        canvas.nativeCanvas.drawText(
                                            "×$cnt",
                                            cx,
                                            top + cellPx * 0.76f,
                                            coordPaint,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 搜索高亮框
                    highlight?.let { (hx, hy) ->
                        if (hx in firstX..lastX && hy in firstY..lastY) {
                            val l = hx * cellPx - ox
                            val t = hy * cellPx - oy
                            drawRoundRect(
                                color = PcpColors.TextPrimary,
                                topLeft = Offset(l + pad, t + pad),
                                size = Size(cellPx - pad * 2, cellPx - pad * 2),
                                cornerRadius = corner,
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    }
                }

            }
            // 右侧固定滑块列（与 Y 标尺对称）
            QuickBar(
                fraction = {
                    if (vScroll.maxValue > 0) vScroll.value.toFloat() / vScroll.maxValue else 0f
                },
                thumbFraction = {
                    val content = grid.height * cellPx
                    if (content > 0f) (viewportSize.height / content).coerceIn(0.05f, 1f) else 1f
                },
                onSeek = { ratio ->
                    scope.launch { vScroll.scrollTo((ratio * vScroll.maxValue).toInt()) }
                },
                vertical = true,
                modifier = Modifier
                    .width(rulerW)
                    .fillMaxHeight()
                    .clipToBounds()
                    .padding(bottom = 110.dp), // 底部留出手势条 / 弧角区域，保证全程可拖
            )
        }
    }
}

/** X 标尺：显示列号（随水平滚动同步）。 */
@Composable
private fun XRuler(
    scroll: ScrollState,
    cellPx: Float,
    count: Int,
    paint: Paint,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (count > 0 && cellPx > 0f) {
            val ox = scroll.value.toFloat()
            val first = (ox / cellPx).toInt().coerceIn(0, count - 1)
            val last = ((ox + size.width) / cellPx).toInt().coerceIn(0, count - 1)
            val baseline = size.height * 0.72f
            for (x in first..last) {
                val cx = x * cellPx - ox + cellPx / 2f
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText("X${x + 1}", cx, baseline, paint)
                }
            }
        }
    }
}

/** Y 标尺：显示行号（随垂直滚动同步）。 */
@Composable
private fun YRuler(
    scroll: ScrollState,
    cellPx: Float,
    count: Int,
    paint: Paint,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (count > 0 && cellPx > 0f) {
            val oy = scroll.value.toFloat()
            val first = (oy / cellPx).toInt().coerceIn(0, count - 1)
            val last = ((oy + size.height) / cellPx).toInt().coerceIn(0, count - 1)
            val baselineOffset = paint.textSize * 0.36f
            for (y in first..last) {
                val cy = y * cellPx - oy + cellPx / 2f
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText("Y${y + 1}", size.width / 2f, cy + baselineOffset, paint)
                }
            }
        }
    }
}

/** 坐标搜索对话框：输入 X / Y（1-based），确认后跳转。 */
@Composable
private fun CoordinateSearchDialog(
    maxX: Int,
    maxY: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var textX by remember { mutableStateOf("") }
    var textY by remember { mutableStateOf("") }
    val x = textX.toIntOrNull()
    val y = textY.toIntOrNull()
    val valid = x != null && y != null && x in 1..maxX && y in 1..maxY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.palette_search_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = textX,
                    onValueChange = { textX = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("X") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = textY,
                    onValueChange = { textY = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Y") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val xx = x
                    val yy = y
                    if (xx != null && yy != null && xx in 1..maxX && yy in 1..maxY) {
                        onConfirm(xx, yy)
                    }
                },
                enabled = valid,
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * 虚拟占位组件：向父级报告指定尺寸（用于滚动范围计算），
 * 但不通过 Modifier.size 创建 fixed Constraints，
 * 从而绕过 Compose 布局约束的 32766px 硬上限（大网格必需）。
 */
@Composable
private fun VirtualSpacer(width: Dp, height: Dp) {
    Layout(content = {}) { _, _ ->
        layout(width.roundToPx(), height.roundToPx()) {}
    }
}

/** 复制文本到剪贴板。 */
internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("palette", text))
}