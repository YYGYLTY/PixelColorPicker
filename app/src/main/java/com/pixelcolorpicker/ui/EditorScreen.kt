package com.pixelcolorpicker.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.ui.components.ColorPickerPanel
import com.pixelcolorpicker.ui.components.PixelCanvas
import com.pixelcolorpicker.ui.icons.AppIcons
import com.pixelcolorpicker.ui.theme.MonoStyle
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.util.ColorUtils
import com.pixelcolorpicker.util.PngExporter

/**
 * 空白画布编辑器：
 *  - 任意 W×H 像素网格，画笔 / 橡皮 / 吸管 / 填充
 *  - 撤销 / 重做 / 清空 / 全部填充
 *  - 导出 PNG
 *  - 「添加图片到首页」：选一张图片带回首页展示框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val art = viewModel.editorArt
    val selected = viewModel.editorSelected

    var showColorSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 系统返回键：有修改才弹「是否保存」对话框
    BackHandler {
        if (viewModel.editorNeedsSavePrompt()) showExitDialog = true else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.editorNeedsSavePrompt()) showExitDialog = true else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.editorUndo() },
                        enabled = art.canUndo,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo), modifier = Modifier.size(24.dp))
                    }
                    IconButton(
                        onClick = { viewModel.editorRedo() },
                        enabled = art.canRedo,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo), modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { viewModel.openHistory() }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.editor_history), modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        // 画布视图控制（滑块）：极端比例画布（如 1×999 / 999×1）难以用双指精确导航，提供滑块
        var canvasScale by remember { mutableFloatStateOf(1f) }
        // 0 = 顶部/最左（第 1 行/列），1 = 底部/最右（最后一行/列）
        var canvasOffsetX by remember { mutableFloatStateOf(0f) }
        var canvasOffsetY by remember { mutableFloatStateOf(0f) }
        val extremeAspect = art.width > 0 && art.height > 0 &&
            (art.width.toFloat() / art.height.toFloat() > 6f || art.height.toFloat() / art.width.toFloat() > 6f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ---------- 固定画布区（不随下方功能滚动） ----------
            // 极端比例（如 1×999）时画布按比例会变成一条线，因此限制其最大高度，
            // 让下方的导航滑块始终可见可用。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .then(
                        if (extremeAspect) Modifier.height(220.dp) else Modifier,
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp),
            ) {
                PixelCanvas(
                    pixelArt = art,
                    selectedIndex = selected,
                    contentDescription = stringResource(R.string.cd_pixel_canvas),
                    onPixelTouched = { x, y -> viewModel.editorTouch(x, y) },
                    onPixelSelected = { x, y -> viewModel.editorSelect(x, y) },
                    onStrokeStart = { viewModel.editorStrokeStart() },
                    onStrokeEnd = { viewModel.editorStrokeEnd() },
                    externalScale = if (extremeAspect) canvasScale else null,
                    externalOffset = if (extremeAspect) Offset(canvasOffsetX, canvasOffsetY) else null,
                    fillHeight = extremeAspect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                )
            }

            // ---------- 极端比例画布：滑块导航（缩放 + 水平/垂直滚动，显示当前行列） ----------
            if (extremeAspect) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                ) {
                    // 缩放：格子大小倍数
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.editor_canvas_zoom),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Slider(
                            value = canvasScale,
                            onValueChange = { canvasScale = it },
                            valueRange = 1f..16f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(canvasScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                    // 水平滚动：显示当前列（只有 1 列时无需滚动 → 滑块满格，表示"已看到全部"）
                    val canScrollX = art.width > 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.editor_pan_x),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Slider(
                            value = if (canScrollX) canvasOffsetX else 1f,
                            onValueChange = { canvasOffsetX = it },
                            valueRange = 0f..1f,
                            enabled = canScrollX,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(canvasOffsetX * (art.width - 1)).toInt() + 1}/${art.width}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                    // 垂直滚动：显示当前行（只有 1 行时无需滚动 → 滑块满格）
                    val canScrollY = art.height > 1
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.editor_pan_y),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp),
                        )
                        Slider(
                            value = if (canScrollY) canvasOffsetY else 1f,
                            onValueChange = { canvasOffsetY = it },
                            valueRange = 0f..1f,
                            enabled = canScrollY,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(canvasOffsetY * (art.height - 1)).toInt() + 1}/${art.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(48.dp),
                        )
                    }
                }
            }

            // ---------- 下方功能区（上划时收到固定框后面） ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                // 工具
                ToolBar(
                    currentTool = viewModel.editorTool,
                    onToolSelected = { viewModel.editorSetTool(it) },
                )

                Spacer(Modifier.height(16.dp))

                // 自定义色板（+ 添加当前颜色；长按色块删除；长期保存）
                EditorPaletteRow(
                    colors = viewModel.customPalette,
                    current = viewModel.editorColor,
                    onPick = { viewModel.editorSetColor(it) },
                    onAdd = { viewModel.addEditorPaletteColor() },
                    onRemove = { viewModel.removeEditorPaletteColor(it) },
                )

                Spacer(Modifier.height(16.dp))

                // 颜色信息
                ColorInfoCard(
                    selectedIndex = selected,
                    art = art,
                    currentColor = viewModel.editorColor,
                    onPickColor = { showColorSheet = true },
                )

                Spacer(Modifier.height(16.dp))

                // 仅添加图片到首页（不写保存历史，点击后直接回首页）
                Button(
                    onClick = {
                        viewModel.addEditorArtToHome()
                        Toast.makeText(context, context.getString(R.string.editor_added_to_home), Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.editor_add_to_home),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 主操作：保存图片（保存到首页展示 + 记录历史，不退出编辑器）
                Button(
                    onClick = {
                        onSave()
                        Toast.makeText(context, context.getString(R.string.editor_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.editor_save),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            val uri = PngExporter.exportPng(
                                context = context,
                                pixels = art.snapshot(),
                                width = art.width,
                                height = art.height,
                                fileName = "pixel_art_${System.currentTimeMillis()}.png",
                            )
                            val msg = if (uri != null) R.string.saved_png else R.string.save_failed
                            Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.editor_export_png),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.editorFillAll()
                            Toast.makeText(context, context.getString(R.string.toast_filled), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.editor_fill_all),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.editor_clear),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 颜色选择面板
    if (showColorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showColorSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.editor_custom_color),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                ColorPickerPanel(
                    color = viewModel.editorColor,
                    onColorChange = { viewModel.editorSetColor(it) },
                )
                Spacer(Modifier.height(24.dp))
            }
            }
        }

    // 清空确认
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = { Text(stringResource(R.string.dialog_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editorClear()
                    showClearDialog = false
                    Toast.makeText(context, context.getString(R.string.toast_cleared), Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // 退出确认：是否保存（保存并退出 / 不保存退出）
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.editor_exit_title)) },
            text = { Text(stringResource(R.string.editor_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onSave()
                    onBack()
                }) {
                    Text(stringResource(R.string.editor_exit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.editor_exit_discard))
                }
            },
        )
    }

    }

@Composable
private fun ToolBar(
    currentTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
) {
    val tools: List<Triple<EditorTool, ImageVector, Int>> = listOf(
        Triple(EditorTool.Pencil, Icons.Default.Edit, R.string.editor_tool_pencil),
        Triple(EditorTool.Eraser, Icons.Default.AutoFixNormal, R.string.editor_tool_eraser),
        Triple(EditorTool.Eyedropper, Icons.Default.Colorize, R.string.editor_tool_eyedropper),
        Triple(EditorTool.Fill, Icons.Default.FormatColorFill, R.string.editor_tool_fill),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tools.forEach { (tool, icon, labelRes) ->
            val selected = tool == currentTool
            val containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val interaction = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(containerColor)
                    .clickable(interactionSource = interaction, indication = null) { onToolSelected(tool) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                )
            }
        }
    }
}

/** 自定义色板行：+ 添加当前颜色；点选颜色；长按删除。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EditorPaletteRow(
    colors: List<Int>,
    current: Int,
    onPick: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val addInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PcpColors.BgCardPrimary)
                .border(1.dp, PcpColors.AccentPurple.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .clickable(interactionSource = addInteraction, indication = null, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.editor_palette_add),
                tint = PcpColors.AccentPurple,
                modifier = Modifier.size(18.dp),
            )
        }
        colors.forEach { c ->
            val selected = c == current
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(c))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) PcpColors.AccentPurple else Color(0x40FFFFFF),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .combinedClickable(
                        onClick = { onPick(c) },
                        onLongClick = { onRemove(c) },
                    ),
            )
        }
    }
}

@Composable
private fun ColorInfoCard(
    selectedIndex: Int,
    art: com.pixelcolorpicker.model.PixelArt,
    currentColor: Int,
    onPickColor: () -> Unit,
) {
    val hasSelection = selectedIndex in 0 until art.width * art.height
    val selectedColor = if (hasSelection) art.get(selectedIndex) else currentColor
    val x = if (hasSelection) selectedIndex % art.width else -1
    val y = if (hasSelection) selectedIndex / art.width else -1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(currentColor))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = interaction, indication = null, onClick = onPickColor)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.editor_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = ColorUtils.toHexRgb(currentColor),
                    style = MonoStyle,
                    fontWeight = FontWeight.Bold,
                )
            }
            FilledTonalButton(onClick = onPickColor) {
                Text(stringResource(R.string.editor_custom_color))
            }
        }

        Spacer(Modifier.height(12.dp))

        if (hasSelection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(selectedColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${stringResource(R.string.editor_coord)}: ($x, $y)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${ColorUtils.toHexRgb(selectedColor)}  A=${ColorUtils.alpha(selectedColor)}",
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}