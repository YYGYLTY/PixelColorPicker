package com.pixelcolorpicker.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.image.PixelAlgorithm
import com.pixelcolorpicker.ui.components.ImageDisplayBox
import kotlin.math.roundToInt
import com.pixelcolorpicker.ui.icons.AppIcons
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.ui.theme.PcpDecelerate
import com.pixelcolorpicker.ui.theme.circleIconBg
import com.pixelcolorpicker.ui.theme.glowCardBorder
import com.pixelcolorpicker.ui.theme.inputBoxBorder

/**
 * 首页（复刻旧版 PixelColorPicker 首页布局）。
 *
 * 结构：
 *   1. 顶部标题行（Logo + 标题 + 语言按钮）
 *   2. 画布尺寸卡片 + 常用尺寸卡片（并排）
 *   3. 展示框（发光边框 + 欢迎动画 / 图片预览）
 *   4. 网格显示开关 + 导入图片卡片（并排）
 *   5. 微调控制卡片
 *   6. 算法选择卡片
 *   7. 生成像素画按钮
 */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onPickImage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val scrollRestored = remember { mutableStateOf(false) }
    // 进入时恢复上次滚动位置：等滚动范围稳定后再恢复（避免被内容初始高度夹取）
    LaunchedEffect(Unit) {
        var lastMax = -1
        var stable = 0
        var tries = 0
        while (tries < 40 && stable < 2) {
            val m = scrollState.maxValue
            if (m != Int.MAX_VALUE && m == lastMax) stable++ else stable = 0
            lastMax = m
            withFrameNanos { }
            tries++
        }
        scrollState.scrollTo(viewModel.homeScrollY.coerceAtMost(scrollState.maxValue))
        scrollRestored.value = true
        android.util.Log.d("PCPDEBUG", "HOME_ENTER restoreY=${viewModel.homeScrollY} curY=${scrollState.value} max=${scrollState.maxValue}")
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { v ->
            if (scrollRestored.value) viewModel.homeScrollY = v
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("PCPDEBUG", "HOME_EXIT curY=${scrollState.value} savedY=${viewModel.homeScrollY}")
        }
    }

    var showSizeDialog by remember { mutableStateOf(false) }
    var sizeDialogIsWidth by remember { mutableStateOf(true) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // 首页操作行（更换图片/重新裁剪/网格）显示条件：有原图，或画布上有内容（含空白画布绘制）
    val showHomeActions = viewModel.sourceBitmap != null || viewModel.croppedBitmap != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                com.pixelcolorpicker.util.DebugLog.log("homeCol size=${it.width}x${it.height}")
            }
            .background(PcpColors.BgPage)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        // ---------- 1. 顶部标题行（独立绘制层：动画期间不重绘） ----------
        TopHeader(
            onOpenSettings = onOpenSettings,
            modifier = Modifier.graphicsLayer { },
        )

        Spacer(Modifier.height(16.dp))

        // ---------- 2. 画布尺寸 + 常用尺寸（两卡片间距 16dp：复刻旧版 8dp+8dp；独立绘制层） ----------
        Row(
            modifier = Modifier.graphicsLayer { },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CanvasSizeCard(
                width = viewModel.canvasWidth,
                height = viewModel.canvasHeight,
                onWidthClick = {
                    sizeDialogIsWidth = true
                    showSizeDialog = true
                },
                onHeightClick = {
                    sizeDialogIsWidth = false
                    showSizeDialog = true
                },
                onSwap = {
                    if (!viewModel.setCanvasSize(viewModel.canvasHeight, viewModel.canvasWidth)) {
                        Toast.makeText(context, context.getString(R.string.size_ratio_locked), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            CommonSizesCard(
                preset1 = viewModel.presetSize(0),
                preset2 = viewModel.presetSize(1),
                width = viewModel.canvasWidth,
                height = viewModel.canvasHeight,
                onPick = { w, h ->
                    if (!viewModel.setCanvasSize(w, h)) {
                        Toast.makeText(context, context.getString(R.string.size_ratio_locked), Toast.LENGTH_SHORT).show()
                    }
                },
                onMore = { showCustomDialog = true },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---------- 3. 展示框（独立绘制层） ----------
        ImageDisplayBox(
            viewModel = viewModel,
            onPick = onPickImage,
            modifier = Modifier.graphicsLayer { },
        )

        Spacer(Modifier.height(if (showHomeActions && !viewModel.homeActionsExpanded) 0.dp else 16.dp))

        // ---------- 展示框下方：更换图片 / 重新裁剪 / 网格显示（有内容时显示；同排箭头可折叠） ----------
        if (showHomeActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (viewModel.homeActionsExpanded) {
                    RoundActionButton(
                        icon = AppIcons.Swap,
                        text = stringResource(R.string.change_image),
                        onClick = onPickImage,
                        modifier = Modifier.weight(1f),
                    )
                    RoundActionButton(
                        icon = Icons.Default.Crop,
                        text = stringResource(R.string.recrop),
                        onClick = { viewModel.recrop() },
                        modifier = Modifier.weight(1f),
                    )
                    RoundToggleButton(
                        icon = painterResource(R.drawable.old_ic_grid),
                        text = stringResource(R.string.grid_display),
                        checked = viewModel.gridEnabled,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.gridEnabled = !viewModel.gridEnabled
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // 折叠：与三个按钮同款圆形容器，用暖橙色区分（不与三个蓝色冲突）
                    RoundActionButton(
                        icon = Icons.Default.ExpandLess,
                        text = stringResource(R.string.tap_to_collapse),
                        onClick = { viewModel.homeActionsExpanded = false },
                        accent = Color(0xFFFF8A65),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // 折叠后：紧凑显示（图标按钮本身无额外留白，间隔由下方 Spacer 控制）
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = { viewModel.homeActionsExpanded = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = PcpColors.TextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
            // 折叠后不额外留白（间隔收紧到最小）
            Spacer(Modifier.height(if (viewModel.homeActionsExpanded) 16.dp else 0.dp))
        }

        // ---------- 4~7. 展示框下方内容（整体独立绘制层：位置随动画移动时不重绘内容） ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 画布编辑 + 生成像素画（两卡片间距 16dp：复刻旧版 8dp+8dp）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    // 两卡片等高（取内容较高者），避免高度不一致
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    NewCanvasCard(
                        onClick = { viewModel.openEditor() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    GenerateCard(
                        onClick = {
                            if (viewModel.croppedBitmap == null) {
                                Toast.makeText(context, context.getString(R.string.pick_first), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.generate()
                                if (viewModel.pendingReCropHint) {
                                    viewModel.consumeReCropHint()
                                    Toast.makeText(context, context.getString(R.string.size_ratio_locked), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.generated), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 微调控制
                FineTuneCard(
                    onZoomIn = { viewModel.zoomInStep() },
                    onZoomOut = { viewModel.zoomOutStep() },
                    onPanUp = { viewModel.panStep(0f, -0.1f) },
                    onPanDown = { viewModel.panStep(0f, 0.1f) },
                    onPanLeft = { viewModel.panStep(-0.1f, 0f) },
                    onPanRight = { viewModel.panStep(0.1f, 0f) },
                    onRotateCcw = {
                        if (!viewModel.rotateStep(-90f)) {
                            Toast.makeText(context, context.getString(R.string.rotate_square_only), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRotateCw = {
                        if (!viewModel.rotateStep(90f)) {
                            Toast.makeText(context, context.getString(R.string.rotate_square_only), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReset = { viewModel.resetSample() },
                    dragAdjust = viewModel.dragAdjustEnabled,
                    onToggleDragAdjust = { viewModel.dragAdjustEnabled = !viewModel.dragAdjustEnabled },
                )

                Spacer(Modifier.height(16.dp))

                // 算法选择
                AlgorithmCard(
                    current = viewModel.algorithm,
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.selectAlgorithm(it)
                    },
                    customKernel = viewModel.customKernel,
                    customSamples = viewModel.customSamples,
                    customPreview = { k, s -> viewModel.renderCustomPreview(k, s) },
                    onSelectCustom = { kernel, samples ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.selectCustomAlgorithm(kernel, samples)
                    },
                )

                Spacer(Modifier.height(20.dp))

                // 保存图片到相册（生成像素画后可用）
                OutlinedButton(
                    onClick = { viewModel.savePixelArtToAlbum() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_to_album), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 底部留白：与上方 Spacer(20dp) 对称——生成按钮底到 tab 栏顶 ≈ 20dp
        Spacer(Modifier.height(95.dp))
    }

    // 尺寸输入对话框
    if (showSizeDialog) {
        NumberInputDialog(
            title = stringResource(
                if (sizeDialogIsWidth) R.string.canvas_width else R.string.canvas_height
            ),
            initial = if (sizeDialogIsWidth) viewModel.canvasWidth else viewModel.canvasHeight,
            onDismiss = { showSizeDialog = false },
            onConfirm = { value ->
                val ok = if (sizeDialogIsWidth) {
                    viewModel.setCanvasSize(value, viewModel.canvasHeight)
                } else {
                    viewModel.setCanvasSize(viewModel.canvasWidth, value)
                }
                if (!ok) {
                    Toast.makeText(context, context.getString(R.string.size_ratio_locked), Toast.LENGTH_SHORT).show()
                }
                showSizeDialog = false
            },
        )
    }

    if (showCustomDialog) {
        CustomSizeDialog(
            initialW = viewModel.canvasWidth,
            initialH = viewModel.canvasHeight,
            onDismiss = { showCustomDialog = false },
            onConfirm = { w, h ->
                viewModel.saveCustomPreset(w, h)
                showCustomDialog = false
            },
        )
    }
}

// ==================== 1. 顶部标题行 ====================

@Composable
private fun TopHeader(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo（圆形底 + 旧版像素图标）
        Box(
            modifier = Modifier
                .size(44.dp)
                .circleIconBg(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.old_ic_app_icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 标题：单一字体 + 整字渐变（淡紫 → 天依蓝），干净利落
            Text(
                text = "PixelColorPicker",
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFA99BFF), Color(0xFF66CCFF)),
                    ),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                ),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                color = PcpColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 11.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
        // 设置按钮（圆形底 + 齿轮图标；marginStart4dp / marginTop-2dp / marginEnd8dp）
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .offset(y = (-2).dp)
                .padding(start = 4.dp, end = 8.dp)
                .size(32.dp)
                .circleIconBg()
                .clickable(interactionSource = interaction, indication = null, onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = PcpColors.TextIcon,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ==================== 2. 画布尺寸卡片 ====================

@Composable
private fun CanvasSizeCard(
    width: Int,
    height: Int,
    onWidthClick: () -> Unit,
    onHeightClick: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlowCard(modifier = modifier) {
        // 标题行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_canvas_size),
                contentDescription = null,
                tint = PcpColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.canvas_size),
                color = PcpColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = 16.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        // W [32] ⇄ H [16]
        Row(verticalAlignment = Alignment.CenterVertically) {
            SizeInput(
                label = "W",
                value = width,
                onClick = onWidthClick,
                modifier = Modifier.weight(1f),
            )
            // 交换按钮（复刻旧版 ImageButton：白色图标 + borderless 涟漪）
            val swapInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(16.dp)
                    .clickable(
                        interactionSource = swapInteraction,
                        indication = ripple(bounded = false, color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                        onClick = onSwap,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.old_ic_swap),
                    contentDescription = stringResource(R.string.swap_size),
                    tint = PcpColors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            SizeInput(
                label = "H",
                value = height,
                onClick = onHeightClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SizeInput(
    label: String,
    value: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(40.dp)
            .inputBoxBorder(radius = 6.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = PcpColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        // 值：30dp 宽居中（支持 3 位数字如 999；两位数字约 14sp）
        Text(
            text = value.toString(),
            color = PcpColors.TextPrimary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.width(30.dp),
        )
        Icon(
            painter = painterResource(R.drawable.old_ic_arrow_down),
            contentDescription = null,
            tint = PcpColors.TextPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

// ==================== 2b. 常用尺寸卡片 ====================

@Composable
private fun CommonSizesCard(
    preset1: Pair<Int, Int>,
    preset2: Pair<Int, Int>,
    width: Int,
    height: Int,
    onPick: (Int, Int) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 预设选中态（复刻旧版 togglePreset：点击选中蓝框、再点取消、互斥）
    var selected by rememberSaveable { mutableStateOf(0) } // 0=无，1=预设1，2=预设2
    // 画布尺寸/预设变化时校验选中态（复刻旧版 clearPresetSelections）
    LaunchedEffect(width, height, preset1, preset2) {
        if (selected == 1 && (width != preset1.first || height != preset1.second)) selected = 0
        if (selected == 2 && (width != preset2.first || height != preset2.second)) selected = 0
    }
    GlowCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_presets),
                contentDescription = null,
                tint = PcpColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.common_sizes),
                color = PcpColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = 16.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PresetChip(
                top = preset1.first.toString(),
                bottom = preset1.second.toString(),
                selected = selected == 1,
                modifier = Modifier.weight(1f),
            ) {
                if (selected == 1) {
                    selected = 0
                } else {
                    selected = 1
                    onPick(preset1.first, preset1.second)
                }
            }
            PresetChip(
                top = preset2.first.toString(),
                bottom = preset2.second.toString(),
                selected = selected == 2,
                modifier = Modifier.weight(1f),
            ) {
                if (selected == 2) {
                    selected = 0
                } else {
                    selected = 2
                    onPick(preset2.first, preset2.second)
                }
            }
            MoreChip(Modifier.weight(1f), onMore)
        }
    }
}

@Composable
private fun PresetChip(
    top: String,
    bottom: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(40.dp)
            .inputBoxBorder(
                radius = 6.dp,
                stroke = if (selected) PcpColors.AccentPurple else PcpColors.InputBorder,
                strokeWidth = if (selected) 2.dp else 1.dp,
            )
            .offset(y = (-0.7).dp) // 补偿字体字形在排版盒内的自然偏下，使上下留白对称（仅内容偏移，边框不动）
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 三行紧凑排布（每行在固定高度内垂直居中，保证上、下留白对称）
        Box(modifier = Modifier.height(13.dp), contentAlignment = Alignment.Center) {
            Text(
                top,
                color = PcpColors.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 11.sp,
            )
        }
        Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {
            Text(
                "×",
                color = PcpColors.TextPrimary,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }
        Box(modifier = Modifier.height(13.dp), contentAlignment = Alignment.Center) {
            Text(
                bottom,
                color = PcpColors.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 11.sp,
            )
        }
    }
}

@Composable
private fun MoreChip(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(40.dp)
            .inputBoxBorder(radius = 6.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 三个小灰点（用户喜欢的版本：4dp 灰点、紧凑间距）
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(vertical = 1.5.dp)
                    .size(4.dp)
                    .background(PcpColors.TextSecondary, CircleShape),
            )
        }
    }
}

// ==================== 4. 网格开关 + 导入图片 ====================

@Composable
private fun GridSwitchCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 复刻旧版：卡片高 82dp、内边距(14,6,14,6)；视觉圆角 16dp（bg_card_stroke）
    Column(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(16.dp))
            .glowCardBorder(radius = 16.dp, backgroundColor = PcpColors.BgCardPrimary)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .circleIconBg(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.old_ic_grid),
                    contentDescription = null,
                    tint = PcpColors.AccentPurple,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.grid_display),
                color = PcpColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = 14.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
        // 旧版尺寸的小开关（22dp 高），marginStart 42dp
        SmallSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 42.dp),
        )
    }
}

/** 小尺寸开关（复刻旧版 SwitchCompat：22dp 高、40dp 宽）。 */
@Composable
private fun SmallSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "thumbOffset",
    )
    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) PcpColors.TianyiBlueDark else PcpColors.BorderInput,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "trackColor",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(trackColor)
            .clickable(interactionSource = interaction, indication = null) {
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .background(PcpColors.TextPrimary, CircleShape),
        )
    }
}

@Composable
private fun NewCanvasCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val accent = PcpColors.AccentPurple
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .glowCardBorder(
                radius = 18.dp,
                backgroundColor = PcpColors.BgCardPrimary,
                accentStroke = accent.copy(alpha = if (pressed) 0.95f else 0.75f),
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.15f)),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标：淡紫圆底 + 画笔图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (pressed) 0.30f else 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_canvas),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.new_blank_canvas),
                color = PcpColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = 14.sp,
                    stepSize = 0.5.sp,
                ),
            )
            Text(
                text = stringResource(R.string.new_blank_hint),
                color = PcpColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 11.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

/** 功能卡片：生成像素画（主操作：发光边框内侧加一圈橙色描边，克制而醒目）。 */
@Composable
private fun GenerateCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    // 强调色：珊瑚橙（与折叠按钮同色系，暖色在蓝紫主调中自然突出）
    val accent = Color(0xFFFF8A65)
    Row(
        modifier = modifier
            .clip(shape)
            .glowCardBorder(
                radius = 18.dp,
                backgroundColor = PcpColors.BgCardPrimary,
                accentStroke = accent.copy(alpha = if (pressed) 0.98f else 0.80f),
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accent.copy(alpha = 0.18f)),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.50f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_generate),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.generate),
                color = PcpColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp,
                    maxFontSize = 14.sp,
                    stepSize = 0.5.sp,
                ),
            )
            Text(
                text = stringResource(R.string.generate_hint),
                color = PcpColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 11.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
        Spacer(Modifier.width(6.dp))
        // 小箭头暗示"立即执行"
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 圆形图标操作按钮（更换图片 / 重新裁剪 / 折叠）：圆底图标 + 下方文字。 */
@Composable
private fun RoundActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PcpColors.AccentPurple,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.10f)),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.height(28.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = PcpColors.TextPrimary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 圆形图标切换按钮（网格显示）：开启时亮蓝、关闭时灰色。 */
@Composable
private fun RoundToggleButton(
    icon: Painter,
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = if (checked) PcpColors.AccentPurple else PcpColors.TextSecondary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.10f)),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.height(28.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (checked) PcpColors.TextPrimary else PcpColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ==================== 5. 微调控制 ====================

@Composable
private fun FineTuneCard(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onPanUp: () -> Unit,
    onPanDown: () -> Unit,
    onPanLeft: () -> Unit,
    onPanRight: () -> Unit,
    onRotateCcw: () -> Unit,
    onRotateCw: () -> Unit,
    onReset: () -> Unit,
    dragAdjust: Boolean,
    onToggleDragAdjust: () -> Unit,
) {
    // 复刻旧版：视觉圆角 16dp（bg_card_stroke）
    var showResetDialog by remember { mutableStateOf(false) }
    // 四个微调按钮的文字：图标 + 6dp + 文字自然宽度紧凑居中（复刻旧版）
    val zoomInText = stringResource(R.string.zoom_in)
    val zoomOutText = stringResource(R.string.zoom_out)
    val ccwText = stringResource(R.string.rotate_ccw)
    val cwText = stringResource(R.string.rotate_cw)
    GlowCard(contentPadding = 16.dp, radius = 16.dp) {
        // 标题行：微调控制 + 拖拽调整胶囊 + 重置胶囊（复刻旧版）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.fine_tune),
                color = PcpColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 11.sp,
                    maxFontSize = 16.sp,
                    stepSize = 0.5.sp,
                ),
                // 复刻旧版 marginTop=-4dp（文字视觉上移）
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-4).dp),
            )
            // 拖拽调整胶囊（32dp 高，圆角16dp，深色底 + 白描边；点击切换蓝框）
            PillButton(
                text = stringResource(R.string.drag_adjust),
                icon = null,
                onClick = onToggleDragAdjust,
                modifier = Modifier.padding(end = 8.dp),
                toggleSelected = dragAdjust,
            )
            // 重置胶囊（正圆矢量图标；红色 + 二次确认）
            PillButton(
                text = stringResource(R.string.reset),
                icon = { Icon(painterResource(R.drawable.ic_reset), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                onClick = { showResetDialog = true },
                danger = true,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左：放大 / 缩小（40dp 高，圆角12dp）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MicroButton(
                    text = zoomInText,
                    icon = { Icon(painterResource(R.drawable.old_ic_add), null, tint = PcpColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                    onClick = onZoomIn,
                )
                MicroButton(
                    text = zoomOutText,
                    icon = { Icon(painterResource(R.drawable.old_ic_remove), null, tint = PcpColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                    onClick = onZoomOut,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 中：十字方向键（40dp 方块，中间留 40dp 空格）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SquareBtn(R.drawable.old_ic_arrow_up, onPanUp)
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    SquareBtn(R.drawable.old_ic_arrow_left, onPanLeft)
                    SquareBtn(R.drawable.old_ic_arrow_right, onPanRight)
                }
                SquareBtn(R.drawable.old_ic_arrow_down, onPanDown)
            }

            Spacer(Modifier.width(8.dp))

            // 右：旋转
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MicroButton(
                    text = ccwText,
                    icon = { Icon(painterResource(R.drawable.old_ic_rotate_ccw), null, tint = PcpColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                    onClick = onRotateCcw,
                )
                MicroButton(
                    text = cwText,
                    icon = { Icon(painterResource(R.drawable.old_ic_rotate_cw), null, tint = PcpColors.TextPrimary, modifier = Modifier.size(16.dp)) },
                    onClick = onRotateCw,
                )
            }
        }

        // 重置二次确认
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.fine_tune_reset_title)) },
                text = { Text(stringResource(R.string.fine_tune_reset_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        onReset()
                    }) {
                        Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/**
 * 胶囊按钮（复刻旧版：32dp 高、圆角16dp、#25283B 底、白描边）。
 *
 * - toggleSelected = null：普通按钮（重置），按下时边框变天依蓝；
 * - toggleSelected != null：切换按钮（拖拽调整），选中时边框天依蓝，否则白色。
 */
@Composable
private fun PillButton(
    text: String,
    icon: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    toggleSelected: Boolean? = null,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    // 快速点按也要能看到描边闪烁：直接监听交互事件（不依赖重组时机），按下后至少保持 120ms
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { i ->
            when (i) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> flash = true
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel,
                -> {
                    delay(120)
                    flash = false
                }
            }
        }
    }
    val dangerColor = MaterialTheme.colorScheme.error
    val stroke = when {
        // 按下时描边变天依蓝（复刻旧版交互）
        flash -> PcpColors.AccentPurple
        danger -> dangerColor
        toggleSelected == true -> PcpColors.AccentPurple
        else -> PcpColors.TextSecondary
    }
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .inputBoxBorder(
                radius = 16.dp,
                fill = PcpColors.BgCardSecondary,
                stroke = stroke,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = if (danger) dangerColor else PcpColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 12.sp,
                stepSize = 0.5.sp,
            ),
        )
    }
}

/** 微调按钮（复刻旧版：40dp 高、圆角12dp；图标 + 6dp + 文字紧凑居中，按下边框变天依蓝）。 */
@Composable
private fun MicroButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // 快速点按也要能看到描边闪烁：直接监听交互事件（不依赖重组时机），按下后至少保持 120ms
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { i ->
            when (i) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> flash = true
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel,
                -> {
                    delay(120)
                    flash = false
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .inputBoxBorder(
                radius = 12.dp,
                // 按下仅描边变天依蓝（复刻旧版；不改填充色）
                fill = PcpColors.BgCardSecondary,
                stroke = if (flash) PcpColors.AccentPurple else PcpColors.TextSecondary,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // 图标容器：固定宽度，随组合整体居中——所有按钮图标严格对齐同一列
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(6.dp))
        // 文字：自然宽度（复刻旧版——图标与文字紧凑，无多余空隙）
        Text(
            text,
            color = PcpColors.TextPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 10.sp,
                maxFontSize = 13.sp,
                stepSize = 0.5.sp,
            ),
        )
    }
}

/** 方向键方块（复刻旧版：40dp、圆角12dp；按下边框变天依蓝）。 */
@Composable
private fun SquareBtn(
    @androidx.annotation.DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    // 快速点按也要能看到描边闪烁：直接监听交互事件（不依赖重组时机），按下后至少保持 120ms
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(interaction) {
        interaction.interactions.collectLatest { i ->
            when (i) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> flash = true
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel,
                -> {
                    delay(120)
                    flash = false
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .inputBoxBorder(
                radius = 12.dp,
                // 按下仅描边变天依蓝（复刻旧版；不改填充色）
                fill = PcpColors.BgCardSecondary,
                stroke = if (flash) PcpColors.AccentPurple else PcpColors.TextSecondary,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), null, tint = PcpColors.TextPrimary, modifier = Modifier.size(18.dp))
    }
}

// ==================== 6. 算法选择 ====================

private val ALGORITHMS = listOf(
    PixelAlgorithm.Nearest to R.string.algo_nearest,
    PixelAlgorithm.Bilinear to R.string.algo_bilinear,
    PixelAlgorithm.Bicubic to R.string.algo_bicubic,
    PixelAlgorithm.Lanczos to R.string.algo_lanczos,
    PixelAlgorithm.Average to R.string.algo_average,
    PixelAlgorithm.Custom to R.string.algo_custom,
)

/** 自定义算法可选的内核（不含 Custom / Average）。 */
private val CUSTOM_KERNELS = listOf(
    PixelAlgorithm.Nearest to R.string.algo_nearest,
    PixelAlgorithm.Bilinear to R.string.algo_bilinear,
    PixelAlgorithm.Bicubic to R.string.algo_bicubic,
    PixelAlgorithm.Lanczos to R.string.algo_lanczos,
)

@Composable
private fun AlgorithmCard(
    current: PixelAlgorithm,
    onSelect: (PixelAlgorithm) -> Unit,
    customKernel: PixelAlgorithm,
    customSamples: Int,
    customPreview: (PixelAlgorithm, Int) -> android.graphics.Bitmap?,
    onSelectCustom: (PixelAlgorithm, Int) -> Unit,
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    GlowCard(contentPadding = 16.dp) {
        Text(
            text = stringResource(R.string.algorithm),
            color = PcpColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        // 横向滚动卡片（复刻旧版 HorizontalScrollView + 112×118dp 卡片）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ALGORITHMS.forEach { (algo, labelRes) ->
                AlgorithmItem(
                    algo = algo,
                    labelRes = labelRes,
                    selected = algo == current,
                    descOverride = if (algo == PixelAlgorithm.Custom) {
                        stringResource(
                            R.string.algo_custom_desc_fmt,
                            stringResource(labelResOf(customKernel)),
                            customSamples,
                        )
                    } else {
                        null
                    },
                    onClick = {
                        if (algo == PixelAlgorithm.Custom) {
                            showCustomDialog = true
                        } else {
                            onSelect(algo)
                        }
                    },
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomAlgorithmDialog(
            initialKernel = customKernel,
            initialSamples = customSamples,
            previewProvider = customPreview,
            onDismiss = { showCustomDialog = false },
            onConfirm = { kernel, samples ->
                showCustomDialog = false
                onSelectCustom(kernel, samples)
            },
        )
    }
}

/** 自定义算法设置弹窗：实时预览 + 取色方式 + 平滑程度。 */
@Composable
private fun CustomAlgorithmDialog(
    initialKernel: PixelAlgorithm,
    initialSamples: Int,
    previewProvider: (PixelAlgorithm, Int) -> android.graphics.Bitmap?,
    onDismiss: () -> Unit,
    onConfirm: (PixelAlgorithm, Int) -> Unit,
) {
    var kernel by remember {
        mutableStateOf(if (initialKernel == PixelAlgorithm.Custom) PixelAlgorithm.Bilinear else initialKernel)
    }
    var samples by remember { mutableStateOf(initialSamples.coerceIn(1, 8)) }

    // 实时效果预览：内核 / 平滑程度变化时重新渲染小图
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(kernel, samples) {
        preview = withContext(Dispatchers.Default) { previewProvider(kernel, samples) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_algo_title)) },
        text = {
            Column {
                // 效果预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22000000))
                        .border(1.dp, PcpColors.TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = preview
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            filterQuality = FilterQuality.None,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.custom_preview_empty),
                            fontSize = 11.sp,
                            color = PcpColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 取色方式
                Text(
                    text = stringResource(R.string.custom_kernel_label),
                    fontSize = 13.sp,
                    color = PcpColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                CUSTOM_KERNELS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (k, labelRes) ->
                            KernelChip(
                                label = stringResource(labelRes),
                                selected = k == kernel,
                                modifier = Modifier.weight(1f),
                                onClick = { kernel = k },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // 当前方式说明（通俗描述）
                Text(
                    text = stringResource(kernelDescResOf(kernel)),
                    fontSize = 11.sp,
                    color = PcpColors.TextSecondary,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                // 平滑程度
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.custom_samples_label),
                        fontSize = 13.sp,
                        color = PcpColors.TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$samples ×",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PcpColors.AccentPurple,
                    )
                }
                Slider(
                    value = samples.toFloat(),
                    onValueChange = { samples = it.roundToInt().coerceIn(1, 8) },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                // 两端方向说明
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.custom_samples_sharp),
                        fontSize = 10.sp,
                        color = PcpColors.TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.custom_samples_soft),
                        fontSize = 10.sp,
                        color = PcpColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.custom_samples_hint),
                    fontSize = 11.sp,
                    color = PcpColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(kernel, samples) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 取色方式（内核）的通俗说明。 */
private fun kernelDescResOf(kernel: PixelAlgorithm): Int = when (kernel) {
    PixelAlgorithm.Nearest -> R.string.custom_kernel_desc_nearest
    PixelAlgorithm.Bilinear -> R.string.custom_kernel_desc_bilinear
    PixelAlgorithm.Bicubic -> R.string.custom_kernel_desc_bicubic
    PixelAlgorithm.Lanczos -> R.string.custom_kernel_desc_lanczos
    else -> R.string.custom_kernel_desc_bilinear
}

/** 内核选择小胶囊。 */
@Composable
private fun KernelChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PcpColors.AlgorithmSelectedBg else Color.Transparent)
            .inputBoxBorder(
                radius = 10.dp,
                fill = if (selected) PcpColors.AlgorithmSelectedBg else Color.Transparent,
                stroke = if (selected) PcpColors.AccentPurple else PcpColors.TextSecondary.copy(alpha = 0.5f),
                strokeWidth = if (selected) 1.5.dp else 1.dp,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) PcpColors.AccentPurple else PcpColors.TextPrimary,
        )
    }
}

@Composable
private fun AlgorithmItem(
    algo: PixelAlgorithm,
    labelRes: Int,
    selected: Boolean,
    descOverride: String? = null,
    onClick: () -> Unit,
) {
    // 选中动画：上移 2dp + 放大 1.015（复刻旧版 180ms DecelerateInterpolator）
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.015f else 1f,
        animationSpec = tween(180, easing = PcpDecelerate),
        label = "algoScale",
    )
    val translationY by animateFloatAsState(
        targetValue = if (selected) -2f else 0f, // dp 值，绘制时转 px
        animationSpec = tween(180, easing = PcpDecelerate),
        label = "algoTranslation",
    )
    // 圆点：选中时先放大 1.2 再回弹（复刻旧版 dot 动画：180ms 放大 + 100ms 回弹）
    var dotPulse by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected) {
            dotPulse = true
            kotlinx.coroutines.delay(180)
            dotPulse = false
        }
    }
    val dotScale by animateFloatAsState(
        targetValue = if (dotPulse) 1.2f else 1f,
        animationSpec = tween(if (dotPulse) 180 else 100, easing = PcpDecelerate),
        label = "dotScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.translationY = translationY * density
            }
            .width(112.dp)
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) PcpColors.AlgorithmSelectedBg else Color.Transparent
            )
            .inputBoxBorder(
                radius = 18.dp,
                fill = if (selected) PcpColors.AlgorithmSelectedBg else Color.Transparent,
                stroke = if (selected) PcpColors.AccentPurple else PcpColors.TextSecondary,
                strokeWidth = if (selected) 2.dp else 1.dp,
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                onClick = onClick,
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 圆环单选（18dp，选中：紫环+紫心；未选中：半透明白环）
        Box(
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // 外圈
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .inputBoxBorder(
                            radius = 9.dp,
                            fill = Color.Transparent,
                            stroke = PcpColors.AccentPurple,
                            strokeWidth = 2.dp,
                        ),
                )
                // 中心实心点（10dp）
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(PcpColors.AccentPurple, CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .inputBoxBorder(
                            radius = 9.dp,
                            fill = Color.Transparent,
                            stroke = PcpColors.TextSecondary,
                            strokeWidth = 2.dp,
                        ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(labelRes),
            color = PcpColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 11.sp,
                maxFontSize = 15.sp,
                stepSize = 0.5.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = descOverride ?: stringResource(descResOf(algo)),
            color = PcpColors.TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
        )
    }
}

private fun descResOf(algo: PixelAlgorithm): Int = when (algo) {
    PixelAlgorithm.Nearest -> R.string.algo_nearest_desc
    PixelAlgorithm.Bilinear -> R.string.algo_bilinear_desc
    PixelAlgorithm.Bicubic -> R.string.algo_bicubic_desc
    PixelAlgorithm.Lanczos -> R.string.algo_lanczos_desc
    PixelAlgorithm.Average -> R.string.algo_average_desc
    PixelAlgorithm.Custom -> R.string.algo_custom_desc
}

private fun labelResOf(algo: PixelAlgorithm): Int = when (algo) {
    PixelAlgorithm.Nearest -> R.string.algo_nearest
    PixelAlgorithm.Bilinear -> R.string.algo_bilinear
    PixelAlgorithm.Bicubic -> R.string.algo_bicubic
    PixelAlgorithm.Lanczos -> R.string.algo_lanczos
    PixelAlgorithm.Average -> R.string.algo_average
    PixelAlgorithm.Custom -> R.string.algo_custom
}

// ==================== 7. 生成按钮 ====================

@Composable
private fun GradientButton(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(PcpColors.ButtonGradStart, PcpColors.ButtonGradEnd)
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = PcpColors.TextPrimary.copy(alpha = 0.2f)),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = PcpColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = 18.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

// ==================== 通用：发光卡片容器 ====================

@Composable
private fun GlowCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 12.dp,
    radius: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .glowCardBorder(radius = radius, backgroundColor = PcpColors.BgCardPrimary)
            .padding(contentPadding),
        content = content,
    )
}

// ==================== 对话框（保持不变） ====================

@Composable
private fun NumberInputDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val valid = text.toIntOrNull()?.let { it in 1..999 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(3) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("1 - 999") },
                )
                if (!valid && text.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.size_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let { onConfirm(it.coerceIn(1, 999)) } },
                enabled = valid,
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CustomSizeDialog(
    initialW: Int,
    initialH: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var textW by remember { mutableStateOf(initialW.toString()) }
    var textH by remember { mutableStateOf(initialH.toString()) }
    val w = textW.toIntOrNull()
    val h = textH.toIntOrNull()
    val valid = w != null && h != null && w in 1..999 && h in 1..999

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_size_preset)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = textW,
                        onValueChange = { new -> textW = new.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("W") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Text("×", modifier = Modifier.padding(horizontal = 10.dp))
                    OutlinedTextField(
                        value = textH,
                        onValueChange = { new -> textH = new.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        label = { Text("H") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.size_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (w != null && h != null) onConfirm(w.coerceIn(1, 999), h.coerceIn(1, 999)) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}