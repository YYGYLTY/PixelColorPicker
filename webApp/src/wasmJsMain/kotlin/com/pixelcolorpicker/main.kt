package com.pixelcolorpicker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.CanvasBasedWindow
import com.pixelcolorpicker.ui.icons.AppIcons

@Composable
fun App() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 验证 AppIcons 能否直接复用（零 Android 依赖）
            androidx.compose.material3.Icon(
                imageVector = AppIcons.Home,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("AppIcons 编译成功")
        }
    }
}

fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
}
