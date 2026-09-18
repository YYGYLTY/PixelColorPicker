package com.pixelcolorpicker.image

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/** 像素化算法。 */
enum class PixelAlgorithm {
    /** 最近邻：保留像素，清晰锐利。 */
    Nearest,

    /** 双线性：平滑过滤。 */
    Bilinear,

    /** 双三次：平衡效果，细节好。 */
    Bicubic,

    /** Lanczos：高质量缩放，细节最好。 */
    Lanczos,

    /** 区域平均：适合照片。 */
    Average,

    /** 自定义：采样内核 + 采样密度可调。 */
    Custom,
}

/**
 * 像素化引擎：把「裁剪后的图片 + 变换（缩放/平移/旋转）」按目标尺寸重采样。
 *
 * 实现为「自适应超采样 + 核函数插值」：
 *  - 每个输出像素根据当前缩放比例计算其在源图上的覆盖范围（footprint）；
 *  - 在覆盖范围内取 N×N 个采样点；
 *  - 每个采样点使用对应算法（最近邻/双线性/双三次/Lanczos）取值；
 *  - 对采样结果求平均。
 *
 * 这样在缩小时不会只取中心点（避免锯齿/丢细节），放大时也能得到对应算法风格。
 */
object Pixelator {

    /**
     * 执行像素化。
     *
     * @param src      源像素（行优先 ARGB）
     * @param srcW     源宽
     * @param srcH     源高
     * @param outW     输出宽
     * @param outH     输出高
     * @param scale    用户缩放（1 = 展示框完整显示源图）
     * @param offsetX  水平平移（占源宽比例）
     * @param offsetY  垂直平移（占源高比例）
     * @param rotationDeg 旋转角度（度）
     * @param algorithm 算法
     * @return 输出像素数组（outW * outH）
     */
    fun pixelate(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        outW: Int,
        outH: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotationDeg: Float,
        algorithm: PixelAlgorithm,
        customKernel: PixelAlgorithm = PixelAlgorithm.Bilinear,
        customSamples: Int = 4,
    ): IntArray {
        require(src.size >= srcW * srcH)
        require(outW >= 1 && outH >= 1)

        // 自定义算法：实际使用「内核 + 采样密度」
        val kernel = if (algorithm == PixelAlgorithm.Custom) {
            if (customKernel == PixelAlgorithm.Custom) PixelAlgorithm.Bilinear else customKernel
        } else {
            algorithm
        }

        // 旧版兼容路径：Nearest + 无旋转 + 无平移 + 未缩放
        // → 直接调用 Android createScaledBitmap(filter=false)，与旧版/主流实现结果完全一致。
        val singleNearest = algorithm == PixelAlgorithm.Nearest ||
            (algorithm == PixelAlgorithm.Custom &&
                customKernel == PixelAlgorithm.Nearest && customSamples <= 1)
        if (singleNearest &&
            rotationDeg == 0f && offsetX == 0f && offsetY == 0f && scale == 1f
        ) {
            return nearestViaAndroid(src, srcW, srcH, outW, outH)
        }

        val out = IntArray(outW * outH)

        val safeScale = scale.coerceAtLeast(0.01f)
        val rad = Math.toRadians(-rotationDeg.toDouble())
        val cosT = cos(rad).toFloat()
        val sinT = sin(rad).toFloat()

        // 每个输出像素在源图上的覆盖范围（像素单位）
        val fw = srcW / (safeScale * outW)
        val fh = srcH / (safeScale * outH)

        // 采样网格数量：覆盖范围越大，取样越多（上限 6x6）；自定义算法用用户设定的密度
        val gridN = if (algorithm == PixelAlgorithm.Custom) {
            customSamples.coerceIn(1, 8)
        } else when (algorithm) {
            PixelAlgorithm.Nearest -> 1
            PixelAlgorithm.Bilinear -> 2
            PixelAlgorithm.Bicubic -> 3
            PixelAlgorithm.Lanczos -> 4
            PixelAlgorithm.Average -> 6
            PixelAlgorithm.Custom -> 8 // 已在上方分支处理
        }

        // 采样网格数量：默认算法按覆盖范围自适应（每轴最多 gridN），避免小覆盖时过度采样；
        // 自定义算法则严格按用户设定的密度采样（让「采样密度」调节真正可感知）。
        val nx: Int
        val ny: Int
        if (algorithm == PixelAlgorithm.Custom) {
            nx = gridN
            ny = gridN
        } else {
            nx = ceil(fw).toInt().coerceIn(1, gridN)
            ny = ceil(fh).toInt().coerceIn(1, gridN)
        }

        val srcCenterX = srcW / 2f + offsetX * srcW
        val srcCenterY = srcH / 2f + offsetY * srcH

        for (j in 0 until outH) {
            for (i in 0 until outW) {
                // 统一使用「格中心映射」（与 Android createScaledBitmap 的采样中心一致）
                val u = (i + 0.5f) / outW
                val v = (j + 0.5f) / outH

                // 目标点相对中心
                val lx = (u - 0.5f) * srcW
                val ly = (v - 0.5f) * srcH

                // 逆旋转
                val rx = lx * cosT - ly * sinT
                val ry = lx * sinT + ly * cosT

                // 逆缩放 + 中心/平移
                val cx = srcCenterX + rx / safeScale
                val cy = srcCenterY + ry / safeScale

                if (kernel == PixelAlgorithm.Nearest) {
                    out[j * outW + i] = sampleNearest(src, srcW, srcH, cx, cy)
                    continue
                }

                // 采样网格（按覆盖范围自适应；覆盖小于 1 像素时退化为单点）
                var accR = 0L; var accG = 0L; var accB = 0L; var accA = 0L
                var count = 0
                for (gy in 0 until ny) {
                    val oy = if (ny == 1) 0f else (((gy + 0.5f) / ny) - 0.5f) * fh
                    for (gx in 0 until nx) {
                        val ox = if (nx == 1) 0f else (((gx + 0.5f) / nx) - 0.5f) * fw
                        val c = sample(src, srcW, srcH, cx + ox, cy + oy, kernel)
                        accA += (c ushr 24) and 0xFF
                        accR += (c ushr 16) and 0xFF
                        accG += (c ushr 8) and 0xFF
                        accB += c and 0xFF
                        count++
                    }
                }
                out[j * outW + i] = if (count == 0) {
                    0
                } else {
                    (((accA / count).toInt() and 0xFF) shl 24) or
                        (((accR / count).toInt() and 0xFF) shl 16) or
                        (((accG / count).toInt() and 0xFF) shl 8) or
                        ((accB / count).toInt() and 0xFF)
                }
            }
        }
        return out
    }

    private fun sample(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        px: Float,
        py: Float,
        algorithm: PixelAlgorithm,
    ): Int {
        return when (algorithm) {
            PixelAlgorithm.Nearest -> sampleNearest(src, srcW, srcH, px, py)
            PixelAlgorithm.Bilinear, PixelAlgorithm.Average ->
                sampleBilinear(src, srcW, srcH, px, py)
            PixelAlgorithm.Bicubic -> sampleBicubic(src, srcW, srcH, px, py)
            PixelAlgorithm.Lanczos -> sampleLanczos(src, srcW, srcH, px, py)
            PixelAlgorithm.Custom -> sampleBilinear(src, srcW, srcH, px, py) // 兜底（正常不会走到）
        }
    }

    /**
     * 旧版兼容的最近邻缩放：直接使用 Android `Bitmap.createScaledBitmap(filter=false)`。
     * 与旧版 PixelConverter.convertToPixelArt 的行为完全一致。
     */
    private fun nearestViaAndroid(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        outW: Int,
        outH: Int,
    ): IntArray {
        return try {
            val bmp = android.graphics.Bitmap.createBitmap(src, srcW, srcH, android.graphics.Bitmap.Config.ARGB_8888)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, outW, outH, false)
            val out = IntArray(outW * outH)
            scaled.getPixels(out, 0, outW, 0, 0, outW, outH)
            bmp.recycle()
            if (scaled !== bmp) scaled.recycle()
            out
        } catch (t: Throwable) {
            // 失败时退化为自研采样
            val out = IntArray(outW * outH)
            for (j in 0 until outH) {
                for (i in 0 until outW) {
                    val cx = (i + 0.5f) * srcW / outW
                    val cy = (j + 0.5f) * srcH / outH
                    out[j * outW + i] = sampleNearest(src, srcW, srcH, cx, cy)
                }
            }
            out
        }
    }

    private fun sampleNearest(src: IntArray, srcW: Int, srcH: Int, px: Float, py: Float): Int {
        val x = round(px).toInt().coerceIn(0, srcW - 1)
        val y = round(py).toInt().coerceIn(0, srcH - 1)
        return src[y * srcW + x]
    }

    private fun getClamped(src: IntArray, srcW: Int, srcH: Int, x: Int, y: Int): Int {
        val cx = x.coerceIn(0, srcW - 1)
        val cy = y.coerceIn(0, srcH - 1)
        return src[cy * srcW + cx]
    }

    private fun sampleBilinear(src: IntArray, srcW: Int, srcH: Int, px: Float, py: Float): Int {
        val x = px - 0.5f
        val y = py - 0.5f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0

        val c00 = getClamped(src, srcW, srcH, x0, y0)
        val c10 = getClamped(src, srcW, srcH, x0 + 1, y0)
        val c01 = getClamped(src, srcW, srcH, x0, y0 + 1)
        val c11 = getClamped(src, srcW, srcH, x0 + 1, y0 + 1)

        return blend4(c00, c10, c01, c11, fx, fy)
    }

    private fun blend4(c00: Int, c10: Int, c01: Int, c11: Int, fx: Float, fy: Float): Int {
        val w00 = (1 - fx) * (1 - fy)
        val w10 = fx * (1 - fy)
        val w01 = (1 - fx) * fy
        val w11 = fx * fy
        val a = ((c00 ushr 24) and 0xFF) * w00 + ((c10 ushr 24) and 0xFF) * w10 +
            ((c01 ushr 24) and 0xFF) * w01 + ((c11 ushr 24) and 0xFF) * w11
        val r = ((c00 ushr 16) and 0xFF) * w00 + ((c10 ushr 16) and 0xFF) * w10 +
            ((c01 ushr 16) and 0xFF) * w01 + ((c11 ushr 16) and 0xFF) * w11
        val g = ((c00 ushr 8) and 0xFF) * w00 + ((c10 ushr 8) and 0xFF) * w10 +
            ((c01 ushr 8) and 0xFF) * w01 + ((c11 ushr 8) and 0xFF) * w11
        val b = (c00 and 0xFF) * w00 + (c10 and 0xFF) * w10 +
            (c01 and 0xFF) * w01 + (c11 and 0xFF) * w11
        return (a.toInt().coerceIn(0, 255) shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)
    }

    private fun sampleBicubic(src: IntArray, srcW: Int, srcH: Int, px: Float, py: Float): Int {
        val x = px - 0.5f
        val y = py - 0.5f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0

        // 预计算 4x4 权重（局部变量，避免每次采样分配数组）
        val wx0 = cubicWeight(-1 - fx); val wx1 = cubicWeight(-fx)
        val wx2 = cubicWeight(1 - fx); val wx3 = cubicWeight(2 - fx)
        val wy0 = cubicWeight(-1 - fy); val wy1 = cubicWeight(-fy)
        val wy2 = cubicWeight(1 - fy); val wy3 = cubicWeight(2 - fy)

        var accA = 0f; var accR = 0f; var accG = 0f; var accB = 0f
        var wSum = 0f
        for (dy in 0..3) {
            val wy = when (dy) { 0 -> wy0; 1 -> wy1; 2 -> wy2; else -> wy3 }
            for (dx in 0..3) {
                val w = (when (dx) { 0 -> wx0; 1 -> wx1; 2 -> wx2; else -> wx3 }) * wy
                val c = getClamped(src, srcW, srcH, x0 - 1 + dx, y0 - 1 + dy)
                accA += ((c ushr 24) and 0xFF) * w
                accR += ((c ushr 16) and 0xFF) * w
                accG += ((c ushr 8) and 0xFF) * w
                accB += (c and 0xFF) * w
                wSum += w
            }
        }
        if (wSum <= 0f) return sampleNearest(src, srcW, srcH, px, py)
        return pack(accA / wSum, accR / wSum, accG / wSum, accB / wSum)
    }

    private fun sampleLanczos(src: IntArray, srcW: Int, srcH: Int, px: Float, py: Float): Int {
        val x = px - 0.5f
        val y = py - 0.5f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        // 预计算 4x4 权重（局部变量，避免每次采样分配数组）
        val wx0 = lanczosWeight(-1 - fx); val wx1 = lanczosWeight(-fx)
        val wx2 = lanczosWeight(1 - fx); val wx3 = lanczosWeight(2 - fx)
        val wy0 = lanczosWeight(-1 - fy); val wy1 = lanczosWeight(-fy)
        val wy2 = lanczosWeight(1 - fy); val wy3 = lanczosWeight(2 - fy)

        var accA = 0f; var accR = 0f; var accG = 0f; var accB = 0f
        var wSum = 0f
        for (dy in 0..3) {
            val wy = when (dy) { 0 -> wy0; 1 -> wy1; 2 -> wy2; else -> wy3 }
            for (dx in 0..3) {
                val w = (when (dx) { 0 -> wx0; 1 -> wx1; 2 -> wx2; else -> wx3 }) * wy
                val c = getClamped(src, srcW, srcH, x0 - 1 + dx, y0 - 1 + dy)
                accA += ((c ushr 24) and 0xFF) * w
                accR += ((c ushr 16) and 0xFF) * w
                accG += ((c ushr 8) and 0xFF) * w
                accB += (c and 0xFF) * w
                wSum += w
            }
        }
        if (wSum <= 0f) return sampleNearest(src, srcW, srcH, px, py)
        return pack(accA / wSum, accR / wSum, accG / wSum, accB / wSum)
    }

    private fun pack(a: Float, r: Float, g: Float, b: Float): Int {
        return (a.toInt().coerceIn(0, 255) shl 24) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)
    }

    /** 三次卷积核（a = -0.5，Catmull-Rom 风格）。 */
    private fun cubicWeight(t: Float): Float {
        val a = -0.5f
        val at = abs(t)
        return when {
            at <= 1f -> (a + 2) * at * at * at - (a + 3) * at * at + 1
            at < 2f -> a * at * at * at - 5 * a * at * at + 8 * a * at - 4 * a
            else -> 0f
        }
    }

    /** Lanczos 核（a = 2）。 */
    private fun lanczosWeight(t: Float): Float {
        val a = 2f
        val at = abs(t)
        if (at < 1e-6f) return 1f
        if (at >= a) return 0f
        val pt = Math.PI.toFloat() * t
        return (sin(pt) / pt) * (sin(pt / a) / (pt / a))
    }

    /** 计算预览用尺寸：限制最大像素数，保持宽高比。 */
    fun previewSize(outW: Int, outH: Int, maxEdge: Int): Pair<Int, Int> {
        val maxDim = maxOf(outW, outH)
        if (maxDim <= maxEdge) return outW to outH
        val s = maxEdge.toFloat() / maxDim
        return maxOf(1, (outW * s).toInt()) to maxOf(1, (outH * s).toInt())
    }

    /** 粗略估算像素数，用于判断是否降采样预览。 */
    fun pixelCount(w: Int, h: Int): Long = w.toLong() * h
}