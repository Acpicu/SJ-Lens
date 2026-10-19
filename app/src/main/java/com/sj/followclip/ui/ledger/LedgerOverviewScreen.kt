package com.sj.followclip.ui.ledger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.RecordStatus
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

/**
 * 数据本 · 全部目标：一屏看清手上有多少牌。
 *
 * 只做汇总与跳转，不做编辑——改记录一律进对应目标的数据本，
 * 免得两处入口写出两种结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerOverviewScreen(
    personNames: Map<Long, String>,
    onBack: () -> Unit,
    onOpenTarget: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    val data = remember { LedgerStore.all(context) }
    val targets = data.entries
        .sortedBy { personNames[it.key].orEmpty() }
        .map { (personId, records) ->
            Triple(
                personId,
                personNames[personId]?.takeIf { it.isNotBlank() } ?: "目标 ${personId}",
                records
            )
        }

    val matched = if (query.isBlank()) {
        targets
    } else {
        targets.mapNotNull { target ->
            val hits = target.third.filter { it.searchBlob.contains(query.trim(), ignoreCase = true) }
            if (hits.isEmpty()) null else Triple(target.first, target.second, hits)
        }
    }

    val stats = LedgerStore.stats(targets.flatMap { it.third })

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = LedgerStore.buildMarkdown(
                targets.map { it.second to it.third }
            )
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            hint = if (ok) "已导出全部数据本" else "导出失败"
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("数据本 · 全部目标") },
                actions = {
                    TextButton(
                        enabled = stats.total > 0,
                        onClick = { exportLauncher.launch("ledger-all.md") }
                    ) { Text("导出") }
                }
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
                // 顶部留白：否则第一张卡会贴着标题栏，像被压住一样
                top = Spacing.gridGap,
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            if (hint.isNotEmpty()) {
                item {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item { LedgerStatsCard(stats) }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("跨目标搜索：字段 / 值 / 出处 / 标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (targets.isEmpty()) {
                item {
                    Text(
                        text = "数据本还是空的。进任意目标的详情页，点「数据本」开始登记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            } else if (matched.isEmpty()) {
                item {
                    Text(
                        text = "没有匹配的记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            items(matched.size) { index ->
                val (personId, name, records) = matched[index]
                TargetCard(
                    name = name,
                    records = records,
                    onClick = { onOpenTarget(personId) }
                )
            }
        }
    }
}

/** 一个目标的汇总卡：计数 + 冲突 + 最近三条已证实记录，点进去详看。 */
@Composable
private fun TargetCard(
    name: String,
    records: List<LedgerRecord>,
    onClick: () -> Unit
) {
    val stats = LedgerStore.stats(records)
    val highlights = records
        .filter { it.status == RecordStatus.CONFIRMED }
        .sortedByDescending { it.at }
        .take(3)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = "${stats.total} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = "已证实 ${stats.confirmed} · 待验证 ${stats.pending} · 已推翻 ${stats.refuted}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )

            if (stats.conflicts.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "冲突 ${stats.conflicts.size} 处：" +
                        stats.conflicts.joinToString("、") { it.field },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            highlights.forEach { record ->
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "${record.title}：${record.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
