/*
 * PixelColorPicker Web · 主逻辑
 * 结构与交互对应 MainActivity.kt + MainViewModel.kt + HomeScreen.kt
 */
(function () {
  'use strict';

  var Codec = window.PCPCodec;
  var Pix = window.PCPPixelator;
  var STR = window.PCP_STRINGS;

  // ==================== 国际化 ====================
  var lang = 'zh';

  function S(key, vars) {
    var d = STR[lang] || STR.zh;
    var s = d[key];
    if (s == null) s = (STR.zh[key] != null ? STR.zh[key] : key);
    if (vars) {
      s = s.replace(/\{p(\d+)\}/g, function (m, i) {
        return vars['p' + i] != null ? String(vars['p' + i]) : m;
      });
    }
    return s;
  }

  function applyI18n() {
    var nodes = document.querySelectorAll('[data-s]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var k = el.getAttribute('data-s');
      // 有子元素标题的不覆盖（如带 svg 的按钮）
      var txt = S(k);
      if (txt && txt !== k) {
        // 只替换纯文本节点（保留 svg）
        var replaced = false;
        for (var j = 0; j < el.childNodes.length; j++) {
          if (el.childNodes[j].nodeType === 3) {
            el.childNodes[j].nodeValue = txt;
            replaced = true;
          }
        }
        if (!replaced && !el.querySelector('svg') && !el.querySelector('canvas')) {
          el.textContent = txt;
        } else if (!replaced && (el.querySelector('svg') || el.querySelector('canvas'))) {
          // 有图标：找 span 或直接 append
          var sp = el.querySelector('span:not([data-s])');
          if (sp) sp.textContent = txt;
        }
      }
    }
    // [对齐 App] SizeInput 的 label 在源码中固定为 "W"/"H"，不随语言变化，故不翻译。

    // 保存按钮带图标，需手动设文案（结构变化后 data-s 不再适用）
    var saveLb = document.getElementById('saveAlbumLabel');
    if (saveLb) saveLb.textContent = S('save_to_album');

    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : lang;
  }

  // ==================== 状态（对应 MainViewModel） ====================
  var VM = {
    canvasWidth: 16,
    canvasHeight: 16,
    algorithm: 'nearest',
    customKernel: 'bilinear',
    customSamples: 4,
    gridEnabled: false, // 默认关闭（用户要求）
    dragAdjustEnabled: false,
    themeMode: 'system',
    autoGenerate: false,

    // [对齐 App] 常用尺寸：自定义预设 + 使用历史
    customPreset0: '',
    customPreset1: '',
    sizeHistory: {},

    // 源图
    originalBitmap: null, // 选图时的原图（裁剪页使用，可反复重新裁剪）
    originalW: 0,
    originalH: 0,
    sourceBitmap: null,
    sourceW: 0,
    sourceH: 0,
    sourceName: '',

    // 裁剪
    scale: 1, offsetX: 0, offsetY: 0, rotation: 0,

    // [对齐 App] 是否已像素化：false 时展示框显示原图，点「生成」后才像素化
    isPixelated: false,

    // 结果
    pixelArt: null,
    artW: 0, artH: 0, genMs: 0,

    // 编辑器
    editPixels: null, editW: 16, editH: 16, editTool: 'brush',
    editColor: 0xFFFFFFFF | 0,
    undo: [], redo: [],

    // 其它
    homeActionsExpanded: true,
    welcomePlayed: false,
    genCount: 0,
    tab: 'home',
    subScreen: null
  };

  // 主题模式 -> 文案键（显式映射，避免动态拼接）
  var THEME_LABEL = {
    system: 'theme_system',
    light: 'theme_light',
    dark: 'theme_dark'
  };

  // ==================== 工具 ====================
  function $(id) { return document.getElementById(id); }
  function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

  var toastTimer = null;
  function toast(msg) {
    var el = $('toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, 2000);
  }

  function copyText(text, okMsg) {
    function done() { toast(okMsg || S('code_copied')); }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done).catch(function () {
        fallback(text); done();
      });
    } else {
      fallback(text); done();
    }
  }

  function fallback(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'position:fixed;opacity:0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); } catch (e) {}
    document.body.removeChild(ta);
  }

  function toImageData(px, w, h) {
    var img = new ImageData(w, h);
    var d = img.data;
    for (var i = 0, j = 0; i < px.length; i++, j += 4) {
      var c = px[i];
      d[j] = (c >>> 16) & 0xFF;
      d[j + 1] = (c >>> 8) & 0xFF;
      d[j + 2] = c & 0xFF;
      d[j + 3] = (c >>> 24) & 0xFF;
    }
    return img;
  }

  function toCanvas(px, w, h) {
    var cv = document.createElement('canvas');
    cv.width = w; cv.height = h;
    cv.getContext('2d').putImageData(toImageData(px, w, h), 0, 0);
    return cv;
  }

  function uniqCount(px) {
    var m = new Set();
    for (var i = 0; i < px.length; i++) m.add(px[i]);
    return m.size;
  }

  // ==================== 主题 ====================
  function applyTheme() {
    var mode = VM.themeMode;
    var dark = mode === 'dark' || (mode === 'system' &&
      !window.matchMedia('(prefers-color-scheme: light)').matches);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    document.querySelector('meta[name=theme-color]')
      .setAttribute('content', dark ? '#08090F' : '#F4F5FA');
  }

  // ==================== 绘制 Logo（16x16 像素方块） ====================
  /** [对齐 App] old_ic_app_icon.xml：6 个彩色像素块 + 白色取色圆 + 深色圆 + 吸管。 */
  function drawLogo(cv, size) {
    var ctx = cv.getContext('2d');
    // 用 canvas 自身像素尺寸（CSS 会再缩到 32px），避免画出界导致歪
    var S = cv.width || size;
    var s = S / 108; // 源 viewport 108×108
    ctx.clearRect(0, 0, S, S);

    function block(x, y, c) {
      ctx.fillStyle = c;
      ctx.fillRect(x * s, y * s, 18 * s, 18 * s);
    }
    block(18, 20, '#FF5C8A');
    block(45, 15, '#FFD166');
    block(72, 22, '#06D6A0');
    block(15, 70, '#118AB2');
    block(45, 78, '#8338EC');
    block(75, 68, '#EF476F');

    // 中心取色圆（白，r22）
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(54 * s, 54 * s, 22 * s, 0, Math.PI * 2);
    ctx.fill();

    // 中心像素点（深色，r12）
    ctx.fillStyle = '#20202A';
    ctx.beginPath();
    ctx.arc(54 * s, 54 * s, 12 * s, 0, Math.PI * 2);
    ctx.fill();

    // 吸管（白色斜杆）
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.moveTo(62 * s, 58 * s);
    ctx.lineTo(83 * s, 79 * s);
    ctx.lineTo(78 * s, 84 * s);
    ctx.lineTo(57 * s, 63 * s);
    ctx.closePath();
    ctx.fill();
  }

  // ==================== 欢迎动画（复刻 WelcomeAnimation.kt） ====================
  // 源码：welcomeText 与 appName 在同一个 Row 内，渲染为一行
  function playWelcome() {
    var welcomeText = S('welcome_prefix');
    var appName = 'PixelColorPicker';
    var wl = $('welcomeLine');
    var tap = $('tapHint');

    wl.innerHTML = '';
    tap.classList.remove('show');

    welcomeText.split('').forEach(function (ch) {
      var s = document.createElement('span');
      s.className = 'ch';
      s.textContent = ch;
      wl.appendChild(s);
    });
    appName.split('').forEach(function (ch) {
      var s = document.createElement('span');
      s.className = 'ch pcp-ch-app';
      s.textContent = ch;
      wl.appendChild(s);
    });

    var wchs = wl.querySelectorAll('.ch');

    if (VM.welcomePlayed) {
      wchs.forEach(function (c) { c.classList.add('show'); });
      tap.classList.add('show');
      return;
    }

    var i = 0;
    var welcomeLen = welcomeText.length;
    function step() {
      if (i >= wchs.length) {
        setTimeout(function () {
          tap.classList.add('show');
          VM.welcomePlayed = true;
        }, 200);
        return;
      }
      wchs[i].classList.add('show');
      i++;
      // 「欢迎使用」80ms/字；app 名 30ms/字（中间 280ms 停顿）
      if (i === welcomeLen) {
        setTimeout(step, 280);
      } else {
        setTimeout(step, i < welcomeLen ? 80 : 30);
      }
    }
    setTimeout(step, 120);
  }

  // ==================== 常用尺寸（对应 viewModel.presetSize / saveCustomPreset） ====================
  // App 逻辑：优先自定义预设（customPreset0/1）；不足时用历史使用次数 top2；
  // 再不足用默认 16×16 / 32×32。网页版原先硬编码 12 个固定值，与 App 不一致。

  function parseSizeKey(k) {
    if (!k) return null;
    var parts = String(k).split('×');
    if (parts.length !== 2) return null;
    var w = parseInt(parts[0], 10), h = parseInt(parts[1], 10);
    if (!(w >= 1 && w <= 999) || !(h >= 1 && h <= 999)) return null;
    return [w, h];
  }

  function presetSize(idx) {
    var custom = parseSizeKey(idx === 0 ? VM.customPreset0 : VM.customPreset1);
    if (custom) return custom;
    var keys = Object.keys(VM.sizeHistory);
    keys.sort(function (a, b) { return VM.sizeHistory[b] - VM.sizeHistory[a]; });
    var sorted = [];
    for (var i = 0; i < keys.length; i++) {
      var p = parseSizeKey(keys[i]);
      if (p) sorted.push(p);
    }
    if (sorted.length > idx) return sorted[idx];
    return idx === 0 ? [16, 16] : [32, 32];
  }

  /** 保存自定义常用尺寸（新值进预设 1，旧预设 1 顺延到预设 2），并应用。 */
  function saveCustomPreset(w, h) {
    var nw = clamp(w | 0, 1, 999), nh = clamp(h | 0, 1, 999);
    var old0 = VM.customPreset0;
    if (!old0) {
      var p = presetSize(0);
      old0 = p[0] + '×' + p[1];
    }
    VM.customPreset1 = old0;
    VM.customPreset0 = nw + '×' + nh;
    try {
      localStorage.setItem('pcp.preset_0', VM.customPreset0);
      localStorage.setItem('pcp.preset_1', VM.customPreset1);
    } catch (e) {}
    setCanvasSize(nw, nh);
  }

  /** 记录一次尺寸使用（复刻旧版 recordSizeHistory）。 */
  function recordSizeHistory() {
    var key = VM.canvasWidth + '×' + VM.canvasHeight;
    VM.sizeHistory[key] = (VM.sizeHistory[key] || 0) + 1;
    var parts = [];
    Object.keys(VM.sizeHistory).forEach(function (k) {
      parts.push(k + ':' + VM.sizeHistory[k]);
    });
    try { localStorage.setItem('pcp.size_history', parts.join(';')); } catch (e) {}
  }

  function renderPresets() {
    var box = $('presetList');
    box.innerHTML = '';

    // 两个预设 chip + 一个「⋯」chip（对应 PresetChip x2 + MoreChip）
    for (var i = 0; i < 2; i++) {
      var p = presetSize(i);
      var b = document.createElement('button');
      b.className = 'pcp-preset-chip';
      if (p[0] === VM.canvasWidth && p[1] === VM.canvasHeight) {
        b.classList.add('is-active');
      }
      var t = document.createElement('span');
      t.className = 't';
      t.textContent = p[0];
      var x = document.createElement('span');
      x.className = 'x';
      x.textContent = '×';
      var bo = document.createElement('span');
      bo.className = 'b';
      bo.textContent = p[1];
      b.appendChild(t); b.appendChild(x); b.appendChild(bo);
      (function (w, h, el) {
        el.addEventListener('click', function () {
          // 复刻旧版：点击选中，再点取消
          if (el.classList.contains('is-active')) {
            el.classList.remove('is-active');
            return;
          }
          setCanvasSize(w, h);
        });
      })(p[0], p[1], b);
      box.appendChild(b);
    }

    // MoreChip：三个小灰点
    var more = document.createElement('button');
    more.className = 'pcp-preset-chip pcp-more-chip';
    more.innerHTML = '<i></i><i></i><i></i>';
    more.addEventListener('click', showPresetDialog);
    box.appendChild(more);
  }

  // ==================== 画布尺寸 ====================
  function setCanvasSize(w, h) {
    w = clamp(w | 0, 1, 999);
    h = clamp(h | 0, 1, 999);
    var oldRatio = VM.canvasWidth / VM.canvasHeight;
    var newRatio = w / h;
    // 正方形才能旋转（对应 rotate_square_only）
    VM.canvasWidth = w;
    VM.canvasHeight = h;
    recordSizeHistory(); // [对齐 App] 记录使用历史
    updateSizeUI();
    renderPresets();
    // [对齐 App] setCanvasSize：已生成时，比例不同需要重新框选
    if (VM.isPixelated) {
      if (Math.abs(oldRatio - newRatio) > 1e-6) {
        VM.isPixelated = false;
        VM.pixelArt = null;
        $('saveAlbumBtn').disabled = true;
        renderPalette();
        renderCodes();
        toast(S('crop_hint'));
      }
    }
    drawPreview();
  }

  function updateSizeUI() {
    $('sizeWVal').textContent = VM.canvasWidth;
    $('sizeHVal').textContent = VM.canvasHeight;
  }

  // ==================== 图片加载 ====================
  var MAX_EDGE = 2000;

  function loadImageFile(file) {
    if (!file || !/^image\//.test(file.type)) return;
    var url = URL.createObjectURL(file);
    var img = new Image();
    img.onload = function () {
      URL.revokeObjectURL(url);
      var w = img.naturalWidth || img.width;
      var h = img.naturalHeight || img.height;
      if (Math.max(w, h) > MAX_EDGE) {
        var s = MAX_EDGE / Math.max(w, h);
        w = Math.round(w * s); h = Math.round(h * s);
      }
      var cv = document.createElement('canvas');
      cv.width = w; cv.height = h;
      var ctx = cv.getContext('2d', { willReadFrequently: true });
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, w, h);
      var data = ctx.getImageData(0, 0, w, h).data;
      var px = new Int32Array(w * h);
      for (var i = 0, j = 0; i < px.length; i++, j += 4) {
        px[i] = ((data[j + 3] << 24) | (data[j] << 16) | (data[j + 1] << 8) | data[j + 2]) | 0;
      }
      VM.originalBitmap = px;
      VM.originalW = w;
      VM.originalH = h;
      VM.sourceBitmap = px;
      VM.sourceW = w;
      VM.sourceH = h;
      VM.sourceName = file.name || 'image';
      VM.scale = 1; VM.offsetX = 0; VM.offsetY = 0; VM.rotation = 0;

      $('welcomeBox').hidden = true;
      $('previewCanvas').hidden = false;
      $('actionsArea').hidden = false;
      VM.homeActionsExpanded = true;
      updateActionsUI();
      drawPreview();
      // [对齐 App] 选图后先进入裁剪页（CropScreen），确认后才进入首页
      openCrop();
    };
    img.onerror = function () { URL.revokeObjectURL(url); toast('图片加载失败'); };
    img.src = url;
  }

  /**
   * 绘制展示框。
   * @param {boolean} fast 拖动中用低分辨率快速重算（256），否则 1024
   */
  function drawPreview(fast) {
    var cv = $('previewCanvas');
    if (!VM.sourceBitmap) { cv.hidden = true; return; }
    cv.hidden = false;

    // [对齐 App] isPixelated=false 时显示原图（未像素化），点「生成」后才像素化
    if (!VM.isPixelated) {
      var tmp = toCanvas(VM.sourceBitmap, VM.sourceW, VM.sourceH);
      cv.width = VM.sourceW;
      cv.height = VM.sourceH;
      cv.getContext('2d').drawImage(tmp, 0, 0);
      cv.style.width = '100%';
      cv.style.height = 'auto';
      cv.style.maxWidth = '100%';
      cv.style.maxHeight = '100%';
      cv.style.aspectRatio = VM.sourceW + ' / ' + VM.sourceH;
      cv.style.transform = '';
      return;
    }

    // 预览直接用画布尺寸渲染（CSS 放大后 pixelated，保持锐利）
    var pw = VM.canvasWidth;
    var ph = VM.canvasHeight;
    // [对齐 App] PREVIEW_MAX_EDGE = 1024；拖动中用 256 保证跟手
    var maxEdge = fast ? 256 : 1024;
    var mx = Math.max(pw, ph);
    if (mx > maxEdge) {
      var k = maxEdge / mx;
      pw = Math.max(1, Math.round(pw * k));
      ph = Math.max(1, Math.round(ph * k));
    }

    var out;
    var ES = effectiveSource();
    try {
      out = Pix.pixelate(ES.px, ES.w, ES.h, pw, ph, {
        algorithm: VM.algorithm,
        customKernel: VM.customKernel,
        customSamples: VM.customSamples,
        scale: ES.scale, offsetX: ES.ox, offsetY: ES.oy,
        rotationDeg: VM.rotation
      });
    } catch (e) { return; }

    // [对齐 App] 放大后绘制，保证网格线是 1px 真实像素
    var mult = Math.max(1, Math.min(8, Math.floor(1024 / Math.max(pw, ph))));
    cv.width = pw * mult;
    cv.height = ph * mult;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    var tmp = document.createElement('canvas');
    tmp.width = pw; tmp.height = ph;
    tmp.getContext('2d').putImageData(toImageData(out, pw, ph), 0, 0);
    ctx.drawImage(tmp, 0, 0, cv.width, cv.height);

    // [对齐 App] ImageDisplayBox 网格覆盖：画布<=160 且格子>=2.5px
    if (VM.gridEnabled && pw <= 160 && ph <= 160) {
      var cellW = cv.width / pw;
      var cellH = cv.height / ph;
      if (cellW >= 2.5 && cellH >= 2.5) {
        ctx.strokeStyle = 'rgba(0,0,0,0.33)'; // 0x55000000
        ctx.lineWidth = 1;
        for (var gi = 0; gi <= pw; gi++) {
          var gx = Math.round(gi * cellW) + 0.5;
          ctx.beginPath(); ctx.moveTo(gx, 0); ctx.lineTo(gx, cv.height); ctx.stroke();
        }
        for (var gj = 0; gj <= ph; gj++) {
          var gy = Math.round(gj * cellH) + 0.5;
          ctx.beginPath(); ctx.moveTo(0, gy); ctx.lineTo(cv.width, gy); ctx.stroke();
        }
      }
    }
    cv.style.width = '100%';
    cv.style.height = 'auto';
    cv.style.maxWidth = '100%';
    cv.style.maxHeight = '100%';
    // [对齐 App] ImageDisplayBox 用 fitRect(按图片比例 contain)：
    // 展示框始终完整显示整张图，像素画铺满该区域（而不是按画布比例拉伸）
    cv.style.aspectRatio = VM.sourceW + ' / ' + VM.sourceH;
    cv.style.objectFit = 'contain';
  }

  // ==================== 生成 ====================
  function generate() {
    if (!VM.sourceBitmap) { toast(S('pick_first')); return; }
    // [对齐 App] 首次生成时先重置变换，再按新参数采样
    if (!VM.isPixelated) {
      VM.scale = 1; VM.offsetX = 0; VM.offsetY = 0; VM.rotation = 0;
    }
    var t0 = performance.now();
    var out;
    var ES = effectiveSource();
    try {
      out = Pix.pixelate(ES.px, ES.w, ES.h,
        VM.canvasWidth, VM.canvasHeight, {
        algorithm: VM.algorithm,
        customKernel: VM.customKernel,
        customSamples: VM.customSamples,
        scale: ES.scale, offsetX: ES.ox, offsetY: ES.oy,
        rotationDeg: VM.rotation
      });
    } catch (e) { toast('生成失败：' + e.message); return; }

    // [对齐 App] 首次生成时重置变换（已生成过则保留当前缩放/平移/旋转）
    VM.isPixelated = true;

    VM.pixelArt = out;
    VM.artW = VM.canvasWidth;
    VM.artH = VM.canvasHeight;
    VM.genMs = Math.round(performance.now() - t0);
    VM.genCount++;
    try { localStorage.setItem('pcp.genCount', VM.genCount); } catch (e) {}

    $('saveAlbumBtn').disabled = false;
    renderPalette();
    renderCodes();
    drawPreview(); // [修复] 生成后必须重绘，否则展示框停在原图
    toast(S('generated'));
  }

  // ==================== 色板 ====================
  // ============ 色板（照抄 PaletteGrid：虚拟化 + 圆角格子 + 三行文字 + 双滑块） ============
  function palCellSize() {
    var dpr = window.devicePixelRatio || 1;
    return clamp(210000 / (999 * dpr), 24, 76);
  }

  function roundRectPath(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /** 同 ColorUtils.contrastingText。 */
  function contrastingText(c) {
    var r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
    var lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return lum > 0.6 ? '#101018' : '#FFFFFF';
  }

  function renderPalette() {
    var empty = $('paletteEmpty');
    var body = $('paletteBody');
    var sb = $('palSearchBtn'); if (sb) sb.hidden = !VM.pixelArt;
    var rf = $('palRefreshBtn'); if (rf) rf.hidden = !VM.pixelArt;
    if (!VM.pixelArt) {
      if (empty) empty.hidden = false;
      if (body) body.hidden = true;
      return;
    }
    if (empty) empty.hidden = true;
    if (body) body.hidden = false;

    var W = VM.artW, H = VM.artH;
    var titleEl = $('paletteTitle');
    if (titleEl) titleEl.textContent = S('palette_title', { p1: W + '×' + H });

    // 同色计数（同 PixelGrid.countOf）
    var counts = {};
    for (var i = 0; i < VM.pixelArt.length; i++) {
      var cc = VM.pixelArt[i] >>> 0;
      counts[cc] = (counts[cc] || 0) + 1;
    }
    VM.palCounts = counts;
    VM.palCell = palCellSize();

    var sp = $('palSpacer');
    if (sp) {
      sp.style.width = (W * VM.palCell) + 'px';
      sp.style.height = (H * VM.palCell + 110) + 'px';
    }
    var view = $('palView');
    if (view) { view.scrollTop = 0; view.scrollLeft = 0; }
    requestAnimationFrame(function () {
      drawPaletteView();
      renderPalRulers();
      syncPalBars();
    });
  }

  function drawPaletteView() {
    var view = $('palView'), cv = $('paletteCanvas');
    if (!view || !cv || !VM.pixelArt) return;
    var cell = VM.palCell, W = VM.artW, H = VM.artH;
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var vw = view.clientWidth, vh = view.clientHeight;
    if (vw <= 0 || vh <= 0) return;
    if (cv.width !== Math.round(vw * dpr) || cv.height !== Math.round(vh * dpr)) {
      cv.width = Math.round(vw * dpr);
      cv.height = Math.round(vh * dpr);
    }
    cv.style.width = vw + 'px';
    cv.style.height = vh + 'px';

    var ctx = cv.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, vw, vh);

    var ox = view.scrollLeft, oy = view.scrollTop;
    var firstX = clamp(Math.floor(ox / cell), 0, W - 1);
    var firstY = clamp(Math.floor(oy / cell), 0, H - 1);
    var lastX = clamp(Math.floor((ox + vw) / cell), 0, W - 1);
    var lastY = clamp(Math.floor((oy + vh) / cell), 0, H - 1);

    var pad = cell * 0.05;
    var corner = cell * 0.16;
    var showText = cell >= 52;
    var hexSize = Math.max(8, Math.round(cell * 0.17));
    var coordSize = Math.max(7, Math.round(cell * 0.15));
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    for (var y = firstY; y <= lastY; y++) {
      for (var x = firstX; x <= lastX; x++) {
        var c = VM.pixelArt[y * W + x] >>> 0;
        var left = x * cell - ox;
        var top = y * cell - oy;
        var hex = '#' + (c & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
        ctx.fillStyle = hex;
        roundRectPath(ctx, left + pad, top + pad, cell - pad * 2, cell - pad * 2, corner);
        ctx.fill();
        if (showText) {
          var tc = contrastingText(c);
          ctx.fillStyle = tc;
          ctx.font = hexSize + 'px ui-monospace, Menlo, Consolas, monospace';
          ctx.fillText(hex, left + cell / 2, top + cell * 0.36);
          ctx.font = coordSize + 'px ui-monospace, Menlo, Consolas, monospace';
          ctx.fillText('(' + (x + 1) + ',' + (y + 1) + ')', left + cell / 2, top + cell * 0.56);
          var cnt = VM.palCounts[c] || 0;
          if (cnt >= 2) {
            ctx.fillText('×' + cnt, left + cell / 2, top + cell * 0.76);
          }
        }
      }
    }

    if (VM.palHi) {
      var hx = VM.palHi.x, hy = VM.palHi.y;
      if (hx >= firstX && hx <= lastX && hy >= firstY && hy <= lastY) {
        ctx.strokeStyle = '#66CCFF';
        ctx.lineWidth = Math.max(2, cell * 0.06);
        roundRectPath(ctx, hx * cell - ox + pad, hy * cell - oy + pad, cell - pad * 2, cell - pad * 2, corner);
        ctx.stroke();
      }
    }
  }

  function renderPalRulers() {
    var view = $('palView');
    if (!view || !VM.pixelArt) return;
    var cell = VM.palCell, W = VM.artW, H = VM.artH;
    var step = Math.max(1, Math.ceil(46 / cell));
    var ox = view.scrollLeft, oy = view.scrollTop;

    var rx = $('rulerX');
    if (rx) {
      rx.innerHTML = '';
      var fx = Math.max(0, Math.floor(ox / cell) - 1);
      var lx = Math.min(W - 1, Math.floor((ox + view.clientWidth) / cell) + 1);
      for (var x = fx; x <= lx; x++) {
        if (x % step) continue;
        var s = document.createElement('span');
        s.textContent = x;
        s.style.left = (x * cell + cell / 2 - ox) + 'px';
        rx.appendChild(s);
      }
    }
    var ry = $('rulerY');
    if (ry) {
      ry.innerHTML = '';
      var fy = Math.max(0, Math.floor(oy / cell) - 1);
      var ly = Math.min(H - 1, Math.floor((oy + view.clientHeight) / cell) + 1);
      for (var y = fy; y <= ly; y++) {
        if (y % step) continue;
        var t = document.createElement('span');
        t.textContent = y;
        t.style.top = (y * cell + cell / 2 - oy) + 'px';
        ry.appendChild(t);
      }
    }
  }

  /** 双 QuickBar 同步（同 PaletteGrid 的横/纵 QuickBar）。 */
  function syncPalBars() {
    var view = $('palView');
    if (!view || !VM.pixelArt) return;
    var cell = VM.palCell, W = VM.artW, H = VM.artH;
    var contentW = W * cell, contentH = H * cell;

    var hb = $('palHBar'), ht = $('palHThumb');
    if (hb && ht) {
      var trackW = hb.clientWidth;
      var maxX = Math.max(0, contentW - view.clientWidth);
      if (maxX <= 0) {
        hb.style.visibility = 'hidden';
      } else {
        hb.style.visibility = 'visible';
        var ratioW = Math.min(1, Math.max(0.05, view.clientWidth / contentW));
        var tw = Math.max(28, Math.round(trackW * ratioW));
        ht.style.width = tw + 'px';
        ht.style.height = '6px';
        ht.style.top = '6px';
        var fx = view.scrollLeft / maxX;
        ht.style.left = Math.round(fx * (trackW - tw)) + 'px';
      }
    }
    var vb = $('palVBar'), vt = $('palVThumb');
    if (vb && vt) {
      var trackH = vb.clientHeight;
      var maxY = Math.max(0, contentH - view.clientHeight);
      if (maxY <= 0) {
        vb.style.visibility = 'hidden';
      } else {
        vb.style.visibility = 'visible';
        var ratioH = Math.min(1, Math.max(0.05, view.clientHeight / contentH));
        var th = Math.max(28, Math.round(trackH * ratioH));
        vt.style.height = th + 'px';
        vt.style.width = '6px';
        vt.style.left = '7px';
        var fy = view.scrollTop / maxY;
        vt.style.top = Math.round(fy * (trackH - th)) + 'px';
      }
    }
  }

  /** 色板交互：滚动重绘 + 点击复制 + 双滑块拖动。 */
  function initPaletteView() {
    var view = $('palView');
    if (!view) return;
    view.addEventListener('scroll', function () {
      drawPaletteView();
      renderPalRulers();
      syncPalBars();
    }, { passive: true });
    window.addEventListener('resize', function () {
      if (VM.pixelArt && !$('paletteBody').hidden) {
        drawPaletteView(); renderPalRulers(); syncPalBars();
      }
    });

    view.addEventListener('click', function (e) {
      if (!VM.pixelArt) return;
      var cell = VM.palCell, W = VM.artW, H = VM.artH;
      var r = view.getBoundingClientRect();
      var x = Math.floor((view.scrollLeft + e.clientX - r.left) / cell);
      var y = Math.floor((view.scrollTop + e.clientY - r.top) / cell);
      if (x < 0 || x >= W || y < 0 || y >= H) return;
      var c = VM.pixelArt[y * W + x];
      var hex = Codec.hexOf(c);
      var tip = $('paletteTip');
      if (tip) tip.textContent = '(' + x + ', ' + y + ')  ' + hex;
      try {
        navigator.clipboard.writeText(hex);
        toast(S('palette_copied', { p1: hex }));
      } catch (err) { toast(hex); }
    });

    function bindBar(bar, isH) {
      if (!bar) return;
      var dragging = false;
      function seek(e) {
        var r = bar.getBoundingClientRect();
        var f = isH
          ? clamp((e.clientX - r.left) / r.width, 0, 1)
          : clamp((e.clientY - r.top) / r.height, 0, 1);
        if (isH) {
          var maxX = Math.max(0, VM.artW * VM.palCell - view.clientWidth);
          view.scrollLeft = f * maxX;
        } else {
          var maxY = Math.max(0, VM.artH * VM.palCell - view.clientHeight);
          view.scrollTop = f * maxY;
        }
        syncPalBars();
      }
      bar.addEventListener('pointerdown', function (e) {
        dragging = true;
        bar.classList.add('is-pressed');
        try { bar.setPointerCapture(e.pointerId); } catch (err) {}
        seek(e); e.preventDefault();
      });
      bar.addEventListener('pointermove', function (e) {
        if (!dragging) return;
        e.preventDefault(); seek(e);
      });
      function up() { dragging = false; bar.classList.remove('is-pressed'); }
      bar.addEventListener('pointerup', up);
      bar.addEventListener('pointercancel', up);
    }
    bindBar($('palHBar'), true);
    bindBar($('palVBar'), false);
  }

  /** 坐标搜索对话框（同 CoordinateSearchDialog，1-based）。 */
  function showPaletteSearch() {
    if (!VM.pixelArt) return;
    var W = VM.artW, H = VM.artH;
    openDialog(
      '<h3 class="pcp-dialog-title">' + S('palette_search_title') + '</h3>' +
      '<p class="pcp-dialog-hint">X: 1 - ' + W + ' &nbsp; Y: 1 - ' + H + '</p>' +
      '<div class="pcp-dialog-row">' +
      '<input class="pcp-dialog-input" id="srX" type="number" min="1" max="' + W + '" placeholder="X">' +
      '<input class="pcp-dialog-input" id="srY" type="number" min="1" max="' + H + '" placeholder="Y">' +
      '</div>' +
      '<div class="pcp-dialog-actions">' +
      '<button class="pcp-dialog-btn" id="dlgCancel">' + S('action_cancel') + '</button>' +
      '<button class="pcp-dialog-btn primary" id="dlgOk">' + S('action_confirm') + '</button>' +
      '</div>'
    );
    $('dlgCancel').onclick = closeDialog;
    $('dlgOk').onclick = function () {
      var x = parseInt($('srX').value, 10);
      var y = parseInt($('srY').value, 10);
      if (!(x >= 1 && x <= W && y >= 1 && y <= H)) {
        toast('1 - ' + W + ' / 1 - ' + H);
        return;
      }
      closeDialog();
      scrollPaletteTo(x - 1, y - 1);
    };
  }

  /** 滚动到指定格子并短暂高亮。 */
  function scrollPaletteTo(cx, cy) {
    VM.palHi = { x: cx, y: cy };
    var view = $('palView');
    var cell = VM.palCell || 56;
    if (view) {
      var left = cx * cell + cell / 2 - view.clientWidth / 2;
      var top = cy * cell + cell / 2 - view.clientHeight / 2;
      try {
        view.scrollTo({ left: Math.max(0, left), top: Math.max(0, top), behavior: 'smooth' });
      } catch (err) {
        view.scrollLeft = Math.max(0, left);
        view.scrollTop = Math.max(0, top);
      }
    }
    drawPaletteView();
    renderPalRulers();
    syncPalBars();
    clearTimeout(VM._palHiTimer);
    VM._palHiTimer = setTimeout(function () {
      VM.palHi = null;
      drawPaletteView();
    }, 2600);
  }

  function renderRulers(W, H, cell) {
    // X 标尺
    var rx = $('rulerX');
    rx.innerHTML = '';
    // 保证相邻刻度间距 >= 26px，否则数字会叠在一起
    var stepX = Math.max(1, Math.ceil(26 / cell));
    for (var x = 0; x < W; x += stepX) {
      var s = document.createElement('span');
      s.textContent = x;
      s.style.cssText = 'position:absolute;left:' + (x * cell) + 'px;top:0;';
      rx.appendChild(s);
    }
    rx.style.height = '18px';

    // Y 标尺
    var ry = $('rulerY');
    ry.innerHTML = '';
    var stepY = Math.max(1, Math.ceil(26 / cell));
    for (var y = 0; y < H; y += stepY) {
      var t = document.createElement('span');
      t.textContent = y;
      t.style.cssText = 'position:absolute;top:' + (y * cell) + 'px;left:0;line-height:1;';
      ry.appendChild(t);
    }
    ry.style.height = (H * cell) + 'px';
  }

  function initPaletteEvents() {
    var cv = $('paletteCanvas');
    var tip = $('paletteTip');

    cv.addEventListener('mousemove', function (e) {
      if (!VM.pixelArt) return;
      var rect = cv.getBoundingClientRect();
      var x = Math.floor((e.clientX - rect.left) / rect.width * VM.artW);
      var y = Math.floor((e.clientY - rect.top) / rect.height * VM.artH);
      if (x < 0 || x >= VM.artW || y < 0 || y >= VM.artH) return;
      var c = VM.pixelArt[y * VM.artW + x];
      tip.textContent = '(' + x + ', ' + y + ')  ' + Codec.hexOf(c);
    });

    cv.addEventListener('click', function (e) {
      if (!VM.pixelArt) return;
      var rect = cv.getBoundingClientRect();
      var x = Math.floor((e.clientX - rect.left) / rect.width * VM.artW);
      var y = Math.floor((e.clientY - rect.top) / rect.height * VM.artH);
      if (x < 0 || x >= VM.artW || y < 0 || y >= VM.artH) return;
      var hex = Codec.hexOf(VM.pixelArt[y * VM.artW + x]);
      tip.textContent = '(' + x + ', ' + y + ')  ' + hex;
      copyText(hex, hex);
    });

    // 触摸
    cv.addEventListener('touchmove', function (e) {
      if (!VM.pixelArt || !e.touches[0]) return;
      var t = e.touches[0];
      var rect = cv.getBoundingClientRect();
      var x = Math.floor((t.clientX - rect.left) / rect.width * VM.artW);
      var y = Math.floor((t.clientY - rect.top) / rect.height * VM.artH);
      if (x < 0 || x >= VM.artW || y < 0 || y >= VM.artH) return;
      tip.textContent = '(' + x + ', ' + y + ')  ' + Codec.hexOf(VM.pixelArt[y * VM.artW + x]);
    }, { passive: true });
  }

  // ==================== 编码 ====================
  function renderCodes() {
    var empty = $('codeEmpty');
    var list = $('codeList');
    if (!VM.pixelArt) {
      empty.hidden = false; list.hidden = true;
      return;
    }
    empty.hidden = true; list.hidden = false;
    var crf = $('codeRefreshBtn');
    if (crf) crf.hidden = false;
    list.innerHTML = '';

    var W = VM.artW, H = VM.artH;
    // 按需渲染：先渲染前 20 列，滚动到底再追加
    var shown = Math.min(20, W);

    function append(from, to) {
      for (var col = from; col < to; col++) {
        var colors = Codec.sampleColumnColors(VM.pixelArt, W, H, col);
        list.appendChild(buildCodeItem(col, colors));
      }
    }

    append(0, shown);

    // 滚动加载
    var scrollEl = list.parentNode;
    scrollEl.onscroll = function () {
      if (scrollEl.scrollTop + scrollEl.clientHeight >= scrollEl.scrollHeight - 200) {
        if (shown < W) {
          var next = Math.min(shown + 20, W);
          append(shown, next);
          shown = next;
        }
      }
      if (VM._syncQuickBar) VM._syncQuickBar();
    };
    if (VM._syncQuickBar) VM._syncQuickBar();
  }

  function buildCodeItem(col, colors) {
    var div = document.createElement('div');
    div.className = 'pcp-code-item';

    var left = document.createElement('div');
    left.className = 'pcp-code-left';

    var colEl = document.createElement('div');
    colEl.className = 'pcp-code-col';
    colEl.textContent = S('code_column', { p1: col + 1 });
    left.appendChild(colEl);

    var strip = document.createElement('div');
    strip.className = 'pcp-code-strip';
    for (var i = 0; i < 16; i++) {
      var chip = document.createElement('i');
      chip.style.background = Codec.hexOf(colors[i]);
      strip.appendChild(chip);
    }
    left.appendChild(strip);

    var val = document.createElement('div');
    val.className = 'pcp-code-value';
    val.textContent = '…';
    left.appendChild(val);

    var btn = document.createElement('button');
    btn.className = 'pcp-code-copy';
    btn.textContent = S('code_copy');

    div.appendChild(left);
    div.appendChild(btn);

    Codec.encodeColumn(colors).then(function (b64) {
      val.textContent = b64;
      val.title = b64;
      val.addEventListener('click', function () {
        copyText(b64, S('code_copied', { p1: col + 1 }));
      });
      btn.addEventListener('click', function () {
        copyText(b64, S('code_copied', { p1: col + 1 }));
      });
    }).catch(function () {
      var b64 = Codec.encodeColumnSync(colors);
      val.textContent = b64;
      val.title = b64;
    });

    return div;
  }

  // ==================== 算法选择 ====================
  var ALGOS = [
    { id: 'nearest', n: 'algo_nearest', d: 'algo_nearest_desc' },
    { id: 'bilinear', n: 'algo_bilinear', d: 'algo_bilinear_desc' },
    { id: 'bicubic', n: 'algo_bicubic', d: 'algo_bicubic_desc' },
    { id: 'lanczos', n: 'algo_lanczos', d: 'algo_lanczos_desc' },
    { id: 'average', n: 'algo_average', d: 'algo_average_desc' },
    { id: 'custom', n: 'algo_custom', d: 'algo_custom_desc' }
  ];

  function renderAlgos() {
    var box = $('algoScroll');
    box.innerHTML = '';
    ALGOS.forEach(function (a) {
      var b = document.createElement('button');
      b.className = 'pcp-algo-card' + (VM.algorithm === a.id ? ' is-active' : '');
      var n = document.createElement('span');
      n.className = 'n';
      n.textContent = S(a.n);
      var d = document.createElement('span');
      d.className = 'd';
      if (a.id === 'custom') {
        d.textContent = S('algo_custom_desc_fmt', {
          p1: KERNEL_NAME[VM.customKernel] || VM.customKernel,
          p2: VM.customSamples
        });
      } else {
        d.textContent = S(a.d);
      }
      b.appendChild(n); b.appendChild(d);
      b.addEventListener('click', function () {
        VM.algorithm = a.id;
        $('customPanel').hidden = (a.id !== 'custom');
        renderAlgos();
        drawCustomPreview();
        drawPreview();
      });
      box.appendChild(b);
    });
  }

  var KERNELS = [
    { id: 'nearest', n: 'algo_nearest', d: 'custom_kernel_desc_nearest' },
    { id: 'bilinear', n: 'algo_bilinear', d: 'custom_kernel_desc_bilinear' },
    { id: 'bicubic', n: 'algo_bicubic', d: 'custom_kernel_desc_bicubic' },
    { id: 'lanczos', n: 'algo_lanczos', d: 'custom_kernel_desc_lanczos' }
  ];

  // 内核 id -> 文案键（避免动态拼 algo_ 前缀导致查不到）
  var KERNEL_NAME = {
    nearest: 'Nearest',
    bilinear: 'Bilinear',
    bicubic: 'Bicubic',
    lanczos: 'Lanczos'
  };

  function renderKernels() {
    var box = $('kernelList');
    box.innerHTML = '';
    KERNELS.forEach(function (k) {
      var b = document.createElement('button');
      b.className = 'pcp-kernel-item' + (VM.customKernel === k.id ? ' is-active' : '');
      var n = document.createElement('span');
      n.className = 'kn';
      n.textContent = S(k.n);
      var d = document.createElement('span');
      d.className = 'kd';
      d.textContent = S(k.d);
      b.appendChild(n); b.appendChild(d);
      b.addEventListener('click', function () {
        VM.customKernel = k.id;
        renderKernels();
        renderAlgos();
        drawCustomPreview();
      });
      box.appendChild(b);
    });
  }

  function drawCustomPreview() {
    if (VM.algorithm !== 'custom') return;
    var cv = $('customPreview');
    var ctx = cv.getContext('2d');
    if (!VM.sourceBitmap) {
      ctx.clearRect(0, 0, cv.width, cv.height);
      return;
    }
    var size = 144;
    var ES = effectiveSource();
    var out = Pix.pixelate(ES.px, ES.w, ES.h,
      size, size, {
      algorithm: 'custom',
      customKernel: VM.customKernel,
      customSamples: VM.customSamples,
      scale: ES.scale, offsetX: ES.ox, offsetY: ES.oy,
      rotationDeg: VM.rotation
    });
    ctx.putImageData(toImageData(out, size, size), 0, 0);
  }

  // ==================== 微调 ====================
  function initFineTune() {
    var btns = document.querySelectorAll('.pcp-micro-btn, .pcp-sq-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () {
        var act = this.getAttribute('data-ft');
        if (!VM.sourceBitmap) { toast(S('pick_first')); return; }
        // 参数只影响像素化结果，原图态下先切到像素化
        if (!VM.isPixelated) VM.isPixelated = true;
        switch (act) {
          // [对齐 App] zoomInStep/zoomOutStep：ZOOM_STEP=0.3 加法，范围 1~12
          case 'zoomin': VM.scale = Math.min(12, VM.scale + 0.3); break;
          case 'zoomout':
            if (VM.scale - 0.3 < 1) { toast(S('toast_min_scale')); return; }
            VM.scale = Math.max(1, VM.scale - 0.3);
            break;
          case 'panup': VM.offsetY = clamp(VM.offsetY - 0.1, -2, 2); break;
          case 'pandown': VM.offsetY = clamp(VM.offsetY + 0.1, -2, 2); break;
          case 'panleft': VM.offsetX = clamp(VM.offsetX - 0.1, -2, 2); break;
          case 'panright': VM.offsetX = clamp(VM.offsetX + 0.1, -2, 2); break;
          case 'ccw':
          case 'cw':
            if (VM.canvasWidth !== VM.canvasHeight) {
              toast(S('rotate_square_only'));
              return;
            }
            VM.rotation += (act === 'cw' ? 90 : -90);
            break;
        }
        drawPreview();
        drawCustomPreview();
      });
    }

    // 拖拽调整
    var box = $('displayBox');
    var dragging = false, sx = 0, sy = 0;
    var ptrs = {};          // pointerId -> {x, y}：支持双指 pinch（同 detectTransformGestures）
    var pinchPrev = 0;
    function ptrList() {
      return Object.keys(ptrs).map(function (k) { return ptrs[k]; });
    }
    function ptrDist() {
      var l = ptrList();
      if (l.length < 2) return 0;
      var dx = l[0].x - l[1].x, dy = l[0].y - l[1].y;
      return Math.sqrt(dx * dx + dy * dy);
    }
    box.addEventListener('pointerdown', function (e) {
      if (!VM.dragAdjustEnabled || !VM.sourceBitmap) return;
      ptrs[e.pointerId] = { x: e.clientX, y: e.clientY };
      var n = ptrList().length;
      if (n === 1) {
        dragging = true;
        sx = e.clientX; sy = e.clientY;
      } else if (n === 2) {
        pinchPrev = ptrDist();
        dragging = false;
      }
      // 原图态下拖动无意义（参数只影响像素化结果），自动切到像素化预览
      if (!VM.isPixelated) { VM.isPixelated = true; drawPreview(true); }
      try { box.setPointerCapture(e.pointerId); } catch (err) {}
    });
    box.addEventListener('pointermove', function (e) {
      if (!(e.pointerId in ptrs)) return;
      e.preventDefault();
      ptrs[e.pointerId] = { x: e.clientX, y: e.clientY };
      var n = ptrList().length;
      // 双指：缩放采样区域（同 detectTransformGestures 的 zoom）
      if (n >= 2) {
        var d = ptrDist();
        if (pinchPrev > 0 && d > 0) {
          dragAdjustZoom(d / pinchPrev);
          drawPreview(true);
        }
        pinchPrev = d;
        return;
      }
      if (!dragging) return;
      var rect = box.getBoundingClientRect();
      // [对齐 App] dragAdjustBy：增量式像素位移，边界动态计算
      dragAdjustBy(e.clientX - sx, e.clientY - sy, rect.width, rect.height);
      sx = e.clientX; sy = e.clientY;
      drawPreview(true);
    });
    function endDrag(e) {
      if (e.pointerId in ptrs) delete ptrs[e.pointerId];
      var n = ptrList().length;
      if (n === 1) {
        // 从双指变单指：重新以剩下那根手指为起点，避免跳变
        var l = ptrList();
        sx = l[0].x; sy = l[0].y;
        dragging = true;
        pinchPrev = 0;
        return;
      }
      if (n > 1) return;
      dragging = false;
      pinchPrev = 0;
      try { box.releasePointerCapture(e.pointerId); } catch (err) {}
      // 松手后用全分辨率重算
      drawPreview();
      drawCustomPreview();
    }
    box.addEventListener('pointerup', endDrag);
    box.addEventListener('pointercancel', endDrag);

    // 滚轮缩放
    box.addEventListener('wheel', function (e) {
      if (!VM.sourceBitmap) return;
      e.preventDefault();
      // [对齐 App] dragAdjustZoom：缩放后重新约束 offset
      dragAdjustZoom(e.deltaY > 0 ? 0.92 : 1.08);
      drawPreview(true);
    }, { passive: false });
  }

  /** [C 方案] 采样源：有原图 + 裁剪矩形时用原图（可拖出裁剪范围）。 */
  function effectiveSource() {
    var cr = VM.cropRect;
    if (VM.originalBitmap && cr && cr.w > 0 && cr.h > 0) {
      var winW = cr.w / VM.scale;
      var winH = cr.h / VM.scale;
      var cx = cr.x + cr.w / 2 + VM.offsetX * cr.w;
      var cy = cr.y + cr.h / 2 + VM.offsetY * cr.h;
      var halfW = winW / 2, halfH = winH / 2;
      cx = clamp(cx, halfW, Math.max(halfW, VM.originalW - halfW));
      cy = clamp(cy, halfH, Math.max(halfH, VM.originalH - halfH));
      return {
        px: VM.originalBitmap,
        w: VM.originalW,
        h: VM.originalH,
        scale: VM.originalW / winW,
        ox: (cx - VM.originalW / 2) / VM.originalW,
        oy: (cy - VM.originalH / 2) / VM.originalH,
      };
    }
    return {
      px: VM.sourceBitmap, w: VM.sourceW, h: VM.sourceH,
      scale: VM.scale, ox: VM.offsetX, oy: VM.offsetY,
    };
  }

  // ==================== 拖拽调整（对应 MainViewModel.dragAdjustBy / dragAdjustZoom） ====================
  /** 计算采样窗口与可移动范围（cover 语义，与 App 完全一致）。 */
  /** 计算采样窗口与可移动范围（C 方案：基于原图 + 裁剪矩形，非对称）。 */
  function dragLimits() {
    var cr = VM.cropRect;
    if (VM.originalBitmap && cr && cr.w > 0 && cr.h > 0) {
      var winW = cr.w / VM.scale;
      var winH = cr.h / VM.scale;
      var cx0 = cr.x + cr.w / 2;
      var cy0 = cr.y + cr.h / 2;
      var minCx = Math.min(cx0, winW / 2);
      var maxCx = Math.max(cx0, VM.originalW - winW / 2);
      var minCy = Math.min(cy0, winH / 2);
      var maxCy = Math.max(cy0, VM.originalH - winH / 2);
      return {
        winW: winW, winH: winH, baseW: cr.w, baseH: cr.h,
        xMin: (minCx - cx0) / cr.w, xMax: (maxCx - cx0) / cr.w,
        yMin: (minCy - cy0) / cr.h, yMax: (maxCy - cy0) / cr.h,
      };
    }
    var srcW = VM.sourceW, srcH = VM.sourceH;
    var targetRatio = VM.canvasWidth / VM.canvasHeight;
    var maxWinW, maxWinH;
    if (srcW / srcH > targetRatio) { maxWinH = srcH; maxWinW = srcH * targetRatio; }
    else { maxWinW = srcW; maxWinH = srcW / targetRatio; }
    var winW2 = maxWinW / VM.scale;
    var winH2 = maxWinH / VM.scale;
    var limX = ((srcW - winW2) / 2 / srcW);
    var limY = ((srcH - winH2) / 2 / srcH);
    if (limX < 0) limX = 0;
    if (limY < 0) limY = 0;
    return {
      winW: winW2, winH: winH2, baseW: srcW, baseH: srcH,
      xMin: -limX, xMax: limX, yMin: -limY, yMax: limY,
    };
  }

  /** [对齐 App] dragAdjustBy：增量式移动采样窗口（C 方案下可拖出裁剪范围）。 */
  function dragAdjustBy(dxPx, dyPx, boxW, boxH) {
    if (!VM.sourceBitmap || boxW <= 0 || boxH <= 0) return;
    var L = dragLimits();
    if (L.xMax - L.xMin <= 0.0001 && L.yMax - L.yMin <= 0.0001) {
      var now = Date.now();
      if (now - (VM._dragHintAt || 0) > 2500) {
        VM._dragHintAt = now;
        toast(S('drag_zoom_first_hint'));
      }
      return;
    }
    var dOffX = -(dxPx / boxW) * (L.winW / L.baseW);
    var dOffY = -(dyPx / boxH) * (L.winH / L.baseH);
    VM.offsetX = clamp(VM.offsetX + dOffX, L.xMin, L.xMax);
    VM.offsetY = clamp(VM.offsetY + dOffY, L.yMin, L.yMax);
  }

  /** [对齐 App] dragAdjustZoom：缩放采样区域并重新约束 offset。 */
  function dragAdjustZoom(factor) {
    if (!VM.sourceBitmap) return;
    var ns = clamp(VM.scale * factor, 0.5, 12);
    if (ns === VM.scale) return;
    VM.scale = ns;
    var L = dragLimits();
    VM.offsetX = clamp(VM.offsetX, L.xMin, L.xMax);
    VM.offsetY = clamp(VM.offsetY, L.yMin, L.yMax);
  }

  // ==================== 操作行 ====================
  function updateActionsUI() {
    var show = !!VM.sourceBitmap;
    $('actionsArea').hidden = !show;
    if (!show) {
      $('collapsedRow').hidden = true;
      return;
    }
    $('actionsRow').hidden = !VM.homeActionsExpanded;
    $('collapsedRow').hidden = VM.homeActionsExpanded;
    $('gridBtn').classList.toggle('is-on', VM.gridEnabled);
    var db = $('displayBox');
    if (db) db.classList.toggle('is-drag', !!VM.dragAdjustEnabled);
  }

  // ==================== 对话框 ====================
  function openDialog(html) {
    $('dialogBox').innerHTML = html;
    $('dialogMask').hidden = false;
  }
  function closeDialog() {
    $('dialogMask').hidden = true;
    $('dialogBox').innerHTML = '';
  }

  function showSizeDialog(isWidth) {
    var cur = isWidth ? VM.canvasWidth : VM.canvasHeight;
    var label = isWidth ? S('canvas_width') : S('canvas_height');
    openDialog(
      '<h3 class="pcp-dialog-title">' + label + '</h3>' +
      '<input class="pcp-size-input" id="dlgSize" type="number" min="1" max="999" value="' + cur + '">' +
      '<div class="pcp-size-err" id="dlgErr"></div>' +
      '<div class="pcp-dialog-actions">' +
      '<button class="pcp-dialog-btn" id="dlgCancel">' + S('action_cancel') + '</button>' +
      '<button class="pcp-dialog-btn primary" id="dlgOk">' + S('action_confirm') + '</button>' +
      '</div>'
    );
    var inp = $('dlgSize');
    inp.focus(); inp.select();
    $('dlgCancel').onclick = closeDialog;
    $('dlgOk').onclick = function () {
      var v = parseInt(inp.value, 10);
      if (!(v >= 1 && v <= 999)) {
        $('dlgErr').textContent = S('size_invalid');
        return;
      }
      if (isWidth) VM.canvasWidth = v; else VM.canvasHeight = v;
      updateSizeUI();
      renderPresets();
      drawPreview();
      closeDialog();
    };
  }

  /** 复刻 HomeScreen.kt 的 CustomSizeDialog：输入 W×H 并保存为常用尺寸。 */
  function showPresetDialog() {
    openDialog(
      '<h3 class="pcp-dialog-title">' + S('custom_size_preset') + '</h3>' +
      '<div class="pcp-custom-size-row">' +
      '<input class="pcp-size-input" id="dlgW" type="number" min="1" max="999" value="' + VM.canvasWidth + '" placeholder="W">' +
      '<span class="pcp-custom-x">×</span>' +
      '<input class="pcp-size-input" id="dlgH" type="number" min="1" max="999" value="' + VM.canvasHeight + '" placeholder="H">' +
      '</div>' +
      '<div class="pcp-size-err" id="dlgErr"></div>' +
      '<div class="pcp-dialog-actions">' +
      '<button class="pcp-dialog-btn" id="dlgCancel">' + S('action_cancel') + '</button>' +
      '<button class="pcp-dialog-btn primary" id="dlgOk">' + S('action_confirm') + '</button>' +
      '</div>'
    );
    var iw = $('dlgW'), ih = $('dlgH'), err = $('dlgErr'), ok = $('dlgOk');
    function validate() {
      var w = parseInt(iw.value, 10), h = parseInt(ih.value, 10);
      var valid = (w >= 1 && w <= 999) && (h >= 1 && h <= 999);
      err.textContent = valid ? '' : S('size_invalid');
      ok.disabled = !valid;
      return valid ? [w, h] : null;
    }
    iw.addEventListener('input', validate);
    ih.addEventListener('input', validate);
    iw.focus(); iw.select();
    $('dlgCancel').onclick = closeDialog;
    ok.onclick = function () {
      var v = validate();
      if (!v) return;
      saveCustomPreset(v[0], v[1]); // [对齐 App] 保存为常用尺寸并应用
      closeDialog();
    };
    validate();
  }

  function showLangDialog() {
    var langs = [
      { id: 'zh', name: '简体中文' },
      { id: 'en', name: 'English' },
      { id: 'ja', name: '日本語' }
    ];
    var html = '<h3 class="pcp-dialog-title">Language</h3><div class="pcp-lang-list">';
    langs.forEach(function (l) {
      html += '<button class="pcp-lang-item' + (lang === l.id ? ' is-active' : '') +
        '" data-lang="' + l.id + '">' + l.name + '</button>';
    });
    html += '</div>';
    openDialog(html);
    var bts = $('dialogBox').querySelectorAll('.pcp-lang-item');
    for (var i = 0; i < bts.length; i++) {
      bts[i].onclick = function () {
        lang = this.getAttribute('data-lang');
        try { localStorage.setItem('pcp.lang', lang); } catch (e) {}
        closeDialog();
        applyI18n();
        renderPresets();
        renderAlgos();
        renderKernels();
        renderCodes();
        playWelcome();
        renderAbout();
      };
    }
  }

  // ==================== 设置页 ====================
  function openSettings() {
    var html =
      '<div class="pcp-editor-head" style="padding:16px">' +
      '<button class="pcp-back-btn" id="setBack"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg></button>' +
      '<h2 class="pcp-editor-title">' + S('settings') + '</h2></div>' +
      '<div class="pcp-scroll" style="padding-top:0">' +
      '<div class="pcp-glow-card"><div class="pcp-glow-inner pcp-card-pad">' +

      '<div class="pcp-set-row">' +
      '<div class="pcp-set-main">' +
      '<div class="pcp-set-title">' + S('settings_auto_generate') + '</div>' +
      '<div class="pcp-set-desc">' + S('settings_auto_generate_desc') + '</div>' +
      '</div>' +
      '<button class="pcp-switch' + (VM.autoGenerate ? ' on' : '') + '" id="swAuto"></button>' +
      '</div>' +

      '<div class="pcp-set-row">' +
      '<div class="pcp-set-main">' +
      '<div class="pcp-set-title">' + S('settings_theme') + '</div>' +
      '<div class="pcp-set-desc" id="themeDesc">' + S(THEME_LABEL[VM.themeMode]) + '</div>' +
      '</div>' +
      '<div class="pcp-set-value" id="themeVal"><span id="themeLabel">' + S(THEME_LABEL[VM.themeMode]) + '</span>' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>' +
      '</div>' +
      '</div>' +

      '<div class="pcp-set-row">' +
      '<div class="pcp-set-main">' +
      '<div class="pcp-set-title">Language</div>' +
      '<div class="pcp-set-desc" id="langDesc">' + LANG_NAME[lang] + '</div>' +
      '</div>' +
      '<div class="pcp-set-value" id="langVal"><span id="langLabel">' + LANG_NAME[lang] + '</span>' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>' +
      '</div>' +
      '</div>' +

      '</div></div></div>';

    var sub = document.createElement('div');
    sub.className = 'pcp-subpage';
    sub.id = 'subSettings';
    sub.innerHTML = html;
    $('app').appendChild(sub);

    $('setBack').onclick = function () { sub.remove(); };

    $('swAuto').onclick = function () {
      VM.autoGenerate = !VM.autoGenerate;
      this.classList.toggle('on', VM.autoGenerate);
      try { localStorage.setItem('pcp.autoGen', VM.autoGenerate ? '1' : '0'); } catch (e) {}
    };

    $('themeVal').onclick = function () {
      var modes = ['system', 'light', 'dark'];
      var idx = modes.indexOf(VM.themeMode);
      VM.themeMode = modes[(idx + 1) % 3];
      try { localStorage.setItem('pcp.theme', VM.themeMode); } catch (e) {}
      applyTheme();
      var txt = S(THEME_LABEL[VM.themeMode]);
      $('themeDesc').textContent = txt;
      $('themeLabel').textContent = txt;
    };

    $('langVal').onclick = function () {
      var langs = ['zh', 'en', 'ja'];
      var i = langs.indexOf(lang);
      lang = langs[(i + 1) % 3];
      try { localStorage.setItem('pcp.lang', lang); } catch (e) {}
      applyI18n();
      renderPresets();
      renderAlgos();
      renderKernels();
      renderCodes();
      playWelcome();
      renderAbout();
      $('langDesc').textContent = LANG_NAME[lang];
      $('langLabel').textContent = LANG_NAME[lang];
    };
  }

  var LANG_NAME = { zh: '简体中文', en: 'English', ja: '日本語' };

  // ==================== 关于页 ====================
  // 文案与结构完全对应 AboutScreen.kt + strings.xml 的 about_* 条目
  function renderAbout() {
    var box = $('aboutScroll');
    var html = '';

    html += '<div class="pcp-about-hero">';
    // [对齐 App] AboutScreen 顶部不是 app 图标，而是渐变方块 + “PC” 文字
    html += '<div class="pcp-about-logo pcp-about-pc">PC</div>';
    html += '<h2 class="pcp-about-name">PixelColorPicker</h2>';
    html += '<div class="pcp-about-ver">' + S('about_version') + '</div>';
    html += '</div>';

    if (VM.genCount > 0) {
      html += '<div class="pcp-about-stat">' +
        S('about_stat_fmt', { p1: VM.genCount }) + '</div>';
    }

    // 这是什么
    html += card(S('about_what_title'), '<p class="pcp-about-p">' + S('about_what_body') + '</p>');
    // 使用流程
    html += card(S('about_flow_title'), '<p class="pcp-about-p">' +
      esc(S('about_flow_body')).replace(/\n/g, '<br>') + '</p>');
    // 色板码协议
    html += card(S('about_codec_title'), '<p class="pcp-about-p">' + S('about_codec_body') + '</p>');
    // 隐私
    html += card(S('about_privacy_title'), '<p class="pcp-about-p">' + S('about_privacy_body') + '</p>');
    // 贡献者
    html += card(S('about_credits_title'), '<p class="pcp-about-p">' + S('about_credits_body') + '</p>');

    // 网页端额外说明（诚实标注差异）
    html += card('Web 版说明',
      '<p class="pcp-about-p">本项目是 Android 版的浏览器移植。' +
      '色板码协议与 App 完全一致，同一份像素画在两边生成的码可互换使用。</p>' +
      '<p class="pcp-about-p" style="margin-top:8px">' +
      '差异：保存图片改为浏览器下载 PNG；无「检查更新」；图片选择使用网页文件选择器。</p>');

    // 链接
    html += '<div class="pcp-glow-card pcp-about-card"><div class="pcp-glow-inner pcp-card-pad">';
    html += '<a class="pcp-about-link" href="https://github.com/YYGYLTY/PixelColorPicker" target="_blank" rel="noopener">GitHub Repository →</a>';
    html += '</div></div>';

    html += '<div style="height:24px"></div>';
    box.innerHTML = html;

    var logo = $('aboutLogo');
    if (logo) drawLogo(logo, 152);
  }

  function card(title, body) {
    return '<div class="pcp-glow-card pcp-about-card"><div class="pcp-glow-inner pcp-card-pad">' +
      '<div class="pcp-about-sect-title">' + esc(title) + '</div>' + body + '</div></div>';
  }

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ==================== 编辑器 ====================
  function openEditor(blank) {
    // [对齐 App] MainViewModel.openEditor：优先加载已生成的像素画，
    // 没有才用空白画布。原先永远新建空白，导致「画布编辑」打开的是空图。
    if (blank || !VM.pixelArt) {
      VM.editW = VM.canvasWidth;
      VM.editH = VM.canvasHeight;
      VM.editPixels = new Int32Array(VM.editW * VM.editH).fill(0);
    } else {
      VM.editW = VM.artW;
      VM.editH = VM.artH;
      VM.editPixels = new Int32Array(VM.pixelArt);
    }
    VM.undo = [];
    VM.redo = [];

    var sub = document.createElement('div');
    sub.className = 'pcp-subpage';
    sub.id = 'subEditor';
    sub.innerHTML =
      '<div class="pcp-editor-head">' +
      '<button class="pcp-back-btn" id="edBack"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg></button>' +
      '<h2 class="pcp-editor-title">' + S('new_blank_canvas') + '</h2>' +
      '<span class="pcp-editor-meta" id="edMeta"></span>' +
      '</div>' +
      '<div class="pcp-editor-wrap"><canvas id="editorCanvas"></canvas></div>' +
      '<div class="pcp-ed-body">' +
      '<div class="pcp-ed-tools" id="edTools"></div>' +
      '<div class="pcp-ed-palette" id="edPalette"></div>' +
      '<div class="pcp-ed-info" id="edInfo"></div>' +
      '<div class="pcp-ed-bottom" id="edBottom"></div>' +
      '</div>';
    $('app').appendChild(sub);

    $('edBack').onclick = function () {
      sub.remove();
      VM.subScreen = null;
    };

    // ---------- 工具栏（图标 + 文字，同 ToolBar） ----------
    var tools = [
      { id: 'brush', key: 'editor_tool_pencil', icon: '<path fill="currentColor" stroke="none" d="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z"/>' },
      { id: 'eraser', key: 'editor_tool_eraser', icon: '<path fill="currentColor" stroke="none" d="M7.5,5.6L10,7 8.6,9.5 6.1,8.1 7.5,5.6M19.5,15.4L22,16.8 20.6,19.3 18.1,17.9 19.5,15.4M22,5.8L20.6,3.3 18.1,4.7 19.5,7.2 22,5.8M13.6,8.4L11.1,7 12.5,4.5 15,5.9 13.6,8.4M7.8,14.8L5.3,13.4 6.7,10.9 9.2,12.3 7.8,14.8M11.4,19.4L8.9,18 10.3,15.5 12.8,16.9 11.4,19.4M19.1,9.4L16.6,8 18,5.5 20.5,6.9 19.1,9.4M4.9,9.4L2.4,8 3.8,5.5 6.3,6.9 4.9,9.4M16.6,20.4L14.1,19 15.5,16.5 18,17.9 16.6,20.4M2.5,19.4L5,18 3.6,15.5 1.1,16.9 2.5,19.4z"/>' },
      { id: 'eyedropper', key: 'editor_tool_eyedropper', icon: '<path fill="currentColor" stroke="none" d="M17.66,5.41l0.92,0.92 -2.69,2.69 -0.92,-0.92 2.69,-2.69M17.67,3c-0.26,0 -0.51,0.1 -0.71,0.29l-3.12,3.12 -1.93,-1.91 -1.41,1.41 0.42,0.42 -3.25,3.25V21h4.75l8.92,-8.92 0.42,0.42 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.4 0.4,-1.03 0,-1.42l-2.34,-2.34c-0.2,-0.19 -0.45,-0.29 -0.71,-0.29zM6.92,19L5,17.08l8.06,-8.06 1.92,1.92L6.92,19z"/>' },
      { id: 'fill', key: 'editor_tool_fill', icon: '<path fill="currentColor" stroke="none" d="M16.56,8.94L7.62,0 6.21,1.41l2.38,2.38 -5.15,5.15c-0.59,0.59 -0.59,1.54 0,2.12l5.5,5.5c0.29,0.29 0.68,0.44 1.06,0.44s0.77,-0.15 1.06,-0.44l5.5,-5.5c0.59,-0.58 0.59,-1.53 0,-2.12zM5.21,10L10,5.21 14.79,10H5.21zM19,11.5s-2,2.17 -2,3.5c0,1.1 0.9,2 2,2s2,-0.9 2,-2c0,-1.33 -2,-3.5 -2,-3.5z"/>' }
    ];
    var edTools = $('edTools');
    tools.forEach(function (t) {
      var b = document.createElement('button');
      b.className = 'pcp-ed-tool' + (VM.editTool === t.id ? ' is-active' : '');
      b.innerHTML = '<svg viewBox="0 0 24 24" fill="none">' + t.icon + '</svg>' +
        '<span>' + S(t.key) + '</span>';
      b.onclick = function () {
        VM.editTool = t.id;
        edTools.querySelectorAll('.pcp-ed-tool').forEach(function (x) { x.classList.remove('is-active'); });
        b.classList.add('is-active');
      };
      edTools.appendChild(b);
    });

    // ---------- 颜色信息卡片（同 ColorInfoCard） ----------
    var edInfo = $('edInfo');
    edInfo.innerHTML =
      '<div class="pcp-ed-info-row">' +
      '<button class="pcp-ed-swatch" id="edSwatch"></button>' +
      '<div class="pcp-ed-info-txt">' +
      '<div class="pcp-ed-lbl">' + S('editor_color') + '</div>' +
      '<div class="pcp-ed-hex" id="edHex"></div>' +
      '</div>' +
      '<button class="pcp-ed-custom" id="edCustom">' + S('editor_custom_color') + '</button>' +
      '</div>' +
      '<div class="pcp-ed-coord" id="edCoord"></div>';
    $('edSwatch').onclick = function () { pickEditorColor(); };
    $('edCustom').onclick = function () { pickEditorColor(); };

    // ---------- 底部按钮（同 EditorScreen 底部） ----------
    var edBottom = $('edBottom');
    edBottom.innerHTML =
      '<button class="pcp-ed-primary" id="edAddHome">' +
      '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="none"><path fill="currentColor" d="M21,5h-3V2h-2v3H13v2h3v3h2V7h3zM4,5h7v2H4v12h12v-7h2v7a2,2 0 0 1 -2,2H4a2,2 0 0 1 -2,-2V7a2,2 0 0 1 2,-2z"/></svg>' +
      '<span>' + S('editor_add_to_home') + '</span></button>' +
      '<button class="pcp-ed-primary" id="edSave">' +
      '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="none"><path fill="currentColor" d="M17,3H5a2,2 0 0 0 -2,2v14a2,2 0 0 0 2,2h14a2,2 0 0 0 2,-2V7l-4,-4zM12,19a3,3 0 1 1 0,-6 3,3 0 0 1 0,6zM15,9H5V5h10z"/></svg>' +
      '<span>' + S('editor_save') + '</span></button>' +
      '<div class="pcp-ed-row">' +
      '<button class="pcp-ed-outline" id="edExport">' + S('editor_export_png') + '</button>' +
      '<button class="pcp-ed-outline" id="edFillAll">' + S('editor_fill_all') + '</button>' +
      '<button class="pcp-ed-outline is-danger" id="edClear">' + S('editor_clear') + '</button>' +
      '</div>';
    $('edAddHome').onclick = function () { editorToHome(false); };
    $('edSave').onclick = function () { editorToHome(true); };
    $('edExport').onclick = function () { exportEditorPng(); };
    $('edFillAll').onclick = function () { fillEditorAll(); };
    $('edClear').onclick = function () { doClear(); };

    initEditorCanvas();
    renderEditor();
    renderEditorPalette();
    renderEdInfo();
  }

  /** [对齐 App] EditorScreen 的调色板：从当前作品提取颜色 + 「+」添加。 */
  function renderEditorPalette() {
    var box = $('edPalette');
    if (!box || !VM.editPixels) return;
    box.innerHTML = '';

    var seen = {};
    for (var i = 0; i < VM.editPixels.length; i++) {
      var c = VM.editPixels[i];
      if (((c >>> 24) & 0xFF) === 0) continue;
      seen[c >>> 0] = c;
    }
    var colors = Object.keys(seen).map(Number).sort(function (a, b) { return a - b; });

    // 「+」添加颜色
    var add = document.createElement('button');
    add.className = 'pcp-pal-add';
    add.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>';
    add.onclick = function () {
      var inp = document.createElement('input');
      inp.type = 'color';
      inp.value = '#' + ((VM.editColor >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
      inp.style.cssText = 'position:fixed;left:-9999px';
      document.body.appendChild(inp);
      inp.onchange = function () {
        VM.editColor = Codec.parseHex(inp.value);
        document.body.removeChild(inp);
        renderEditorPalette();
      };
      inp.click();
    };
    box.appendChild(add);

    colors.forEach(function (cv) {
      var b = document.createElement('button');
      var hex = '#' + ((cv >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
      b.className = 'pcp-pal-color' + (VM.editColor === cv ? ' is-active' : '');
      b.style.background = hex;
      b.title = hex;
      b.onclick = function () {
        VM.editColor = cv;
        renderEditorPalette();
      };
      box.appendChild(b);
    });
  }

  /** 选颜色（自定义）：App 是 ColorSheet，网页用原生取色器。 */
  function pickEditorColor() {
    var inp = document.createElement('input');
    inp.type = 'color';
    inp.value = '#' + ((VM.editColor >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
    inp.style.cssText = 'position:fixed;left:-9999px';
    document.body.appendChild(inp);
    inp.onchange = function () {
      VM.editColor = Codec.parseHex(inp.value);
      document.body.removeChild(inp);
      renderEditorPalette();
      renderEdInfo();
    };
    inp.click();
  }

  /** 颜色信息卡片（同 ColorInfoCard）：当前色块 + hex + 选中坐标。 */
  function renderEdInfo() {
    var sw = $('edSwatch'), hx = $('edHex'), co = $('edCoord');
    if (!sw) return;
    var cur = VM.editColor;
    var hex = '#' + ((cur >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
    sw.style.background = hex;
    hx.textContent = hex;
    var idx = VM.edSel;
    if (idx != null && idx >= 0 && idx < VM.editPixels.length) {
      var x = idx % VM.editW;
      var y = (idx / VM.editW) | 0;
      var c = VM.editPixels[idx];
      var ch = '#' + ((c >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
      var a = (c >>> 24) & 0xFF;
      co.innerHTML =
        '<span class="pcp-ed-coord-dot" style="background:' + ch + '"></span>' +
        '<span class="pcp-ed-coord-txt">' + S('editor_coord') + ': (' + x + ', ' + y + ')<br>' +
        ch + '  A=' + a + '</span>';
      co.classList.add('is-on');
    } else {
      co.innerHTML = '';
      co.classList.remove('is-on');
    }
  }

  /** 添加图片到首页 / 保存图片（同 addEditorArtToHome / saveEditorArt）。 */
  function editorToHome(withToast) {
    if (!VM.editPixels) return;
    VM.pixelArt = new Int32Array(VM.editPixels);
    VM.artW = VM.editW;
    VM.artH = VM.editH;
    VM.isPixelated = true;
    var sb = $('saveAlbumBtn');
    if (sb) sb.disabled = false;
    drawPreview();
    renderPalette();
    renderCodes();
    toast(S(withToast ? 'editor_saved' : 'editor_added_to_home'));
    if (!withToast) {
      var sub = $('subEditor');
      if (sub) sub.remove();
      VM.subScreen = null;
    }
  }

  /** 导出 PNG（App 用 PngExporter 写相册，网页用下载）。 */
  function exportEditorPng() {
    if (!VM.editPixels) return;
    var W = VM.editW, H = VM.editH;
    var c = document.createElement('canvas');
    c.width = W; c.height = H;
    var cx = c.getContext('2d');
    var img = cx.createImageData(W, H);
    for (var i = 0; i < W * H; i++) {
      var p = VM.editPixels[i];
      img.data[i * 4] = (p >>> 16) & 0xFF;
      img.data[i * 4 + 1] = (p >>> 8) & 0xFF;
      img.data[i * 4 + 2] = p & 0xFF;
      img.data[i * 4 + 3] = (p >>> 24) & 0xFF;
    }
    cx.putImageData(img, 0, 0);
    c.toBlob(function (blob) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'pixel_art_' + Date.now() + '.png';
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
      toast(S('editor_saved'));
    }, 'image/png');
  }

  /** 全部填充为当前颜色（同 editorFillAll）。 */
  function fillEditorAll() {
    if (!VM.editPixels) return;
    pushUndo();
    VM.editPixels.fill(VM.editColor);
    renderEditor();
    renderEditorPalette();
    renderEdInfo();
  }

  var edCtx = null, edPainting = false, edLastX = -1, edLastY = -1;

  /** 编码页右侧快速定位滑块（同 QuickBar）。 */
  function initQuickBar() {
    var sc = $('codeScroll'), qb = $('codeQb'), th = $('codeQbThumb');
    if (!sc || !qb || !th) return;

    function sync() {
      if (!VM.pixelArt) { qb.hidden = true; return; }
      var total = sc.scrollHeight - sc.clientHeight;
      if (total <= 4) { qb.hidden = true; return; }
      qb.hidden = false;
      var trackH = qb.clientHeight;
      var visRatio = Math.min(1, Math.max(0.05, sc.clientHeight / Math.max(1, sc.scrollHeight)));
      var thH = Math.max(28, Math.round(trackH * visRatio));
      th.style.height = thH + 'px';
      var f = total > 0 ? sc.scrollTop / total : 0;
      th.style.top = Math.round(f * (trackH - thH)) + 'px';
    }
    VM._syncQuickBar = sync;

    var dragging = false;
    function seek(e) {
      var r = qb.getBoundingClientRect();
      var f = clamp((e.clientY - r.top) / r.height, 0, 1);
      sc.scrollTop = f * (sc.scrollHeight - sc.clientHeight);
      sync();
    }
    qb.addEventListener('pointerdown', function (e) {
      dragging = true;
      qb.classList.add('is-pressed');
      try { qb.setPointerCapture(e.pointerId); } catch (err) {}
      seek(e);
      e.preventDefault();
    });
    qb.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      e.preventDefault();
      seek(e);
    });
    function up() { dragging = false; qb.classList.remove('is-pressed'); }
    qb.addEventListener('pointerup', up);
    qb.addEventListener('pointercancel', up);
    sc.addEventListener('scroll', sync, { passive: true });
    window.addEventListener('resize', sync);
    sync();
  }

  function initEditorCanvas() {
    var cv = $('editorCanvas');
    if (!cv) return;

    function cellFrom(e) {
      var r = cv.getBoundingClientRect();
      return {
        x: clamp(Math.floor((e.clientX - r.left) / r.width * VM.editW), 0, VM.editW - 1),
        y: clamp(Math.floor((e.clientY - r.top) / r.height * VM.editH), 0, VM.editH - 1)
      };
    }

    function paint(e) {
      var c = cellFrom(e);
      if (c.x === edLastX && c.y === edLastY) return;
      edLastX = c.x; edLastY = c.y;
      VM.edSel = c.y * VM.editW + c.x;
      if (VM.editTool === 'brush') VM.editPixels[c.y * VM.editW + c.x] = VM.editColor;
      else if (VM.editTool === 'eraser') VM.editPixels[c.y * VM.editW + c.x] = 0;
      renderEditor();
      renderEdInfo();
    }

    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var c = cellFrom(e);
      VM.edSel = c.y * VM.editW + c.x;

      if (VM.editTool === 'eyedropper') {
        var col = VM.editPixels[c.y * VM.editW + c.x];
        if ((col >>> 24) !== 0) {
          VM.editColor = col;
          var ci = document.querySelector('.pcp-color-input');
          if (ci) ci.value = Codec.hexOf(col);
          toast(Codec.hexOf(col));
        }
        renderEdInfo();
        return;
      }

      pushUndo();

      if (VM.editTool === 'fill') {
        doFill(c.x, c.y, VM.editColor);
        renderEditor();
        renderEdInfo();
        return;
      }

      edPainting = true;
      edLastX = -1; edLastY = -1;
      paint(e);
      cv.setPointerCapture(e.pointerId);
    });

    cv.addEventListener('pointermove', function (e) {
      if (!edPainting) return;
      e.preventDefault();
      paint(e);
    });

    function stop(e) {
      edPainting = false;
      edLastX = -1; edLastY = -1;
      try { cv.releasePointerCapture(e.pointerId); } catch (x) {}
    }
    cv.addEventListener('pointerup', stop);
    cv.addEventListener('pointercancel', stop);
  }

  function renderEditor() {
    var cv = $('editorCanvas');
    if (!cv || !VM.editPixels) return;
    var W = VM.editW, H = VM.editH;

    var wrapEl = document.querySelector('.pcp-editor-wrap');
    var wrapH = wrapEl ? wrapEl.clientHeight - 24 : Math.min(window.innerHeight * 0.5, 460);
    var maxH = Math.max(160, wrapH);
    var maxW = window.innerWidth - 60;
    var s = Math.min(maxW / W, maxH / H);
    var mult = s >= 1 ? Math.max(1, Math.floor(s)) : s;
    var cw = Math.max(1, Math.round(W * mult));
    var ch = Math.max(1, Math.round(H * mult));

    cv.width = cw;
    cv.height = ch;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;

    // 一次性 ImageData 绘制（999×999 也不会卡）
    var img = ctx.createImageData(cw, ch);
    var d = img.data;
    for (var y = 0; y < H; y++) {
      var y0 = Math.floor(y * mult);
      var y1 = Math.max(y0 + 1, Math.floor((y + 1) * mult));
      for (var x = 0; x < W; x++) {
        var c = VM.editPixels[y * W + x];
        var a = (c >>> 24) & 0xFF;
        var r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF;
        var x0 = Math.floor(x * mult);
        var x1 = Math.max(x0 + 1, Math.floor((x + 1) * mult));
        for (var py = y0; py < y1; py++) {
          var base = py * cw * 4;
          for (var px = x0; px < x1; px++) {
            var i = base + px * 4;
            if (a === 0) {
              // 棋盘格底纹（同 drawCheckerFill）
              var cx = Math.floor(px / Math.max(1, mult));
              var cy2 = Math.floor(py / Math.max(1, mult));
              var v = ((cx + cy2) & 1) ? 168 : 196;
              d[i] = v; d[i + 1] = v; d[i + 2] = v; d[i + 3] = 255;
            } else {
              d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = a;
            }
          }
        }
      }
    }
    ctx.putImageData(img, 0, 0);

    // 网格线（格子够大时才画）
    if (mult >= 6 && W <= 200 && H <= 200) {
      ctx.strokeStyle = 'rgba(128,128,128,0.22)';
      ctx.lineWidth = 1;
      for (var gx = 1; gx < W; gx++) {
        var px2 = Math.round(gx * mult) + 0.5;
        ctx.beginPath(); ctx.moveTo(px2, 0); ctx.lineTo(px2, ch); ctx.stroke();
      }
      for (var gy = 1; gy < H; gy++) {
        var py2 = Math.round(gy * mult) + 0.5;
        ctx.beginPath(); ctx.moveTo(0, py2); ctx.lineTo(cw, py2); ctx.stroke();
      }
    }
  }

  function renderEditorPalette() {
    var box = $('edPalette');
    if (!box || !VM.editPixels) return;
    box.innerHTML = '';

    var seen = {};
    for (var i = 0; i < VM.editPixels.length; i++) {
      var c = VM.editPixels[i];
      if (((c >>> 24) & 0xFF) === 0) continue;
      seen[c >>> 0] = c;
    }
    var colors = Object.keys(seen).map(Number).sort(function (a, b) { return a - b; });

    // 「+」添加颜色
    var add = document.createElement('button');
    add.className = 'pcp-pal-add';
    add.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>';
    add.onclick = function () {
      var inp = document.createElement('input');
      inp.type = 'color';
      inp.value = '#' + ((VM.editColor >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
      inp.style.cssText = 'position:fixed;left:-9999px';
      document.body.appendChild(inp);
      inp.onchange = function () {
        VM.editColor = Codec.parseHex(inp.value);
        document.body.removeChild(inp);
        renderEditorPalette();
      };
      inp.click();
    };
    box.appendChild(add);

    colors.forEach(function (cv) {
      var b = document.createElement('button');
      var hex = '#' + ((cv >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
      b.className = 'pcp-pal-color' + (VM.editColor === cv ? ' is-active' : '');
      b.style.background = hex;
      b.title = hex;
      b.onclick = function () {
        VM.editColor = cv;
        renderEditorPalette();
      };
      box.appendChild(b);
    });
  }

  /** 选颜色（自定义）：App 是 ColorSheet，网页用原生取色器。 */
  function pickEditorColor() {
    var inp = document.createElement('input');
    inp.type = 'color';
    inp.value = '#' + ((VM.editColor >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
    inp.style.cssText = 'position:fixed;left:-9999px';
    document.body.appendChild(inp);
    inp.onchange = function () {
      VM.editColor = Codec.parseHex(inp.value);
      document.body.removeChild(inp);
      renderEditorPalette();
      renderEdInfo();
    };
    inp.click();
  }

  /** 颜色信息卡片（同 ColorInfoCard）：当前色块 + hex + 选中坐标。 */
  function renderEdInfo() {
    var sw = $('edSwatch'), hx = $('edHex'), co = $('edCoord');
    if (!sw) return;
    var cur = VM.editColor;
    var hex = '#' + ((cur >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
    sw.style.background = hex;
    hx.textContent = hex;
    var idx = VM.edSel;
    if (idx != null && idx >= 0 && idx < VM.editPixels.length) {
      var x = idx % VM.editW;
      var y = (idx / VM.editW) | 0;
      var c = VM.editPixels[idx];
      var ch = '#' + ((c >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
      var a = (c >>> 24) & 0xFF;
      co.innerHTML =
        '<span class="pcp-ed-coord-dot" style="background:' + ch + '"></span>' +
        '<span class="pcp-ed-coord-txt">' + S('editor_coord') + ': (' + x + ', ' + y + ')<br>' +
        ch + '  A=' + a + '</span>';
      co.classList.add('is-on');
    } else {
      co.innerHTML = '';
      co.classList.remove('is-on');
    }
  }

  /** 添加图片到首页 / 保存图片（同 addEditorArtToHome / saveEditorArt）。 */
  function editorToHome(withToast) {
    if (!VM.editPixels) return;
    VM.pixelArt = new Int32Array(VM.editPixels);
    VM.artW = VM.editW;
    VM.artH = VM.editH;
    VM.isPixelated = true;
    var sb = $('saveAlbumBtn');
    if (sb) sb.disabled = false;
    drawPreview();
    renderPalette();
    renderCodes();
    toast(S(withToast ? 'editor_saved' : 'editor_added_to_home'));
    if (!withToast) {
      var sub = $('subEditor');
      if (sub) sub.remove();
      VM.subScreen = null;
    }
  }

  /** 导出 PNG（App 用 PngExporter 写相册，网页用下载）。 */
  function exportEditorPng() {
    if (!VM.editPixels) return;
    var W = VM.editW, H = VM.editH;
    var c = document.createElement('canvas');
    c.width = W; c.height = H;
    var cx = c.getContext('2d');
    var img = cx.createImageData(W, H);
    for (var i = 0; i < W * H; i++) {
      var p = VM.editPixels[i];
      img.data[i * 4] = (p >>> 16) & 0xFF;
      img.data[i * 4 + 1] = (p >>> 8) & 0xFF;
      img.data[i * 4 + 2] = p & 0xFF;
      img.data[i * 4 + 3] = (p >>> 24) & 0xFF;
    }
    cx.putImageData(img, 0, 0);
    c.toBlob(function (blob) {
      var a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'pixel_art_' + Date.now() + '.png';
      a.click();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
      toast(S('editor_saved'));
    }, 'image/png');
  }

  /** 全部填充为当前颜色（同 editorFillAll）。 */
  function fillEditorAll() {
    if (!VM.editPixels) return;
    pushUndo();
    VM.editPixels.fill(VM.editColor);
    renderEditor();
    renderEditorPalette();
    renderEdInfo();
  }

  var edCtx = null, edPainting = false, edLastX = -1, edLastY = -1;

  /** 编码页右侧快速定位滑块（同 QuickBar）。 */
  function initQuickBar() {
    var sc = $('codeScroll'), qb = $('codeQb'), th = $('codeQbThumb');
    if (!sc || !qb || !th) return;

    function sync() {
      if (!VM.pixelArt) { qb.hidden = true; return; }
      var total = sc.scrollHeight - sc.clientHeight;
      if (total <= 4) { qb.hidden = true; return; }
      qb.hidden = false;
      var trackH = qb.clientHeight;
      var visRatio = Math.min(1, Math.max(0.05, sc.clientHeight / Math.max(1, sc.scrollHeight)));
      var thH = Math.max(28, Math.round(trackH * visRatio));
      th.style.height = thH + 'px';
      var f = total > 0 ? sc.scrollTop / total : 0;
      th.style.top = Math.round(f * (trackH - thH)) + 'px';
    }
    VM._syncQuickBar = sync;

    var dragging = false;
    function seek(e) {
      var r = qb.getBoundingClientRect();
      var f = clamp((e.clientY - r.top) / r.height, 0, 1);
      sc.scrollTop = f * (sc.scrollHeight - sc.clientHeight);
      sync();
    }
    qb.addEventListener('pointerdown', function (e) {
      dragging = true;
      qb.classList.add('is-pressed');
      try { qb.setPointerCapture(e.pointerId); } catch (err) {}
      seek(e);
      e.preventDefault();
    });
    qb.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      e.preventDefault();
      seek(e);
    });
    function up() { dragging = false; qb.classList.remove('is-pressed'); }
    qb.addEventListener('pointerup', up);
    qb.addEventListener('pointercancel', up);
    sc.addEventListener('scroll', sync, { passive: true });
    window.addEventListener('resize', sync);
    sync();
  }

  function initEditorCanvas() {
    var cv = $('editorCanvas');
    if (!cv) return;

    function cellFrom(e) {
      var r = cv.getBoundingClientRect();
      return {
        x: clamp(Math.floor((e.clientX - r.left) / r.width * VM.editW), 0, VM.editW - 1),
        y: clamp(Math.floor((e.clientY - r.top) / r.height * VM.editH), 0, VM.editH - 1)
      };
    }

    function paint(e) {
      var c = cellFrom(e);
      if (c.x === edLastX && c.y === edLastY) return;
      edLastX = c.x; edLastY = c.y;
      VM.edSel = c.y * VM.editW + c.x;
      if (VM.editTool === 'brush') VM.editPixels[c.y * VM.editW + c.x] = VM.editColor;
      else if (VM.editTool === 'eraser') VM.editPixels[c.y * VM.editW + c.x] = 0;
      renderEditor();
      renderEdInfo();
    }

    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var c = cellFrom(e);
      VM.edSel = c.y * VM.editW + c.x;

      if (VM.editTool === 'eyedropper') {
        var col = VM.editPixels[c.y * VM.editW + c.x];
        if ((col >>> 24) !== 0) {
          VM.editColor = col;
          var ci = document.querySelector('.pcp-color-input');
          if (ci) ci.value = Codec.hexOf(col);
          toast(Codec.hexOf(col));
        }
        renderEdInfo();
        return;
      }

      pushUndo();

      if (VM.editTool === 'fill') {
        doFill(c.x, c.y, VM.editColor);
        renderEditor();
        renderEdInfo();
        return;
      }

      edPainting = true;
      edLastX = -1; edLastY = -1;
      paint(e);
      cv.setPointerCapture(e.pointerId);
    });

    cv.addEventListener('pointermove', function (e) {
      if (!edPainting) return;
      e.preventDefault();
      paint(e);
    });

    function stop(e) {
      edPainting = false;
      edLastX = -1; edLastY = -1;
      try { cv.releasePointerCapture(e.pointerId); } catch (x) {}
    }
    cv.addEventListener('pointerup', stop);
    cv.addEventListener('pointercancel', stop);
  }

  function renderEditor() {
    var cv = $('editorCanvas');
    if (!cv || !VM.editPixels) return;
    var W = VM.editW, H = VM.editH;

    // 计算显示倍率（整数倍放大，保持像素锐利）
    var wrapEl = document.querySelector('.pcp-editor-wrap');
    var wrapH = wrapEl ? wrapEl.clientHeight - 24 : Math.min(window.innerHeight * 0.5, 460);
    var maxH = Math.max(160, wrapH);
    var maxW = window.innerWidth - 60;
    var s = Math.min(maxW / W, maxH / H);
    var mult = s >= 1 ? Math.max(1, Math.floor(s)) : s;
    var cw = Math.max(1, Math.round(W * mult));
    var ch = Math.max(1, Math.round(H * mult));

    cv.width = cw;
    cv.height = ch;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;

    // [修复] 先铺一层格子底纹，透明像素也能看见（否则浅色主题下整块白，完全看不见）
    var cell = Math.max(4, Math.round(mult));
    for (var cy = 0; cy < ch; cy += cell) {
      for (var cx = 0; cx < cw; cx += cell) {
        var odd = ((cx / cell) | 0) + ((cy / cell) | 0);
        ctx.fillStyle = odd % 2 ? 'rgba(140,145,160,0.16)' : 'rgba(140,145,160,0.07)';
        ctx.fillRect(cx, cy, cell, cell);
      }
    }

    // 再按像素块填色（跳过全透明，保留底纹）
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var c = VM.editPixels[y * W + x];
        if (((c >>> 24) & 0xFF) === 0) continue;
        ctx.fillStyle = '#' + ((c >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
        ctx.fillRect(Math.floor(x * mult), Math.floor(y * mult),
          Math.ceil(mult), Math.ceil(mult));
      }
    }

    // 网格线（格子够大时才画，否则一片糊）
    if (mult >= 6) {
      ctx.strokeStyle = 'rgba(128,132,150,0.28)';
      ctx.lineWidth = 1;
      for (var i = 1; i < W; i++) {
        var px = Math.round(i * mult) + 0.5;
        ctx.beginPath(); ctx.moveTo(px, 0); ctx.lineTo(px, ch); ctx.stroke();
      }
      for (var j = 1; j < H; j++) {
        var py = Math.round(j * mult) + 0.5;
        ctx.beginPath(); ctx.moveTo(0, py); ctx.lineTo(cw, py); ctx.stroke();
      }
    }

    cv.style.width = cw + 'px';
    cv.style.height = ch + 'px';

    var meta = $('edMeta');
    if (meta) meta.textContent = W + '×' + H;
    renderEditorPalette();
  }

  function pushUndo() {
    if (!VM.editPixels) return;
    VM.undo.push(VM.editPixels.slice());
    if (VM.undo.length > 60) VM.undo.shift();
    VM.redo.length = 0;
  }

  function doUndo() {
    if (!VM.undo.length) return;
    VM.redo.push(VM.editPixels.slice());
    VM.editPixels = VM.undo.pop();
    renderEditor();
  }

  function doRedo() {
    if (!VM.redo.length) return;
    VM.undo.push(VM.editPixels.slice());
    VM.editPixels = VM.redo.pop();
    renderEditor();
  }

  function doClear() {
    pushUndo();
    VM.editPixels = new Int32Array(VM.editW * VM.editH).fill(0);
    renderEditor();
  }

  function doFill(x, y, color) {
    var target = VM.editPixels[y * VM.editW + x];
    if (target === color) return;
    var stack = [[x, y]];
    var seen = new Uint8Array(VM.editW * VM.editH);
    while (stack.length) {
      var p = stack.pop();
      var cx = p[0], cy = p[1];
      if (cx < 0 || cx >= VM.editW || cy < 0 || cy >= VM.editH) continue;
      var idx = cy * VM.editW + cx;
      if (seen[idx]) continue;
      if (VM.editPixels[idx] !== target) continue;
      seen[idx] = 1;
      VM.editPixels[idx] = color;
      stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
    }
  }

  // ==================== 导出 ====================
  function saveAlbum() {
    if (!VM.pixelArt) { toast(S('save_need_generate')); return; }
    // [对齐 App] MainViewModel.savePixelArtToAlbum：放大到约 1024，上限 64 倍
    var scale = Math.max(1, Math.min(64, Math.floor(1024 / Math.max(VM.artW, VM.artH))));
    var small = toCanvas(VM.pixelArt, VM.artW, VM.artH);
    var cv = document.createElement('canvas');
    cv.width = VM.artW * scale;
    cv.height = VM.artH * scale;
    var ctx = cv.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.drawImage(small, 0, 0, cv.width, cv.height);

    cv.toBlob(function (b) {
      if (!b) { toast(S('save_failed')); return; }
      var url = URL.createObjectURL(b);
      var a = document.createElement('a');
      a.href = url;
      a.download = 'PixelColorPicker_' + VM.artW + 'x' + VM.artH + '_' + Date.now() + '.png';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 3000);
      toast(S('saved_png'));
    }, 'image/png');
  }


  // ==================== 裁剪页（对应 CropScreen.kt） ====================
  /**
   * 图片可拖动/缩放，裁剪框可拖动、4 角可缩放（锁定画布比例）。
   * 确认后从原图提取裁剪区域的像素，替换 sourceBitmap。
   */
  function openCrop() {
    if (!VM.originalBitmap) return;
    var oldSub = document.getElementById('subCrop');
    if (oldSub) oldSub.remove();

    var prev = { px: VM.sourceBitmap, w: VM.sourceW, h: VM.sourceH };

    var sub = document.createElement('div');
    sub.className = 'pcp-subpage';
    sub.id = 'subCrop';
    sub.innerHTML =
      '<div class="pcp-editor-head">' +
      '<button class="pcp-back-btn" id="cropCancel">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>' +
      '</button>' +
      '<h2 class="pcp-editor-title">' + S('crop_title') + '</h2>' +
      '<span class="pcp-editor-meta">' + VM.canvasWidth + '×' + VM.canvasHeight + '</span>' +
      '</div>' +
      '<div class="pcp-crop-area" id="cropArea"><canvas id="cropCanvas"></canvas></div>' +
      '<p class="pcp-crop-hint">' + S('crop_hint') + '</p>' +
      '<div class="pcp-crop-bar">' +
      '<button class="pcp-crop-btn" id="cropReset">' + S('reset') + '</button>' +
      '<button class="pcp-crop-btn" id="cropZoomOut">−</button>' +
      '<button class="pcp-crop-btn" id="cropZoomIn">+</button>' +
      '<button class="pcp-crop-btn primary" id="cropOk">' + S('crop_confirm') + '</button>' +
      '</div>';
    $('app').appendChild(sub);

    var area = $('cropArea');
    var cv = $('cropCanvas');
    var ctx = cv.getContext('2d');
    var srcCanvas = toCanvas(VM.originalBitmap, VM.originalW, VM.originalH);
    var aspect = VM.canvasWidth / VM.canvasHeight;

    var areaW = 0, areaH = 0;
    var imgX = 0, imgY = 0, imgScale = 1;
    var cropX = 0, cropY = 0, cropW = 0, cropH = 0;

    function imgW() { return VM.originalW * imgScale; }
    function imgH() { return VM.originalH * imgScale; }

    function resetAll() {
      imgScale = Math.min(areaW / VM.originalW, areaH / VM.originalH) * 0.92;
      imgX = (areaW - imgW()) / 2;
      imgY = (areaH - imgH()) / 2;
      var maxW = Math.min(imgW(), imgH() * aspect) * 0.94;
      cropW = maxW;
      cropH = cropW / aspect;
      cropX = (areaW - cropW) / 2;
      cropY = (areaH - cropH) / 2;
    }

    function clampCrop() {
      var iw = imgW(), ih = imgH();
      var maxW = Math.min(iw, ih * aspect);
      cropW = Math.min(cropW, maxW);
      cropH = cropW / aspect;
      cropX = clamp(cropX, imgX, imgX + iw - cropW);
      cropY = clamp(cropY, imgY, imgY + ih - cropH);
    }

    function draw() {
      ctx.clearRect(0, 0, areaW, areaH);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(srcCanvas, imgX, imgY, imgW(), imgH());

      // 遮罩：只暗裁剪框外
      ctx.fillStyle = 'rgba(0,0,0,0.60)';
      ctx.fillRect(0, 0, areaW, cropY);
      ctx.fillRect(0, cropY + cropH, areaW, areaH - cropY - cropH);
      ctx.fillRect(0, cropY, cropX, cropH);
      ctx.fillRect(cropX + cropW, cropY, areaW - cropX - cropW, cropH);

      // 边框
      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 2;
      ctx.strokeRect(cropX + 1, cropY + 1, cropW - 2, cropH - 2);

      // 三分线
      ctx.strokeStyle = 'rgba(255,255,255,0.32)';
      ctx.lineWidth = 1;
      for (var i = 1; i < 3; i++) {
        var gx = cropX + cropW * i / 3, gy = cropY + cropH * i / 3;
        ctx.beginPath(); ctx.moveTo(gx, cropY); ctx.lineTo(gx, cropY + cropH); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(cropX, gy); ctx.lineTo(cropX + cropW, gy); ctx.stroke();
      }

      // 4 个角点
      ctx.fillStyle = '#FFFFFF';
      var R = 6;
      [[cropX, cropY], [cropX + cropW, cropY], [cropX, cropY + cropH], [cropX + cropW, cropY + cropH]]
        .forEach(function (p) {
          ctx.beginPath();
          ctx.arc(p[0], p[1], R, 0, Math.PI * 2);
          ctx.fill();
        });
    }

    // ---------- 手势 ----------
    var mode = null, sx = 0, sy = 0;
    var s0 = {};
    var HOT = 26; // 角点热区半径

    function hitTest(x, y) {
      if (Math.hypot(x - cropX, y - cropY) < HOT) return 'tl';
      if (Math.hypot(x - (cropX + cropW), y - cropY) < HOT) return 'tr';
      if (Math.hypot(x - cropX, y - (cropY + cropH)) < HOT) return 'bl';
      if (Math.hypot(x - (cropX + cropW), y - (cropY + cropH)) < HOT) return 'br';
      if (x > cropX && x < cropX + cropW && y > cropY && y < cropY + cropH) return 'move';
      return 'img';
    }

    function localPos(e) {
      var r = cv.getBoundingClientRect();
      return { x: e.clientX - r.left, y: e.clientY - r.top };
    }

    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var p = localPos(e);
      mode = hitTest(p.x, p.y);
      sx = p.x; sy = p.y;
      s0 = { cx: cropX, cy: cropY, cw: cropW, ch: cropH, ix: imgX, iy: imgY };
      try { cv.setPointerCapture(e.pointerId); } catch (x) {}
    });

    cv.addEventListener('pointermove', function (e) {
      if (!mode) return;
      e.preventDefault();
      var p = localPos(e);
      var dx = p.x - sx, dy = p.y - sy;

      if (mode === 'img') {
        imgX = s0.ix + dx;
        imgY = s0.iy + dy;
        clampCrop();
      } else if (mode === 'move') {
        cropX = s0.cx + dx;
        cropY = s0.cy + dy;
        clampCrop();
      } else {
        // 角缩放：以对角为锚点，锁定比例
        var minW = Math.min(60, Math.min(imgW(), imgH() * aspect) * 0.2);
        var maxW = Math.min(imgW(), imgH() * aspect);
        var nw = s0.cw;
        if (mode === 'tl' || mode === 'bl') nw = s0.cw - dx;
        else nw = s0.cw + dx;
        nw = clamp(nw, minW, maxW);
        var nh = nw / aspect;

        if (mode === 'tl') { cropX = s0.cx + s0.cw - nw; cropY = s0.cy + s0.ch - nh; }
        else if (mode === 'tr') { cropX = s0.cx; cropY = s0.cy + s0.ch - nh; }
        else if (mode === 'bl') { cropX = s0.cx + s0.cw - nw; cropY = s0.cy; }
        else { cropX = s0.cx; cropY = s0.cy; }
        cropW = nw; cropH = nh;
        clampCrop();
      }
      draw();
    });

    function stopDrag(e) {
      mode = null;
      try { cv.releasePointerCapture(e.pointerId); } catch (x) {}
    }
    cv.addEventListener('pointerup', stopDrag);
    cv.addEventListener('pointercancel', stopDrag);

    // 双指缩放图片
    var pinchDist = 0, pinchScale = 1;
    cv.addEventListener('touchstart', function (e) {
      if (e.touches.length === 2) {
        var dx = e.touches[0].clientX - e.touches[1].clientX;
        var dy = e.touches[0].clientY - e.touches[1].clientY;
        pinchDist = Math.sqrt(dx * dx + dy * dy);
        pinchScale = imgScale;
        mode = null;
      }
    }, { passive: true });
    cv.addEventListener('touchmove', function (e) {
      if (e.touches.length !== 2 || !pinchDist) return;
      e.preventDefault();
      var dx = e.touches[0].clientX - e.touches[1].clientX;
      var dy = e.touches[0].clientY - e.touches[1].clientY;
      var d = Math.sqrt(dx * dx + dy * dy);
      zoomImage(pinchScale * (d / pinchDist) / imgScale);
    }, { passive: false });
    cv.addEventListener('touchend', function () { pinchDist = 0; });

    cv.addEventListener('wheel', function (e) {
      e.preventDefault();
      zoomImage(e.deltaY > 0 ? 0.94 : 1.06);
    }, { passive: false });

    function zoomImage(factor) {
      var cx = cropX + cropW / 2, cy = cropY + cropH / 2;
      var ns = imgScale * factor;
      var minS = Math.min(areaW / VM.originalW, areaH / VM.originalH) * 0.4;
      var maxS = Math.min(areaW / VM.originalW, areaH / VM.originalH) * 8;
      ns = clamp(ns, minS, maxS);
      // 以裁剪框中心为锚点缩放图片
      imgX = cx - (cx - imgX) * (ns / imgScale);
      imgY = cy - (cy - imgY) * (ns / imgScale);
      imgScale = ns;
      clampCrop();
      draw();
    }

    $('cropReset').onclick = function () { resetAll(); draw(); };
    $('cropZoomIn').onclick = function () { zoomImage(1.2); };
    $('cropZoomOut').onclick = function () { zoomImage(1 / 1.2); };

    $('cropCancel').onclick = function () {
      VM.sourceBitmap = prev.px;
      VM.sourceW = prev.w;
      VM.sourceH = prev.h;
      sub.remove();
      drawPreview();
    };

    $('cropOk').onclick = function () {
      var srcX = Math.round((cropX - imgX) / imgScale);
      var srcY = Math.round((cropY - imgY) / imgScale);
      var srcCw = Math.round(cropW / imgScale);
      var srcCh = Math.round(cropH / imgScale);
      srcX = clamp(srcX, 0, Math.max(0, VM.originalW - 1));
      srcY = clamp(srcY, 0, Math.max(0, VM.originalH - 1));
      srcCw = clamp(srcCw, 1, VM.originalW - srcX);
      srcCh = clamp(srcCh, 1, VM.originalH - srcY);

      var out = new Int32Array(srcCw * srcCh);
      for (var y = 0; y < srcCh; y++) {
        for (var x = 0; x < srcCw; x++) {
          out[y * srcCw + x] = VM.originalBitmap[(srcY + y) * VM.originalW + (srcX + x)];
        }
      }
      VM.sourceBitmap = out;
      VM.sourceW = srcCw;
      VM.sourceH = srcCh;
      // [C 方案] 记录裁剪矩形在原图中的位置，供拖拽调整超出裁剪范围
      VM.cropRect = { x: srcX, y: srcY, w: srcCw, h: srcCh };
      VM.scale = 1; VM.offsetX = 0; VM.offsetY = 0; VM.rotation = 0;

      sub.remove();
      VM.isPixelated = false;
      VM.pixelArt = null;
      VM.artW = 0; VM.artH = 0;
      $('saveAlbumBtn').disabled = true;
      renderPalette();
      renderCodes();
      drawPreview();
      if (VM.autoGenerate) generate();
    };

    function layout() {
      areaW = area.clientWidth || window.innerWidth;
      areaH = area.clientHeight || 300;
      cv.width = Math.round(areaW);
      cv.height = Math.round(areaH);
      resetAll();
      draw();
    }
    requestAnimationFrame(layout);
  }

  // ==================== 标签切换 ====================
  function switchTab(name) {
    VM.tab = name;
    var pages = document.querySelectorAll('.pcp-page');
    for (var i = 0; i < pages.length; i++) {
      pages[i].classList.toggle('is-active', pages[i].id === 'page-' + name);
    }
    var navs = document.querySelectorAll('.pcp-nav-item');
    for (var j = 0; j < navs.length; j++) {
      navs[j].classList.toggle('is-active', navs[j].getAttribute('data-tab') === name);
    }
    if (name === 'about') renderAbout();
    if (name === 'palette') renderPalette();
    if (name === 'code') renderCodes();
  }

  // ==================== 初始化 ====================
  function boot() {
    initQuickBar();
    initPaletteView();
    // 语言
    try {
      var l = localStorage.getItem('pcp.lang');
      if (l && STR[l]) lang = l;
    } catch (e) {}
    // 主题
    try {
      var th = localStorage.getItem('pcp.theme');
      if (th) VM.themeMode = th;
      var ag = localStorage.getItem('pcp.autoGen');
      if (ag === '1') VM.autoGenerate = true;
      var gc = localStorage.getItem('pcp.genCount');
      if (gc) VM.genCount = parseInt(gc, 10) || 0;
      // [对齐 App] 恢复自定义常用尺寸与使用历史
      VM.customPreset0 = localStorage.getItem('pcp.preset_0') || '';
      VM.customPreset1 = localStorage.getItem('pcp.preset_1') || '';
      var hist = localStorage.getItem('pcp.size_history') || '';
      if (hist) {
        hist.split(';').forEach(function (kv) {
          var pair = kv.split(':');
          if (pair.length === 2 && pair[0]) {
            VM.sizeHistory[pair[0]] = parseInt(pair[1], 10) || 0;
          }
        });
      }
    } catch (e) {}

    applyTheme();
    applyI18n();

    // Logo
    drawLogo($('topLogo'), 80);

    // 尺寸
    updateSizeUI();
    renderPresets();

    // 算法
    renderAlgos();
    renderKernels();

    // 欢迎动画
    playWelcome();

    // 事件：标签
    var navs = document.querySelectorAll('.pcp-nav-item');
    for (var i = 0; i < navs.length; i++) {
      navs[i].addEventListener('click', function () {
        switchTab(this.getAttribute('data-tab'));
      });
    }

    // 尺寸点击
    $('sizeW').onclick = function () { showSizeDialog(true); };
    $('sizeH').onclick = function () { showSizeDialog(false); };
    $('swapBtn').onclick = function () {
      var t = VM.canvasWidth;
      VM.canvasWidth = VM.canvasHeight;
      VM.canvasHeight = t;
      updateSizeUI();
      renderPresets();
      drawPreview();
    };

    // 拖拽调整胶囊
    $('dragPill').onclick = function () {
      VM.dragAdjustEnabled = !VM.dragAdjustEnabled;
      updateActionsUI();
      this.classList.toggle('is-on', VM.dragAdjustEnabled);
    };

    // 重置胶囊（二次确认，对应 FineTuneCard 的 AlertDialog）
    $('resetPill').onclick = function () {
      openDialog(
        '<h3 class="pcp-dialog-title">' + S('fine_tune_reset_title') + '</h3>' +
        '<div class="pcp-dialog-body">' + S('fine_tune_reset_message') + '</div>' +
        '<div class="pcp-dialog-actions">' +
        '<button class="pcp-dialog-btn" id="rsCancel">' + S('action_cancel') + '</button>' +
        '<button class="pcp-dialog-btn primary" id="rsOk" style="background:' + 'var(--pcp-DarkError)' + '">' + S('reset') + '</button>' +
        '</div>'
      );
      $('rsCancel').onclick = closeDialog;
      $('rsOk').onclick = function () {
        VM.scale = 1; VM.offsetX = 0; VM.offsetY = 0; VM.rotation = 0;
        drawPreview();
        drawCustomPreview();
        closeDialog();
      };
    };

    // 图片选择（拖拽调整模式下点击不触发选图）
    $('displayBox').onclick = function () {
      if (VM.dragAdjustEnabled) return;
      if (!VM.sourceBitmap) $('fileInput').click();
    };
    $('changeImgBtn').onclick = function () { $('fileInput').click(); };
    $('fileInput').onchange = function () {
      if (this.files && this.files[0]) loadImageFile(this.files[0]);
      this.value = '';
    };

    // 裁剪 = 重新选图（网页端简化）
    // [对齐 App] 「重新裁剪」回到裁剪页（不是重新选图）
    $('recropBtn').onclick = function () {
      if (VM.originalBitmap) openCrop();
      else $('fileInput').click();
    };

    // 网格
    $('gridBtn').onclick = function () {
      VM.gridEnabled = !VM.gridEnabled;
      updateActionsUI();
      drawPreview();
    };

    // 折叠
    $('collapseBtn').onclick = function () {
      VM.homeActionsExpanded = false;
      updateActionsUI();
    };
    $('expandBtn').onclick = function () {
      VM.homeActionsExpanded = true;
      updateActionsUI();
    };

    // 生成 / 画布编辑
    $('generateBtn').onclick = generate;
    $('saveAlbumBtn').onclick = saveAlbum;
    // [对齐 App] 该卡片文案是「画布编辑」，应加载已生成的像素画（不是空白）
    $('newCanvasBtn').onclick = function () { openEditor(false); };
    var palSb = $('palSearchBtn');
    if (palSb) palSb.onclick = function () { showPaletteSearch(); };
    var palRf = $('palRefreshBtn');
    if (palRf) palRf.onclick = function () {
      renderPalette();
      toast(S('action_done'));
    };
    var codeRf = $('codeRefreshBtn');
    if (codeRf) codeRf.onclick = function () {
      renderCodes();
      if (VM._syncQuickBar) VM._syncQuickBar();
      toast(S('action_done'));
    };

    // 设置
    $('settingsBtn').onclick = openSettings;

    // 微调
    initFineTune();

    // 自定义算法滑块
    $('samplesRange').oninput = function () {
      VM.customSamples = parseInt(this.value, 10);
      $('samplesOut').textContent = this.value + '×';
      renderAlgos();
      drawCustomPreview();
    };

    // 色板事件
    initPaletteEvents();

    // 弹窗遮罩点击关闭
    $('dialogMask').onclick = function (e) {
      if (e.target === this) closeDialog();
    };

    // 拖放
    document.addEventListener('dragover', function (e) { e.preventDefault(); });
    document.addEventListener('drop', function (e) {
      e.preventDefault();
      if (e.dataTransfer && e.dataTransfer.files[0]) loadImageFile(e.dataTransfer.files[0]);
    });

    // 粘贴
    document.addEventListener('paste', function (e) {
      var it = e.clipboardData && e.clipboardData.items;
      if (!it) return;
      for (var i = 0; i < it.length; i++) {
        if (it[i].type.indexOf('image') === 0) {
          var f = it[i].getAsFile();
          if (f) { loadImageFile(f); break; }
        }
      }
    });

    // 窗口尺寸变化
    var rt = null;
    window.addEventListener('resize', function () {
      clearTimeout(rt);
      rt = setTimeout(function () {
        if (VM.pixelArt) renderPalette();
        if (VM.editPixels) renderEditor();
      }, 200);
    });

    // 暴露调试
    window.PCP_VM = VM;
    window.PCP_S = S;
    window.PCP_switchTab = switchTab;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();