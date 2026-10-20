package com.sj.followclip.ui.ledger

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.Admiralty
import com.sj.followclip.data.local.Confidence
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.RecordStatus
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报告预览：把数据本按「证实 / 待验证 / 推翻 / 冲突与缺口」重排一遍。
 *
 * 内容和导出的 .md 完全一致——预览里看到的字就是交档时的那份字，
 * 不用导出到别处再核对一遍。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerReportScreen(
    personId: Long,
    personName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val records = remember(personId) { LedgerStore.list(context, personId) }
    val stats = remember(records) { LedgerStore.stats(records) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    // 截图只解码一次，滚动时不再读盘
    val shots = remember(records) {
        records.associate { record ->
            record.id to record.shotPath
                .takeIf { it.isNotBlank() }
                ?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        }
    }

    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = LedgerStore.buildHtml(personName, records)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = LedgerStore.buildMarkdown(personName, records)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }
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
                title = { Text("报告预览", maxLines = 1) },
                actions = {
                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            exportLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}-report.md"
                            )
                        }
                    ) { Text("MD") }
                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            htmlLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}-report.html"
                            )
                        }
                    ) { Text("HTML") }
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
            item {
                Text(
                    text = personName.ifBlank { "未命名" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "记录 ${stats.total} 条 · 已证实 ${stats.confirmed} · " +
                        "待验证 ${stats.pending} · 已推翻 ${stats.refuted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            item {
                SectionCard("一、概览") {
                    MetaLine("冲突", if (stats.conflicts.isEmpty()) "无" else "${stats.conflicts.size} 处")
                    stats.conflicts.forEach { conflict ->
                        Text(
                            text = "· 「${conflict.field}」${conflict.values.joinToString(" ／ ")}" +
                                "（${conflict.ids.joinToString(", ")}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    MetaLine("缺口", if (stats.missing.isEmpty()) "无" else stats.missing.joinToString("、"))
                }
            }

            reportSection("二、已证实", records.filter { it.status == RecordStatus.CONFIRMED }, shots, fmt)
            reportSection("三、待验证", records.filter { it.status == RecordStatus.PENDING }, shots, fmt)
            reportSection("四、已推翻", records.filter { it.status == RecordStatus.REFUTED }, shots, fmt)

            item {
                Text(
                    text = "报告只写记录本身，不做推断。要下结论就新增一条字段名为「结论」的记录，" +
                        "让结论也有出处可查。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportSection(
    title: String,
    records: List<LedgerRecord>,
    shots: Map<String, android.graphics.Bitmap?>,
    fmt: SimpleDateFormat
) {
    item { SectionCard(title) { Text(text = if (records.isEmpty()) "（无）" else "共 ${records.size} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.textMuted) } }
    records.forEach { record ->
        item {
            SectionCard("${record.id} · ${record.kind.label} · ${record.title}") {
                Text(
                    text = record.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.itemGap))
                if (record.grade.isNotBlank()) {
                    MetaLine(
                        "评级",
                        record.grade + Admiralty.describe(record.grade).let {
                            if (it.isBlank()) "" else "（$it）"
                        }
                    )
                }
                MetaLine("置信度", Confidence.label(record.confidence))
                MetaLine("采集", if (record.at > 0L) fmt.format(Date(record.at)) else "（未记）")
                if (record.sourceUrl.isNotBlank()) MetaLine("出处", record.sourceUrl)
                if (record.tags.isNotEmpty()) {
                    MetaLine("标签", record.tags.joinToString(" / ") { "#$it" })
                }
                if (record.note.isNotBlank()) MetaLine("备注", record.note)

                val bitmap = shots[record.id]
                if (bitmap != null) {
                    Spacer(Modifier.height(Spacing.itemGap))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "截图 ${record.id}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (record.shotPath.isNotBlank()) {
                    MetaLine("截图", File(record.shotPath).name + "（文件已丢失）")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(Spacing.itemGap))
            content()
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted,
            modifier = Modifier.width(64.dp)
        )
        Spacer(Modifier.width(Spacing.tinyGap))
        Text(
            text = value.ifBlank { "（未填）" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
