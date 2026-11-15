package com.pixelcolorpicker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pixelcolorpicker.res.str
import com.pixelcolorpicker.R

/** 语言选择对话框（中文 / English / 日本語）。 */
@Composable
fun LanguageDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val options = listOf(
        "zh" to str("lang_zh"),
        "en" to str("lang_en"),
        "ja" to str("lang_ja"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("language")) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    TextButton(
                        onClick = { onPick(code) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(str("action_cancel"))
            }
        },
    )
}
