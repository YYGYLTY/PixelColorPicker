package com.pixelcolorpicker.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * PixelColorPicker 自绘图标集。
 *
 * 全部为手写的圆角线性图标（24x24 viewport，描边 2dp，圆头端点），
 * 与旧版无关，为本次重制专门设计。
 */
object AppIcons {

    /** 在 PathBuilder 中画一个圆（两段半圆弧）。 */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        close()
    }

    private fun builder(name: String): ImageVector.Builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )

    private fun ImageVector.Builder.strokePath(pathBuilder: PathBuilder.() -> Unit) = path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )

    private fun ImageVector.Builder.fillPath(pathBuilder: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.White),
        pathBuilder = pathBuilder,
    )

    /** 首页：圆角房子。 */
    val Home: ImageVector by lazy {
        builder("PcpHome").apply {
            strokePath {
                moveTo(3.6f, 10.8f)
                lineTo(12f, 4.2f)
                lineTo(20.4f, 10.8f)
                moveTo(6f, 9.6f)
                lineTo(6f, 19.6f)
                lineTo(18f, 19.6f)
                lineTo(18f, 9.6f)
                moveTo(10.2f, 19.6f)
                lineTo(10.2f, 14.2f)
                lineTo(13.8f, 14.2f)
                lineTo(13.8f, 19.6f)
            }
        }.build()
    }

    /** 色板：调色盘 + 色点。 */
    val Palette: ImageVector by lazy {
        builder("PcpPalette").apply {
            strokePath {
                circle(12f, 12f, 8.4f)
            }
            fillPath {
                circle(9f, 9.4f, 1.5f)
                circle(15f, 9.4f, 1.5f)
                circle(9f, 15f, 1.5f)
                circle(15.2f, 15.2f, 1.5f)
            }
        }.build()
    }

    /** 编码：尖括号 + 斜杠。 */
    val Code: ImageVector by lazy {
        builder("PcpCode").apply {
            strokePath {
                moveTo(8.6f, 6.4f)
                lineTo(4.2f, 12f)
                lineTo(8.6f, 17.6f)
                moveTo(15.4f, 6.4f)
                lineTo(19.8f, 12f)
                lineTo(15.4f, 17.6f)
                moveTo(13.4f, 5.4f)
                lineTo(10.6f, 18.6f)
            }
        }.build()
    }

    /** 关于：信息圆。 */
    val About: ImageVector by lazy {
        builder("PcpAbout").apply {
            strokePath {
                circle(12f, 12f, 8.4f)
                moveTo(12f, 11f)
                lineTo(12f, 16.4f)
            }
            fillPath {
                circle(12f, 8f, 1.3f)
            }
        }.build()
    }

    /** 地球：语言切换。 */
    val Globe: ImageVector by lazy {
        builder("PcpGlobe").apply {
            strokePath {
                circle(12f, 12f, 8.4f)
                moveTo(3.6f, 12f)
                lineTo(20.4f, 12f)
                // 经线（两段半椭圆）
                moveTo(12f, 3.6f)
                arcTo(4.8f, 8.4f, 0f, false, true, 12f, 20.4f)
                arcTo(4.8f, 8.4f, 0f, false, true, 12f, 3.6f)
            }
        }.build()
    }

    /** 网格显示：2x2 方块。 */
    val Grid: ImageVector by lazy {
        builder("PcpGrid").apply {
            strokePath {
                moveTo(4.4f, 4.4f)
                lineTo(10f, 4.4f)
                lineTo(10f, 10f)
                lineTo(4.4f, 10f)
                close()
                moveTo(14f, 4.4f)
                lineTo(19.6f, 4.4f)
                lineTo(19.6f, 10f)
                lineTo(14f, 10f)
                close()
                moveTo(4.4f, 14f)
                lineTo(10f, 14f)
                lineTo(10f, 19.6f)
                lineTo(4.4f, 19.6f)
                close()
                moveTo(14f, 14f)
                lineTo(19.6f, 14f)
                lineTo(19.6f, 19.6f)
                lineTo(14f, 19.6f)
                close()
            }
        }.build()
    }

    /** 新建空白画布：空白方块 + 加号。 */
    val BlankCanvas: ImageVector by lazy {
        builder("PcpBlankCanvas").apply {
            strokePath {
                moveTo(4.6f, 5.2f)
                lineTo(13.4f, 5.2f)
                lineTo(13.4f, 14f)
                lineTo(4.6f, 14f)
                close()
                moveTo(17.4f, 13.2f)
                lineTo(17.4f, 19.2f)
                moveTo(14.4f, 16.2f)
                lineTo(20.4f, 16.2f)
            }
        }.build()
    }

    /** 图片：圆角矩形 + 山 + 太阳。 */
    val Image: ImageVector by lazy {
        builder("PcpImage").apply {
            strokePath {
                moveTo(6f, 4.8f)
                lineTo(18f, 4.8f)
                arcTo(3.2f, 3.2f, 0f, false, true, 21.2f, 8f)
                lineTo(21.2f, 16f)
                arcTo(3.2f, 3.2f, 0f, false, true, 18f, 19.2f)
                lineTo(6f, 19.2f)
                arcTo(3.2f, 3.2f, 0f, false, true, 2.8f, 16f)
                lineTo(2.8f, 8f)
                arcTo(3.2f, 3.2f, 0f, false, true, 6f, 4.8f)
                close()
                // 山
                moveTo(5.4f, 16.2f)
                lineTo(10f, 11.4f)
                lineTo(13.2f, 14.6f)
                lineTo(15.4f, 12.6f)
                lineTo(18.6f, 15.8f)
            }
            fillPath {
                circle(8.4f, 9f, 1.4f)
            }
        }.build()
    }

    /** 画笔。 */
    val Brush: ImageVector by lazy {
        builder("PcpBrush").apply {
            strokePath {
                moveTo(4.6f, 19.4f)
                curveTo(6.2f, 15.4f, 7.4f, 13.4f, 9.2f, 11.6f)
                lineTo(14.8f, 6f)
                curveTo(15.8f, 5f, 17.4f, 5f, 18.4f, 6f)
                curveTo(19.4f, 7f, 19.4f, 8.6f, 18.4f, 9.6f)
                lineTo(12.8f, 15.2f)
                curveTo(11f, 17f, 9f, 18.2f, 4.6f, 19.4f)
                close()
            }
        }.build()
    }

    /** 橡皮。 */
    val Eraser: ImageVector by lazy {
        builder("PcpEraser").apply {
            strokePath {
                moveTo(8.8f, 18.6f)
                lineTo(4.8f, 14.6f)
                curveTo(4f, 13.8f, 4f, 12.6f, 4.8f, 11.8f)
                lineTo(12.8f, 3.8f)
                curveTo(13.6f, 3f, 14.8f, 3f, 15.6f, 3.8f)
                lineTo(19.6f, 7.8f)
                curveTo(20.4f, 8.6f, 20.4f, 9.8f, 19.6f, 10.6f)
                lineTo(11.6f, 18.6f)
                close()
                moveTo(9f, 7.6f)
                lineTo(16.4f, 15f)
                moveTo(8.6f, 19.6f)
                lineTo(19.8f, 19.6f)
            }
        }.build()
    }

    /** 吸管。 */
    val Dropper: ImageVector by lazy {
        builder("PcpDropper").apply {
            strokePath {
                moveTo(4.2f, 19.8f)
                lineTo(4.2f, 16.4f)
                lineTo(12.4f, 8.2f)
                moveTo(10.6f, 6.4f)
                lineTo(14f, 3f)
                curveTo(14.8f, 2.2f, 16f, 2.2f, 16.8f, 3f)
                lineTo(21f, 7.2f)
                curveTo(21.8f, 8f, 21.8f, 9.2f, 21f, 10f)
                lineTo(17.6f, 13.4f)
                close()
                moveTo(13.4f, 9.4f)
                lineTo(17.6f, 13.6f)
            }
        }.build()
    }

    /** 填充（油漆桶）。 */
    val Fill: ImageVector by lazy {
        builder("PcpFill").apply {
            strokePath {
                moveTo(7.6f, 4.4f)
                lineTo(16.6f, 13.4f)
                curveTo(17.4f, 14.2f, 17.4f, 15.4f, 16.6f, 16.2f)
                lineTo(11.4f, 21.4f)
                curveTo(10.6f, 22.2f, 9.4f, 22.2f, 8.6f, 21.4f)
                lineTo(2.8f, 15.6f)
                curveTo(2f, 14.8f, 2f, 13.6f, 2.8f, 12.8f)
                close()
                moveTo(4.4f, 14f)
                lineTo(15f, 14f)
                moveTo(20.6f, 15.4f)
                curveTo(21.8f, 17f, 22.2f, 18.2f, 22.2f, 19f)
                curveTo(22.2f, 20.1f, 21.3f, 21f, 20.2f, 21f)
                curveTo(19.1f, 21f, 18.2f, 20.1f, 18.2f, 19f)
                curveTo(18.2f, 18.2f, 18.6f, 17f, 19.8f, 15.4f)
                close()
            }
        }.build()
    }

    /** 放大。 */
    val ZoomIn: ImageVector by lazy {
        builder("PcpZoomIn").apply {
            strokePath {
                circle(10.6f, 10.6f, 6.8f)
                moveTo(15.8f, 15.8f)
                lineTo(20.6f, 20.6f)
                moveTo(10.6f, 7.6f)
                lineTo(10.6f, 13.6f)
                moveTo(7.6f, 10.6f)
                lineTo(13.6f, 10.6f)
            }
        }.build()
    }

    /** 缩小。 */
    val ZoomOut: ImageVector by lazy {
        builder("PcpZoomOut").apply {
            strokePath {
                circle(10.6f, 10.6f, 6.8f)
                moveTo(15.8f, 15.8f)
                lineTo(20.6f, 20.6f)
                moveTo(7.6f, 10.6f)
                lineTo(13.6f, 10.6f)
            }
        }.build()
    }

    /** 逆时针旋转。 */
    val RotateCcw: ImageVector by lazy {
        builder("PcpRotateCcw").apply {
            strokePath {
                // 大弧（从左侧逆时针 230°）
                moveTo(4.4f, 11.4f)
                arcTo(7.6f, 7.6f, 0f, true, false, 16.9f, 5.6f)
                // 箭头
                moveTo(4.4f, 11.4f)
                lineTo(4.4f, 6.2f)
                moveTo(4.4f, 11.4f)
                lineTo(9.6f, 11.4f)
            }
        }.build()
    }

    /** 顺时针旋转。 */
    val RotateCw: ImageVector by lazy {
        builder("PcpRotateCw").apply {
            strokePath {
                moveTo(19.6f, 11.4f)
                arcTo(7.6f, 7.6f, 0f, true, true, 7.1f, 5.6f)
                moveTo(19.6f, 11.4f)
                lineTo(19.6f, 6.2f)
                moveTo(19.6f, 11.4f)
                lineTo(14.4f, 11.4f)
            }
        }.build()
    }

    /** 重置（与逆时针同款）。 */
    val Reset: ImageVector by lazy { RotateCcw }

    /** 上箭头。 */
    val ArrowUp: ImageVector by lazy {
        builder("PcpArrowUp").apply {
            strokePath {
                moveTo(12f, 19f)
                lineTo(12f, 5.6f)
                moveTo(6.4f, 11.2f)
                lineTo(12f, 5.6f)
                lineTo(17.6f, 11.2f)
            }
        }.build()
    }

    /** 下箭头。 */
    val ArrowDown: ImageVector by lazy {
        builder("PcpArrowDown").apply {
            strokePath {
                moveTo(12f, 5f)
                lineTo(12f, 18.4f)
                moveTo(6.4f, 12.8f)
                lineTo(12f, 18.4f)
                lineTo(17.6f, 12.8f)
            }
        }.build()
    }

    /** 左箭头。 */
    val ArrowLeft: ImageVector by lazy {
        builder("PcpArrowLeft").apply {
            strokePath {
                moveTo(19f, 12f)
                lineTo(5.6f, 12f)
                moveTo(11.2f, 6.4f)
                lineTo(5.6f, 12f)
                lineTo(11.2f, 17.6f)
            }
        }.build()
    }

    /** 右箭头。 */
    val ArrowRight: ImageVector by lazy {
        builder("PcpArrowRight").apply {
            strokePath {
                moveTo(5f, 12f)
                lineTo(18.4f, 12f)
                moveTo(12.8f, 6.4f)
                lineTo(18.4f, 12f)
                lineTo(12.8f, 17.6f)
            }
        }.build()
    }

    /** 复制。 */
    val Copy: ImageVector by lazy {
        builder("PcpCopy").apply {
            strokePath {
                moveTo(9.4f, 9.4f)
                lineTo(19.4f, 9.4f)
                lineTo(19.4f, 19.4f)
                lineTo(9.4f, 19.4f)
                close()
                moveTo(6.6f, 14.6f)
                lineTo(4.6f, 14.6f)
                lineTo(4.6f, 4.6f)
                lineTo(14.6f, 4.6f)
                lineTo(14.6f, 6.6f)
            }
        }.build()
    }

    /** 保存。 */
    val Save: ImageVector by lazy {
        builder("PcpSave").apply {
            strokePath {
                moveTo(12f, 3.8f)
                lineTo(12f, 14.2f)
                moveTo(7.8f, 10.4f)
                lineTo(12f, 14.6f)
                lineTo(16.2f, 10.4f)
                moveTo(4.4f, 16.4f)
                lineTo(4.4f, 18.6f)
                arcTo(2.2f, 2.2f, 0f, false, true, 6.6f, 20.8f)
                lineTo(17.4f, 20.8f)
                arcTo(2.2f, 2.2f, 0f, false, true, 19.6f, 18.6f)
                lineTo(19.6f, 16.4f)
            }
        }.build()
    }

    /** 生成/魔法棒。 */
    val Magic: ImageVector by lazy {
        builder("PcpMagic").apply {
            strokePath {
                moveTo(5f, 19f)
                lineTo(15.4f, 8.6f)
                moveTo(13.2f, 6.4f)
                lineTo(17.6f, 10.8f)
                moveTo(6.6f, 4.6f)
                lineTo(6.6f, 8.6f)
                moveTo(4.6f, 6.6f)
                lineTo(8.6f, 6.6f)
                moveTo(17.8f, 13.8f)
                lineTo(17.8f, 17.4f)
                moveTo(16f, 15.6f)
                lineTo(19.6f, 15.6f)
            }
        }.build()
    }

    /** 加号。 */
    val Add: ImageVector by lazy {
        builder("PcpAdd").apply {
            strokePath {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    /** 对勾。 */
    val Check: ImageVector by lazy {
        builder("PcpCheck").apply {
            strokePath {
                moveTo(5f, 12.6f)
                lineTo(10f, 17.4f)
                lineTo(19f, 6.6f)
            }
        }.build()
    }

    /** 返回箭头。 */
    val Back: ImageVector by lazy {
        builder("PcpBack").apply {
            strokePath {
                moveTo(19f, 12f)
                lineTo(5.6f, 12f)
                moveTo(11.2f, 5.6f)
                lineTo(4.8f, 12f)
                lineTo(11.2f, 18.4f)
            }
        }.build()
    }

    /** 撤销。 */
    val Undo: ImageVector by lazy {
        builder("PcpUndo").apply {
            strokePath {
                moveTo(4.4f, 11.4f)
                arcTo(7.6f, 7.6f, 0f, true, false, 16.9f, 5.6f)
                moveTo(4.4f, 11.4f)
                lineTo(4.4f, 6.2f)
                moveTo(4.4f, 11.4f)
                lineTo(9.6f, 11.4f)
            }
        }.build()
    }

    /** 重做。 */
    val Redo: ImageVector by lazy {
        builder("PcpRedo").apply {
            strokePath {
                moveTo(19.6f, 11.4f)
                arcTo(7.6f, 7.6f, 0f, true, true, 7.1f, 5.6f)
                moveTo(19.6f, 11.4f)
                lineTo(19.6f, 6.2f)
                moveTo(19.6f, 11.4f)
                lineTo(14.4f, 11.4f)
            }
        }.build()
    }

    /** 交换（上下双箭头）。 */
    val Swap: ImageVector by lazy {
        builder("PcpSwap").apply {
            strokePath {
                // 上箭头（向右）
                moveTo(4f, 8.4f)
                lineTo(19f, 8.4f)
                moveTo(15.4f, 4.8f)
                lineTo(19f, 8.4f)
                lineTo(15.4f, 12f)
                // 下箭头（向左）
                moveTo(20f, 15.6f)
                lineTo(5f, 15.6f)
                moveTo(8.6f, 12f)
                lineTo(5f, 15.6f)
                lineTo(8.6f, 19.2f)
            }
        }.build()
    }

    /** 减号。 */
    val Remove: ImageVector by lazy {
        builder("PcpRemove").apply {
            strokePath {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }
}