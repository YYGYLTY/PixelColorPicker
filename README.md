<div align="center">

<img src="assets/PixelColorPicker-banner.png" width="780" alt="PixelColorPicker banner">

# PixelColorPicker

**把任意图片变成像素画 · 逐格取色 · 生成色板码**

[![Release](https://img.shields.io/github/v/release/YYGYLTY/PixelColorPicker?color=66CCFF&label=release&logo=github)](https://github.com/YYGYLTY/PixelColorPicker/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#-下载安装)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-A97BFF?logo=kotlin&logoColor=white)](#-从源码构建)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](#-从源码构建)
[![License](https://img.shields.io/badge/License-MIT-blue)](#-许可)

[**⬇️ 下载最新版 APK**](https://github.com/YYGYLTY/PixelColorPicker/releases/latest) · [更新日志](CHANGELOG.md) · [开发文档](DEVELOPMENT.md)

</div>

---

## 📖 这是什么

**PixelColorPicker** 是一个 **本地、离线、无广告** 的图片像素化工具：

> 选一张图 → 裁成画布比例 → 生成像素画 → 逐格查看颜色 / 复制色板码

本仓库是 **v2.0 Compose 重制版**（Kotlin + Jetpack Compose + Material 3）。
旧版 Java 源码保留在 [`v1.0.1`](https://github.com/YYGYLTY/PixelColorPicker/tree/v1.0.1) 标签中，随时可查阅。

## ✨ 功能一览

### 🖼 图片 → 像素画
- 画布尺寸 **1 ~ 999** 任意设置，内置常用预设
- 按画布比例**裁剪**：拖拽移动、双指缩放、四角 / 四边调整
- **微调控制**：放大 / 缩小 / 四向平移 / 顺逆时针旋转 / 重置 / **拖拽调整**（在展示框内直接拖动、双指捏合）
- **5 种重采样算法 + 自定义算法**（内核 × 平滑程度，带实时效果预览）

| 算法 | 特点 |
|---|---|
| **最近邻** | 最锐利、颗粒感强（默认） |
| **双线性** | 轻微柔和，通用均衡 |
| **双三次** | 平滑自然，照片质感 |
| **Lanczos** | 细节保留最好 |
| **区域平均** | 最柔和、噪点最少 |

### 🎨 查看与使用
- **色板页**：双向自由滚动网格，显示 HEX / 坐标 / 同色计数，点击复制
- **编码页**：按列复制「18 色板码」，与旧版 `PaletteExporter` **逐字节兼容**
- 保存 W×H PNG 到相册（整数倍最近邻放大，保持像素锐利）

### ✏️ 空白画布编辑器
- 任意尺寸新建；画笔 / 橡皮 / 吸管 / 填充 / 撤销 / 重做 / 清空
- 作品保存到**历史记录**，随时载入继续编辑

### 🌏 体验
- 中文 / English / 日本語
- 深色 / 浅色主题（跟随系统，可手动切换）
- **检查更新** + 本地使用统计（仅存本机，不上传）
- 无网络权限、无广告、无多余权限（图片选择使用系统照片选择器）

## 📱 下载安装

1. 前往 [**Releases**](https://github.com/YYGYLTY/PixelColorPicker/releases/latest) 下载最新 APK
2. 安装时按提示允许「安装未知来源应用」

- 支持 **Android 7.0（API 24）** 及以上
- ⚠️ 当前使用 debug 签名（与测试版保持覆盖安装兼容）；如需上架应用商店请换成正式签名

## 🛠 从源码构建

```bash
# 环境：JDK 17+、Android SDK（compileSdk 37）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

<details>
<summary><b>技术栈与实现要点</b></summary>

- Kotlin 2.3 / AGP 9.0 / Jetpack Compose（BOM 2026.01.01）+ Material 3，单 Activity
- 自研像素化引擎：自适应超采样 + 核函数插值（最近邻 / 双线性 / 双三次 / Lanczos / 区域平均）
- 大画布优化：预览结果复用、采样密度自适应、同色计数后台计算
- 色板网格虚拟化绘制（滚动占位 + 可视区 Canvas），999×999 依然流畅
- 无第三方网络库；更新检查仅请求一个静态 JSON

</details>

## 📂 项目结构

```
com.pixelcolorpicker
├── MainActivity.kt          # 导航（Tab + 全屏子页）、语言、图片选择
├── codec/PaletteCodec.kt    # 18 色板码协议（纯 Kotlin，含单元测试）
├── image/
│   ├── BitmapLoader.kt      # 加载 / EXIF / 采样
│   └── Pixelator.kt         # 像素化引擎（5 算法 + 自定义 + 变换采样）
├── model/PixelArt.kt        # 可编辑画布（撤销 / 重做）+ PixelGrid
├── ui/                      # 首页 / 裁剪 / 编辑器 / 色板 / 编码 / 关于 / 设置
│   └── theme/               # 配色、主题、发光边框修饰符
└── util/                    # PNG 导出 / 颜色工具 / 语言 / 更新检查
```

## 📄 更新日志

见 [**CHANGELOG.md**](CHANGELOG.md)

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=YYGYLTY/PixelColorPicker&type=Date)](https://star-history.com/#YYGYLTY/PixelColorPicker&Date)

## 🙏 致谢

| 贡献者 | 分工 |
|---|---|
| **Astnote** | 统筹 · 测试 |
| **DeepSeek** | 全栈开发 |
| **ChatGPT** | 操控逻辑 |
| **Doubao** | 图标绘制 |

由 **Astnote** 与 AI 协作完成的产品重制 ❤️

## 📃 许可

本项目基于 **MIT License** 开源。

---

<div align="center">

如果这个项目帮到了你，欢迎点个 **Star** ⭐

</div>