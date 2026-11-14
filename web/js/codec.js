/*
 * PixelColorPicker Web · 18 色板码编解码器
 * ------------------------------------------------------------
 * 与 App 内 com.pixelcolorpicker.codec.PaletteCodec 完全一致的协议实现：
 *
 *   1. 输入一列 16 个颜色（ARGB int），追加 2 个白色 → 共 18 色；
 *   2. 每个颜色编码为 Protobuf Color 消息：
 *        r != 0 → 0x08 + varint(r)
 *        g != 0 → 0x10 + varint(g)
 *        b != 0 → 0x18 + varint(b)
 *        永远   → 0x20 + varint(255)      // alpha 恒为 255
 *   3. 外层 Palette：0A 02 '1' '8' + 每个颜色 0x12 + varint(len) + ColorMessage
 *   4. GZIP 压缩（header 与 Java GZIPOutputStream 一致：mtime=0、OS=0）
 *   5. 标准 Base64（保留 padding，无换行）
 *
 * 该文件既可在浏览器直接 <script> 引入（挂到 window.PCPCodec），
 * 也可在 Node 中 require() 用于单元测试。
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.PCPCodec = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var SIZE = 16;
  var COLOR_COUNT = 18;
  var WHITE = 0xFFFFFFFF | 0;

  // ---------------- varint ----------------

  function writeVarint(out, value) {
    var v = value;
    while (true) {
      if ((v & ~0x7F) === 0) {
        out.push(v & 0xFF);
        return;
      }
      out.push((v & 0x7F) | 0x80);
      v = v >>> 7;
    }
  }

  // ---------------- Protobuf Color ----------------

  function encodeColor(argb) {
    var out = [];
    var r = (argb >>> 16) & 0xFF;
    var g = (argb >>> 8) & 0xFF;
    var b = argb & 0xFF;
    if (r !== 0) { out.push(0x08); writeVarint(out, r); }
    if (g !== 0) { out.push(0x10); writeVarint(out, g); }
    if (b !== 0) { out.push(0x18); writeVarint(out, b); }
    out.push(0x20); writeVarint(out, 255);
    return out;
  }

  function writeColorField(out, argb) {
    var msg = encodeColor(argb);
    out.push(0x12);
    writeVarint(out, msg.length);
    for (var i = 0; i < msg.length; i++) out.push(msg[i]);
  }

  /** 构造 Palette 消息原始字节（未压缩 / 未 Base64）。 */
  function buildPaletteBytes(colors16) {
    if (!colors16 || colors16.length !== SIZE) {
      throw new Error('列颜色必须为 16 个，实际 ' + (colors16 ? colors16.length : 0));
    }
    var out = [];
    // field 1: string "18"
    out.push(0x0A, 0x02, 0x31, 0x38);
    for (var i = 0; i < SIZE; i++) writeColorField(out, colors16[i] | 0);
    writeColorField(out, WHITE);
    writeColorField(out, WHITE);
    return new Uint8Array(out);
  }

  // ---------------- CRC32 ----------------

  var CRC_TABLE = (function () {
    var t = new Int32Array(256);
    for (var n = 0; n < 256; n++) {
      var c = n;
      for (var k = 0; k < 8; k++) {
        c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
      }
      t[n] = c;
    }
    return t;
  })();

  function crc32(bytes) {
    var c = -1;
    for (var i = 0; i < bytes.length; i++) {
      c = CRC_TABLE[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    }
    return (c ^ -1) >>> 0;
  }

  // ---------------- deflate / gzip ----------------

  /**
   * 是否支持原生压缩：只检测一次，避免每列编码都重复探测。
   * 注意：GZIP 的 zlib 封装仍然是手写的（为了与 Java 的 header 完全一致），
   * 这里只借用浏览器的 deflate-raw 生成压缩块。
   */
  var HAS_COMPRESSION = (
    typeof CompressionStream === 'function' &&
    typeof Blob === 'function' &&
    typeof Response === 'function'
  );

  /** 无压缩（stored）deflate：任何环境都能跑，作为兜底。 */
  function storedDeflate(bytes) {
    var chunks = [];
    var MAX = 65535;
    if (bytes.length === 0) {
      return new Uint8Array([0x01, 0x00, 0x00, 0xFF, 0xFF]);
    }
    var pos = 0;
    while (pos < bytes.length) {
      var len = Math.min(MAX, bytes.length - pos);
      var last = (pos + len >= bytes.length) ? 1 : 0;
      chunks.push(last, len & 0xFF, (len >>> 8) & 0xFF, (~len) & 0xFF, ((~len) >>> 8) & 0xFF);
      for (var i = 0; i < len; i++) chunks.push(bytes[pos + i]);
      pos += len;
    }
    return new Uint8Array(chunks);
  }

  /** 优先使用浏览器原生 deflate-raw（体积更小），失败则退回 stored。 */
  function deflateRaw(bytes) {
    if (HAS_COMPRESSION) {
      try {
        var cs = new CompressionStream('deflate-raw');
        var stream = new Blob([bytes]).stream().pipeThrough(cs);
        return new Response(stream).arrayBuffer().then(function (buf) {
          return new Uint8Array(buf);
        }).catch(function () {
          return storedDeflate(bytes);
        });
      } catch (e) {
        return Promise.resolve(storedDeflate(bytes));
      }
    }
    return Promise.resolve(storedDeflate(bytes));
  }

  /**
   * 组装 gzip 容器。
   * header 固定 10 字节：1F 8B 08 00 | mtime=0(4B) | XFL=0 | OS=0
   * 与 java.util.zip.GZIPOutputStream 默认输出一致。
   */
  function wrapGzip(deflated, rawLength) {
    var out = new Uint8Array(10 + deflated.length + 8);
    out[0] = 0x1F; out[1] = 0x8B; out[2] = 0x08; out[3] = 0x00;
    out[4] = out[5] = out[6] = out[7] = 0x00; // mtime
    out[8] = 0x00;                            // XFL
    out[9] = 0x00;                            // OS: 0 (FAT/Java)
    out.set(deflated, 10);
    var p = 10 + deflated.length;
    return { out: out, p: p };
  }

  function gzipBytes(bytes) {
    var crc = crc32(bytes);
    var isize = bytes.length >>> 0;
    return deflateRaw(bytes).then(function (deflated) {
      var w = wrapGzip(deflated, bytes.length);
      var out = w.out;
      var p = w.p;
      // CRC32（小端）
      out[p++] = crc & 0xFF; out[p++] = (crc >>> 8) & 0xFF;
      out[p++] = (crc >>> 16) & 0xFF; out[p++] = (crc >>> 24) & 0xFF;
      // ISIZE（小端）
      out[p++] = isize & 0xFF; out[p++] = (isize >>> 8) & 0xFF;
      out[p++] = (isize >>> 16) & 0xFF; out[p++] = (isize >>> 24) & 0xFF;
      return out;
    });
  }

  /** 同步版本：只用 stored deflate（体积略大，但完全无异步依赖）。 */
  function gzipBytesSync(bytes) {
    var deflated = storedDeflate(bytes);
    var crc = crc32(bytes);
    var isize = bytes.length >>> 0;
    var w = wrapGzip(deflated, bytes.length);
    var out = w.out, p = w.p;
    out[p++] = crc & 0xFF; out[p++] = (crc >>> 8) & 0xFF;
    out[p++] = (crc >>> 16) & 0xFF; out[p++] = (crc >>> 24) & 0xFF;
    out[p++] = isize & 0xFF; out[p++] = (isize >>> 8) & 0xFF;
    out[p++] = (isize >>> 16) & 0xFF; out[p++] = (isize >>> 24) & 0xFF;
    return out;
  }

  // ---------------- Base64 ----------------

  var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

  function toBase64(bytes) {
    var s = '';
    var i = 0;
    for (; i + 2 < bytes.length; i += 3) {
      var n = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
      s += B64[(n >>> 18) & 63] + B64[(n >>> 12) & 63] + B64[(n >>> 6) & 63] + B64[n & 63];
    }
    var rest = bytes.length - i;
    if (rest === 1) {
      var n1 = bytes[i] << 16;
      s += B64[(n1 >>> 18) & 63] + B64[(n1 >>> 12) & 63] + '==';
    } else if (rest === 2) {
      var n2 = (bytes[i] << 16) | (bytes[i + 1] << 8);
      s += B64[(n2 >>> 18) & 63] + B64[(n2 >>> 12) & 63] + B64[(n2 >>> 6) & 63] + '=';
    }
    return s;
  }

  function fromBase64(str) {
    var clean = String(str).replace(/[\s\r\n]/g, '');
    var out = [];
    var buf = 0, bits = 0;
    for (var i = 0; i < clean.length; i++) {
      var ch = clean[i];
      if (ch === '=') break;
      var v = B64.indexOf(ch);
      if (v < 0) continue;
      buf = (buf << 6) | v;
      bits += 6;
      if (bits >= 8) {
        bits -= 8;
        out.push((buf >>> bits) & 0xFF);
      }
    }
    return new Uint8Array(out);
  }

  // ---------------- 对外 API ----------------

  /**
   * 编码一列 16 色 → 旧版兼容 Base64 字符串（异步，优先原生 deflate）。
   * @param {Array<number>|Int32Array} colors16 长度 16 的 ARGB 颜色
   * @returns {Promise<string>}
   */
  function encodeColumn(colors16) {
    return gzipBytes(buildPaletteBytes(colors16)).then(toBase64);
  }

  /** 同步版本，结果同样合法（只是不压缩，字符串稍长）。 */
  function encodeColumnSync(colors16) {
    return toBase64(gzipBytesSync(buildPaletteBytes(colors16)));
  }

  /**
   * 从像素网格取样一列 16 个颜色（与 App 的 sampleColumnColors 一致）。
   * 高度 ≥ 16 时均匀取样；不足 16 行用白色补齐。
   */
  function sampleColumnColors(pixels, width, height, column) {
    if (column < 0 || column >= width) throw new Error('column 越界: ' + column);
    var out = new Int32Array(SIZE);
    if (height >= SIZE) {
      for (var i = 0; i < SIZE; i++) {
        var y = Math.floor(((i + 0.5) * height) / SIZE);
        if (y > height - 1) y = height - 1;
        out[i] = pixels[y * width + column] | 0;
      }
    } else {
      for (var j = 0; j < SIZE; j++) {
        out[j] = (j < height) ? (pixels[j * width + column] | 0) : WHITE;
      }
    }
    return out;
  }

  /** ARGB int → "#RRGGBB" */
  function hexOf(argb) {
    var r = (argb >>> 16) & 0xFF;
    var g = (argb >>> 8) & 0xFF;
    var b = argb & 0xFF;
    return '#' + ((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1).toUpperCase();
  }

  /** "#RRGGBB" → ARGB int（alpha 补 255） */
  function parseHex(hex) {
    var s = String(hex).replace('#', '').trim();
    if (s.length === 3) s = s[0] + s[0] + s[1] + s[1] + s[2] + s[2];
    var v = parseInt(s, 16);
    if (isNaN(v)) return WHITE;
    return (0xFF000000 | (v & 0xFFFFFF)) | 0;
  }

  // 供测试使用的内部函数
  var _internal = {
    encodeColor: encodeColor,
    writeVarint: writeVarint,
    crc32: crc32,
    storedDeflate: storedDeflate,
    gzipBytes: gzipBytes,
    gzipBytesSync: gzipBytesSync,
    toBase64: toBase64,
    fromBase64: fromBase64
  };

  return {
    SIZE: SIZE,
    COLOR_COUNT: COLOR_COUNT,
    WHITE: WHITE,
    buildPaletteBytes: buildPaletteBytes,
    encodeColumn: encodeColumn,
    encodeColumnSync: encodeColumnSync,
    sampleColumnColors: sampleColumnColors,
    hexOf: hexOf,
    parseHex: parseHex,
    toBase64: toBase64,
    fromBase64: fromBase64,
    _internal: _internal
  };
});
