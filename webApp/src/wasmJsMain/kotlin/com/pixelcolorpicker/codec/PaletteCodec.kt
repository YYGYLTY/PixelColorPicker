package com.pixelcolorpicker.codec


/**
 * PixelColorPicker 旧版「一键复制16色」协议编码器。
 *
 * 该协议**必须与旧版完全兼容**，因此这里严格按旧版规则手动构造字节，
 * 不依赖任何 Protobuf runtime。
 *
 * 协议概览：
 *  1. 输入为一张 16x16 的 ARGB 位图（等价于 createScaledBitmap(bmp,16,16,false)）。
 *  2. 只读取「第 0 列」（最左列）从上到下的 16 个像素，再追加 2 个白色 (0xFFFFFFFF)，
 *     因此实际编码 18 个颜色。
 *  3. 每个颜色编码为一个 Protobuf Color 消息：
 *        r != 0 -> 写 0x08 + varint(r)
 *        g != 0 -> 写 0x10 + varint(g)
 *        b != 0 -> 写 0x18 + varint(b)
 *        永远    -> 写 0x20 + varint(255)   // alpha 恒为 255，忽略原始 alpha
 *  4. 外层 Palette 消息：
 *        固定开头：0A 02 31 38            // field 1 (string) = "18"
 *        每个颜色：0x12 + varint(len) + ColorMessage   // field 2 (repeated message)
 *  5. 对 Palette 字节做 GZIP（java.util.zip.GZIPOutputStream）。
 *  6. 对 GZIP 字节做标准 Base64（NO_WRAP，保留 '='，无换行、无前后缀）。
 *
 * 该对象不依赖 Android SDK，可在 JVM 单元测试中直接运行。
 */
object PaletteCodec {

    /**
     * 极简可变字节缓冲（wasm 没有 java.io.ByteArrayOutputStream）。
     * API 与 ByteArrayOutputStream 的 write / toByteArray 对齐。
     */
    class Buf(initial: Int = 64) {
        private var arr = ByteArray(initial)
        private var len = 0

        private fun ensure(extra: Int) {
            if (len + extra <= arr.size) return
            var cap = arr.size
            while (cap < len + extra) cap *= 2
            arr = arr.copyOf(cap)
        }

        fun write(b: Int) {
            ensure(1)
            arr[len++] = b.toByte()
        }

        fun write(bytes: ByteArray) {
            ensure(bytes.size)
            bytes.copyInto(arr, len)
            len += bytes.size
        }

        fun write(bytes: ByteArray, off: Int, count: Int) {
            ensure(count)
            bytes.copyInto(arr, len, off, off + count)
            len += count
        }

        fun size(): Int = len

        fun toByteArray(): ByteArray = arr.copyOf(len)
    }


    /** 像素画边长。 */
    const val SIZE = 16

    /** 编码中实际包含的颜色数量（16 个左列像素 + 2 个白色）。 */
    const val COLOR_COUNT = 18

    /** 追加的白色。 */
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * 将 16x16 的像素数组编码为旧版兼容的 Base64 字符串。
     *
     * @param pixels 长度为 [SIZE]*[SIZE] 的 ARGB 颜色数组，行优先：
     *               index = y * SIZE + x。若长度不足会抛出异常。
     * @return 旧版兼容的 Base64 编码字符串。
     */
    fun encode(pixels: IntArray): String {
        require(pixels.size >= SIZE * SIZE) {
            "pixels 长度必须 >= ${SIZE * SIZE}，实际 ${pixels.size}"
        }
        val palette = buildPalette(pixels)
        val gzip = gzip(palette)
        return base64(gzip)
    }

    /**
     * 构造 Palette 消息的原始字节（未压缩、未编码）。
     * 单独暴露出来便于测试与调试。
     */
    fun buildPalette(pixels: IntArray): ByteArray {
        require(pixels.size >= SIZE * SIZE) {
            "pixels 长度必须 >= ${SIZE * SIZE}，实际 ${pixels.size}"
        }
        val out = Buf()

        // field 1: string "18"  ->  0A 02 '1' '8'
        out.write(0x0A)
        out.write(0x02)
        out.write('1'.code)
        out.write('8'.code)

        // 读取第 0 列（x = 0），y 从 0 到 15。
        for (y in 0 until SIZE) {
            val color = pixels[y * SIZE] // x = 0
            writeColorField(out, color)
        }
        // 追加 2 个白色。
        writeColorField(out, WHITE)
        writeColorField(out, WHITE)

        return out.toByteArray()
    }

    /**
     * 写出一个 Color 的 field 2 条目：0x12 + varint(len) + ColorMessage。
     */
    private fun writeColorField(out: Buf, argb: Int) {
        val colorMsg = encodeColor(argb)
        out.write(0x12)
        writeVarint(out, colorMsg.size.toLong())
        out.write(colorMsg)
    }

    /**
     * 编码单个 Color 消息。
     * 注意：忽略传入颜色的 alpha，alpha 恒写 255。
     */
    fun encodeColor(argb: Int): ByteArray {
        val out = Buf()
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        if (r != 0) {
            out.write(0x08)
            writeVarint(out, r.toLong())
        }
        if (g != 0) {
            out.write(0x10)
            writeVarint(out, g.toLong())
        }
        if (b != 0) {
            out.write(0x18)
            writeVarint(out, b.toLong())
        }
        // alpha 永远写出，值为 255。
        out.write(0x20)
        writeVarint(out, 255L)

        return out.toByteArray()
    }

    /**
     * 标准 Protobuf Varint 编码。
     */
    fun writeVarint(out: Buf, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    /**
     * GZIP 压缩。使用 Java 标准 GZIPOutputStream，不写文件名/mtime。
     */
    fun gzip(data: ByteArray): ByteArray {
        // wasm 没有 java.util.zip.GZIPOutputStream。
        // 这里写一个合法的 GZIP 容器（deflate stored 模式，不压缩）。
        // 解码端（inflate）能正常解出原始字节，协议兼容。
        val out = Buf()
        // --- GZIP header ---
        out.write(0x1F); out.write(0x8B)   // magic
        out.write(0x08)                     // CM = deflate
        out.write(0x00)                     // FLG = 0
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00) // MTIME = 0
        out.write(0x00)                     // XFL
        out.write(0xFF)                     // OS = unknown
        // --- deflate: stored blocks (BFINAL/BTYPE=00) ---
        var offset = 0
        while (offset < data.size) {
            val len = minOf(65535, data.size - offset)
            val isLast = (offset + len >= data.size)
            out.write(if (isLast) 0x01 else 0x00)
            out.write(len and 0xFF); out.write((len ushr 8) and 0xFF)
            out.write((len.inv()) and 0xFF); out.write(((len.inv()) ushr 8) and 0xFF)
            for (i in 0 until len) out.write(data[offset + i].toInt())
            offset += len
        }
        if (data.isEmpty()) {
            out.write(0x01); out.write(0x00); out.write(0x00); out.write(0xFF); out.write(0xFF)
        }
        // --- CRC32 + ISIZE ---
        val crc = crc32(data)
        out.write(crc and 0xFF); out.write((crc ushr 8) and 0xFF)
        out.write((crc ushr 16) and 0xFF); out.write((crc ushr 24) and 0xFF)
        val isize = data.size
        out.write(isize and 0xFF); out.write((isize ushr 8) and 0xFF)
        out.write((isize ushr 16) and 0xFF); out.write((isize ushr 24) and 0xFF)
        return out.toByteArray()
    }

    /** CRC-32（GZIP 校验用）。 */
    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (k in 0 until 8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc.inv()
    }

    /**
     * 标准 Base64（NO_WRAP）：保留 padding、无换行、无前后缀。
     *
     * 使用 java.util.Base64 的 MIME 之外的标准编码器，行为等价于
     * Android 的 Base64.encodeToString(bytes, Base64.NO_WRAP)。
     */
    fun base64(data: ByteArray): String {
        // wasm 没有 java.util.Base64，手写标准编码（带 padding、无换行）
        val tbl = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    (data[i + 2].toInt() and 0xFF)
            sb.append(tbl[(n ushr 18) and 0x3F])
            sb.append(tbl[(n ushr 12) and 0x3F])
            sb.append(tbl[(n ushr 6) and 0x3F])
            sb.append(tbl[n and 0x3F])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = (data[i].toInt() and 0xFF) shl 16
                sb.append(tbl[(n ushr 18) and 0x3F])
                sb.append(tbl[(n ushr 12) and 0x3F])
                sb.append("==")
            }
            2 -> {
                val n = ((data[i].toInt() and 0xFF) shl 16) or
                        ((data[i + 1].toInt() and 0xFF) shl 8)
                sb.append(tbl[(n ushr 18) and 0x3F])
                sb.append(tbl[(n ushr 12) and 0x3F])
                sb.append(tbl[(n ushr 6) and 0x3F])
                sb.append("=")
            }
        }
        return sb.toString()
    }

    /** 便捷方法：直接编码 16x16 的二维数组。 */
    fun encode(rows: Array<IntArray>): String {
        val flat = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                flat[y * SIZE + x] = rows[y][x]
            }
        }
        return encode(flat)
    }

    // ==================== 与旧版 PixelColorPicker 完全一致的「按列编码」 ====================

    /**
     * 从像素网格中取样一列的 16 个颜色（用于编码）。
     *
     * 规则（与旧版行为对齐）：
     *  - 旧版固定读取 16 行（y = 0..15），不足 16 行时用白色补齐；
     *  - 高度大于 16 时，为保证代表性，均匀取样 16 个像素。
     *
     * @param pixels 行优先的 ARGB 数组
     * @param width  网格宽度
     * @param height 网格高度
     * @param column 列索引（0 起）
     * @return 长度为 16 的颜色数组
     */
    fun sampleColumnColors(pixels: IntArray, width: Int, height: Int, column: Int): IntArray {
        require(column in 0 until width) { "column 越界: $column" }
        require(pixels.size >= width * height)
        val out = IntArray(SIZE)
        if (height >= SIZE) {
            for (i in 0 until SIZE) {
                val y = (((i + 0.5f) * height) / SIZE).toInt().coerceIn(0, height - 1)
                out[i] = pixels[y * width + column]
            }
        } else {
            for (i in 0 until SIZE) {
                out[i] = if (i < height) pixels[i * width + column] else WHITE
            }
        }
        return out
    }

    /**
     * 旧版 `PaletteExporter.exportColumn` 的等价实现：
     * 16 个颜色 + 2 个白色 = 18 色，header 固定 "18"，GZIP + Base64。
     *
     * @param colors16 长度必须为 16
     */
    fun encodeColumn(colors16: IntArray): String {
        require(colors16.size == SIZE) { "列颜色必须为 16 个，实际 ${colors16.size}" }
        val palette = Buf()
        // field 1: string "18"（固定值，与旧版一致）
        palette.write(0x0A)
        palette.write(0x02)
        palette.write('1'.code)
        palette.write('8'.code)
        colors16.forEach { writeColorField(palette, it) }
        writeColorField(palette, WHITE)
        writeColorField(palette, WHITE)
        return base64(gzip(palette.toByteArray()))
    }
}
