/*
 * PixelColorPicker Web · 主逻辑
 */
(function () {
  'use strict';

  var Codec = window.PCPCodec;
  var Pix = window.PCPPixelator;
  var I18n = window.PCPI18n;
  var t = I18n.t;

  // ---------------- 全局状态 ----------------
  var state = {
    // 源图
    srcPixels: null,      // Int32Array ARGB
    srcW: 0,
    srcH: 0,
    srcName: '',

    // 裁剪参数
    scale: 1,
    offsetX: 0,
    offsetY: 0,
    rotation: 0,

    // 画布
    outW: 16,
    outH: 16,

    // 算法
    algorithm: 'nearest',
    customKernel: 'bilinear',
    customSamples: 4,

    // 结果
    result: null,         // Int32Array
    resultW: 0,
    resultH: 0,
    resultTime: 0,

    // 编辑器
    editPixels: null,
    editW: 16,
    editH: 16,
    editTool: 'brush',
    editColor: 0xFFFFFFFF | 0,
    undoStack: [],
    redoStack: [],

    // 编码分页
    codeShown: 16,

    // 主题/语言
    theme: 'dark'
  };

  // ---------------- 工具函数 ----------------

  function $(id) { return document.getElementById(id); }

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  var toastTimer = null;
  function toast(msg) {
    var el = $('toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 1900);
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).then(function () {
        toast(t('toast.copied'));
      }).catch(function () {
        fallbackCopy(text);
      });
    }
    fallbackCopy(text);
    return Promise.resolve();
  }

  function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand('copy');
      toast(t('toast.copied'));
    } catch (e) {
      toast(t('toast.copyFail'));
    }
    document.body.removeChild(ta);
  }

  /** Int32Array ARGB → ImageData */
  function toImageData(pixels, w, h) {
    var img = new ImageData(w, h);
    var d = img.data;
    for (var i = 0, j = 0; i < pixels.length; i++, j += 4) {
      var c = pixels[i];
      d[j] = (c >>> 16) & 0xFF;
      d[j + 1] = (c >>> 8) & 0xFF;
      d[j + 2] = c & 0xFF;
      d[j + 3] = (c >>> 24) & 0xFF;
    }
    return img;
  }

  /** 计算唯一颜色集合 */
  function uniqueColors(pixels) {
    var map = new Map();
    for (var i = 0; i < pixels.length; i++) {
      var c = pixels[i];
      map.set(c, (map.get(c) || 0) + 1);
    }
    return map;
  }

  // ---------------- 主题 ----------------

  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('pcp.theme', theme); } catch (e) {}
  }

  function initTheme() {
    var saved = null;
    try { saved = localStorage.getItem('pcp.theme'); } catch (e) {}
    if (!saved) {
      saved = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
        ? 'light' : 'dark';
    }
    applyTheme(saved);
  }

  // ---------------- 语言 ----------------

  function applyLang(lang) {
    I18n.setLang(lang);
    try { localStorage.setItem('pcp.lang', lang); } catch (e) {}

    // 更新所有 data-i18n 节点
    var nodes = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      el.textContent = t(el.getAttribute('data-i18n'));
    }

    // 语言按钮
    var btns = document.querySelectorAll('#langSeg button');
    for (var j = 0; j < btns.length; j++) {
      btns[j].classList.toggle('active', btns[j].getAttribute('data-lang') === lang);
    }

    // 动态内容刷新
    refreshResultMeta();
    refreshPaletteMeta();
    if (state.result) {
      renderPalette();
      renderCodes(true);
    }
    updateEditorMeta();
    updateSamplesOut();
    $('scaleOut').textContent = state.scale.toFixed(2) + '×';
    $('rotOut').textContent = Math.round(state.rotation) + '°';
    renderHistory();
  }

  function initLang() {
    var saved = null;
    try { saved = localStorage.getItem('pcp.lang'); } catch (e) {}
    if (!saved) {
      var nav = (navigator.language || 'zh').toLowerCase();
      if (nav.indexOf('ja') === 0) saved = 'ja';
      else if (nav.indexOf('zh') === 0) saved = 'zh';
      else if (nav.indexOf('en') === 0) saved = 'en';
      else saved = 'zh';
    }
    applyLang(saved);
  }

  // ---------------- 标签切换 ----------------

  function switchTab(name) {
    var tabs = document.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.toggle('active', tabs[i].getAttribute('data-tab') === name);
    }
    var panels = document.querySelectorAll('.panel');
    for (var j = 0; j < panels.length; j++) {
      panels[j].classList.toggle('active', panels[j].id === 'panel-' + name);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function initTabs() {
    var tabs = document.querySelectorAll('.tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].addEventListener('click', function () {
        switchTab(this.getAttribute('data-tab'));
      });
    }
  }

  // ---------------- 图片加载 ----------------

  var MAX_SRC_EDGE = 2600; // 超大图先降采样，保证浏览器性能

  function loadImageFile(file) {
    if (!file || !/^image\//.test(file.type)) return;
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function () {
      URL.revokeObjectURL(url);

      var w = img.naturalWidth || img.width;
      var h = img.naturalHeight || img.height;
      var scaled = false;

      if (Math.max(w, h) > MAX_SRC_EDGE) {
        var s = MAX_SRC_EDGE / Math.max(w, h);
        w = Math.max(1, Math.round(w * s));
        h = Math.max(1, Math.round(h * s));
        scaled = true;
      }

      var cv = document.createElement('canvas');
      cv.width = w;
      cv.height = h;
      var ctx = cv.getContext('2d', { willReadFrequently: true });
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, w, h);

      var data = ctx.getImageData(0, 0, w, h).data;
      var px = new Int32Array(w * h);
      for (var i = 0, j = 0; i < px.length; i++, j += 4) {
        px[i] = ((data[j + 3] << 24) | (data[j] << 16) | (data[j + 1] << 8) | data[j + 2]) | 0;
      }

      state.srcPixels = px;
      state.srcW = w;
      state.srcH = h;
      state.srcName = file.name || 'image';

      // 重置裁剪参数
      state.scale = 1;
      state.offsetX = 0;
      state.offsetY = 0;
      state.rotation = 0;
      $('scaleRange').value = 1;
      $('rotRange').value = 0;
      $('scaleOut').textContent = '1.00×';
      $('rotOut').textContent = '0°';

      showImagePreview();
      $('cropCard').hidden = false;
      drawCropStage();

      toast(t('toast.loadedImage', { name: state.srcName }));
      if (scaled) {
        setTimeout(function () {
          toast(t('toast.imageTooBig', { n: MAX_SRC_EDGE }));
        }, 900);
      }
    };
    img.onerror = function () {
      URL.revokeObjectURL(url);
      toast('图片加载失败');
    };
    img.src = url;
  }

  function showImagePreview() {
    $('dzInner').hidden = true;
    $('dzPreview').hidden = false;

    var cv = $('previewCanvas');
    var maxW = 320, maxH = 220;
    var s = Math.min(maxW / state.srcW, maxH / state.srcH, 1);
    cv.width = Math.max(1, Math.round(state.srcW * s));
    cv.height = Math.max(1, Math.round(state.srcH * s));
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(toCanvas(state.srcPixels, state.srcW, state.srcH), 0, 0, cv.width, cv.height);

    $('previewMeta').textContent = state.srcW + ' × ' + state.srcH;
  }

  function toCanvas(pixels, w, h) {
    var cv = document.createElement('canvas');
    cv.width = w;
    cv.height = h;
    cv.getContext('2d').putImageData(toImageData(pixels, w, h), 0, 0);
    return cv;
  }

  // ---------------- 裁剪预览 ----------------

  function drawCropStage() {
    if (!state.srcPixels) return;

    var stage = $('cropStage');
    var cv = $('cropCanvas');
    var dpr = Math.min(window.devicePixelRatio || 1, 2);

    var stageW = stage.clientWidth - 2;
    var maxH = 330;
    var boxAspect = state.outW / state.outH;

    // 展示框尺寸（适配 stage）
    var boxW = Math.min(stageW, maxH * boxAspect);
    var boxH = boxW / boxAspect;
    if (boxH > maxH) { boxH = maxH; boxW = boxH * boxAspect; }

    cv.style.width = boxW + 'px';
    cv.style.height = boxH + 'px';
    cv.width = Math.round(boxW * dpr);
    cv.height = Math.round(boxH * dpr);

    var ctx = cv.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, boxW, boxH);

    // 源图 canvas
    var srcCv = toCanvas(state.srcPixels, state.srcW, state.srcH);

    var vp = Pix.computeViewport(
      state.srcW, state.srcH, state.outW, state.outH,
      state.scale, state.offsetX, state.offsetY
    );

    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, boxW, boxH);
    ctx.clip();

    // 目标：把源图上 vp.visibleW × vp.visibleH 的区域画到 boxW × boxH
    var k = boxW / vp.visibleW;
    var cx = vp.centerX;
    var cy = vp.centerY;

    ctx.translate(boxW / 2, boxH / 2);
    ctx.rotate(-state.rotation * Math.PI / 180);
    ctx.scale(k, k);
    ctx.translate(-cx, -cy);

    ctx.drawImage(srcCv, 0, 0);
    ctx.restore();

    // 网格线（格子多时不画）
    if (state.outW <= 64 && state.outH <= 64) {
      ctx.save();
      ctx.strokeStyle = 'rgba(255,255,255,0.18)';
      ctx.lineWidth = 1;
      var cw = boxW / state.outW;
      var ch = boxH / state.outH;
      for (var i = 1; i < state.outW; i++) {
        ctx.beginPath();
        ctx.moveTo(i * cw, 0);
        ctx.lineTo(i * cw, boxH);
        ctx.stroke();
      }
      for (var j = 1; j < state.outH; j++) {
        ctx.beginPath();
        ctx.moveTo(0, j * ch);
        ctx.lineTo(boxW, j * ch);
        ctx.stroke();
      }
      ctx.restore();
    }
  }

  // ---------------- 拖动交互 ----------------

  function initCropDrag() {
    var stage = $('cropStage');
    var dragging = false;
    var startX = 0, startY = 0;
    var startOffX = 0, startOffY = 0;
    var pinchStart = 0;
    var pinchScale = 1;

    function getViewportBox() {
      var cv = $('cropCanvas');
      var boxW = parseFloat(cv.style.width) || 200;
      var vp = Pix.computeViewport(
        state.srcW, state.srcH, state.outW, state.outH,
        state.scale, state.offsetX, state.offsetY
      );
      var k = boxW / vp.visibleW; // px per source-px
      return { boxW: boxW, k: k, vp: vp };
    }

    stage.addEventListener('pointerdown', function (e) {
      if (!state.srcPixels) return;
      dragging = true;
      startX = e.clientX;
      startY = e.clientY;
      startOffX = state.offsetX;
      startOffY = state.offsetY;
      stage.setPointerCapture(e.pointerId);
    });

    stage.addEventListener('pointermove', function (e) {
      if (!dragging || !state.srcPixels) return;

      var info = getViewportBox();
      var dx = e.clientX - startX;
      var dy = e.clientY - startY;

      // 屏幕像素 → 源图像素 → 归一化
      var sx = dx / info.k;
      var sy = dy / info.k;

      state.offsetX = startOffX + sx / state.srcW;
      state.offsetY = startOffY + sy / state.srcH;

      drawCropStage();
    });

    function endDrag(e) {
      if (!dragging) return;
      dragging = false;
      try { stage.releasePointerCapture(e.pointerId); } catch (err) {}
    }

    stage.addEventListener('pointerup', endDrag);
    stage.addEventListener('pointercancel', endDrag);

    // 滚轮缩放
    stage.addEventListener('wheel', function (e) {
      if (!state.srcPixels) return;
      e.preventDefault();
      var delta = e.deltaY > 0 ? 0.92 : 1.08;
      state.scale = clamp(state.scale * delta, 0.2, 4);
      $('scaleRange').value = state.scale;
      $('scaleOut').textContent = state.scale.toFixed(2) + '×';
      drawCropStage();
    }, { passive: false });

    // 双指缩放
    stage.addEventListener('touchstart', function (e) {
      if (e.touches.length === 2) {
        pinchStart = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        pinchScale = state.scale;
      }
    }, { passive: true });

    stage.addEventListener('touchmove', function (e) {
      if (e.touches.length === 2 && pinchStart > 0) {
        e.preventDefault();
        var d = Math.hypot(
          e.touches[0].clientX - e.touches[1].clientX,
          e.touches[0].clientY - e.touches[1].clientY
        );
        state.scale = clamp(pinchScale * (d / pinchStart), 0.2, 4);
        $('scaleRange').value = state.scale;
        $('scaleOut').textContent = state.scale.toFixed(2) + '×';
        drawCropStage();
      }
    }, { passive: false });

    stage.addEventListener('touchend', function () { pinchStart = 0; }, { passive: true });
  }

  // ---------------- 生成 ----------------

  function readSizeInputs() {
    var w = clamp(parseInt($('inW').value, 10) || 16, 1, 999);
    var h = clamp(parseInt($('inH').value, 10) || 16, 1, 999);
    $('inW').value = w;
    $('inH').value = h;
    state.outW = w;
    state.outH = h;
  }

  function doGenerate() {
    if (!state.srcPixels) {
      toast(t('toast.noImage'));
      return;
    }
    readSizeInputs();

    var btn = $('generateBtn');
    var labelEl = btn.querySelector('span');
    var oldLabel = labelEl.getAttribute('data-i18n');
    btn.disabled = true;
    labelEl.textContent = t('btn.generating');

    // 让 UI 有机会刷新
    setTimeout(function () {
      var t0 = performance.now();
      var out;
      try {
        out = Pix.pixelate(
          state.srcPixels, state.srcW, state.srcH,
          state.outW, state.outH,
          {
            algorithm: state.algorithm,
            customKernel: state.customKernel,
            customSamples: state.customSamples,
            scale: state.scale,
            offsetX: state.offsetX,
            offsetY: state.offsetY,
            rotationDeg: state.rotation
          }
        );
      } catch (err) {
        toast('生成失败：' + err.message);
        btn.disabled = false;
        labelEl.textContent = t(oldLabel);
        return;
      }
      var ms = Math.round(performance.now() - t0);

      state.result = out;
      state.resultW = state.outW;
      state.resultH = state.outH;
      state.resultTime = ms;
      state.codeShown = 16;

      drawResult();
      renderPalette();
      renderCodes(true);

      btn.disabled = false;
      labelEl.textContent = t(oldLabel);

      toast(t('toast.generated', { w: state.outW, h: state.outH }));
    }, 16);
  }

  function drawResult() {
    if (!state.result) return;

    $('resultEmpty').hidden = true;
    var cv = $('resultCanvas');
    cv.hidden = false;

    cv.width = state.resultW;
    cv.height = state.resultH;
    var ctx = cv.getContext('2d');
    ctx.putImageData(toImageData(state.result, state.resultW, state.resultH), 0, 0);

    // 显示尺寸：整数倍放大，保持锐利
    var stage = $('resultStage');
    var availW = stage.clientWidth - 24;
    var availH = Math.min(window.innerHeight * 0.6, 520);

    var base = Math.min(availW / state.resultW, availH / state.resultH);
    var mult = base >= 1 ? Math.max(1, Math.floor(base)) : base;

    cv.style.width = Math.round(state.resultW * mult) + 'px';
    cv.style.height = Math.round(state.resultH * mult) + 'px';

    $('resultActions').hidden = false;
    refreshResultMeta();
  }

  function refreshResultMeta() {
    if (!state.result) {
      $('resultMeta').textContent = '';
      return;
    }
    var uniq = uniqueColors(state.result).size;
    var s = t('result.meta', { w: state.resultW, h: state.resultH, n: uniq });
    if (state.resultTime) {
      s += ' · ' + t('result.time', { ms: state.resultTime });
    }
    $('resultMeta').textContent = s;
  }

  // ---------------- 色板 ----------------

  /** 超过此数量改用 canvas 渲染，避免 DOM 节点爆炸 */
  var PALETTE_DOM_LIMIT = 2500;

  function renderPalette() {
    var emptyEl = $('paletteEmpty');
    var bodyEl = $('paletteBody');

    if (!state.result) {
      emptyEl.hidden = false;
      bodyEl.hidden = true;
      $('paletteMeta').textContent = '';
      return;
    }

    emptyEl.hidden = true;
    bodyEl.hidden = false;

    var W = state.resultW, H = state.resultH;
    var uniq = uniqueColors(state.result);
    var total = W * H;

    var metaText = W + ' × ' + H + ' · ' + uniq.size + ' 色';
    if (total > PALETTE_DOM_LIMIT) {
      metaText += ' · ' + t('palette.large');
    }
    $('paletteMeta').textContent = metaText;

    var grid = $('paletteGrid');
    grid.innerHTML = '';

    // 清掉可能存在的旧 canvas 与提示条（可能在 palette-scroll 内）
    var scroll = $('paletteScroll');
    var oldCv = scroll.querySelector('.palette-canvas');
    if (oldCv) oldCv.parentNode.removeChild(oldCv);
    var oldTip = scroll.querySelector('.palette-tip');
    if (oldTip) oldTip.parentNode.removeChild(oldTip);

    if (total > PALETTE_DOM_LIMIT) {
      renderPaletteCanvas(W, H);
    } else {
      renderPaletteDom(W, H, total);
    }
  }

  /** 小画布：DOM 格子（可 hover 放大、点击复制） */
  function renderPaletteDom(W, H, total) {
    var grid = $('paletteGrid');
    grid.classList.remove('canvas-mode');

    var cellSize = total > 2000 ? 22 : (total > 800 ? 28 : 34);
    grid.style.gridTemplateColumns = 'repeat(' + W + ', ' + cellSize + 'px)';
    grid.style.gap = total > 2000 ? '2px' : '4px';

    var frag = document.createDocumentFragment();
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var c = state.result[y * W + x];
        var hex = Codec.hexOf(c);
        var d = document.createElement('div');
        d.className = 'swatch';
        d.style.background = hex;
        d.dataset.x = x;
        d.dataset.y = y;
        d.dataset.hex = hex;
        d.title = hex + '  (' + x + ', ' + y + ')';
        frag.appendChild(d);
      }
    }
    grid.appendChild(frag);
  }

  /** 大画布：单个 canvas 渲染 + 坐标换算点击 */
  function renderPaletteCanvas(W, H) {
    var grid = $('paletteGrid');
    grid.classList.add('canvas-mode');
    grid.style.gridTemplateColumns = '';
    grid.style.gap = '0';

    // 每格像素数：限制总画布尺寸，避免超出浏览器 canvas 上限
    var cellPx = 1;
    var maxCanvasEdge = 4096;
    while (Math.max(W, H) * cellPx < 720 && cellPx < 12) cellPx++;
    if (Math.max(W, H) * cellPx > maxCanvasEdge) {
      cellPx = Math.max(1, Math.floor(maxCanvasEdge / Math.max(W, H)));
    }

    var cv = document.createElement('canvas');
    cv.className = 'palette-canvas';
    cv.width = W * cellPx;
    cv.height = H * cellPx;

    var ctx = cv.getContext('2d');
    var img = ctx.createImageData(W * cellPx, H * cellPx);
    var d = img.data;

    // 按缩放块填充，比逐像素 putImageData 快得多
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var c = state.result[y * W + x];
        var r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
        var a = (c >>> 24) & 0xFF;
        for (var dy = 0; dy < cellPx; dy++) {
          var rowBase = ((y * cellPx + dy) * (W * cellPx)) * 4;
          for (var dx = 0; dx < cellPx; dx++) {
            var idx = rowBase + (x * cellPx + dx) * 4;
            d[idx] = r; d[idx + 1] = g; d[idx + 2] = b; d[idx + 3] = a;
          }
        }
      }
    }
    ctx.putImageData(img, 0, 0);

    // 显示尺寸
    cv.style.width = Math.min(W * Math.max(1, Math.floor(700 / Math.max(W, H))), 720) + 'px';
    cv.style.imageRendering = 'pixelated';

    grid.appendChild(cv);

    // 提示 + 悬浮坐标
    var tip = document.createElement('div');
    tip.className = 'palette-tip';
    tip.textContent = '·';
    grid.parentNode.appendChild(tip);

    cv.addEventListener('mousemove', function (e) {
      var rect = cv.getBoundingClientRect();
      var x = Math.floor((e.clientX - rect.left) / rect.width * W);
      var y = Math.floor((e.clientY - rect.top) / rect.height * H);
      if (x < 0 || x >= W || y < 0 || y >= H) return;
      var c = state.result[y * W + x];
      tip.textContent = '(' + x + ', ' + y + ')  ' + Codec.hexOf(c);
    });

    cv.addEventListener('click', function (e) {
      var rect = cv.getBoundingClientRect();
      var x = Math.floor((e.clientX - rect.left) / rect.width * W);
      var y = Math.floor((e.clientY - rect.top) / rect.height * H);
      if (x < 0 || x >= W || y < 0 || y >= H) return;
      copyText(Codec.hexOf(state.result[y * W + x]));
    });
  }

  function initPalette() {
    var grid = $('paletteGrid');

    grid.addEventListener('click', function (e) {
      var el = e.target.closest('.swatch');
      if (!el) return;
      copyText(el.dataset.hex);
    });

    $('paletteCopyAll').addEventListener('click', function () {
      if (!state.result) return;
      var lines = [];
      var W = state.resultW, H = state.resultH;
      for (var y = 0; y < H; y++) {
        var row = [];
        for (var x = 0; x < W; x++) {
          row.push(Codec.hexOf(state.result[y * W + x]));
        }
        lines.push(row.join(' '));
      }
      copyText(lines.join('\n'));
    });

    $('lookupGo').addEventListener('click', function () {
      if (!state.result) return;
      var x = clamp(parseInt($('lookupX').value, 10) || 0, 0, state.resultW - 1);
      var y = clamp(parseInt($('lookupY').value, 10) || 0, 0, state.resultH - 1);

      var hex = Codec.hexOf(state.result[y * state.resultW + x]);

      // canvas 模式：直接显示坐标信息
      var tip = document.querySelector('.palette-tip');
      if (tip) {
        tip.textContent = '(' + x + ', ' + y + ')  ' + hex;
        toast('(' + x + ', ' + y + ') ' + hex);
        copyText(hex);
        return;
      }

      // DOM 模式：高亮并滚动到目标
      var target = grid.querySelector('.swatch[data-x="' + x + '"][data-y="' + y + '"]');
      if (!target) return;

      var prev = grid.querySelector('.swatch.highlight');
      if (prev) prev.classList.remove('highlight');

      target.classList.add('highlight');
      target.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });

      toast('(' + x + ', ' + y + ') ' + hex);
      copyText(hex);
    });
  }

  function refreshPaletteMeta() {
    if (!state.result) return;
    var uniq = uniqueColors(state.result);
    var total = state.resultW * state.resultH;
    var s = state.resultW + ' × ' + state.resultH + ' · ' + uniq.size + ' 色';
    if (total > PALETTE_DOM_LIMIT) s += ' · ' + t('palette.large');
    $('paletteMeta').textContent = s;
  }

  // ---------------- 编码 ----------------

  /** 每批渲染的列数，避免大画布一次创建过多 DOM */
  var CODE_BATCH = 12;

  function renderCodes(reset) {
    if (!state.result) {
      $('codeEmpty').hidden = false;
      $('codeBody').hidden = true;
      return;
    }
    $('codeEmpty').hidden = true;
    $('codeBody').hidden = false;

    var list = $('codeList');
    if (reset) {
      list.innerHTML = '';
      state.codeShown = Math.min(CODE_BATCH, state.resultW);
    }

    var W = state.resultW, H = state.resultH;
    var start = list.children.length;

    for (var col = start; col < state.codeShown; col++) {
      var colors = Codec.sampleColumnColors(state.result, W, H, col);
      var item = buildCodeItem(col, colors);
      list.appendChild(item);
      attachCodeAsync(item, colors);
    }

    $('loadMoreCode').hidden = state.codeShown >= W;
  }

  function buildCodeItem(col, colors) {
    var div = document.createElement('div');
    div.className = 'code-item';

    var label = document.createElement('span');
    label.className = 'code-label';
    label.textContent = t('code.column', { n: col });

    var mid = document.createElement('div');
    mid.style.minWidth = '0';

    var val = document.createElement('div');
    val.className = 'code-value';
    val.textContent = '…';
    mid.appendChild(val);

    var strip = document.createElement('div');
    strip.className = 'code-strip';
    for (var i = 0; i < 16; i++) {
      var chip = document.createElement('i');
      chip.style.background = Codec.hexOf(colors[i]);
      chip.title = Codec.hexOf(colors[i]);
      strip.appendChild(chip);
    }
    mid.appendChild(strip);

    var btn = document.createElement('button');
    btn.className = 'btn ghost tiny';
    btn.textContent = t('code.copy');

    div.appendChild(label);
    div.appendChild(mid);
    div.appendChild(btn);

    return div;
  }

  function attachCodeAsync(item, colors) {
    var val = item.querySelector('.code-value');
    var btn = item.querySelector('button');

    Codec.encodeColumn(colors).then(function (b64) {
      val.textContent = b64;
      val.title = b64;

      val.addEventListener('click', function () { copyText(b64); });
      btn.addEventListener('click', function () { copyText(b64); });
    }).catch(function () {
      // 兜底：同步版本
      var b64 = Codec.encodeColumnSync(colors);
      val.textContent = b64;
      val.title = b64;
      val.addEventListener('click', function () { copyText(b64); });
      btn.addEventListener('click', function () { copyText(b64); });
    });
  }

  function initCode() {
    $('loadMoreCode').addEventListener('click', function () {
      state.codeShown = Math.min(state.resultW, state.codeShown + CODE_BATCH);
      renderCodes(false);
    });
  }

  // ---------------- 导出 PNG ----------------

  function exportPng() {
    if (!state.result) return;

    // 整数倍放大，保持像素锐利
    var maxEdge = 2048;
    var scale = Math.max(1, Math.floor(maxEdge / Math.max(state.resultW, state.resultH)));
    scale = Math.min(scale, 64);

    var w = state.resultW * scale;
    var h = state.resultH * scale;

    var small = toCanvas(state.result, state.resultW, state.resultH);
    var cv = document.createElement('canvas');
    cv.width = w;
    cv.height = h;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(small, 0, 0, w, h);

    cv.toBlob(function (blob) {
      if (!blob) { toast('导出失败'); return; }
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'pixelart_' + state.resultW + 'x' + state.resultH + '.png';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 3000);
      toast(t('toast.exported'));
    }, 'image/png');
  }

  // ---------------- 编辑器 ----------------

  function initEditor() {
    var cv = $('editorCanvas');
    var ctx = cv.getContext('2d');

    // 默认空白画布
    state.editW = 16;
    state.editH = 16;
    state.editPixels = new Int32Array(16 * 16).fill(0);
    renderEditor();

    var painting = false;
    var lastX = -1, lastY = -1;

    function cellFromEvent(e) {
      var rect = cv.getBoundingClientRect();
      var px = (e.clientX - rect.left) / rect.width * state.editW;
      var py = (e.clientY - rect.top) / rect.height * state.editH;
      return {
        x: clamp(Math.floor(px), 0, state.editW - 1),
        y: clamp(Math.floor(py), 0, state.editH - 1)
      };
    }

    function pushUndo() {
      state.undoStack.push(state.editPixels.slice());
      if (state.undoStack.length > 80) state.undoStack.shift();
      state.redoStack.length = 0;
    }

    function setPixel(x, y, color) {
      state.editPixels[y * state.editW + x] = color;
    }

    function doFill(x, y, color) {
      var target = state.editPixels[y * state.editW + x];
      if (target === color) return;

      var stack = [[x, y]];
      var visited = new Uint8Array(state.editW * state.editH);

      while (stack.length) {
        var p = stack.pop();
        var cx = p[0], cy = p[1];
        if (cx < 0 || cx >= state.editW || cy < 0 || cy >= state.editH) continue;
        var idx = cy * state.editW + cx;
        if (visited[idx]) continue;
        if (state.editPixels[idx] !== target) continue;
        visited[idx] = 1;
        state.editPixels[idx] = color;
        stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
      }
    }

    function handlePaint(e) {
      var c = cellFromEvent(e);
      if (c.x === lastX && c.y === lastY) return;
      lastX = c.x;
      lastY = c.y;

      if (state.editTool === 'brush') {
        setPixel(c.x, c.y, state.editColor);
      } else if (state.editTool === 'eraser') {
        setPixel(c.x, c.y, 0);
      }
      renderEditor();
    }

    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var c = cellFromEvent(e);

      if (state.editTool === 'eyedropper') {
        var col = state.editPixels[c.y * state.editW + c.x];
        if ((col >>> 24) !== 0) {
          state.editColor = col;
          $('brushColor').value = Codec.hexOf(col);
          toast(Codec.hexOf(col));
        }
        return;
      }

      pushUndo();
      painting = true;
      lastX = -1;
      lastY = -1;

      if (state.editTool === 'fill') {
        doFill(c.x, c.y, state.editColor);
        renderEditor();
        painting = false;
        return;
      }

      handlePaint({ clientX: e.clientX, clientY: e.clientY });
      cv.setPointerCapture(e.pointerId);
    });

    cv.addEventListener('pointermove', function (e) {
      if (!painting) return;
      e.preventDefault();
      handlePaint(e);
    });

    function stopPainting(e) {
      painting = false;
      lastX = -1;
      lastY = -1;
      try { cv.releasePointerCapture(e.pointerId); } catch (err) {}
    }

    cv.addEventListener('pointerup', stopPainting);
    cv.addEventListener('pointercancel', stopPainting);

    // 工具切换
    var toolBtns = document.querySelectorAll('#toolSeg button');
    for (var i = 0; i < toolBtns.length; i++) {
      toolBtns[i].addEventListener('click', function () {
        state.editTool = this.getAttribute('data-tool');
        for (var j = 0; j < toolBtns.length; j++) {
          toolBtns[j].classList.toggle('active', toolBtns[j] === this);
        }
      });
    }

    $('brushColor').addEventListener('input', function () {
      state.editColor = Codec.parseHex(this.value);
    });

    $('undoBtn').addEventListener('click', function () {
      if (!state.undoStack.length) return;
      state.redoStack.push(state.editPixels.slice());
      state.editPixels = state.undoStack.pop();
      renderEditor();
    });

    $('redoBtn').addEventListener('click', function () {
      if (!state.redoStack.length) return;
      state.undoStack.push(state.editPixels.slice());
      state.editPixels = state.redoStack.pop();
      renderEditor();
    });

    $('clearBtn').addEventListener('click', function () {
      state.undoStack.push(state.editPixels.slice());
      state.redoStack.length = 0;
      state.editPixels = new Int32Array(state.editW * state.editH).fill(0);
      renderEditor();
      toast(t('toast.cleared'));
    });

    $('importResultBtn').addEventListener('click', function () {
      if (!state.result) {
        toast(t('palette.empty'));
        return;
      }
      state.editW = state.resultW;
      state.editH = state.resultH;
      state.editPixels = state.result.slice();
      state.undoStack.length = 0;
      state.redoStack.length = 0;
      renderEditor();
      toast(t('toast.imported'));
    });

    $('saveWorkBtn').addEventListener('click', saveWork);
  }

  function renderEditor() {
    var cv = $('editorCanvas');
    var W = state.editW, H = state.editH;

    cv.width = W;
    cv.height = H;

    var ctx = cv.getContext('2d');
    ctx.clearRect(0, 0, W, H);
    if (state.editPixels) {
      ctx.putImageData(toImageData(state.editPixels, W, H), 0, 0);
    }

    // 显示尺寸
    var stage = $('editorStage');
    var availW = Math.max(200, stage.clientWidth - 26);
    var availH = Math.min(window.innerHeight * 0.5, 480);
    var base = Math.min(availW / W, availH / H);
    var mult = base >= 1 ? Math.max(1, Math.floor(base)) : base;
    cv.style.width = Math.max(1, Math.round(W * mult)) + 'px';
    cv.style.height = Math.max(1, Math.round(H * mult)) + 'px';

    updateEditorMeta();
  }

  function updateEditorMeta() {
    $('editorMeta').textContent = state.editW + ' × ' + state.editH;
  }

  // ---------------- 历史记录 ----------------

  var HISTORY_KEY = 'pcp.history';
  var MAX_HISTORY = 24;

  function loadHistoryStore() {
    try {
      var raw = localStorage.getItem(HISTORY_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  function saveHistoryStore(items) {
    try {
      localStorage.setItem(HISTORY_KEY, JSON.stringify(items));
    } catch (e) {
      toast('存储空间不足');
    }
  }

  function saveWork() {
    if (!state.editPixels) return;

    // 检查是否全透明
    var hasContent = false;
    for (var i = 0; i < state.editPixels.length; i++) {
      if ((state.editPixels[i] >>> 24) !== 0) { hasContent = true; break; }
    }
    if (!hasContent) {
      toast('画布是空的');
      return;
    }

    var items = loadHistoryStore();

    // 编码成 base64（紧凑存储）
    var bytes = new Uint8Array(state.editPixels.length * 4);
    for (var k = 0; k < state.editPixels.length; k++) {
      var c = state.editPixels[k];
      bytes[k * 4] = (c >>> 24) & 0xFF;
      bytes[k * 4 + 1] = (c >>> 16) & 0xFF;
      bytes[k * 4 + 2] = (c >>> 8) & 0xFF;
      bytes[k * 4 + 3] = c & 0xFF;
    }

    var b64 = '';
    for (var p = 0; p < bytes.length; p++) {
      b64 += String.fromCharCode(bytes[p]);
    }
    b64 = btoa(b64);

    var item = {
      id: Date.now(),
      w: state.editW,
      h: state.editH,
      data: b64
    };

    items.unshift(item);
    if (items.length > MAX_HISTORY) items.length = MAX_HISTORY;

    saveHistoryStore(items);
    renderHistory();
    toast(t('toast.saved'));
  }

  function decodeWork(item) {
    var bin = atob(item.data);
    var px = new Int32Array(item.w * item.h);
    for (var i = 0; i < px.length; i++) {
      var a = bin.charCodeAt(i * 4);
      var r = bin.charCodeAt(i * 4 + 1);
      var g = bin.charCodeAt(i * 4 + 2);
      var b = bin.charCodeAt(i * 4 + 3);
      px[i] = ((a << 24) | (r << 16) | (g << 8) | b) | 0;
    }
    return px;
  }

  function renderHistory() {
    var items = loadHistoryStore();
    var grid = $('historyGrid');
    var emptyEl = $('historyEmpty');

    grid.innerHTML = '';

    if (!items.length) {
      emptyEl.hidden = false;
      return;
    }
    emptyEl.hidden = true;

    items.forEach(function (item) {
      var div = document.createElement('div');
      div.className = 'history-item';

      var cv = document.createElement('canvas');
      cv.width = item.w;
      cv.height = item.h;
      try {
        var px = decodeWork(item);
        cv.getContext('2d').putImageData(toImageData(px, item.w, item.h), 0, 0);
      } catch (e) {}

      cv.addEventListener('click', function () {
        try {
          var px = decodeWork(item);
          state.editW = item.w;
          state.editH = item.h;
          state.editPixels = px;
          state.undoStack.length = 0;
          state.redoStack.length = 0;
          renderEditor();
          toast(t('editor.load'));
        } catch (e) {
          toast('载入失败');
        }
      });

      var meta = document.createElement('div');
      meta.className = 'history-meta';

      var label = document.createElement('span');
      label.textContent = item.w + '×' + item.h;

      var del = document.createElement('button');
      del.className = 'history-del';
      del.textContent = '✕';
      del.title = t('editor.delete');
      del.addEventListener('click', function (e) {
        e.stopPropagation();
        var rest = items.filter(function (x) { return x.id !== item.id; });
        saveHistoryStore(rest);
        renderHistory();
      });

      meta.appendChild(label);
      meta.appendChild(del);

      div.appendChild(cv);
      div.appendChild(meta);
      grid.appendChild(div);
    });
  }

  // ---------------- 算法预览 ----------------

  function updateSamplesOut() {
    $('customSamplesOut').textContent = t('algo.samplesVal', { n: $('customSamples').value });
  }

  function drawAlgoPreview() {
    if (!state.srcPixels) return;

    var cv = $('algoPreview');
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var cssW = 96;
    cv.width = Math.round(cssW * dpr);
    cv.height = Math.round(cssW * dpr);

    // 预览目标尺寸：最长边 96（与 App 一致）
    var aspect = state.outW / state.outH;
    var pw, ph;
    if (aspect >= 1) {
      pw = 96;
      ph = Math.max(1, Math.round(96 / aspect));
    } else {
      ph = 96;
      pw = Math.max(1, Math.round(96 * aspect));
    }

    var out = Pix.pixelate(
      state.srcPixels, state.srcW, state.srcH,
      pw, ph,
      {
        algorithm: state.algorithm,
        customKernel: state.customKernel,
        customSamples: state.customSamples,
        scale: state.scale,
        offsetX: state.offsetX,
        offsetY: state.offsetY,
        rotationDeg: state.rotation
      }
    );

    var small = toCanvas(out, pw, ph);
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, cv.width, cv.height);

    // 居中、保持宽高比
    var s = Math.min(cv.width / pw, cv.height / ph);
    var dw = pw * s;
    var dh = ph * s;
    ctx.drawImage(small, (cv.width - dw) / 2, (cv.height - dh) / 2, dw, dh);
  }

  // ---------------- 事件绑定 ----------------

  function initEvents() {
    // 主题
    $('themeBtn').addEventListener('click', function () {
      applyTheme(state.theme === 'dark' ? 'light' : 'dark');
      if (state.result) drawResult();
    });

    // 语言
    var langBtns = document.querySelectorAll('#langSeg button');
    for (var i = 0; i < langBtns.length; i++) {
      langBtns[i].addEventListener('click', function () {
        applyLang(this.getAttribute('data-lang'));
        if (state.result) drawResult();
      });
    }

    // 尺寸
    $('inW').addEventListener('change', function () {
      readSizeInputs();
      drawCropStage();
      drawAlgoPreview();
    });
    $('inH').addEventListener('change', function () {
      readSizeInputs();
      drawCropStage();
      drawAlgoPreview();
    });

    // 预设
    var presets = $('presets').querySelectorAll('button');
    for (var p = 0; p < presets.length; p++) {
      presets[p].addEventListener('click', function () {
        var sz = this.getAttribute('data-size').split(',');
        $('inW').value = sz[0];
        $('inH').value = sz[1];
        readSizeInputs();
        drawCropStage();
        drawAlgoPreview();
      });
    }

    // 文件选择
    var dz = $('dropzone');
    var fi = $('fileInput');

    dz.addEventListener('click', function (e) {
      if (e.target.closest('#changeImgBtn')) return;
      fi.click();
    });

    fi.addEventListener('change', function () {
      if (this.files && this.files[0]) loadImageFile(this.files[0]);
      this.value = '';
    });

    $('changeImgBtn').addEventListener('click', function (e) {
      e.stopPropagation();
      fi.click();
    });

    // 拖放
    ['dragenter', 'dragover'].forEach(function (ev) {
      dz.addEventListener(ev, function (e) {
        e.preventDefault();
        e.stopPropagation();
        dz.classList.add('dragover');
      });
    });

    ['dragleave', 'drop'].forEach(function (ev) {
      dz.addEventListener(ev, function (e) {
        e.preventDefault();
        e.stopPropagation();
        dz.classList.remove('dragover');
      });
    });

    dz.addEventListener('drop', function (e) {
      var dt = e.dataTransfer;
      if (dt && dt.files && dt.files[0]) loadImageFile(dt.files[0]);
    });

    // 粘贴
    document.addEventListener('paste', function (e) {
      var items = e.clipboardData && e.clipboardData.items;
      if (!items) return;
      for (var i = 0; i < items.length; i++) {
        if (items[i].type.indexOf('image') === 0) {
          var f = items[i].getAsFile();
          if (f) { loadImageFile(f); break; }
        }
      }
    });

    // 滑块
    $('scaleRange').addEventListener('input', function () {
      state.scale = parseFloat(this.value);
      $('scaleOut').textContent = state.scale.toFixed(2) + '×';
      drawCropStage();
      drawAlgoPreview();
    });

    $('rotRange').addEventListener('input', function () {
      state.rotation = parseFloat(this.value);
      $('rotOut').textContent = Math.round(state.rotation) + '°';
      drawCropStage();
      drawAlgoPreview();
    });

    $('rotLeft').addEventListener('click', function () {
      state.rotation = normalizeAngle(state.rotation - 90);
      $('rotRange').value = state.rotation;
      $('rotOut').textContent = Math.round(state.rotation) + '°';
      drawCropStage();
      drawAlgoPreview();
    });

    $('rotRight').addEventListener('click', function () {
      state.rotation = normalizeAngle(state.rotation + 90);
      $('rotRange').value = state.rotation;
      $('rotOut').textContent = Math.round(state.rotation) + '°';
      drawCropStage();
      drawAlgoPreview();
    });

    $('cropReset').addEventListener('click', function () {
      state.scale = 1;
      state.offsetX = 0;
      state.offsetY = 0;
      state.rotation = 0;
      $('scaleRange').value = 1;
      $('rotRange').value = 0;
      $('scaleOut').textContent = '1.00×';
      $('rotOut').textContent = '0°';
      drawCropStage();
      drawAlgoPreview();
    });

    // 算法
    var radios = document.querySelectorAll('input[name="algo"]');
    for (var r = 0; r < radios.length; r++) {
      radios[r].addEventListener('change', function () {
        state.algorithm = this.value;
        $('customPanel').hidden = state.algorithm !== 'custom';
        drawAlgoPreview();
      });
    }

    $('customKernel').addEventListener('change', function () {
      state.customKernel = this.value;
      drawAlgoPreview();
    });

    $('customSamples').addEventListener('input', function () {
      state.customSamples = parseInt(this.value, 10);
      updateSamplesOut();
      drawAlgoPreview();
    });

    // 生成
    $('generateBtn').addEventListener('click', doGenerate);

    // 结果操作
    $('exportBtn').addEventListener('click', exportPng);

    $('copyAllBtn').addEventListener('click', function () {
      if (!state.result) return;
      var lines = [];
      for (var y = 0; y < state.resultH; y++) {
        var row = [];
        for (var x = 0; x < state.resultW; x++) {
          row.push(Codec.hexOf(state.result[y * state.resultW + x]));
        }
        lines.push(row.join(' '));
      }
      copyText(lines.join('\n'));
    });

    $('editBtn').addEventListener('click', function () {
      if (!state.result) return;
      state.editW = state.resultW;
      state.editH = state.resultH;
      state.editPixels = state.result.slice();
      state.undoStack.length = 0;
      state.redoStack.length = 0;
      renderEditor();
      switchTab('editor');
    });

    // 窗口尺寸变化
    var rt = null;
    window.addEventListener('resize', function () {
      clearTimeout(rt);
      rt = setTimeout(function () {
        if (state.srcPixels) {
          drawCropStage();
          drawAlgoPreview();
        }
        if (state.result) drawResult();
        if (state.editPixels) renderEditor();
      }, 150);
    });
  }

  function normalizeAngle(a) {
    while (a > 180) a -= 360;
    while (a < -180) a += 360;
    return a;
  }

  // ---------------- 启动 ----------------

  function boot() {
    initTheme();
    initTabs();
    initLang();
    initEvents();
    initCropDrag();
    initPalette();
    initCode();
    initEditor();
    renderHistory();

    updateSamplesOut();
    readSizeInputs();

    // 键盘快捷键
    document.addEventListener('keydown', function (e) {
      var tag = (e.target.tagName || '').toLowerCase();
      if (tag === 'input' || tag === 'select' || tag === 'textarea') return;

      if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
        e.preventDefault();
        if (e.shiftKey) $('redoBtn').click();
        else $('undoBtn').click();
      }
    });

    window.PCP_STATE = state;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();