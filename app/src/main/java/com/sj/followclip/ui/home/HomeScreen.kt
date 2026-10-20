package com.sj.followclip.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.check.CheckController
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.source.bili.AccountCheckResult
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.domain.model.Person
import com.sj.followclip.ui.common.TechProgressBar
import com.sj.followclip.ui.preview.FakeData
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页：品牌标题 + 文件夹（人物）网格。
 *
 * 顺序由用户决定：**长按卡片弹出选项**，可以置顶、上移、下移，顺序会落盘。
 * 刻意不做拖拽动画——格子大小固定、动画很难做得服帖，不如选项来得干脆准确。
 *
 * 也不再按「有没有更新」自动排序：那样用户排好的顺序每次刷新都会被打乱。
 * 「谁更新了」改用卡片角标表达，不靠位置。
 */
@Composable
fun HomeScreen(
    persons: List<Person>,
    onPersonClick: (Long) -> Unit,
    onDeletePerson: (Long) -> Unit,
    onMovePerson: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var manageMode by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Person?>(null) }
    var actionTarget by remember { mutableStateOf<Person?>(null) }

    val context = LocalContext.current

    // 检查状态全部来自 CheckController（应用级单例）：
    // 切到别的页面、甚至退到后台，这一轮都会自己跑完。
    val checking = CheckController.running
    val status = CheckController.statusLine
    val progress = CheckController.progress
    val showResultDialog = CheckController.showDetail
    val lastResults = CheckController.lastResults
    val lastError = CheckController.lastError
    val lastWasManual = CheckController.wasManual()

    // 管理模式下拦截系统返回：手势返回或返回键都先退出管理模式
    BackHandler(enabled = manageMode) { manageMode = false }

    /** 点一下检查：真正的执行体在 CheckController 里，界面不需要自己持协程。 */
    fun performCheck(includeWeb: Boolean) {
        CheckController.start(context, persons, includeWeb)
    }

    // 进应用自动查一轮，三类都覆盖。
    // 抖音走纯 HTTP（后台也能查），快手需要真实渲染的 WebView（仅前台）。
    // 只在「本次启动」第一次进首页时跑：从其它页面返回不该再查一遍。
    LaunchedEffect(Unit) {
        if (!AutoCheckGate.done && persons.isNotEmpty()) {
            AutoCheckGate.done = true
            performCheck(includeWeb = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppHeader(
            manageMode = manageMode,
            checking = checking,
            status = status,
            onToggleManage = { manageMode = !manageMode },
            onCheckAll = { performCheck(includeWeb = true) }
        )

        // 进度条占位：高度固定，检查开始/结束都不会让下面的卡片跳一下。
        // 高度 = 条本身 + 一段留白：进度条贴着标题，同时和下面的卡片拉开距离，
        // 否则会像「卡在卡片上」，看着很挤。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp + Spacing.itemGap),
            contentAlignment = Alignment.TopCenter
        ) {
            if (checking) {
                TechProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.pageHorizontal),
                    height = 3.dp
                )
            }
        }

        if (persons.isEmpty()) {
            EmptyHome(modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = Spacing.pageHorizontal,
                        end = Spacing.pageHorizontal,
                        bottom = Spacing.sectionGap
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
            ) {
                persons.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.gridGap)
                    ) {
                        rowItems.forEach { person ->
                            PersonCard(
                                modifier = Modifier.weight(1f),
                                person = person,
                                onClick = { if (!manageMode) onPersonClick(person.id) },
                                manageMode = manageMode,
                                onDelete = { pendingDelete = person },
                                onLongClick = { if (!manageMode) actionTarget = person }
                            )
                        }
                        // 奇数个时补一个空位，避免最后一张被拉成整行宽
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // 长按选项：置顶 / 上移 / 下移 / 删除
    val action = actionTarget
    if (action != null) {
        val index = persons.indexOfFirst { it.id == action.id }
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(
                    text = action.remarkName,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    OptionRow("移到最前（置顶）", enabled = index > 0) {
                        if (index > 0) onMovePerson(index, 0)
                        actionTarget = null
                    }
                    OptionRow("上移一位", enabled = index > 0) {
                        if (index > 0) onMovePerson(index, index - 1)
                        actionTarget = null
                    }
                    OptionRow("下移一位", enabled = index in 0 until persons.lastIndex) {
                        if (index in 0 until persons.lastIndex) onMovePerson(index, index + 1)
                        actionTarget = null
                    }
                    OptionRow("删除这个文件夹", danger = true) {
                        actionTarget = null
                        pendingDelete = action
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认
    val target = pendingDelete
    if (target != null) {        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个文件夹？") },
            text = {
                Text(
                    "将移除「${target.remarkName}」以及它下面 ${target.accounts.size} 个平台账号的本地记录。\n" +
                        "平台上的内容不受影响。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePerson(target.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 手动检查后的逐账号明细
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { CheckController.showDetail = false },
            title = { Text("检查结果") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val err = lastError
                    if (err != null) {
                        Text(
                            text = "中断：$err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                    }
                    if (lastResults.isEmpty()) {
                        Text(
                            text = "这一轮没有可查的账号（先在文件夹里加平台主页）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                    val changed = lastResults.filter { it.newCount > 0 || it.note.isNotBlank() }
                    val unchanged = lastResults.size - changed.size
                    changed.forEach { r ->
                        Text(
                            text = "${r.accountLabel} · ${r.platform.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = buildString {
                                append("抓取 ").append(r.fetched).append(" 条")
                                when {
                                    r.note.isNotBlank() -> append(" · ").append(r.note)
                                    r.baseline -> append(" · 首次检查已建基线，下次起才报新增")
                                    r.newCount > 0 -> append(" · 新增 ").append(r.newCount).append(" 条")
                                    else -> append(" · 都是见过的，确实没有新增")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                    }
                    Text(
                        text = "首次检查的账号只建基线（记下当前作品数与昵称），第二轮起才报变化。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                    Spacer(Modifier.height(Spacing.tinyGap))
                    if (unchanged > 0) {
                        Text(
                            text = "其余 $unchanged 个账号无变化",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                        Spacer(Modifier.height(Spacing.tinyGap))
                    }
                    if (!lastWasManual) {
                        Text(
                            text = "这一轮只查了 B站；抖音/快手要手动点「检查」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { CheckController.showDetail = false }) { Text("知道了") }
            }
        )
    }
}

/** 选项弹窗里的一行。 */
@Composable
private fun OptionRow(
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = when {
            !enabled -> MaterialTheme.colorScheme.textMuted
            danger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Spacing.gridGap)
    )
}

/** 顶部标识区。右侧是「检查」与「管理」两个动作。 */
@Composable
private fun AppHeader(
    manageMode: Boolean,
    checking: Boolean,
    status: String,
    onToggleManage: () -> Unit,
    onCheckAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                top = Spacing.gridGap,
                bottom = Spacing.gridGap
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (manageMode) {
                    buildAnnotatedString { append("管理文件夹") }
                } else {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        ) { append("SJ") }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append(" LENS")
                        }
                    }
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = when {
                    manageMode -> "点卡片右上角的删除按钮"
                    checking -> status.ifEmpty { "检查中…" }
                    status.isNotEmpty() -> status
                    else -> "OSINT"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = if (manageMode || checking || status.isNotEmpty()) 0.sp else 3.sp
            )
        }

        TextButton(onClick = onCheckAll, enabled = !checking) {
            Text(if (checking) "…" else "检查")
        }
        TextButton(onClick = onToggleManage) {
            Text(if (manageMode) "完成" else "管理")
        }
    }
}

/** 空态：还没有任何文件夹时的引导，只用文案不用插画。 */
@Composable
private fun EmptyHome(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.pageHorizontal),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "还没有文件夹",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Spacing.itemGap))
        Text(
            text = "点右下角的 + 新建一个文件夹，再往里加他的平台主页",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.textMuted
        )
    }
}

@Preview(showBackground = true, name = "首页 · 文件夹网格")
@Composable
private fun HomeScreenPreview() {
    SJFollowClipTheme {
        HomeScreen(
            persons = FakeData.persons,
            onPersonClick = {},
            onDeletePerson = {},
            onMovePerson = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "首页 · 空态")
@Composable
private fun HomeScreenEmptyPreview() {
    SJFollowClipTheme {
        HomeScreen(
            persons = emptyList(),
            onPersonClick = {},
            onDeletePerson = {},
            onMovePerson = { _, _ -> }
        )
    }
}


/** 进程级标记：本次启动是否已自动查过一轮，避免从其它页面返回时反复检查。 */
private object AutoCheckGate {
    var done = false
}
