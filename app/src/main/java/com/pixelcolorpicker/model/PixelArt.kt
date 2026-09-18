package com.pixelcolorpicker.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 可编辑像素画布（任意 W×H）。
 *
 * 内部使用长度为 width*height 的 IntArray 存储 ARGB 颜色（行优先）。
 * 通过 [version] 计数器触发 Compose 重组。
 * 内置撤销/重做（按内存预算限制历史深度）。
 */
@Stable
class PixelArt(
    val width: Int,
    val height: Int,
    initial: IntArray? = null,
) {
    private val pixels: IntArray = initial?.copyOf() ?: IntArray(width * height)

    /** 实例唯一 ID（用于 Compose 侧区分不同画布实例，避免 remember 复用旧位图）。 */
    val instanceId: Int = nextInstanceId()

    var version by mutableStateOf(0)
        private set

    private val undoStack = ArrayDeque<IntArray>()
    private val redoStack = ArrayDeque<IntArray>()

    /** 笔画快照（拖动期间合并撤销）。 */
    private var strokeSnapshot: IntArray? = null
    private var strokeChanged = false

    /** 开始一次连续笔画（撤销按整笔合并）。 */
    fun beginStroke() {
        if (strokeSnapshot != null) return
        strokeSnapshot = pixels.copyOf()
        strokeChanged = false
    }

    /** 结束笔画：有变化则提交一次撤销记录。 */
    fun endStroke() {
        val snap = strokeSnapshot ?: return
        strokeSnapshot = null
        if (strokeChanged) {
            undoStack.addLast(snap)
            while (undoStack.size > maxHistory) undoStack.removeFirst()
            redoStack.clear()
            updateFlags()
        }
        strokeChanged = false
    }

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    /** 历史记录最大条数：按 64MB 预算动态限制，避免大画布内存爆炸。 */
    private val maxHistory: Int = run {
        val bytesPerSnapshot = width.toLong() * height * 4
        if (bytesPerSnapshot <= 0) 100
        else ((64L * 1024 * 1024) / bytesPerSnapshot).toInt().coerceIn(1, 100)
    }

    fun get(x: Int, y: Int): Int = pixels[y * width + x]

    fun get(index: Int): Int = pixels[index]

    fun snapshot(): IntArray = pixels.copyOf()

    fun setPixel(x: Int, y: Int, color: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val idx = y * width + x
        if (pixels[idx] == color) return
        if (strokeSnapshot == null) pushUndo()
        pixels[idx] = color
        strokeChanged = true
        version++
    }

    fun setPixels(updates: List<Triple<Int, Int, Int>>) {
        if (updates.isEmpty()) return
        val changed = updates.filter { (x, y, c) ->
            x in 0 until width && y in 0 until height && pixels[y * width + x] != c
        }
        if (changed.isEmpty()) return
        if (strokeSnapshot == null) pushUndo()
        changed.forEach { (x, y, c) -> pixels[y * width + x] = c }
        strokeChanged = true
        version++
    }

    fun replaceAll(newPixels: IntArray) {
        require(newPixels.size == width * height)
        pushUndo()
        System.arraycopy(newPixels, 0, pixels, 0, width * height)
        version++
    }

    fun fill(color: Int) {
        pushUndo()
        pixels.fill(color)
        version++
    }

    fun clear() {
        pushUndo()
        pixels.fill(0x00000000)
        version++
    }

    private fun pushUndo() {
        undoStack.addLast(pixels.copyOf())
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
        updateFlags()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(pixels.copyOf())
        val prev = undoStack.removeLast()
        System.arraycopy(prev, 0, pixels, 0, width * height)
        version++
        updateFlags()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(pixels.copyOf())
        val next = redoStack.removeLast()
        System.arraycopy(next, 0, pixels, 0, width * height)
        version++
        updateFlags()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    companion object {
        /**
         * 全局实例序号：每创建一个 PixelArt 递增。
         * 用于 Compose 侧区分"不同实例但宽高相同"的画布（避免 remember 复用旧位图）。
         */
        private var instanceCounter = 0

        fun empty(width: Int, height: Int): PixelArt = PixelArt(width, height)

        fun from(width: Int, height: Int, pixels: IntArray): PixelArt =
            PixelArt(width, height, pixels)

        internal fun nextInstanceId(): Int = ++instanceCounter
    }
}

/** 只读像素网格（用于色板 / 编码展示）。 */
class PixelGrid(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    initialCounts: HashMap<Int, Int>? = null,
) {
    /** 同色计数（可在后台统计完成后回填；Compose 状态，回填后色板自动重绘）。 */
    private var counts by mutableStateOf(initialCounts)

    init {
        require(pixels.size == width * height) {
            "pixels 数量应为 ${width * height}，实际 ${pixels.size}"
        }
    }

    fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /** 该颜色在整张网格中出现的次数（未统计时返回 1）。 */
    fun countOf(color: Int): Int = counts?.get(color) ?: 1

    /** 回填同色计数（主线程调用）。 */
    fun updateCounts(newCounts: HashMap<Int, Int>) {
        counts = newCounts
    }

    companion object {
        /** 统计各颜色出现次数（建议在后台线程调用）。 */
        fun computeCounts(pixels: IntArray): HashMap<Int, Int> {
            val map = HashMap<Int, Int>(512)
            for (p in pixels) {
                map[p] = (map[p] ?: 0) + 1
            }
            return map
        }
    }
}