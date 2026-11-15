package com.pixelcolorpicker.ui

/**
 * 顶层标签页（与 Android 版 MainViewModel.kt 中的定义完全一致）。
 *
 * 单独抽出来，方便在 MainViewModel 尚未移植时先跑 UI。
 */

/** 底部导航的 4 个标签页。 */
enum class Tab { Home, Palette, Code, About }

/** 全屏子页面。 */
enum class SubScreen { None, Crop, Editor, Settings, History }

/** 编辑器工具。 */
enum class EditorTool { Pencil, Eraser, Eyedropper, Fill }