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
    // 短标签（宽/高）
    var kW = document.querySelector('#sizeW .k');
    var kH = document.querySelector('#sizeH .k');
    if (kW) kW.textContent = lang === 'zh' ? '宽' : (lang === 'ja' ? '幅' : 'W');
    if (kH) kH.textContent = lang === 'zh' ? '高' : (lang === 'ja' ? '高' : 'H');

    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : lang;
  }

  // ==================== 状态（对应 MainViewModel） ====================
  var VM = {
    canvasWidth: 16,
    canvasHeight: 16,
    algorithm: 'nearest',
    customKernel: 'bilinear',
    customSamples: 4,
    gridEnabled: false,
    dragAdjustEnabled: false,
    themeMode: 'system',
    autoGenerate: false,

    // 源图
    sourceBitmap: null,
    sourceW: 0,
    sourceH: 0,
    sourceName: '',

    // 裁剪
    scale: 1, offsetX: 0, offsetY: 0, rotation: 0,

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
  function drawLogo(cv, size) {
    var ctx = cv.getContext('2d');
    var colors = ['#5EEAD4', '#38BDF8', '#818CF8', '#2A3040',
      '#38BDF8', '#818CF8', '#2A3040', '#2A3040',
      '#818CF8', '#2A3040', '#F472B6', '#FBBF24',
      '#2A3040', '#2A3040', '#FBBF24', '#F472B6'];
    var g = size / 4;
    ctx.clearRect(0, 0, size, size);
    for (var i = 0; i < 16; i++) {
      ctx.fillStyle = colors[i];
      ctx.fillRect((i % 4) * g, Math.floor(i / 4) * g, g + 0.5, g + 0.5);
    }
  }

  // ==================== 欢迎动画（复刻 WelcomeAnimation.kt） ====================
  function playWelcome() {
    var welcomeText = S('welcome_prefix');
    var appName = 'PixelColorPicker';
    var wl = $('welcomeLine');
    var al = $('appLine');
    var tap = $('tapHint');

    wl.innerHTML = '';
    al.innerHTML = '';
    tap.classList.remove('show');

    welcomeText.split('').forEach(function (ch) {
      var s = document.createElement('span');
      s.className = 'ch';
      s.textContent = ch;
      wl.appendChild(s);
    });
    appName.split('').forEach(function (ch) {
      var s = document.createElement('span');
      s.className = 'ch';
      s.textContent = ch;
      al.appendChild(s);
    });

    var wchs = wl.querySelectorAll('.ch');
    var achs = al.querySelectorAll('.ch');

    if (VM.welcomePlayed) {
      wchs.forEach(function (c) { c.classList.add('show'); });
      achs.forEach(function (c) { c.classList.add('show'); });
      tap.classList.add('show');
      return;
    }

    var i = 0;
    function stepW() {
      if (i >= wchs.length) {
        setTimeout(function () { stepA(0); }, 280);
        return;
      }
      wchs[i].classList.add('show');
      i++;
      setTimeout(stepW, 80);
    }

    function stepA(k) {
      if (k >= achs.length) {
        setTimeout(function () {
          tap.classList.add('show');
          VM.welcomePlayed = true;
        }, 200);
        return;
      }
      achs[k].classList.add('show');
      setTimeout(function () { stepA(k + 1); }, 30);
    }

    setTimeout(stepW, 120);
  }

  // ==================== 预设尺寸 ====================
  var PRESETS = [
    [16, 16], [32, 32], [48, 48], [64, 64],
    [16, 32], [32, 64], [64, 128], [128, 128],
    [8, 8], [24, 24], [96, 96], [256, 256]
  ];

  function presetSize(idx) {
    // 对应 viewModel.presetSize
    return PRESETS[idx] || [16, 16];
  }

  function renderPresets() {
    var box = $('presetList');
    box.innerHTML = '';
    // 只显示前 2 个（对应源码 preset1 / preset2）
    for (var i = 0; i < 2; i++) {
      var p = presetSize(i);
      var b = document.createElement('button');
      b.className = 'pcp-preset-btn';
      b.textContent = p[0] + '×' + p[1];
      if (p[0] === VM.canvasWidth && p[1] === VM.canvasHeight) {
        b.classList.add('is-active');
      }
      (function (w, h) {
        b.addEventListener('click', function () { setCanvasSize(w, h); });
      })(p[0], p[1]);
      box.appendChild(b);
    }
  }

  // ==================== 画布尺寸 ====================
  function setCanvasSize(w, h) {
    w = clamp(w | 0, 1, 999);
    h = clamp(h | 0, 1, 999);
    // 正方形才能旋转（对应 rotate_square_only）
    VM.canvasWidth = w;
    VM.canvasHeight = h;
    updateSizeUI();
    renderPresets();
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
      if (VM.autoGenerate) generate();
    };
    img.onerror = function () { URL.revokeObjectURL(url); toast('图片加载失败'); };
    img.src = url;
  }

  function drawPreview() {
    var cv = $('previewCanvas');
    if (!VM.sourceBitmap) { cv.hidden = true; return; }
    cv.hidden = false;

    // 用当前变换做实时预览（1024 上限保证性能）
    var pw = Math.min(VM.canvasWidth, 512);
    var ph = Math.min(VM.canvasHeight, 512);
    var out;
    try {
      out = Pix.pixelate(VM.sourceBitmap, VM.sourceW, VM.sourceH, pw, ph, {
        algorithm: VM.algorithm,
        customKernel: VM.customKernel,
        customSamples: VM.customSamples,
        scale: VM.scale, offsetX: VM.offsetX, offsetY: VM.offsetY,
        rotationDeg: VM.rotation
      });
    } catch (e) { return; }

    cv.width = pw; cv.height = ph;
    cv.getContext('2d').putImageData(toImageData(out, pw, ph), 0, 0);
    cv.style.width = '100%';
    cv.style.height = 'auto';
    cv.style.aspectRatio = pw + ' / ' + ph;
  }

  // ==================== 生成 ====================
  function generate() {
    if (!VM.sourceBitmap) { toast(S('pick_first')); return; }
    var t0 = performance.now();
    var out;
    try {
      out = Pix.pixelate(VM.sourceBitmap, VM.sourceW, VM.sourceH,
        VM.canvasWidth, VM.canvasHeight, {
        algorithm: VM.algorithm,
        customKernel: VM.customKernel,
        customSamples: VM.customSamples,
        scale: VM.scale, offsetX: VM.offsetX, offsetY: VM.offsetY,
        rotationDeg: VM.rotation
      });
    } catch (e) { toast('生成失败：' + e.message); return; }

    VM.pixelArt = out;
    VM.artW = VM.canvasWidth;
    VM.artH = VM.canvasHeight;
    VM.genMs = Math.round(performance.now() - t0);
    VM.genCount++;
    try { localStorage.setItem('pcp.genCount', VM.genCount); } catch (e) {}

    $('saveAlbumBtn').disabled = false;
    renderPalette();
    renderCodes();
    toast(S('generated'));
  }

  // ==================== 色板 ====================
  function renderPalette() {
    var empty = $('paletteEmpty');
    var body = $('paletteBody');
    if (!VM.pixelArt) {
      empty.hidden = false; body.hidden = true;
      return;
    }
    empty.hidden = true; body.hidden = false;

    var W = VM.artW, H = VM.artH;
    // 格子尺寸统一为「999×999 时的尺寸」（对应源码注释）
    var maxEdgePx = Math.min(window.innerWidth - 80, 680);
    var cell = Math.max(2, Math.floor(maxEdgePx / 999));
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var cs = Math.max(1, Math.round(cell * dpr));

    var cv = $('paletteCanvas');
    cv.width = W * cs;
    cv.height = H * cs;
    // 实际显示尺寸（CSS 像素）
    var dispW = W * cell;
    var dispH = H * cell;
    cv.style.width = dispW + 'px';
    cv.style.height = dispH + 'px';

    var ctx = cv.getContext('2d');
    var img = ctx.createImageData(W * cs, H * cs);
    var d = img.data;
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var c = VM.pixelArt[y * W + x];
        var r = (c >>> 16) & 0xFF, g = (c >>> 8) & 0xFF, b = c & 0xFF, a = (c >>> 24) & 0xFF;
        for (var dy = 0; dy < cs; dy++) {
          var base = ((y * cs + dy) * (W * cs)) * 4;
          for (var dx = 0; dx < cs; dx++) {
            var idx = base + (x * cs + dx) * 4;
            d[idx] = r; d[idx + 1] = g; d[idx + 2] = b; d[idx + 3] = a;
          }
        }
      }
    }
    ctx.putImageData(img, 0, 0);

    // 网格线
    if (cell >= 5 && W <= 200 && H <= 200) {
      ctx.strokeStyle = 'rgba(128,128,128,0.18)';
      ctx.lineWidth = 1;
      for (var i = 1; i < W; i++) {
        ctx.beginPath(); ctx.moveTo(i * cs + 0.5, 0); ctx.lineTo(i * cs + 0.5, H * cs); ctx.stroke();
      }
      for (var j = 1; j < H; j++) {
        ctx.beginPath(); ctx.moveTo(0, j * cs + 0.5); ctx.lineTo(W * cs, j * cs + 0.5); ctx.stroke();
      }
    }

    renderRulers(W, H, cell);
  }

  function renderRulers(W, H, cell) {
    // X 标尺
    var rx = $('rulerX');
    rx.innerHTML = '';
    var stepX = W <= 20 ? 1 : Math.ceil(W / 12);
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
    var stepY = H <= 20 ? 1 : Math.ceil(H / 16);
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
    };
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
    var out = Pix.pixelate(VM.sourceBitmap, VM.sourceW, VM.sourceH,
      size, size, {
      algorithm: 'custom',
      customKernel: VM.customKernel,
      customSamples: VM.customSamples,
      scale: VM.scale, offsetX: VM.offsetX, offsetY: VM.offsetY,
      rotationDeg: VM.rotation
    });
    ctx.putImageData(toImageData(out, size, size), 0, 0);
  }

  // ==================== 微调 ====================
  function initFineTune() {
    var btns = document.querySelectorAll('.pcp-ft-btn');
    for (var i = 0; i < btns.length; i++) {
      btns[i].addEventListener('click', function () {
        var act = this.getAttribute('data-ft');
        if (!VM.sourceBitmap) { toast(S('pick_first')); return; }
        switch (act) {
          case 'zoomin': VM.scale = clamp(VM.scale * 1.25, 0.2, 8); break;
          case 'zoomout': VM.scale = clamp(VM.scale / 1.25, 0.2, 8); break;
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
          case 'drag':
            VM.dragAdjustEnabled = !VM.dragAdjustEnabled;
            this.classList.toggle('is-on', VM.dragAdjustEnabled);
            return;
          case 'reset':
            VM.scale = 1; VM.offsetX = 0; VM.offsetY = 0; VM.rotation = 0;
            break;
        }
        drawPreview();
        drawCustomPreview();
      });
    }

    // 拖拽调整
    var box = $('displayBox');
    var dragging = false, sx = 0, sy = 0, sox = 0, soy = 0;
    box.addEventListener('pointerdown', function (e) {
      if (!VM.dragAdjustEnabled || !VM.sourceBitmap) return;
      dragging = true;
      sx = e.clientX; sy = e.clientY;
      sox = VM.offsetX; soy = VM.offsetY;
      box.setPointerCapture(e.pointerId);
    });
    box.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      var rect = box.getBoundingClientRect();
      VM.offsetX = sox + (e.clientX - sx) / rect.width;
      VM.offsetY = soy + (e.clientY - sy) / rect.height;
      drawPreview();
    });
    function endDrag(e) {
      if (!dragging) return;
      dragging = false;
      try { box.releasePointerCapture(e.pointerId); } catch (err) {}
      drawCustomPreview();
    }
    box.addEventListener('pointerup', endDrag);
    box.addEventListener('pointercancel', endDrag);

    // 滚轮缩放
    box.addEventListener('wheel', function (e) {
      if (!VM.sourceBitmap) return;
      e.preventDefault();
      VM.scale = clamp(VM.scale * (e.deltaY > 0 ? 0.92 : 1.08), 0.2, 8);
      drawPreview();
    }, { passive: false });
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

  function showPresetDialog() {
    var html = '<h3 class="pcp-dialog-title">' + S('common_sizes') + '</h3>';
    html += '<div class="pcp-size-grid">';
    PRESETS.forEach(function (p) {
      var act = (p[0] === VM.canvasWidth && p[1] === VM.canvasHeight) ? ' is-active' : '';
      html += '<button class="' + act + '" data-w="' + p[0] + '" data-h="' + p[1] + '">' +
        p[0] + '×' + p[1] + '</button>';
    });
    html += '</div>';
    html += '<div class="pcp-dialog-actions">' +
      '<button class="pcp-dialog-btn" id="dlgClose">' + S('action_close') + '</button></div>';
    openDialog(html);
    $('dlgClose').onclick = closeDialog;
    var bts = $('dialogBox').querySelectorAll('.pcp-size-grid button');
    for (var i = 0; i < bts.length; i++) {
      bts[i].onclick = function () {
        setCanvasSize(+this.getAttribute('data-w'), +this.getAttribute('data-h'));
        closeDialog();
      };
    }
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
      '<div class="pcp-set-desc">' + S(THEME_LABEL[VM.themeMode]) + '</div>' +
      '</div>' +
      '<div class="pcp-set-value" id="themeVal">' + S(THEME_LABEL[VM.themeMode]) +
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
      this.previousElementSibling.querySelector('.pcp-set-desc').textContent =
        S(THEME_LABEL[VM.themeMode]);
      this.childNodes[0].nodeValue = S(THEME_LABEL[VM.themeMode]);
    };
  }

  // ==================== 关于页 ====================
  // 文案与结构完全对应 AboutScreen.kt + strings.xml 的 about_* 条目
  function renderAbout() {
    var box = $('aboutScroll');
    var html = '';

    html += '<div class="pcp-about-hero">';
    html += '<canvas class="pcp-about-logo" id="aboutLogo" width="152" height="152"></canvas>';
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
  function openEditor() {
    if (!VM.editPixels) {
      VM.editW = VM.canvasWidth;
      VM.editH = VM.canvasHeight;
      VM.editPixels = new Int32Array(VM.editW * VM.editH).fill(0);
    }

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
      '<div class="pcp-editor-bar" id="edBar"></div>';
    $('app').appendChild(sub);

    $('edBack').onclick = function () {
      sub.remove();
      VM.subScreen = null;
    };

    var bar = $('edBar');
    var tools = [
      { id: 'brush', icon: '<path d="M12 20h9M16.5 3.5a2.12 2.12 0 013 3L7 19l-4 1 1-4z"/>' },
      { id: 'eraser', icon: '<path d="M20 20H8l-6-6a2 2 0 010-3l8-8a2 2 0 013 0l5 5a2 2 0 010 3l-8 8"/>' },
      { id: 'eyedropper', icon: '<path d="M2 22l1-1 4-4M17 3a2.8 2.8 0 114 4L7 21l-4 1 1-4L17 3z"/>' },
      { id: 'fill', icon: '<path d="M19 11l-8-8-8.5 8.5a2 2 0 000 3L9 21a2 2 0 003 0z"/><path d="M5 2l5 5"/>' }
    ];
    tools.forEach(function (t) {
      var b = document.createElement('button');
      b.className = 'pcp-tool-btn' + (VM.editTool === t.id ? ' is-active' : '');
      b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">' + t.icon + '</svg>';
      b.onclick = function () {
        VM.editTool = t.id;
        bar.querySelectorAll('.pcp-tool-btn').forEach(function (x) { x.classList.remove('is-active'); });
        b.classList.add('is-active');
      };
      bar.appendChild(b);
    });

    // 颜色
    var colorInp = document.createElement('input');
    colorInp.type = 'color';
    colorInp.className = 'pcp-color-input';
    colorInp.value = '#ffffff';
    colorInp.oninput = function () { VM.editColor = Codec.parseHex(this.value); };
    bar.appendChild(colorInp);

    // 撤销/重做/清空
    var actions = [
      { icon: '<path d="M3 7v6h6"/><path d="M21 17a9 9 0 00-9-9 9 9 0 00-6 2.3L3 13"/>', fn: doUndo },
      { icon: '<path d="M21 7v6h-6"/><path d="M3 17a9 9 0 019-9 9 9 0 016 2.3L21 13"/>', fn: doRedo },
      { icon: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>', fn: doClear }
    ];
    actions.forEach(function (a) {
      var b = document.createElement('button');
      b.className = 'pcp-tool-btn';
      b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">' + a.icon + '</svg>';
      b.onclick = a.fn;
      bar.appendChild(b);
    });

    initEditorCanvas();
    renderEditor();
  }

  var edCtx = null, edPainting = false, edLastX = -1, edLastY = -1;

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
      if (VM.editTool === 'brush') VM.editPixels[c.y * VM.editW + c.x] = VM.editColor;
      else if (VM.editTool === 'eraser') VM.editPixels[c.y * VM.editW + c.x] = 0;
      renderEditor();
    }

    cv.addEventListener('pointerdown', function (e) {
      e.preventDefault();
      var c = cellFrom(e);

      if (VM.editTool === 'eyedropper') {
        var col = VM.editPixels[c.y * VM.editW + c.x];
        if ((col >>> 24) !== 0) {
          VM.editColor = col;
          var ci = document.querySelector('.pcp-color-input');
          if (ci) ci.value = Codec.hexOf(col);
          toast(Codec.hexOf(col));
        }
        return;
      }

      pushUndo();

      if (VM.editTool === 'fill') {
        doFill(c.x, c.y, VM.editColor);
        renderEditor();
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
    cv.width = W; cv.height = H;
    cv.getContext('2d').putImageData(toImageData(VM.editPixels, W, H), 0, 0);

    var maxH = Math.min(window.innerHeight * 0.5, 460);
    var maxW = window.innerWidth - 60;
    var s = Math.min(maxW / W, maxH / H);
    var mult = s >= 1 ? Math.max(1, Math.floor(s)) : s;
    cv.style.width = Math.round(W * mult) + 'px';
    cv.style.height = Math.round(H * mult) + 'px';

    var meta = $('edMeta');
    if (meta) meta.textContent = W + '×' + H;
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
    var scale = Math.max(1, Math.min(32, Math.floor(2048 / Math.max(VM.artW, VM.artH))));
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
    $('moreSizeBtn').onclick = showPresetDialog;

    // 图片选择
    $('displayBox').onclick = function () {
      if (!VM.sourceBitmap) $('fileInput').click();
    };
    $('changeImgBtn').onclick = function () { $('fileInput').click(); };
    $('fileInput').onchange = function () {
      if (this.files && this.files[0]) loadImageFile(this.files[0]);
      this.value = '';
    };

    // 裁剪 = 重新选图（网页端简化）
    $('recropBtn').onclick = function () { $('fileInput').click(); };

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
    $('newCanvasBtn').onclick = openEditor;

    // 设置 / 语言
    $('settingsBtn').onclick = openSettings;
    $('langBtn').onclick = showLangDialog;

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