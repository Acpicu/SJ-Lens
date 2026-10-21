package com.sj.followclip.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.source.bili.BiliSource
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.CacheCleaner
import com.sj.followclip.util.WebCookies

/**
 * 设置页。
 *
 * 这里每一行都是真能点的，不做占位：
 * 登录状态实时读取，清除动作立刻生效，清完后状态马上跟着变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLoginBili: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 每次状态变化后靠这个计数触发重新读取
    var tick by remember { mutableStateOf(0) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmClearPending by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf("") }
    var confirmExport by remember { mutableStateOf(false) }

    val cacheSize = remember(tick) { CacheCleaner.sizeBytes(context) }
    val versionText = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName} ($code)"
        }.getOrDefault("2.8.0")
    }
    val biliLogged = remember(tick) { BiliSource.isLoggedIn() }
    val douyinLogged = remember(tick) { WebCookies.douyinLoggedIn() }
    val pendingCount = PendingStore.items.size

    // 导出：系统文档创建器，用户自己选存哪
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
            "application/json"
        )
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(AppUiState.exportJson(context).toByteArray())
                }
            }
        }
    }

    // 导入：系统文档选择器
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) {
                val count = AppUiState.importJson(context, text)
                importResult = if (count >= 0) "已导入 $count 个文件夹" else "文件格式不对"
                tick++
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("设置") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap)
        ) {
            item {
                SectionTitle("账号与登录")

                SettingCard {
                    LoginRow(
                        name = "B站",
                        loggedIn = biliLogged,
                        hint = "登录后才能稳定抓取，避免被限流",
                        onLogin = onLoginBili,
                        onClear = {
                            BiliSource.logout()
                            tick++
                        }
                    )

                }
            }

            item {
                SectionTitle("数据")

                SettingCard {
                    ActionRow(
                        title = "导出数据",
                        summary = "目标、平台账号与数据本记录存成一个 JSON，换机或交接时用",
                        action = "导出",
                        onAction = { confirmExport = true }
                    )
                    Spacer(Modifier.height(Spacing.gridGap))
                    ActionRow(
                        title = "导入数据",
                        summary = "从备份文件恢复；会覆盖当前全部目标与数据本",
                        action = "导入",
                        onAction = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    )
                    if (importResult.isNotEmpty()) {
                        Text(
                            text = importResult,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.tinyGap)
                        )
                    }
                    Spacer(Modifier.height(Spacing.gridGap))
                    ActionRow(
                        title = "清理缓存",
                        summary = if (cacheSize < 512 * 1024) {
                            "当前没有需要清理的缓存"
                        } else {
                            "网页缓存占 ${CacheCleaner.format(cacheSize)}，清掉不影响数据本、截图与登录"
                        },
                        action = if (cacheSize < 512 * 1024) null else "清理",
                        onAction = if (cacheSize < 512 * 1024) {
                            null
                        } else {
                            {
                                CacheCleaner.clear(context)
                                tick++
                                importResult = "已清缓存"
                            }
                        }
                    )
                    Spacer(Modifier.height(Spacing.gridGap))
                    ActionRow(
                        title = "最近检查结果",
                        summary = "上一轮检查发现 $pendingCount 条，点清空可手动清除",
                        action = if (pendingCount > 0) "清空" else null,
                        onAction = { confirmClearPending = true }
                    )
                    Spacer(Modifier.height(Spacing.gridGap))
                    ActionRow(
                        title = "清除全部本地数据",
                        summary = "目标、账号、数据本、抓取记录全部删除，不可恢复",
                        action = "清除",
                        danger = true,
                        onAction = { confirmClearAll = true }
                    )
                }
            }

            item {
                SectionTitle("OSINT")

                SettingCard {
                    ActionRow(
                        title = "数据本 · 全部目标",
                        summary = "所有目标的记录汇总：计数、冲突、缺口，可跨目标搜索并一键导出",
                        action = "打开",
                        onAction = onOpenLedger
                    )
                    Spacer(Modifier.height(Spacing.gridGap))
                    ActionRow(
                        title = "关联度评估",
                        summary = "填一个目标在各平台的公开资料，算「这几个号能不能被认定成同一个人」",
                        action = "打开",
                        onAction = onOpenAudit
                    )
                }
            }

            item {
                SectionTitle("关于")

                SettingCard {
                    // 开发者致敬：头像 + GitHub
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/Acupicu")
                                        )
                                    )
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                com.sj.followclip.R.drawable.dev_avatar
                            ),
                            contentDescription = "开发者头像",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(Spacing.gridGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HackerPI",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(Spacing.tinyGap))
                            Text(
                                text = "github.com/Acupicu",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "↗",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }

                    Spacer(Modifier.height(Spacing.gridGap))

                    // 开源致敬：不放花哨图形，一句定位就够了
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "开源 · 自托管 · 零第三方 SDK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }

                    Spacer(Modifier.height(Spacing.gridGap))

                    // 名片式第二行：软件名 + 版本
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SJ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.width(Spacing.tinyGap))
                        Text(
                            text = "LENS",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }
        }
    }

    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text("导出全部数据？") },
            text = {
                Text("这份 JSON 含全部目标资料与数据本记录，请勿外发或放在共享目录。")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    exportLauncher.launch("sjlens-backup.json")
                }) { Text("继续导出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExport = false }) { Text("取消") }
            }
        )
    }

    if (confirmClearPending) {
        AlertDialog(
            onDismissRequest = { confirmClearPending = false },
            title = { Text("清空最近检查结果？") },
            text = { Text("会清空 $pendingCount 条显示记录，不影响抓取快照和文件夹。") },
            confirmButton = {
                TextButton(onClick = {
                    PendingStore.clearAll(context)
                    confirmClearPending = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearPending = false }) { Text("取消") }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除全部数据？") },
            text = { Text("所有目标、平台账号、数据本记录、待确认内容和抓取记录都会被删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    AppUiState.clearAllData(context)
                    WebCookies.clearAll { tick++ }
                    BiliSource.logout()
                    confirmClearAll = false
                }) {
                    Text("全部清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = Spacing.itemGap)
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) { content() }
    }
}

/** 一行登录状态：左边是平台名和状态，右边是登录/退出。 */
@Composable
private fun LoginRow(
    name: String,
    loggedIn: Boolean,
    hint: String,
    onLogin: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = if (loggedIn) "已登录" else "未登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.textMuted
                    }
                )
            }
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        TextButton(onClick = if (loggedIn) onClear else onLogin) {
            Text(if (loggedIn) "退出" else "登录")
        }
    }
}

/** 通用动作行。action 为 null 时整行不可点（纯展示）。 */
@Composable
private fun ActionRow(
    title: String,
    summary: String,
    action: String? = null,
    danger: Boolean = false,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    color = if (danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "设置")
@Composable
private fun SettingsScreenPreview() {
    SJFollowClipTheme {
        SettingsScreen(
            onLoginBili = {},
            onOpenAudit = {},
            onOpenLedger = {}
        )
    }
}
