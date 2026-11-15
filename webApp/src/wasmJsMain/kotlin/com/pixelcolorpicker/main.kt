package com.pixelcolorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.pixelcolorpicker.res.Strings
import com.pixelcolorpicker.res.str
import com.pixelcolorpicker.ui.Tab
import com.pixelcolorpicker.ui.components.BottomNav
import com.pixelcolorpicker.ui.components.WelcomeAnimation
import com.pixelcolorpicker.ui.icons.AppIcons
import com.pixelcolorpicker.ui.theme.PixelColorPickerTheme
import com.pixelcolorpicker.util.LocaleHelper

/**
 * 阶段 1 验证页：确认 A 批次移植的文件（主题 / 字符串 / 图标 / 导航 / 欢迎动画）能正常渲染。
 * 页面结构与 HomeScreen 一致，但只放已移植的组件。
 */
@Composable
fun App() {
    // 启动时应用已保存语言
    LaunchedEffect(Unit) {
        mark("App 已组合，开始 applySaved")
        LocaleHelper.applySaved()
        mark("LocaleHelper 完成")
    }

    var tab by remember { mutableStateOf(Tab.Home) }

    PixelColorPickerTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))

                    // ---- 顶部 Logo + 渐变标题（复刻 TopHeader）----
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )
                            .padding(8.dp),
                    ) {
                        Icon(
                            AppIcons.Palette,
                            contentDescription = null,
                            modifier = Modifier.height(32.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "PixelColorPicker",
                        fontSize = 23.sp,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "图片像素化 · 色板码转换 · 画布编辑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(28.dp))

                    // ---- 欢迎动画（A 批次移植）----
                    WelcomeAnimation(played = false, startDelayMs = 0L, onPlayed = {})

                    Spacer(Modifier.height(28.dp))

                    // ---- 移植自检信息 ----
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfoRow("中文字体", "Noto Sans SC 子集 4494 字")
                        InfoRow("主题配色", "深色方案已加载")
                        InfoRow("字符串表", "${Strings.keys.size} 条 × 3 语言")
                        InfoRow("图标", "首页 / 色板 / 编码 / 关于")
                        InfoRow("底部导航", "见下方")
                        InfoRow("像素化算法", "已移植 5 种")
                        InfoRow("色板编码", "GZIP + Base64 已移植")
                    }
                }

                // ---- 底部导航（A 批次移植）----
                BottomNav(
                    current = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


/** 把执行进度写回页面的加载层，方便定位卡点（仅调试用）。 */
@JsFun("(m) => { if (window.__pcpMark) window.__pcpMark(m); }")
external fun mark(msg: String)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    mark("wasm main() 开始")
    // viewportContainerId 指向一个普通的 div（不是 canvas）。
    // ComposeViewport 会在该容器内部自行创建 canvas 与输入用 textarea；
    // 若把容器设成 <canvas>，内部追加的元素不可见，表现为整页黑屏。
    ComposeViewport(viewportContainerId = "ComposeTarget") { App() }
}