package com.pixelcolorpicker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.ui.theme.PcpColors

/**
 * 关于页：现代化设计——顶部品牌区 + 发光信息卡片。
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier, genCount: Int = 0) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // ---------- 顶部品牌标识（标志 + 标题 + 版本胶囊） ----------
        Box(contentAlignment = Alignment.Center) {
            // 标志
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFA99BFF), Color(0xFF66CCFF)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PC",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1A1B22),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        // 标题（保持原色）
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = PcpColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.app_description),
            fontSize = 13.sp,
            color = PcpColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        // 版本胶囊
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PcpColors.BgCardPrimary)
                .border(1.dp, PcpColors.TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text = stringResource(R.string.about_version),
                fontSize = 11.sp,
                color = PcpColors.TextSecondary,
            )
        }

        // 本地统计：已生成次数（仅存本机）
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_stat_fmt, genCount),
            fontSize = 11.sp,
            color = PcpColors.AccentPurple.copy(alpha = 0.9f),
        )

        Spacer(Modifier.height(24.dp))

        // ---------- 信息卡片 ----------
        AboutSection(
            iconRes = R.drawable.ic_edit_canvas,
            accent = PcpColors.AccentPurple,
            title = stringResource(R.string.about_what_title),
            body = stringResource(R.string.about_what_body),
        )
        AboutSection(
            iconRes = R.drawable.ic_generate,
            accent = Color(0xFFFF8A65),
            title = stringResource(R.string.about_flow_title),
            body = stringResource(R.string.about_flow_body),
        )
        AboutSection(
            iconRes = R.drawable.ic_canvas_size,
            accent = PcpColors.AccentBlue,
            title = stringResource(R.string.about_codec_title),
            body = stringResource(R.string.about_codec_body),
        )
        AboutSection(
            iconRes = R.drawable.ic_presets,
            accent = PcpColors.AccentGreen,
            title = stringResource(R.string.about_privacy_title),
            body = stringResource(R.string.about_privacy_body),
        )

        Spacer(Modifier.height(6.dp))

        // ---------- 贡献者卡片 ----------
        ContributorsCard()

        // ---------- 支持作者（可展开） ----------
        SupportCard()

        Spacer(Modifier.height(110.dp))
    }
}

/** 贡献者卡片：4 个头像 + 名字 + 分工，底部 Human-AI Collaboration。 */
@Composable
private fun ContributorsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PcpColors.BgCardPrimary)
            .border(
                width = 1.dp,
                color = PcpColors.GlowStart.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        // 标题行：图标 + 文字（与上方信息卡片风格统一）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PcpColors.AccentSwap.copy(alpha = 0.16f))
                    .border(1.dp, PcpColors.AccentSwap.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_contributors),
                    contentDescription = null,
                    tint = PcpColors.AccentSwap,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.about_credits_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PcpColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ContributorItem(R.drawable.avatar_astnote, "Astnote", stringResource(R.string.credits_astnote_role))
            ContributorItem(R.drawable.avatar_deepseek, "DeepSeek", stringResource(R.string.credits_deepseek_role))
            ContributorItem(R.drawable.avatar_chatgpt, "ChatGPT", stringResource(R.string.credits_chatgpt_role))
            ContributorItem(R.drawable.avatar_doubao, "Doubao", stringResource(R.string.credits_doubao_role))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.credits_made_with),
            fontSize = 13.sp,
            color = PcpColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 支持作者卡片：点击展开微信赞赏码。 */
@Composable
private fun SupportCard() {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PcpColors.BgCardPrimary)
            .border(
                width = 1.dp,
                color = PcpColors.GlowStart.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF8A65).copy(alpha = 0.16f))
                    .border(1.dp, Color(0xFFFF8A65).copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_support),
                    contentDescription = null,
                    tint = Color(0xFFFF8A65),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.about_support_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PcpColors.TextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.about_support_body),
                    fontSize = 12.sp,
                    color = PcpColors.TextSecondary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = PcpColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            // 赞赏码展示区（重新设计：渐变底 + 白底圆角 + 说明文字）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x1FFF8A65), Color(0x1A66CCFF)),
                        ),
                    )
                    .border(1.dp, Color(0x33FF8A65), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(8.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.wechat_reward),
                            contentDescription = stringResource(R.string.about_support_title),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.about_support_body),
                        fontSize = 11.5.sp,
                        color = PcpColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorItem(avatarRes: Int, name: String, role: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(78.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.5.dp, PcpColors.GlowStart.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(avatarRes),
                contentDescription = name,
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PcpColors.TextPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = role,
            fontSize = 10.sp,
            color = PcpColors.TextSecondary,
            maxLines = 2,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutSection(
    iconRes: Int,
    accent: Color,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PcpColors.BgCardPrimary)
            .border(
                width = 1.dp,
                color = PcpColors.GlowStart.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 图标：圆底
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PcpColors.TextPrimary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = body,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = PcpColors.TextSecondary,
            )
        }
    }
}