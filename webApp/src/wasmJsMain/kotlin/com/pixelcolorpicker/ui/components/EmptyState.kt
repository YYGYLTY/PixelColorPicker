package com.pixelcolorpicker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.ui.theme.PcpColors

/**
 * 空状态：圆形图标（天依蓝淡底 + 细边框）+ 标题 + 说明，居中显示。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 圆形图标：天依蓝淡底 + 细边框 + 天依蓝图标
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(PcpColors.AccentPurple.copy(alpha = 0.10f))
                .border(1.dp, PcpColors.AccentPurple.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PcpColors.AccentPurple,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PcpColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = hint,
            fontSize = 13.sp,
            color = PcpColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp),
        )
    }
}