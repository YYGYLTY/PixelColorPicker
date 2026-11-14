/*
 * PixelColorPicker Web · 国际化文案
 */
(function (root) {
  'use strict';

  var DICT = {
    zh: {
      'app.title': 'PixelColorPicker',
      'app.subtitle': '把任意图片变成像素画 · 逐格取色 · 生成色板码',
      'app.badgeOffline': '纯本地 · 离线 · 不上传',

      'tab.generate': '生成',
      'tab.palette': '色板',
      'tab.code': '编码',
      'tab.editor': '画布',
      'tab.about': '关于',

      'size.title': '画布尺寸',
      'size.width': '宽',
      'size.height': '高',
      'size.presets': '常用尺寸',
      'size.hint': '范围 1 ~ 999，任意设置',

      'image.title': '选择图片',
      'image.drop': '点击选择图片，或把图片拖到这里',
      'image.dropSub': '支持 JPG / PNG / WEBP / GIF，也可以直接粘贴 Ctrl+V',
      'image.change': '换一张',
      'image.size': '源图',

      'crop.title': '裁剪与微调',
      'crop.hint': '拖动平移 · 滚轮或双指缩放',
      'crop.zoom': '缩放',
      'crop.rotate': '旋转',
      'crop.rotLeft': '逆时针 90°',
      'crop.rotRight': '顺时针 90°',
      'crop.reset': '重置',

      'algo.title': '重采样算法',
      'algo.nearest': '最近邻',
      'algo.nearest.desc': '最锐利、颗粒感强，不产生新颜色',
      'algo.bilinear': '双线性',
      'algo.bilinear.desc': '轻微柔和，通用均衡',
      'algo.bicubic': '双三次',
      'algo.bicubic.desc': '平滑自然，照片质感',
      'algo.lanczos': 'Lanczos',
      'algo.lanczos.desc': '细节保留最好',
      'algo.average': '区域平均',
      'algo.average.desc': '最柔和、噪点最少',
      'algo.custom': '自定义',
      'algo.custom.desc': '取色方式 × 平滑程度',
      'algo.kernel': '取色方式',
      'algo.samples': '平滑程度（采样密度）',
      'algo.samplesVal': '{n}×',
      'algo.preview': '实时预览',

      'btn.generate': '生成像素画',
      'btn.generating': '生成中…',
      'btn.export': '保存 PNG',
      'btn.copyAll': '复制全部',
      'btn.edit': '继续编辑',
      'btn.reset': '重置',

      'result.title': '像素画',
      'result.empty': '生成后会显示在这里',
      'result.meta': '{w} × {h} · {n} 个唯一颜色',
      'result.time': '耗时 {ms} ms',

      'palette.title': '色板',
      'palette.empty': '先生成一张像素画',
      'palette.copyAll': '复制全部颜色',
      'palette.lookup': '按坐标定位',
      'palette.lookupGo': '定位',
      'palette.count': '×{n}',
      'palette.hint': '点击任意格子复制 HEX',
      'palette.large': '画布较大，仅渲染可视区域',

      'code.title': '色板码',
      'code.hint': '按列生成 18 色板码，与 App 旧版逐字节兼容',
      'code.column': '第 {n} 列',
      'code.copy': '复制',
      'code.loadMore': '加载更多列',
      'code.empty': '先生成一张像素画',
      'code.jump': '跳转到列',

      'editor.title': '画布编辑',
      'editor.brush': '画笔',
      'editor.eraser': '橡皮',
      'editor.eyedropper': '吸管',
      'editor.fill': '填充',
      'editor.undo': '撤销',
      'editor.redo': '重做',
      'editor.clear': '清空',
      'editor.color': '颜色',
      'editor.fromResult': '从当前像素画导入',
      'editor.history': '历史记录',
      'editor.historyEmpty': '还没有保存的作品',
      'editor.save': '保存到历史',
      'editor.delete': '删除',
      'editor.load': '载入',

      'about.title': '关于',
      'about.intro': 'PixelColorPicker 是一个本地、离线、无广告的图片像素化工具。本网页版是 Android 应用核心能力的浏览器移植，全部计算在你的设备上完成，不会上传任何图片。',
      'about.core': '核心特性',
      'about.feature1': '画布尺寸 1 ~ 999 任意设置',
      'about.feature2': '5 种重采样算法 + 自定义组合',
      'about.feature3': '按画布比例裁剪：拖拽、缩放、旋转',
      'about.feature4': '逐格取色、同色计数、一键复制',
      'about.feature5': '18 色板码，与 App 逐字节兼容',
      'about.feature6': '空白画布编辑器与历史记录',
      'about.compat': '兼容性说明',
      'about.compatBody': '色板码协议与 Android 版完全一致，同一份像素画在 App 与网页生成的码可以直接互换使用。像素化的裁剪框语义做了细微改进：网页版按「等比铺满、不拉伸」处理，避免非等比源图变形。',
      'about.links': '相关链接',
      'about.repo': 'GitHub 仓库',
      'about.releases': '下载 APK',
      'about.license': 'MIT License',

      'toast.copied': '已复制',
      'toast.copyFail': '复制失败，请手动选中',
      'toast.noImage': '请先选择图片',
      'toast.generated': '已生成 {w}×{h}',
      'toast.saved': '已保存到历史',
      'toast.cleared': '已清空',
      'toast.exported': 'PNG 已开始下载',
      'toast.loadedImage': '已载入图片 {name}',
      'toast.imageTooBig': '图片过大，已自动缩小到长边 {n}px',
      'toast.imported': '已导入当前像素画'
    },

    en: {
      'app.title': 'PixelColorPicker',
      'app.subtitle': 'Turn any image into pixel art · per-cell colors · palette codes',
      'app.badgeOffline': 'Local · Offline · No upload',

      'tab.generate': 'Create',
      'tab.palette': 'Palette',
      'tab.code': 'Codes',
      'tab.editor': 'Canvas',
      'tab.about': 'About',

      'size.title': 'Canvas size',
      'size.width': 'W',
      'size.height': 'H',
      'size.presets': 'Presets',
      'size.hint': 'Any value from 1 to 999',

      'image.title': 'Choose an image',
      'image.drop': 'Click to pick an image, or drop one here',
      'image.dropSub': 'JPG / PNG / WEBP / GIF — you can also paste with Ctrl+V',
      'image.change': 'Replace',
      'image.size': 'Source',

      'crop.title': 'Crop & fine-tune',
      'crop.hint': 'Drag to pan · scroll or pinch to zoom',
      'crop.zoom': 'Zoom',
      'crop.rotate': 'Rotate',
      'crop.rotLeft': 'Rotate 90° CCW',
      'crop.rotRight': 'Rotate 90° CW',
      'crop.reset': 'Reset',

      'algo.title': 'Resampling algorithm',
      'algo.nearest': 'Nearest',
      'algo.nearest.desc': 'Sharpest, chunky, introduces no new colors',
      'algo.bilinear': 'Bilinear',
      'algo.bilinear.desc': 'Slightly soft, balanced',
      'algo.bicubic': 'Bicubic',
      'algo.bicubic.desc': 'Smooth and natural, photographic',
      'algo.lanczos': 'Lanczos',
      'algo.lanczos.desc': 'Best detail retention',
      'algo.average': 'Area average',
      'algo.average.desc': 'Softest, least noise',
      'algo.custom': 'Custom',
      'algo.custom.desc': 'Kernel × smoothing level',
      'algo.kernel': 'Sampling kernel',
      'algo.samples': 'Smoothing (sample density)',
      'algo.samplesVal': '{n}×',
      'algo.preview': 'Live preview',

      'btn.generate': 'Generate pixel art',
      'btn.generating': 'Generating…',
      'btn.export': 'Save PNG',
      'btn.copyAll': 'Copy all',
      'btn.edit': 'Edit',
      'btn.reset': 'Reset',

      'result.title': 'Pixel art',
      'result.empty': 'Your result will appear here',
      'result.meta': '{w} × {h} · {n} unique colors',
      'result.time': '{ms} ms',

      'palette.title': 'Palette',
      'palette.empty': 'Generate a pixel art first',
      'palette.copyAll': 'Copy all colors',
      'palette.lookup': 'Go to coordinate',
      'palette.lookupGo': 'Go',
      'palette.count': '×{n}',
      'palette.hint': 'Click any cell to copy its HEX',
      'palette.large': 'Large canvas — only visible cells are rendered',

      'code.title': 'Palette codes',
      'code.hint': 'One 18-color code per column, byte-compatible with the Android app',
      'code.column': 'Column {n}',
      'code.copy': 'Copy',
      'code.loadMore': 'Load more columns',
      'code.empty': 'Generate a pixel art first',
      'code.jump': 'Jump to column',

      'editor.title': 'Canvas editor',
      'editor.brush': 'Brush',
      'editor.eraser': 'Eraser',
      'editor.eyedropper': 'Eyedropper',
      'editor.fill': 'Fill',
      'editor.undo': 'Undo',
      'editor.redo': 'Redo',
      'editor.clear': 'Clear',
      'editor.color': 'Color',
      'editor.fromResult': 'Import current pixel art',
      'editor.history': 'History',
      'editor.historyEmpty': 'No saved works yet',
      'editor.save': 'Save to history',
      'editor.delete': 'Delete',
      'editor.load': 'Load',

      'about.title': 'About',
      'about.intro': 'PixelColorPicker is a local, offline, ad-free pixelation tool. This web version ports the Android app core to your browser — everything is computed on your device and no image is ever uploaded.',
      'about.core': 'Highlights',
      'about.feature1': 'Canvas size from 1 to 999',
      'about.feature2': '5 resampling algorithms plus a custom combination',
      'about.feature3': 'Crop to canvas ratio: drag, zoom, rotate',
      'about.feature4': 'Per-cell colors, same-color counts, one-click copy',
      'about.feature5': '18-color palette codes, byte-compatible with the app',
      'about.feature6': 'Blank canvas editor with local history',
      'about.compat': 'Compatibility',
      'about.compatBody': 'The palette-code protocol matches the Android app exactly, so codes generated in either place are interchangeable. The crop frame uses a slightly improved cover semantic (scale to fill without stretching) to avoid distorting non-uniform sources.',
      'about.links': 'Links',
      'about.repo': 'GitHub repository',
      'about.releases': 'Download APK',
      'about.license': 'MIT License',

      'toast.copied': 'Copied',
      'toast.copyFail': 'Copy failed — please select manually',
      'toast.noImage': 'Please choose an image first',
      'toast.generated': 'Generated {w}×{h}',
      'toast.saved': 'Saved to history',
      'toast.cleared': 'Cleared',
      'toast.exported': 'PNG download started',
      'toast.loadedImage': 'Loaded {name}',
      'toast.imageTooBig': 'Image too large — scaled down to {n}px',
      'toast.imported': 'Imported current pixel art'
    },

    ja: {
      'app.title': 'PixelColorPicker',
      'app.subtitle': '画像をドット絵に · マスごとに色を取得 · パレットコード生成',
      'app.badgeOffline': 'ローカル · オフライン · 送信なし',

      'tab.generate': '作成',
      'tab.palette': 'パレット',
      'tab.code': 'コード',
      'tab.editor': 'キャンバス',
      'tab.about': '情報',

      'size.title': 'キャンバスサイズ',
      'size.width': '幅',
      'size.height': '高さ',
      'size.presets': 'よく使うサイズ',
      'size.hint': '1 ～ 999 の任意の値',

      'image.title': '画像を選択',
      'image.drop': 'クリックして画像を選択、またはここにドロップ',
      'image.dropSub': 'JPG / PNG / WEBP / GIF · Ctrl+V で貼り付けも可',
      'image.change': '変更',
      'image.size': '元画像',

      'crop.title': '切り抜きと微調整',
      'crop.hint': 'ドラッグで移動 · ホイール / ピンチで拡大縮小',
      'crop.zoom': '拡大率',
      'crop.rotate': '回転',
      'crop.rotLeft': '反時計回りに 90°',
      'crop.rotRight': '時計回りに 90°',
      'crop.reset': 'リセット',

      'algo.title': 'リサンプリングアルゴリズム',
      'algo.nearest': '最近傍',
      'algo.nearest.desc': '最もシャープ、新しい色を作らない',
      'algo.bilinear': 'バイリニア',
      'algo.bilinear.desc': 'やや柔らかくバランス型',
      'algo.bicubic': 'バイキュービック',
      'algo.bicubic.desc': '滑らかで写真向き',
      'algo.lanczos': 'Lanczos',
      'algo.lanczos.desc': 'ディテール保持が最良',
      'algo.average': '領域平均',
      'algo.average.desc': '最も柔らかくノイズが少ない',
      'algo.custom': 'カスタム',
      'algo.custom.desc': 'カーネル × 滑らかさ',
      'algo.kernel': '色の取得方法',
      'algo.samples': '滑らかさ（サンプル密度）',
      'algo.samplesVal': '{n}×',
      'algo.preview': 'リアルタイムプレビュー',

      'btn.generate': 'ドット絵を生成',
      'btn.generating': '生成中…',
      'btn.export': 'PNG を保存',
      'btn.copyAll': 'すべてコピー',
      'btn.edit': '編集する',
      'btn.reset': 'リセット',

      'result.title': 'ドット絵',
      'result.empty': '生成するとここに表示されます',
      'result.meta': '{w} × {h} · {n} 色',
      'result.time': '{ms} ms',

      'palette.title': 'パレット',
      'palette.empty': '先にドット絵を生成してください',
      'palette.copyAll': 'すべての色をコピー',
      'palette.lookup': '座標で移動',
      'palette.lookupGo': '移動',
      'palette.count': '×{n}',
      'palette.hint': 'マスをクリックすると HEX をコピー',
      'palette.large': '大きいキャンバスのため可視範囲のみ描画します',

      'code.title': 'パレットコード',
      'code.hint': '列ごとに 18 色コードを生成。Android 版とバイト単位で互換',
      'code.column': '{n} 列目',
      'code.copy': 'コピー',
      'code.loadMore': 'さらに列を読み込む',
      'code.empty': '先にドット絵を生成してください',
      'code.jump': '列へ移動',

      'editor.title': 'キャンバス編集',
      'editor.brush': 'ペン',
      'editor.eraser': '消しゴム',
      'editor.eyedropper': 'スポイト',
      'editor.fill': '塗りつぶし',
      'editor.undo': '元に戻す',
      'editor.redo': 'やり直す',
      'editor.clear': 'クリア',
      'editor.color': '色',
      'editor.fromResult': '現在のドット絵を読み込む',
      'editor.history': '履歴',
      'editor.historyEmpty': '保存された作品はまだありません',
      'editor.save': '履歴に保存',
      'editor.delete': '削除',
      'editor.load': '読み込む',

      'about.title': '情報',
      'about.intro': 'PixelColorPicker はローカル・オフライン・広告なしのドット絵ツールです。この Web 版は Android アプリの中核機能をブラウザに移植したもので、すべての処理は端末上で行われ、画像が送信されることはありません。',
      'about.core': '主な機能',
      'about.feature1': 'キャンバスサイズ 1 ～ 999',
      'about.feature2': '5 種類のアルゴリズム + カスタム',
      'about.feature3': '比率に合わせた切り抜き（移動・拡大・回転）',
      'about.feature4': 'マスごとの色取得・同色カウント・ワンクリックコピー',
      'about.feature5': '18 色パレットコード（アプリとバイト互換）',
      'about.feature6': '空白キャンバスエディタと履歴',
      'about.compat': '互換性について',
      'about.compatBody': 'パレットコードのプロトコルは Android 版と完全に同一のため、どちらで生成したコードも相互に利用できます。切り抜き枠は「縦横比を保って埋める」方式に微調整しており、非等比の元画像が歪みません。',
      'about.links': 'リンク',
      'about.repo': 'GitHub リポジトリ',
      'about.releases': 'APK をダウンロード',
      'about.license': 'MIT License',

      'toast.copied': 'コピーしました',
      'toast.copyFail': 'コピーに失敗しました。手動で選択してください',
      'toast.noImage': '先に画像を選択してください',
      'toast.generated': '{w}×{h} を生成しました',
      'toast.saved': '履歴に保存しました',
      'toast.cleared': 'クリアしました',
      'toast.exported': 'PNG のダウンロードを開始しました',
      'toast.loadedImage': '{name} を読み込みました',
      'toast.imageTooBig': '画像が大きいため長辺 {n}px に縮小しました',
      'toast.imported': '現在のドット絵を読み込みました'
    }
  };

  var current = 'zh';

  function t(key, vars) {
    var d = DICT[current] || DICT.zh;
    var s = d[key];
    if (s == null) s = (DICT.zh[key] != null ? DICT.zh[key] : key);
    if (vars) {
      s = s.replace(/\{(\w+)\}/g, function (m, name) {
        return vars[name] != null ? String(vars[name]) : m;
      });
    }
    return s;
  }

  function setLang(lang) {
    if (!DICT[lang]) lang = 'zh';
    current = lang;
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : lang;
  }

  function getLang() { return current; }
  function langs() { return Object.keys(DICT); }

  root.PCPI18n = { t: t, setLang: setLang, getLang: getLang, langs: langs, DICT: DICT };
})(typeof self !== 'undefined' ? self : this);