package com.pixelcolorpicker.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelcolorpicker.R
import com.pixelcolorpicker.ui.components.LanguageDialog
import com.pixelcolorpicker.ui.theme.PcpColors
import com.pixelcolorpicker.ui.theme.glowCardBorder
import com.pixelcolorpicker.util.LocaleHelper

/**
 * 设置页：
 *  - 语言（中文 / English / 日本語）
 *  - 选图裁剪后是否自动生成像素画
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val langCode = LocaleHelper.getSavedLanguage(context) ?: java.util.Locale.getDefault().language
    val langLabel = when (langCode) {
        "en" -> stringResource(R.string.lang_en)
        "ja" -> stringResource(R.string.lang_ja)
        else -> stringResource(R.string.lang_zh)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // 语言
            SettingsRow(
                title = stringResource(R.string.language),
                value = langLabel,
                onClick = { showLanguageDialog = true },
            )

            Spacer(Modifier.height(16.dp))

            // 主题
            val themeLabel = when (viewModel.themeMode) {
                MainViewModel.THEME_LIGHT -> stringResource(R.string.theme_light)
                MainViewModel.THEME_DARK -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
            }
            SettingsRow(
                title = stringResource(R.string.settings_theme),
                value = themeLabel,
                onClick = { showThemeDialog = true },
            )

            Spacer(Modifier.height(16.dp))

            // 自动生成像素画
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_generate),
                subtitle = stringResource(R.string.settings_auto_generate_desc),
                checked = viewModel.autoGenerate,
                onCheckedChange = { viewModel.updateAutoGenerate(it) },
            )
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            onDismiss = { showLanguageDialog = false },
            onPick = { code ->
                LocaleHelper.save(context, code)
                showLanguageDialog = false
                (context as? Activity)?.recreate()
            },
        )
    }

    if (showThemeDialog) {
        val options = listOf(
            MainViewModel.THEME_SYSTEM to stringResource(R.string.theme_system),
            MainViewModel.THEME_LIGHT to stringResource(R.string.theme_light),
            MainViewModel.THEME_DARK to stringResource(R.string.theme_dark),
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_dialog_title)) },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = viewModel.themeMode == mode,
                                onClick = {
                                    viewModel.updateThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 普通设置行：标题 + 当前值 + 箭头。 */
@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glowCardBorder(radius = 16.dp, backgroundColor = PcpColors.BgCardPrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = PcpColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = PcpColors.TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = PcpColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 开关设置行：标题 + 说明 + 开关。 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glowCardBorder(radius = 16.dp, backgroundColor = PcpColors.BgCardPrimary)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PcpColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = PcpColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PcpColors.NavSelected,
            ),
        )
    }
}
