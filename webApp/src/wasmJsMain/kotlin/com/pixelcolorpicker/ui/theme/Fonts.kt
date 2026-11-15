package com.pixelcolorpicker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.pixelcolorpicker.generated.resources.Res
import com.pixelcolorpicker.generated.resources.NotoSansSC_subset
import org.jetbrains.compose.resources.Font as ResFont

/**
 * 中文字体（wasm 关键补丁）
 *
 * Compose Web 的 Skia 只带拉丁字形，没有 CJK。
 * 不注入字体，所有中文都会渲染成方块。
 *
 * 子集文件位于 commonMain/composeResources/font/NotoSansSC-subset.otf，
 * 由 Compose Resources 插件生成 Res.font.NotoSansSC_subset 访问器。
 */
val CjkFontFamily: FontFamily
    @Composable get() = FontFamily(
        ResFont(
            resource = Res.font.NotoSansSC_subset,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
        )
    )
