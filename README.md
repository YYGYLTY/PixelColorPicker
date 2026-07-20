<p align="center">
  <img src="assets/PixelColorPicker-banner.png" width="700">
</p>

<h1 align="center">
PixelColorPicker
</h1>

<p align="center">
Android 像素化 / 色板码转换工具
</p>

<p align="center">

<img src="https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android">

<img src="https://img.shields.io/badge/Language-Java-orange?style=flat-square&logo=java">

<img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square">

<img src="https://img.shields.io/badge/Version-1.0.0-purple?style=flat-square">

</p>


---

# ✨ 简介

PixelColorPicker 是一款专为 **16×16 像素图制作流程优化** 的 Android 图片处理工具。

本项目最初用于解决《像素射击》手游头像制作过程中，
普通取色工具操作繁琐、颜色整理困难的问题。

它可以帮助用户：

- 将图片转换为 16×16 像素图
- 自动分析 256 格像素颜色
- 生成对应色板数据
- 批量复制游戏所需色板码


适用于：

- 🎮 游戏像素头像制作
- 🎨 像素艺术创作
- 🌈 图片颜色分析


---

# 🚀 功能特性


## 🖼️ 图片选择

- 从相册选择需要处理的图片
- 支持常见图片格式


## ✂️ 自定义裁剪

- 自由调整裁剪区域
- 精确选择需要转换的画面范围


## 🎨 16×16 像素转换

- 将图片转换为 16×16 像素规格
- 自动分析每个像素颜色
- 保留原图主要视觉特征


## 🌈 256色色板生成

- 自动提取像素颜色信息
- 生成完整颜色排列
- 快速查看每个像素对应颜色


## 📋 色板码导出

- 自动生成颜色数据
- 支持批量复制纵列16格色板码
- 无需手动记录大量颜色信息


---

# 🎬 Demo Video

（视频制作完成后放置）


---

# 📱 应用截图


<p align="center">

<img src="assets/screenshot_main.jpg" width="260">

<img src="assets/screenshot_crop.jpg" width="260">

<img src="assets/screenshot_palette.jpg" width="260">

</p>


---

# ⚡ 为什么需要 PixelColorPicker？

传统制作16×16像素头像：

```
图片
 ↓
手动取色
 ↓
记录颜色代码
 ↓
整理色板
 ↓
输入游戏
```


使用 PixelColorPicker：

```
图片
 ↓
自动分析
 ↓
生成256色数据
 ↓
复制色板码
 ↓
完成
```


大幅减少重复操作，
提高像素头像制作效率。


---

# 🛠️ 技术实现

- Java
- Android SDK
- Bitmap 图像处理
- 像素采样算法
- Protobuf 数据编码
- GZIP 压缩


---

# 👨‍💻 Credits

- Design & Development: Sylvaine
- Core Algorithm Assistance: ChatGPT
- Crop Component Assistance: DeepSeek
- Icon Design: 豆包


---

# 📄 License

This project is licensed under the MIT License.


---

<p align="center">
Made with ❤️ for Android & Pixel Art
</p>
