package com.pixelcolorpicker.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一键复制16色」协议的兼容性测试。
 *
 * 由于没有旧版 App 可对照，这里通过「手动解析生成的字节」来验证协议结构正确，
 * 并覆盖需求中列出的 7 个用例。
 */
class PaletteCodecTest {

    private val SIZE = PaletteCodec.SIZE

    private fun solid(color: Int): IntArray = IntArray(SIZE * SIZE) { color }

    /** 手动解析 Palette 字节，返回 (field1Value, 颜色列表)。用于验证结构。 */
    private fun parsePalette(bytes: ByteArray): Pair<String, List<Int>> {
        var pos = 0
        var field1 = ""
        val colors = mutableListOf<Int>()

        while (pos < bytes.size) {
            val tag = bytes[pos].toInt() and 0xFF
            pos++
            when (tag) {
                0x0A -> { // field 1, wire type 2 (length-delimited)
                    val len = bytes[pos].toInt() and 0xFF
                    pos++
                    field1 = String(bytes, pos, len, Charsets.US_ASCII)
                    pos += len
                }
                0x12 -> { // field 2, wire type 2
                    val len = readVarint(bytes, pos)
                    pos += len.second
                    val end = pos + len.first
                    colors.add(parseColor(bytes, pos, end))
                    pos = end
                }
                else -> error("未知 tag: 0x${tag.toString(16)} at $pos")
            }
        }
        return field1 to colors
    }

    /** 解析单个 Color 消息，返回 ARGB。 */
    private fun parseColor(bytes: ByteArray, start: Int, end: Int): Int {
        var pos = start
        var r = 0; var g = 0; var b = 0; var a = 0
        while (pos < end) {
            val tag = bytes[pos].toInt() and 0xFF
            pos++
            when (tag) {
                0x08 -> { val v = readVarint(bytes, pos); r = v.first; pos += v.second }
                0x10 -> { val v = readVarint(bytes, pos); g = v.first; pos += v.second }
                0x18 -> { val v = readVarint(bytes, pos); b = v.first; pos += v.second }
                0x20 -> { val v = readVarint(bytes, pos); a = v.first; pos += v.second }
                else -> error("未知 color tag: 0x${tag.toString(16)}")
            }
        }
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = start
        while (true) {
            val byte = bytes[pos].toInt() and 0xFF
            pos++
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return result to (pos - start)
    }

    // ---- 测试 1：纯黑 ----
    @Test
    fun test1_solidBlack() {
        val bytes = PaletteCodec.buildPalette(solid(0xFF000000.toInt()))
        val (field1, colors) = parsePalette(bytes)
        assertEquals("18", field1)
        assertEquals(18, colors.size)
        // 16 个黑 + 2 个白
        repeat(16) { assertEquals(0xFF000000.toInt(), colors[it]) }
        assertEquals(0xFFFFFFFF.toInt(), colors[16])
        assertEquals(0xFFFFFFFF.toInt(), colors[17])
    }

    // ---- 测试 2：纯白 ----
    @Test
    fun test2_solidWhite() {
        val bytes = PaletteCodec.buildPalette(solid(0xFFFFFFFF.toInt()))
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)
        colors.forEach { assertEquals(0xFFFFFFFF.toInt(), it) }
    }

    // ---- 测试 3：纯红 ----
    @Test
    fun test3_solidRed() {
        val bytes = PaletteCodec.buildPalette(solid(0xFFFF0000.toInt()))
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)
        repeat(16) { assertEquals(0xFFFF0000.toInt(), colors[it]) }
        assertEquals(0xFFFFFFFF.toInt(), colors[16])
        assertEquals(0xFFFFFFFF.toInt(), colors[17])
    }

    // ---- 测试 4：纯绿 ----
    @Test
    fun test4_solidGreen() {
        val bytes = PaletteCodec.buildPalette(solid(0xFF00FF00.toInt()))
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)
        repeat(16) { assertEquals(0xFF00FF00.toInt(), colors[it]) }
    }

    // ---- 测试 5：纯蓝 ----
    @Test
    fun test5_solidBlue() {
        val bytes = PaletteCodec.buildPalette(solid(0xFF0000FF.toInt()))
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)
        repeat(16) { assertEquals(0xFF0000FF.toInt(), colors[it]) }
    }

    // ---- 测试 6：混合颜色 ----
    @Test
    fun test6_mixedColors() {
        val pixels = IntArray(SIZE * SIZE)
        // 第 0 列填充 16 种不同的颜色
        val colColors = IntArray(16) { i ->
            (0xFF shl 24) or (i * 16 shl 16) or (i * 8 shl 8) or (255 - i * 16)
        }
        for (y in 0 until SIZE) {
            pixels[y * SIZE] = colColors[y]
        }
        // 其余区域填成另一种颜色（不应被读取）
        for (y in 0 until SIZE) {
            for (x in 1 until SIZE) {
                pixels[y * SIZE + x] = 0xFF123456.toInt()
            }
        }
        val bytes = PaletteCodec.buildPalette(pixels)
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)
        for (y in 0 until SIZE) {
            assertEquals(colColors[y], colors[y])
        }
        assertEquals(0xFFFFFFFF.toInt(), colors[16])
        assertEquals(0xFFFFFFFF.toInt(), colors[17])
    }

    // ---- 测试 7：最重要的列测试 ----
    // 第 0 列全红，其余 15 列全蓝。
    // 正确实现：16 个红 + 2 个白。
    // 错误实现：红+蓝的调色板。
    @Test
    fun test7_columnTest() {
        val pixels = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                pixels[y * SIZE + x] = if (x == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
            }
        }
        val bytes = PaletteCodec.buildPalette(pixels)
        val (_, colors) = parsePalette(bytes)
        assertEquals(18, colors.size)

        // 前 16 个必须是红色
        repeat(16) { i ->
            assertEquals("第 $i 个应为红色", 0xFFFF0000.toInt(), colors[i])
        }
        // 后 2 个是白色
        assertEquals(0xFFFFFFFF.toInt(), colors[16])
        assertEquals(0xFFFFFFFF.toInt(), colors[17])
        // 不应该出现蓝色
        assertTrue("编码中不应包含蓝色", colors.none { it == 0xFF0000FF.toInt() })
    }

    // ---- 额外：alpha 被忽略，永远编码为 255 ----
    @Test
    fun testAlphaIgnored() {
        // 半透明红，alpha=0x80，但编码后 alpha 必须是 255
        val bytes = PaletteCodec.buildPalette(solid(0x80FF0000.toInt()))
        val (_, colors) = parsePalette(bytes)
        repeat(16) { assertEquals(0xFFFF0000.toInt(), colors[it]) }
    }

    // ---- 额外：固定开头必须是 0A 02 31 38 ----
    @Test
    fun testPaletteHeader() {
        val bytes = PaletteCodec.buildPalette(solid(0xFF000000.toInt()))
        assertEquals(0x0A, bytes[0].toInt() and 0xFF)
        assertEquals(0x02, bytes[1].toInt() and 0xFF)
        assertEquals('1'.code, bytes[2].toInt() and 0xFF)
        assertEquals('8'.code, bytes[3].toInt() and 0xFF)
    }

    // ---- 额外：完整编码流程可往返（GZIP + Base64）----
    @Test
    fun testFullEncodeProducesValidBase64() {
        val code = PaletteCodec.encode(solid(0xFF000000.toInt()))
        assertTrue("Base64 不应为空", code.isNotEmpty())
        // 标准 Base64 字符集，无换行
        assertTrue(code.none { it == '\n' || it == '\r' })
        // 可解码回原始 palette 字节
        val decoded = java.util.Base64.getDecoder().decode(code)
        val ungzipped = java.util.zip.GZIPInputStream(decoded.inputStream()).readBytes()
        val (field1, colors) = parsePalette(ungzipped)
        assertEquals("18", field1)
        assertEquals(18, colors.size)
    }

    // ---- 按列编码（encodeColumn）：16 色 + 2 白 ----
    @Test
    fun testEncodeColumn18Colors() {
        val colors = IntArray(16) { i -> 0xFF000000.toInt() or (i * 8 shl 16) or (i * 4 shl 8) or i }
        val code = PaletteCodec.encodeColumn(colors)
        val decoded = java.util.Base64.getDecoder().decode(code)
        val ungzipped = java.util.zip.GZIPInputStream(decoded.inputStream()).readBytes()
        val (field1, parsed) = parsePalette(ungzipped)
        assertEquals("18", field1)
        assertEquals(18, parsed.size)
        for (i in 0 until 16) {
            assertEquals(colors[i] and 0x00FFFFFF, parsed[i] and 0x00FFFFFF)
        }
        assertEquals(0xFFFFFFFF.toInt(), parsed[16])
        assertEquals(0xFFFFFFFF.toInt(), parsed[17])
    }

    // ---- 列取样：16 高时等于原始列 ----
    @Test
    fun testSampleColumn16() {
        val w = 16; val h = 16
        val pixels = IntArray(w * h) { i -> 0xFF000000.toInt() or (i + 1) }
        val colors = PaletteCodec.sampleColumnColors(pixels, w, h, 3)
        assertEquals(16, colors.size)
        for (y in 0 until 16) {
            assertEquals(pixels[y * w + 3], colors[y])
        }
    }

    // ---- 列取样：高度不足 16 时补白 ----
    @Test
    fun testSampleColumnPadsWhite() {
        val w = 8; val h = 5
        val pixels = IntArray(w * h) { i -> 0xFF112233.toInt() }
        val colors = PaletteCodec.sampleColumnColors(pixels, w, h, 0)
        assertEquals(16, colors.size)
        for (i in 0 until 5) {
            assertEquals(0xFF112233.toInt(), colors[i])
        }
        for (i in 5 until 16) {
            assertEquals(0xFFFFFFFF.toInt(), colors[i])
        }
    }

    // ---- 列取样：高度大于 16 时均匀取样 ----
    @Test
    fun testSampleColumn32() {
        val w = 4; val h = 32
        val pixels = IntArray(w * h) { i -> 0xFF000000.toInt() or (i % 256) }
        val colors = PaletteCodec.sampleColumnColors(pixels, w, h, 1)
        assertEquals(16, colors.size)
        // 第一个取样点应为 y=1（floor((0+0.5)*32/16)=1）
        assertEquals(pixels[1 * w + 1], colors[0])
    }
}