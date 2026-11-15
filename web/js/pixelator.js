/**
 * Pixelator.js - 1:1 照搬 app/src/main/java/com/pixelcolorpicker/image/Pixelator.kt
 *
 * 本文件为逐行翻译，不再有历史改动残留。
 * 对应 Kotlin 源：Pixelator.kt（382 行）
 *
 * 约定：
 *  - 颜色用 32 位有符号整数 ARGB（与 Kotlin Int 一致）
 *  - ushr -> >>>，>> -> >>
 *  - Float 运算用 Math.fround 保持单精度语义
 */
(function (global) {
  'use strict';

  /** 像素化算法（与 Kotlin enum PixelAlgorithm 一一对应） */
  var PixelAlgorithm = {
    Nearest: 0,
    Bilinear: 1,
    Bicubic: 2,
    Lanczos: 3,
    Average: 4,
    Custom: 5
  };

  // app.js 传的是字符串（'nearest'/'custom'...），这里统一转成枚举
  var NAME_TO_ALGO = {
    nearest: PixelAlgorithm.Nearest,
    bilinear: PixelAlgorithm.Bilinear,
    bicubic: PixelAlgorithm.Bicubic,
    lanczos: PixelAlgorithm.Lanczos,
    average: PixelAlgorithm.Average,
    custom: PixelAlgorithm.Custom
  };

  function toAlgo(v, dflt) {
    if (typeof v === 'number') return v;
    if (typeof v === 'string') {
      var k = v.toLowerCase();
      if (Object.prototype.hasOwnProperty.call(NAME_TO_ALGO, k)) return NAME_TO_ALGO[k];
    }
    return dflt;
  }

  /** Kotlin Float 单精度语义 */
  function f(x) { return Math.fround(x); }

  /** Kotlin round(Float): Int —— 四舍五入到最近整数（.5 向上） */
  function kRound(x) { return Math.floor(f(x) + 0.5); }

  /** Kotlin Int.coerceIn */
  function coerceIn(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  /** (c ushr 24) and 0xFF */
  function a8(c) { return (c >>> 24) & 0xFF; }
  function r8(c) { return (c >>> 16) & 0xFF; }
  function g8(c) { return (c >>> 8) & 0xFF; }
  function b8(c) { return c & 0xFF; }

  /** Kotlin: (a shl 24) or (r shl 16) or (g shl 8) or b */
  function pack(a, r, g, b) {
    a = coerceIn(Math.trunc(a), 0, 255);
    r = coerceIn(Math.trunc(r), 0, 255);
    g = coerceIn(Math.trunc(g), 0, 255);
    b = coerceIn(Math.trunc(b), 0, 255);
    return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
  }

  // ---------- 采样 ----------

  /** sampleNearest：round 后 clamp */
  function sampleNearest(src, srcW, srcH, px, py) {
    var x = coerceIn(kRound(px), 0, srcW - 1);
    var y = coerceIn(kRound(py), 0, srcH - 1);
    return src[y * srcW + x] | 0;
  }

  function getClamped(src, srcW, srcH, x, y) {
    var cx = coerceIn(x, 0, srcW - 1);
    var cy = coerceIn(y, 0, srcH - 1);
    return src[cy * srcW + cx] | 0;
  }

  function blend4(c00, c10, c01, c11, fx, fy) {
    var w00 = (1 - fx) * (1 - fy);
    var w10 = fx * (1 - fy);
    var w01 = (1 - fx) * fy;
    var w11 = fx * fy;
    var a = a8(c00) * w00 + a8(c10) * w10 + a8(c01) * w01 + a8(c11) * w11;
    var r = r8(c00) * w00 + r8(c10) * w10 + r8(c01) * w01 + r8(c11) * w11;
    var g = g8(c00) * w00 + g8(c10) * w10 + g8(c01) * w01 + g8(c11) * w11;
    var b = b8(c00) * w00 + b8(c10) * w10 + b8(c01) * w01 + b8(c11) * w11;
    return pack(a, r, g, b);
  }

  function sampleBilinear(src, srcW, srcH, px, py) {
    var x = f(px - 0.5);
    var y = f(py - 0.5);
    var x0 = Math.floor(x);
    var y0 = Math.floor(y);
    var fx = f(x - x0);
    var fy = f(y - y0);
    var c00 = getClamped(src, srcW, srcH, x0, y0);
    var c10 = getClamped(src, srcW, srcH, x0 + 1, y0);
    var c01 = getClamped(src, srcW, srcH, x0, y0 + 1);
    var c11 = getClamped(src, srcW, srcH, x0 + 1, y0 + 1);
    return blend4(c00, c10, c01, c11, fx, fy);
  }

  /** 三次卷积核（a = -0.5，Catmull-Rom 风格） */
  function cubicWeight(t) {
    var a = -0.5;
    var at = Math.abs(t);
    // 严格按 JVM 的「左结合 + float 逐步运算」求值，
    // 任何一步用 double 都会导致权重末位不同，最终表现为 255/254 抖动。
    if (at <= 1) {
      var p = f(a + 2);
      p = f(p * at); p = f(p * at); p = f(p * at);   // (a+2)*at*at*at
      var q = f(a + 3);
      q = f(q * at); q = f(q * at);                   // (a+3)*at*at
      return f(f(p - q) + 1);
    }
    if (at < 2) {
      var p2 = f(a * at); p2 = f(p2 * at); p2 = f(p2 * at);   // a*at^3
      var q2 = f(5 * a); q2 = f(q2 * at); q2 = f(q2 * at);    // 5a*at^2
      var r2 = f(8 * a); r2 = f(r2 * at);                      // 8a*at
      var s2 = f(4 * a);                                       // 4a
      return f(f(f(p2 - q2) + r2) - s2);
    }
    return 0;
  }

  /** Lanczos 核（a = 2） */
  function lanczosWeight(t) {
    var a = 2;
    var at = Math.abs(t);
    if (at < 1e-6) return 1;
    if (at >= a) return 0;
    // Kotlin: (sin(pt) / pt) * (sin(pt / a) / (pt / a))，全程 float
    var pt = f(Math.PI * t);
    var s1 = f(Math.sin(pt));
    var r1 = f(s1 / pt);
    var pta = f(pt / a);
    var s2 = f(Math.sin(pta));
    var r2 = f(s2 / pta);
    return f(r1 * r2);
  }

  function sampleBicubic(src, srcW, srcH, px, py) {
    var x = f(px - 0.5);
    var y = f(py - 0.5);
    var x0 = Math.floor(x);
    var y0 = Math.floor(y);
    var fx = f(x - x0);
    var fy = f(y - y0);
    // JVM 里 -1f - fx 是 Float 运算，先 fround 再传参，否则末位差 1
    var wx0 = cubicWeight(f(-1 - fx)), wx1 = cubicWeight(f(-fx));
    var wx2 = cubicWeight(f(1 - fx)), wx3 = cubicWeight(f(2 - fx));
    var wy0 = cubicWeight(f(-1 - fy)), wy1 = cubicWeight(f(-fy));
    var wy2 = cubicWeight(f(1 - fy)), wy3 = cubicWeight(f(2 - fy));
    var accA = 0, accR = 0, accG = 0, accB = 0, wSum = 0;
    for (var dy = 0; dy < 4; dy++) {
      var wy = dy === 0 ? wy0 : dy === 1 ? wy1 : dy === 2 ? wy2 : wy3;
      for (var dx = 0; dx < 4; dx++) {
        var wx = dx === 0 ? wx0 : dx === 1 ? wx1 : dx === 2 ? wx2 : wx3;
        var w = f(wx * wy);
        var c = getClamped(src, srcW, srcH, x0 - 1 + dx, y0 - 1 + dy);
        accA = f(accA + f(a8(c) * w)); accR = f(accR + f(r8(c) * w));
        accG = f(accG + f(g8(c) * w)); accB = f(accB + f(b8(c) * w));
        wSum = f(wSum + w);
      }
    }
    if (wSum <= 0) return sampleNearest(src, srcW, srcH, px, py);
    return pack(f(accA / wSum), f(accR / wSum), f(accG / wSum), f(accB / wSum));
  }

  function sampleLanczos(src, srcW, srcH, px, py) {
    var x = f(px - 0.5);
    var y = f(py - 0.5);
    var x0 = Math.floor(x);
    var y0 = Math.floor(y);
    var fx = f(x - x0);
    var fy = f(y - y0);
    var wx0 = lanczosWeight(f(-1 - fx)), wx1 = lanczosWeight(f(-fx));
    var wx2 = lanczosWeight(f(1 - fx)), wx3 = lanczosWeight(f(2 - fx));
    var wy0 = lanczosWeight(f(-1 - fy)), wy1 = lanczosWeight(f(-fy));
    var wy2 = lanczosWeight(f(1 - fy)), wy3 = lanczosWeight(f(2 - fy));
    var accA = 0, accR = 0, accG = 0, accB = 0, wSum = 0;
    for (var dy = 0; dy < 4; dy++) {
      var wy = dy === 0 ? wy0 : dy === 1 ? wy1 : dy === 2 ? wy2 : wy3;
      for (var dx = 0; dx < 4; dx++) {
        var wx = dx === 0 ? wx0 : dx === 1 ? wx1 : dx === 2 ? wx2 : wx3;
        var w = f(wx * wy);
        var c = getClamped(src, srcW, srcH, x0 - 1 + dx, y0 - 1 + dy);
        accA = f(accA + f(a8(c) * w)); accR = f(accR + f(r8(c) * w));
        accG = f(accG + f(g8(c) * w)); accB = f(accB + f(b8(c) * w));
        wSum = f(wSum + w);
      }
    }
    if (wSum <= 0) return sampleNearest(src, srcW, srcH, px, py);
    return pack(f(accA / wSum), f(accR / wSum), f(accG / wSum), f(accB / wSum));
  }

  function sample(src, srcW, srcH, px, py, algorithm) {
    switch (algorithm) {
      case PixelAlgorithm.Nearest: return sampleNearest(src, srcW, srcH, px, py);
      case PixelAlgorithm.Bilinear:
      case PixelAlgorithm.Average: return sampleBilinear(src, srcW, srcH, px, py);
      case PixelAlgorithm.Bicubic: return sampleBicubic(src, srcW, srcH, px, py);
      case PixelAlgorithm.Lanczos: return sampleLanczos(src, srcW, srcH, px, py);
      default: return sampleBilinear(src, srcW, srcH, px, py);
    }
  }

  /**
   * 旧版兼容的最近邻缩放（对应 Kotlin nearestViaAndroid 的 catch 分支）。
   *
   * Kotlin 用 Android createScaledBitmap(null filter) 做最近邻，其采样中心是
   * (i + 0.5) * srcW / outW。wasm 无 Bitmap，这里直接用同一公式自研采样，
   * 结果与 createScaledBitmap 等价。
   */
  function nearestCompat(src, srcW, srcH, outW, outH) {
    var out = new Int32Array(outW * outH);
    for (var j = 0; j < outH; j++) {
      for (var i = 0; i < outW; i++) {
        // Android createScaledBitmap(filter=false) 的最近邻取整是 floor，不是 round。
        // 例如 2x2 -> 2x2 时 (i+0.5)*2/2 = 0.5，floor 得 0（取第一个像素），
        // 若用 round 会得 1，导致整张图偏向最后一行/列。
        var x = Math.floor((i + 0.5) * srcW / outW);
        var y = Math.floor((j + 0.5) * srcH / outH);
        x = coerceIn(x, 0, srcW - 1);
        y = coerceIn(y, 0, srcH - 1);
        out[j * outW + i] = src[y * srcW + x] | 0;
      }
    }
    return out;
  }

  /**
   * 主入口（1:1 对 Pixelator.pixelate）。
   *
   * @param {Int32Array|number[]} src ARGB 源像素（行优先）
   * @param {number} srcW 源宽
   * @param {number} srcH 源高
   * @param {number} outW 输出宽
   * @param {number} outH 输出高
   * @param {object} opts { scale, offsetX, offsetY, rotationDeg, algorithm, customKernel, customSamples }
   * @returns {Int32Array} 输出像素
   */
  function pixelate(src, srcW, srcH, outW, outH, opts) {
    var o = opts || {};
    var scale = o.scale == null ? 1 : o.scale;
    var offsetX = o.offsetX == null ? 0 : o.offsetX;
    var offsetY = o.offsetY == null ? 0 : o.offsetY;
    var rotationDeg = o.rotationDeg == null ? 0 : o.rotationDeg;
    var algorithm = toAlgo(o.algorithm, PixelAlgorithm.Nearest);
    var customKernel = toAlgo(o.customKernel, PixelAlgorithm.Bilinear);
    var customSamples = o.customSamples == null ? 4 : o.customSamples;

    // require(src.size >= srcW * srcH)
    if (src.length < srcW * srcH) throw new Error('Pixelator: src 像素不足');
    // require(outW >= 1 && outH >= 1)
    if (!(outW >= 1 && outH >= 1)) throw new Error('Pixelator: 输出尺寸非法');

    // 自定义算法：实际使用「内核 + 采样密度」
    var kernel;
    if (algorithm === PixelAlgorithm.Custom) {
      kernel = (customKernel === PixelAlgorithm.Custom) ? PixelAlgorithm.Bilinear : customKernel;
    } else {
      kernel = algorithm;
    }

    // 旧版兼容路径
    var singleNearest = (algorithm === PixelAlgorithm.Nearest) ||
      (algorithm === PixelAlgorithm.Custom &&
        customKernel === PixelAlgorithm.Nearest && customSamples <= 1);
    if (singleNearest && rotationDeg === 0 && offsetX === 0 && offsetY === 0 && scale === 1) {
      return nearestCompat(src, srcW, srcH, outW, outH);
    }

    var out = new Int32Array(outW * outH);
    var safeScale = f(Math.max(scale, 0.01));
    // app.js 额外传 scaleX/scaleY（非等比图片的分轴缩放）。
    // Kotlin 版只有单一 scale；不传时两者等于 safeScale，行为与 App 一致。
    var scaleX = o.scaleX == null ? safeScale : f(Math.max(o.scaleX, 0.01));
    var scaleY = o.scaleY == null ? safeScale : f(Math.max(o.scaleY, 0.01));

    // val rad = Math.toRadians(-rotationDeg)
    var rad = f(-rotationDeg * Math.PI / 180);
    var cosT = f(Math.cos(rad));
    var sinT = f(Math.sin(rad));

    // 每个输出像素在源图上的覆盖范围（像素单位）
    var fw = f(srcW / f(safeScale * outW));
    var fh = f(srcH / f(safeScale * outH));

    // 采样网格数量
    var gridN;
    if (algorithm === PixelAlgorithm.Custom) {
      gridN = coerceIn(customSamples | 0, 1, 8);
    } else {
      switch (algorithm) {
        case PixelAlgorithm.Nearest: gridN = 1; break;
        case PixelAlgorithm.Bilinear: gridN = 2; break;
        case PixelAlgorithm.Bicubic: gridN = 3; break;
        case PixelAlgorithm.Lanczos: gridN = 4; break;
        case PixelAlgorithm.Average: gridN = 6; break;
        default: gridN = 8; break;
      }
    }

    var nx, ny;
    if (algorithm === PixelAlgorithm.Custom) {
      nx = gridN;
      ny = gridN;
    } else {
      // ceil(fw).toInt().coerceIn(1, gridN)
      nx = coerceIn(Math.trunc(Math.ceil(fw)), 1, gridN);
      ny = coerceIn(Math.trunc(Math.ceil(fh)), 1, gridN);
    }

    var srcCenterX = f(f(srcW / 2) + f(offsetX * srcW));
    var srcCenterY = f(f(srcH / 2) + f(offsetY * srcH));

    for (var j = 0; j < outH; j++) {
      for (var i = 0; i < outW; i++) {
        // 格中心映射
        var u = f(f(i + 0.5) / outW);
        var v = f(f(j + 0.5) / outH);
        var lx = f(f(u - 0.5) * srcW);
        var ly = f(f(v - 0.5) * srcH);
        // 逆旋转
        var rx = f(lx * cosT - ly * sinT);
        var ry = f(lx * sinT + ly * cosT);
        // 逆缩放 + 中心/平移
        // [对齐 App] Kotlin 只有单一 scale：cx = center + rotate(...)/scale。
        // 旧版用 scaleX/scaleY 分轴除，旋转时 X/Y 被独立缩放 → 图片拉伸变形。
        // 统一用 safeScale，分轴只在未旋转时可退化使用。
        var useSX = (rotationDeg === 0 && o.scaleX != null) ? scaleX : safeScale;
        var useSY = (rotationDeg === 0 && o.scaleY != null) ? scaleY : safeScale;
        var cx = f(srcCenterX + f(rx / useSX));
        var cy = f(srcCenterY + f(ry / useSY));

        if (kernel === PixelAlgorithm.Nearest) {
          out[j * outW + i] = sampleNearest(src, srcW, srcH, cx, cy);
          continue;
        }

        var accR = 0, accG = 0, accB = 0, accA = 0, count = 0;
        for (var gy = 0; gy < ny; gy++) {
          var oy2 = ny === 1 ? 0 : f((((gy + 0.5) / ny) - 0.5) * fh);
          for (var gx = 0; gx < nx; gx++) {
            var ox2 = nx === 1 ? 0 : f((((gx + 0.5) / nx) - 0.5) * fw);
            var c = sample(src, srcW, srcH, f(cx + ox2), f(cy + oy2), kernel);
            accA += a8(c);
            accR += r8(c);
            accG += g8(c);
            accB += b8(c);
            count++;
          }
        }

        if (count === 0) {
          out[j * outW + i] = 0;
        } else {
          var aAvg = Math.trunc(accA / count) & 0xFF;
          var rAvg = Math.trunc(accR / count) & 0xFF;
          var gAvg = Math.trunc(accG / count) & 0xFF;
          var bAvg = Math.trunc(accB / count) & 0xFF;
          out[j * outW + i] = ((aAvg << 24) | (rAvg << 16) | (gAvg << 8) | bAvg) | 0;
        }
      }
    }
    return out;
  }

  /** 计算预览用尺寸（1:1 对 previewSize） */
  function previewSize(outW, outH, maxEdge) {
    var maxDim = Math.max(outW, outH);
    if (maxDim <= maxEdge) return [outW, outH];
    var s = f(maxEdge / maxDim);
    // 关键：Kotlin 里 `outW * s` 是 Int * Float，JVM 按「float 乘法」算，
    // 999 * 0.5125125f = 512.0f（float 舍入后恰为整数）-> (int) 得 512。
    // 若用 double 乘则得 511.99999 -> 511，与 App 不一致。
    // 所以乘积也必须 fround 回 float 精度。
    return [Math.max(1, Math.trunc(f(outW * s))), Math.max(1, Math.trunc(f(outH * s)))];
  }

  /** 粗略估算像素数 */
  function pixelCount(w, h) { return w * h; }

  var API = {
    PixelAlgorithm: PixelAlgorithm,
    pixelate: pixelate,
    previewSize: previewSize,
    pixelCount: pixelCount
  };

  // app.js 用的是 window.PCPPixelator；同时暴露 Pixelator 供测试与兼容。
  API.ALGO = PixelAlgorithm;
  if (typeof module !== 'undefined' && module.exports) module.exports = API;
  global.PCPPixelator = API;
  global.Pixelator = API;
})(typeof window !== 'undefined' ? window : globalThis);
