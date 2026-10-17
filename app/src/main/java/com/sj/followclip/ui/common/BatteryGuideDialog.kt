package com.sj.followclip.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

private const val PREF = "first_run"
private const val KEY_BATTERY_SHOWN = "battery_guide_shown"

/** 是否还需要弹一次耗电管理引导（只弹一次）。 */
fun shouldShowBatteryGuide(context: Context): Boolean =
    !context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_BATTERY_SHOWN, false)

private fun markBatteryGuideShown(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_BATTERY_SHOWN, true).apply()
}

/**
 * 首次启动的耗电管理引导。
 *
 * 为什么必须提醒：应用靠后台定时任务检查更新，系统一旦限制后台，
 * 任务就不跑，通知自然也不会来 —— 用户会以为是软件坏了。
 *
 * 不强制：用户不想设就跳过，功能照常，只是通知可能不来。
 */
@Composable
fun BatteryGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让通知能正常送达") },
        text = {
            Column {
                Text(
                    text = "应用靠后台定时检查更新。系统如果限制后台，检查就不会跑，通知也发不出来。",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(Spacing.gridGap))

                Text(
                    text = "推荐设置",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "1. 系统设置 → 电池 → 后台耗电管理\n" +
                        "2. 找到「SJ Lens」→ 允许后台运行\n" +
                        "3. 应用管理 → SJ Lens → 自启动 → 允许",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textMuted
                )

                Spacer(Modifier.height(Spacing.gridGap))

                Text(
                    text = "不搞的话不通知，看需求设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    markBatteryGuideShown(context)
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        )
                    }
                    onDismiss()
                }
            ) {
                Text("去设置")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    markBatteryGuideShown(context)
                    onDismiss()
                }
            ) {
                Text("以后再说")
            }
        }
    )
}
