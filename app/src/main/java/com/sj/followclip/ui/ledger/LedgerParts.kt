package com.sj.followclip.ui.ledger

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.Admiralty
import com.sj.followclip.data.local.Confidence
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStats
import com.sj.followclip.data.local.RecordStatus
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 状态色：已证实=强调色，待验证=琥珀，已推翻=红。全应用只在三处出现颜色。 */
@Composable
internal fun statusColor(status: Int) = when (status) {
    RecordStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
    RecordStatus.REFUTED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

/** 单选 chip 行。横向可滚动，选项再多也不会把布局撑破。 */
@Composable
internal fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

/** 概览卡：计数 + 冲突 + 缺口。冲突优先显示——那是必须解释的东西。 */
@Composable
internal fun LedgerStatsCard(stats: LedgerStats, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stats.total.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = "条记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(Spacing.itemGap))
            Text(
                text = "已证实 ${stats.confirmed} · 待验证 ${stats.pending} · 已推翻 ${stats.refuted}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stats.conflicts.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.itemGap))
                stats.conflicts.forEach { conflict ->
                    Text(
                        text = "冲突｜${conflict.field}：" +
                            conflict.values.joinToString(" ／ ") +
                            "（${conflict.ids.joinToString(", ")}）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (stats.missing.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.itemGap))
                Text(
                    text = "缺口｜${stats.missing.joinToString("、")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

/** 一条记录的卡片。证实/推翻/重置就地操作，编辑与删除走回调。 */
@Composable
internal fun LedgerRecordCard(
    record: LedgerRecord,
    onStatus: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }
    val color = statusColor(record.status)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = record.id,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = record.kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
                if (record.grade.isNotBlank()) {
                    Spacer(Modifier.width(Spacing.itemGap))
                    Text(
                        text = record.grade,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = RecordStatus.label(record.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
            }

            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = record.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = record.value.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = buildString {
                    append("置信度 ").append(Confidence.label(record.confidence))
                    if (record.at > 0L) append(" · ").append(fmt.format(Date(record.at)))
                    if (record.tags.isNotEmpty()) {
                        append(" · ").append(record.tags.joinToString(" ") { "#$it" })
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )

            if (record.sourceUrl.isNotBlank()) {
                Text(
                    text = record.sourceUrl,
                    style = MaterialTheme.typography.labelSmall.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { openSource(context, record.sourceUrl) }
                )
            }

            if (record.note.isNotBlank()) {
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onStatus(RecordStatus.CONFIRMED) }) {
                    Text("证实", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onStatus(RecordStatus.REFUTED) }) {
                    Text("推翻", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { onStatus(RecordStatus.PENDING) }) {
                    Text("重置", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Text("改", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = "删",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** 出处能点开就用浏览器打开；不是链接就什么都不做，避免抛 ActivityNotFound。 */
internal fun openSource(context: Context, source: String) {
    val trimmed = source.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

/** 评级读成人话，界面上跟着 chip 一起显示，省得用户背 A–F/1–6 表。 */
@Composable
internal fun GradeHint(grade: String) {
    val text = Admiralty.describe(grade)
    if (text.isEmpty()) return
    Text(
        text = "评级 $grade ＝ $text",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.textMuted
    )
}
