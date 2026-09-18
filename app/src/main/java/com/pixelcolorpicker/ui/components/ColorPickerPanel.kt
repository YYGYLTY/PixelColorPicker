package com.pixelcolorpicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.util.ColorUtils

/** 预设调色板：一组常用像素画颜色（第 6 位为天依蓝 66CCFF）。 */
val PRESET_PALETTE: List<Int> = listOf(
    0xFF000000.toInt(), 0xFF3F3F74.toInt(), 0xFF306082.toInt(), 0xFF5B6EE1.toInt(),
    0xFF639BFF.toInt(), 0xFF66CCFF.toInt(), 0xFFCBDBFC.toInt(), 0xFFFFFFFF.toInt(),
    0xFF9BADB7.toInt(), 0xFF847E87.toInt(), 0xFF696A6A.toInt(), 0xFF595652.toInt(),
    0xFF76428A.toInt(), 0xFFAC3232.toInt(), 0xFFD95763.toInt(), 0xFFD77BBA.toInt(),
    0xFF8F974A.toInt(), 0xFF8A6F30.toInt(), 0xFF6ABE30.toInt(), 0xFF37946E.toInt(),
    0xFF4B692F.toInt(), 0xFF524B24.toInt(), 0xFF323C39.toInt(), 0xFF3F3F74.toInt(),
    0xFFDF7126.toInt(), 0xFFD9A066.toInt(), 0xFFEEC39A.toInt(), 0xFFFBF236.toInt(),
    0xFF99E550.toInt(), 0xFF6ABE30.toInt(), 0xFF37946E.toInt(), 0xFF4B692F.toInt(),
)

/**
 * 颜色编辑面板：色相/饱和度/明度滑块 + HEX 输入 + 预设色板。
 *
 * @param color     当前颜色（ARGB）
 * @param onColorChange 颜色变化回调
 */
@Composable
fun ColorPickerPanel(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // HSV 状态独立保存（避免拖动时 HSV↔ARGB 往返转换造成抖动）
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    var hexText by remember { mutableStateOf(ColorUtils.toHexRgb(color)) }

    // 外部颜色变化时同步 HSV（本面板自身发出的变化不回写，防止抖动）
    LaunchedEffect(color) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(color, arr)
        val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        if (current != color) {
            hue = arr[0]
            sat = arr[1]
            value = arr[2]
        }
        hexText = ColorUtils.toHexRgb(color)
    }

    fun emit() {
        // 使用 Android 的 HSV 转换，准确且与系统一致
        val newColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        onColorChange(newColor)
        hexText = ColorUtils.toHexRgb(newColor)
    }

    Column(modifier = modifier) {
        // 当前颜色预览
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = ColorUtils.toHexRgb(color),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "R ${ColorUtils.red(color)}  G ${ColorUtils.green(color)}  B ${ColorUtils.blue(color)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 色相滑块
        SliderRow(
            label = "H",
            value = hue,
            range = 0f..360f,
            gradient = Brush.horizontalGradient(
                listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                )
            ),
            onValueChange = { hue = it; emit() },
        )
        // 饱和度滑块
        SliderRow(
            label = "S",
            value = sat,
            range = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value))),
                )
            ),
            onValueChange = { sat = it; emit() },
        )
        // 明度滑块
        SliderRow(
            label = "V",
            value = value,
            range = 0f..1f,
            gradient = Brush.horizontalGradient(
                listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))),
            ),
            onValueChange = { value = it; emit() },
        )

        Spacer(Modifier.height(8.dp))

        // HEX 输入
        OutlinedTextField(
            value = hexText,
            onValueChange = { input ->
                hexText = input
                parseHex(input)?.let { onColorChange(it) }
            },
            label = { Text("HEX") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { parseHex(hexText)?.let { onColorChange(it) } }),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "预设",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PRESET_PALETTE) { preset ->
                ColorSwatch(
                    color = preset,
                    selected = preset == color,
                    onClick = {
                        onColorChange(preset)
                        hexText = ColorUtils.toHexRgb(preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    gradient: Brush,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(16.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(gradient)
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 单个颜色色块。 */
@Composable
fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    size: Int = 36,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
    )
}

/** 解析 #RRGGBB / #AARRGGBB / RRGGBB。失败返回 null。 */
private fun parseHex(input: String): Int? {
    val cleaned = input.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> (0xFF000000.toInt()) or cleaned.toLong(16).toInt()
            8 -> cleaned.toLong(16).toInt()
            else -> null
        }
    } catch (t: Throwable) {
        null
    }
}