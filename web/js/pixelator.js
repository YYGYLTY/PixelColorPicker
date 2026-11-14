/*
 * PixelColorPicker Web · 像素化引擎
 * ------------------------------------------------------------
 * 移植自 app/src/main/java/com/pixelcolorpicker/image/Pixelator.kt
 *
 * 核心思想：自适应超采样 + 核函数插值
 *  - 每个输出格在源图上有一个覆盖范围（footprint）；
 *  - 在 coverage 内取 N×N 个采样点，每个点用核函数取值；
 *  - 对采样结果求平均。
 *
 * 坐标映射与 Pixelator.kt 完全一致（拉伸语义，不做 cover 裁剪）：
 *  scale=1 时整张源图铺满输出框，源图与画布比例不同会被拉伸——与 App 行为相同。
 *  18 色板码协议与 App 完全一致。
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.PCPPixelator = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var ALGO = {
    NEAREST: 'nearest',
    BILINEAR: 'bilinear',
    BICUBIC: 'bicubic',
    LANCZOS: 'lanczos',
    AVERAGE: 'average',
    CUSTOM: 'custom'
  };

  /** 各算法的自适应采样上限 */
  var GRID_N = {
    nearest: 1,
    bilinear: 2,
    bicubic: 3,
    lanczos: 4,
    average: 6
  };

  // ---------------- 单点采样 ----------------

  function clampedIndex(v, max) {
    return v < 0 ? 0 : (v > max ? max : v);
  }

  function sampleNearest(src, srcW, srcH, px, py) {
    var x = clampedIndex(Math.round(px), srcW - 1);
    var y = clampedIndex(Math.round(py), srcH - 1);
    return src[y * srcW + x] | 0;
  }

  function getClamped(src, srcW, srcH, x, y) {
    var cx = clampedIndex(x, srcW - 1);
    var cy = clampedIndex(y, srcH - 1);
    return src[cy * srcW + cx] | 0;
  }

  function pack(a, r, g, b) {
    a = a < 0 ? 0 : (a > 255 ? 255 : a);
    r = r < 0 ? 0 : (r > 255 ? 255 : r);
    g = g < 0 ? 0 : (g > 255 ? 255 : g);
    b = b < 0 ? 0 : (b > 255 ? 255 : b);
    return ((a << 24) | (r << 16) | (g << 8) | b) | 0;
  }

  function sampleBilinear(src, srcW, srcH, px, py) {
    var x = px - 0.5, y = py - 0.5;
    var x0 = Math.floor(x), y0 = Math.floor(y);
    var fx = x - x0, fy = y - y0;

    var c00 = getClamped(src, srcW, srcH, x0, y0);
    var c10 = getClamped(src, srcW, srcH, x0 + 1, y0);
    var c01 = getClamped(src, srcW, srcH, x0, y0 + 1);
    var c11 = getClamped(src, srcW, srcH, x0 + 1, y0 + 1);

    var w00 = (1 - fx) * (1 - fy);
    var w10 = fx * (1 - fy);
    var w01 = (1 - fx) * fy;
    var w11 = fx * fy;

    var a = ((c00 >>> 24) & 0xFF) * w00 + ((c10 >>> 24) & 0xFF) * w10 +
            ((c01 >>> 24) & 0xFF) * w01 + ((c11 >>> 24) & 0xFF) * w11;
    var r = ((c00 >>> 16) & 0xFF) * w00 + ((c10 >>> 16) & 0xFF) * w10 +
            ((c01 >>> 16) & 0xFF) * w01 + ((c11 >>> 16) & 0xFF) * w11;
    var g = ((c00 >>> 8) & 0xFF) * w00 + ((c10 >>> 8) & 0xFF) * w10 +
            ((c01 >>> 8) & 0xFF) * w01 + ((c11 >>> 8) & 0xFF) * w11;
    var b = (c00 & 0xFF) * w00 + (c10 & 0xFF) * w10 +
            (c01 & 0xFF) * w01 + (c11 & 0xFF) * w11;

    return pack(a | 0, r | 0, g | 0, b | 0);
  }

  /** Catmull-Rom 三次卷积核（a = -0.5） */
  function cubicWeight(t) {
    var a = -0.5;
    var at = Math.abs(t);
    if (at <= 1) return (a + 2) * at * at * at - (a + 3) * at * at + 1;
    if (at < 2) return a * at * at * at - 5 * a * at * at + 8 * a * at - 4 * a;
    return 0;
  }

  /** Lanczos 核（a = 2） */
  function lanczosWeight(t) {
    var a = 2;
    var at = Math.abs(t);
    if (at < 1e-6) return 1;
    if (at >= a) return 0;
    var pt = Math.PI * t;
    return (Math.sin(pt) / pt) * (Math.sin(pt / a) / (pt / a));
  }

  function sampleConvolution(src, srcW, srcH, px, py, weightFn) {
    var x = px - 0.5, y = py - 0.5;
    var x0 = Math.floor(x), y0 = Math.floor(y);
    var fx = x - x0, fy = y - y0;

    var wx = [weightFn(-1 - fx), weightFn(-fx), weightFn(1 - fx), weightFn(2 - fx)];
    var wy = [weightFn(-1 - fy), weightFn(-fy), weightFn(1 - fy), weightFn(2 - fy)];

    var accA = 0, accR = 0, accG = 0, accB = 0, wSum = 0;
    for (var dy = 0; dy < 4; dy++) {
      for (var dx = 0; dx < 4; dx++) {
        var w = wx[dx] * wy[dy];
        var c = getClamped(src, srcW, srcH, x0 - 1 + dx, y0 - 1 + dy);
        accA += ((c >>> 24) & 0xFF) * w;
        accR += ((c >>> 16) & 0xFF) * w;
        accG += ((c >>> 8) & 0xFF) * w;
        accB += (c & 0xFF) * w;
        wSum += w;
      }
    }
    if (wSum <= 0) return sampleNearest(src, srcW, srcH, px, py);
    return pack((accA / wSum) | 0, (accR / wSum) | 0, (accG / wSum) | 0, (accB / wSum) | 0);
  }

  function sampleByKernel(src, srcW, srcH, px, py, kernel) {
    switch (kernel) {
      case ALGO.NEAREST: return sampleNearest(src, srcW, srcH, px, py);
      case ALGO.BILINEAR:
      case ALGO.AVERAGE: return sampleBilinear(src, srcW, srcH, px, py);
      case ALGO.BICUBIC: return sampleConvolution(src, srcW, srcH, px, py, cubicWeight);
      case ALGO.LANCZOS: return sampleConvolution(src, srcW, srcH, px, py, lanczosWeight);
      default: return sampleBilinear(src, srcW, srcH, px, py);
    }
  }

  // ---------------- 视口几何 ----------------

  /**
   * 计算「画布框在源图上覆盖的区域」。
   *
   * @param srcW,srcH  源图尺寸
   * @param outW,outH  输出画布尺寸（决定框的宽高比）
   * @param scale      1 = 等比铺满画布框（cover）；>1 放大；<1 缩小
   * @param offsetX,offsetY 平移，单位为「源图宽/高的比例」
   * @returns {visibleW, visibleH, centerX, centerY}
   */
  function computeViewport(srcW, srcH, outW, outH, scale, offsetX, offsetY) {
    var boxAspect = outW / outH;      // 宽 / 高
    var srcAspect = srcW / srcH;

    var baseW, baseH;
    if (srcAspect > boxAspect) {
      // 源图更宽 → 高度撑满
      baseH = srcH;
      baseW = srcH * boxAspect;
    } else {
      baseW = srcW;
      baseH = srcW / boxAspect;
    }

    var s = scale > 0 ? scale : 1;
    var visibleW = baseW / s;
    var visibleH = baseH / s;

    return {
      visibleW: visibleW,
      visibleH: visibleH,
      centerX: srcW / 2 + offsetX * srcW,
      centerY: srcH / 2 + offsetY * srcH
    };
  }

  // ---------------- 主函数 ----------------

  /**
   * 执行像素化。
   *
   * @param {Int32Array|number[]} src 源像素（行优先 ARGB）
   * @returns {Int32Array} outW*outH 的 ARGB 像素
   */
  function pixelate(src, srcW, srcH, outW, outH, opts) {
    var o = opts || {};
    var algorithm = o.algorithm || ALGO.NEAREST;
    var customKernel = o.customKernel || ALGO.BILINEAR;
    var customSamples = o.customSamples == null ? 4 : o.customSamples;
    var scale = o.scale == null ? 1 : o.scale;
    var offsetX = o.offsetX || 0;
    var offsetY = o.offsetY || 0;
    var rotationDeg = o.rotationDeg || 0;

    if (!(outW >= 1) || !(outH >= 1)) throw new Error('输出尺寸非法');
    if (src.length < srcW * srcH) throw new Error('源像素不足');

    var out = new Int32Array(outW * outH);

    // 自定义算法 = 内核 + 采样密度
    var kernel;
    var gridN;
    if (algorithm === ALGO.CUSTOM) {
      kernel = (customKernel === ALGO.CUSTOM) ? ALGO.BILINEAR : customKernel;
      gridN = Math.max(1, Math.min(8, customSamples | 0));
    } else {
      kernel = algorithm;
      gridN = GRID_N[algorithm] || 1;
    }

    // [对齐 App] 与 Pixelator.kt 一致：整张源图铺满输出框（拉伸），不做 cover 裁剪
    var safeScale = Math.max(0.01, scale);
    var fw = srcW / (safeScale * outW);
    var fh = srcH / (safeScale * outH);

    // 采样网格数：内置算法按覆盖自适应，自定义严格按用户密度
    var nx, ny;
    if (algorithm === ALGO.CUSTOM) {
      nx = gridN;
      ny = gridN;
    } else {
      nx = Math.max(1, Math.min(gridN, Math.ceil(fw)));
      ny = Math.max(1, Math.min(gridN, Math.ceil(fh)));
    }

    var rad = -rotationDeg * Math.PI / 180;
    var cosT = Math.cos(rad), sinT = Math.sin(rad);
    var srcCenterX = srcW / 2 + offsetX * srcW;
    var srcCenterY = srcH / 2 + offsetY * srcH;

    for (var j = 0; j < outH; j++) {
      var v = (j + 0.5) / outH;
      for (var i = 0; i < outW; i++) {
        var u = (i + 0.5) / outW;

        var lx = (u - 0.5) * srcW;
        var ly = (v - 0.5) * srcH;

        // 逆旋转
        var rx = lx * cosT - ly * sinT;
        var ry = lx * sinT + ly * cosT;

        // 逆缩放 + 中心/平移
        var cx = srcCenterX + rx / safeScale;
        var cy = srcCenterY + ry / safeScale;

        if (kernel === ALGO.NEAREST) {
          out[j * outW + i] = sampleNearest(src, srcW, srcH, cx, cy);
          continue;
        }

        var accA = 0, accR = 0, accG = 0, accB = 0, count = 0;
        for (var gy = 0; gy < ny; gy++) {
          var oy = (ny === 1) ? 0 : ((gy + 0.5) / ny - 0.5) * fh;
          for (var gx = 0; gx < nx; gx++) {
            var ox = (nx === 1) ? 0 : ((gx + 0.5) / nx - 0.5) * fw;
            var c = sampleByKernel(src, srcW, srcH, cx + ox, cy + oy, kernel);
            accA += (c >>> 24) & 0xFF;
            accR += (c >>> 16) & 0xFF;
            accG += (c >>> 8) & 0xFF;
            accB += c & 0xFF;
            count++;
          }
        }
        out[j * outW + i] = count === 0
          ? 0
          : ((accA / count) | 0) << 24 | ((accR / count) | 0) << 16 |
            ((accG / count) | 0) << 8 | ((accB / count) | 0);
      }
    }
    return out;
  }

  /** 预览尺寸：限制最长边 */
  function previewSize(outW, outH, maxEdge) {
    var maxDim = Math.max(outW, outH);
    if (maxDim <= maxEdge) return [outW, outH];
    var s = maxEdge / maxDim;
    return [Math.max(1, Math.floor(outW * s)), Math.max(1, Math.floor(outH * s))];
  }

  return {
    ALGO: ALGO,
    GRID_N: GRID_N,
    pixelate: pixelate,
    previewSize: previewSize,
    computeViewport: computeViewport,
    _internal: {
      sampleNearest: sampleNearest,
      sampleBilinear: sampleBilinear,
      sampleConvolution: sampleConvolution,
      cubicWeight: cubicWeight,
      lanczosWeight: lanczosWeight
    }
  };
});