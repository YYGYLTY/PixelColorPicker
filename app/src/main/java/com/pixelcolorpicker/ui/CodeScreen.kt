package com.pixelcolorpicker.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.codec.PaletteCodec
import com.pixelcolorpicker.model.PixelGrid
import com.pixelcolorpicker.ui.components.EmptyState
import com.pixelcolorpicker.ui.components.QuickBar
import kotlinx.coroutines.launch

/**
 * 编码页：按列生成「18 色板码」（16 个取样像素 + 2 个白色），
 * 与旧版 PixelColorPicker 的 `exportColumn` 完全兼容。
 */
@Composable
fun CodeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.isPixelated, viewModel.gridDirty) {
        if (viewModel.isPixelated) {
            viewModel.ensurePixelGrid()
        }
    }

    val grid = viewModel.pixelGrid

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.code_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.code_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !viewModel.isPixelated -> {
                    EmptyState(
                        icon = ImageVector.vectorResource(R.drawable.old_ic_code),
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
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 44.dp, bottom = 96.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(grid.width) { column ->
                                ColumnCard(
                                    grid = grid,
                                    column = column,
                                    onCopy = {
                                        val colors = PaletteCodec.sampleColumnColors(
                                            grid.pixels, grid.width, grid.height, column,
                                        )
                                        val code = PaletteCodec.encodeColumn(colors)
                                        copyToClipboard(context, code)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.code_copied, column + 1),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                        // 右侧快速定位滑块
                        QuickBar(
                            fraction = {
                                val info = listState.layoutInfo
                                val total = info.totalItemsCount
                                val visible = info.visibleItemsInfo.size
                                val maxFirst = (total - visible).coerceAtLeast(0)
                                if (maxFirst <= 0) 0f
                                else (listState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
                            },
                            thumbFraction = {
                                val total = grid.width
                                val visible = listState.layoutInfo.visibleItemsInfo.size
                                if (total <= 0) 1f else (visible.toFloat() / total).coerceIn(0.05f, 1f)
                            },
                            onSeek = { ratio ->
                                val total = grid.width
                                scope.launch {
                                    // 直接映射到「最后一列」：LazyColumn 会自动收敛到真实底部（含底部留白）
                                    listState.scrollToItem(
                                        (ratio * (total - 1)).toInt().coerceIn(0, total - 1)
                                    )
                                }
                            },
                            vertical = true,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(20.dp)
                                .padding(top = 4.dp, bottom = 110.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnCard(
    grid: PixelGrid,
    column: Int,
    onCopy: () -> Unit,
) {
    // 取样的 16 个颜色（与编码一致）
    val colors = remember16(grid, column)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.code_column, column + 1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            // 16 个取样颜色小方块
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(c)),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onCopy,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(42.dp),
        ) {
            Text(stringResource(R.string.code_copy))
        }
    }
}

@Composable
private fun remember16(grid: PixelGrid, column: Int): IntArray {
    return androidx.compose.runtime.remember(grid, column) {
        PaletteCodec.sampleColumnColors(grid.pixels, grid.width, grid.height, column)
    }
}