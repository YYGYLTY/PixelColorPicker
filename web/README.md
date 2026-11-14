# PixelColorPicker Web

把 [PixelColorPicker](https://github.com/YYGYLTY/PixelColorPicker)（Android 应用）的核心能力移植到浏览器，纯静态站点，可直接部署到 GitHub Pages。

> **所有计算都在你的设备上完成**，图片不会上传到任何服务器。

## 功能

- **图片 → 像素画**：画布尺寸 1 ~ 999 任意设置
- **5 种重采样算法**：最近邻 / 双线性 / 双三次 / Lanczos / 区域平均
- **自定义算法**：取色方式（4 种核函数）× 平滑程度（1× ~ 8×），实时预览
- **裁剪与微调**：拖拽平移、滚轮 / 双指缩放、旋转、重置
- **色板**：逐格查看 HEX、坐标、点击复制、按坐标定位
- **色板码**：按列生成 18 色板码，与 Android 版**逐字节兼容**
- **画布编辑器**：画笔 / 橡皮 / 吸管 / 填充、撤销 / 重做、历史记录
- **多语言**：中文 / English / 日本語
- **深色 / 浅色主题**：跟随系统，可手动切换

## 本地预览

纯静态页面，无需构建。任选一种方式：

```bash
# Python
python3 -m http.server 8080

# Node.js
npx serve .
```

然后打开 <http://localhost:8080>。

> 直接双击 `index.html` 也能用，但 `file://` 协议下部分浏览器会限制剪贴板 API，建议用本地服务器。

## 部署到 GitHub Pages

### 方式一：Actions 自动部署（推荐）

1. 把本目录（`web/`）及 `.github/workflows/pages.yml` 一起提交到仓库根目录
2. 仓库 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**
3. 推送到 `main` 分支即自动部署

站点地址：`https://<用户名>.github.io/<仓库名>/`

### 方式二：分支部署（最简单）

1. 把本目录内容推到 `gh-pages` 分支
2. **Settings → Pages → Source** 选择 **Deploy from a branch** → `gh-pages` / `root`
3. 保存即可

### 方式三：docs/ 目录

把本目录重命名为 `docs/` 放在仓库根目录，Pages 源选 `main` 分支的 `/docs` 目录。

## 目录结构

```
web/
├── index.html          # 单页应用入口
├── css/
│   └── style.css       # 全部样式（含明暗主题、响应式）
├── js/
│   ├── codec.js        # 18 色板码编解码（对应 PaletteCodec.kt）
│   ├── pixelator.js    # 像素化引擎（对应 Pixelator.kt）
│   ├── i18n.js         # 三语文案
│   └── app.js          # 主逻辑与交互
└── assets/
    └── icon.svg
```

## 与 Android 版的差异

### 色板码协议：完全一致 ✅

`js/codec.js` 严格按 `PaletteCodec.kt` 逐字节移植：

```
列取 16 色 + 2 个白色
  → Protobuf Color 消息（r/g/b 为 0 时省略字段，alpha 恒为 255）
  → Palette 外层（0A 02 "18" + 每色 0x12 + len + msg）
  → GZIP（mtime=0、OS=0，与 java.util.zip.GZIPOutputStream 一致）
  → 标准 Base64（保留 padding，无换行）
```

已用 Python 独立实现做交叉验证，两者输出**完全相同**。同一份像素画在 App 与网页生成的码可以互换使用。

### 裁剪框语义：细微改进

Android 版 `scale = 1` 表示「整张源图铺满展示框」，源图与画布比例不同时会**拉伸变形**。

网页版改为 **cover 语义**：等比铺满、居中裁切、不拉伸，再在此基础上叠加缩放 / 平移 / 旋转。这样非等比源图不会变形，更符合直觉。

### 未移植的部分

| App 功能 | 网页版状态 | 说明 |
|---|---|---|
| 保存到相册 | 改为浏览器下载 PNG | 整数倍最近邻放大，保持像素锐利 |
| 检查更新 | 不适用 | 网页永远是最新版 |
| 本地使用统计 | 不适用 | — |
| EXIF 方向读取 | 浏览器自动处理 | 现代浏览器解码时已按 EXIF 摆正 |

## 浏览器要求

- ES5 语法（无箭头函数 / 无 `const`），兼容性极广
- `CompressionStream` 可用时生成压缩后的短码；不可用时自动退回无压缩 deflate，**结果依然合法可解**
- 需要 Canvas 2D 与 `ImageData`（所有现代浏览器均支持）

## 许可

MIT License，与原项目一致。

## 致谢

上游项目由 Astnote 与 AI 协作完成。本网页移植版保留原有算法特性与协议兼容性。
