package com.sj.followclip.ui.evidence

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.sj.followclip.data.local.EvidenceStore
import com.sj.followclip.data.local.NoteStore
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 报告预览：把结论 / 证据 / 待验证在应用内直接看，不用导出去别处才能读。
 *
 * 截图就地显示；点「复制」拿纯 Markdown，点「导出」存 .md 文件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    personId: Long,
    personName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val conclusion = remember(personId) { NoteStore.load(context, personId) }
    val entries = remember(personId) { EvidenceStore.list(context, personId) }
    val todos = remember(personId) { EvidenceStore.todos(context, personId) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    // 截图只解码一次，避免滚动时反复读盘
    val bitmaps = remember(personId, entries) {
        entries.map { e ->
            if (e.photoPath.isBlank()) {
                null
            } else {
                runCatching { BitmapFactory.decodeFile(e.photoPath) }.getOrNull()
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("报告预览", maxLines = 1) }
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
                    text = "证据 ${entries.size} 条 · 待验证 ${todos.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            // ---------- 一、结论 ----------
            item {
                SectionCard("一、结论") {
                    Text(
                        text = conclusion.ifBlank { "（未填写）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ---------- 二、证据 ----------
            items(entries.size) { index ->
                val e = entries[index]
                SectionCard("${e.id}${if (e.grade.isBlank()) "" else "  [${e.grade}]"}") {
                    MetaLine("采集时间", fmt.format(Date(e.collectedAt)))
                    if (e.source.isNotBlank()) MetaLine("来源", e.source)
                    if (e.sha256.isNotBlank()) MetaLine("SHA-256", e.sha256)
                    if (e.note.isNotBlank()) MetaLine("备注", e.note)

                    val bmp = bitmaps.getOrNull(index)
                    if (bmp != null) {
                        Spacer(Modifier.height(Spacing.itemGap))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "截图 ${e.id}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (e.photoPath.isNotBlank()) {
                        MetaLine("截图", File(e.photoPath).name + "（文件已丢失）")
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    SectionCard("二、证据") {
                        Text(
                            text = "（无）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }

            // ---------- 三、置信度 ----------
            item {
                SectionCard("三、置信度") {
                    Text(
                        text = "（高 / 中 / 低 + 一句理由，例如「中：只有单一信源，时间戳未互证」）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            // ---------- 四、待验证 ----------
            item {
                SectionCard("四、待验证清单") {
                    if (todos.isEmpty()) {
                        Text(
                            text = "（无）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    } else {
                        todos.forEach { t ->
                            val box = when (t.status) {
                                1 -> "[x] 已证实"
                                2 -> "[x] 已推翻"
                                else -> "[ ] 未验证"
                            }
                            Text(
                                text = "$box  ${t.id}  ${t.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (t.check.isNotBlank()) {
                                Text(
                                    text = "    查：${t.check}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.textMuted
                                )
                            }
                            Spacer(Modifier.height(Spacing.tinyGap))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "这份预览与导出的 .md 内容一致；导出后可发给别人，" +
                        "截图只在应用内显示，不进 .md 文件本身。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted,
            modifier = Modifier.width(64.dp)
        )
        Spacer(Modifier.width(Spacing.tinyGap))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
