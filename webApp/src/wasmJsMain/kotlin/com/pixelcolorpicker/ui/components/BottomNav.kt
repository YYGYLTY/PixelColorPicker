package com.pixelcolorpicker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.pixelcolorpicker.res.str
import androidx.compose.ui.unit.dp
import com.pixelcolorpicker.ui.Tab
import com.pixelcolorpicker.ui.icons.AppIcons
import com.pixelcolorpicker.ui.theme.glowBorder

/**
 * 底部悬浮导航栏（毛玻璃风格）。
 *
 * 半透明渐变底 + 发光细边框 + 阴影，选中项带缩放与颜色动画。
 */
@Composable
fun BottomNav(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavItem(Tab.Home, "tab_home") { tint, size ->
            Icon(AppIcons.Home, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        },
        NavItem(Tab.Palette, "tab_palette") { tint, size ->
            Icon(AppIcons.Palette, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        },
        NavItem(Tab.Code, "tab_code") { tint, size ->
            // 旧版「编码」图标（复刻）；20dp 视觉对齐其他 24dp 图标
            // wasm 版：没有 drawable 资源，用 AppIcons.Code（同一套自绘图标）
            Icon(AppIcons.Code, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        },
        NavItem(Tab.About, "tab_about") { tint, size ->
            Icon(AppIcons.About, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        },
    )

    val shape = RoundedCornerShape(28.dp)
    val surface = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .shadow(18.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = 0.86f),
                        surface.copy(alpha = 0.94f),
                    )
                )
            )
            .glowBorder(28.dp, MaterialTheme.colorScheme.primary, pulse = 0.5f)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavBarItem(
                item = item,
                selected = item.tab == current,
                onClick = { onSelect(item.tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class NavItem(
    val tab: Tab,
    val labelRes: String,
    val icon: @Composable (tint: Color, size: androidx.compose.ui.unit.Dp) -> Unit,
)

@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "navTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(220),
        label = "navScale",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            item.icon(tint, 24.dp)
        }
        Text(
            text = str(item.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}