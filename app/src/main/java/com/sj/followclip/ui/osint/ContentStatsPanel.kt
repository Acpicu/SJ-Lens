package com.sj.followclip.ui.osint

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.osint.PostSample
import com.sj.followclip.data.osint.PublicContentStats
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.util.Locale

/**
 * 内容统计面板：把页面上已经抓到的条目当场算成节奏画像。
 *
 * 纯本地统计，零新增网络请求。样本不足时只显示提示，不硬凑结论。
 */
@Composable
fun ContentStatsPanel(
    posts: List<PostSample>,
    modifier: Modifier = Modifier
) {
    val stats = PublicContentStats.analyze(posts)
    if (stats.isEmpty) return

    val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "内容统计",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${stats.count} 条 · 跨度 ${stats.spanDays} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            Spacer(Modifier.height(Spacing.gridGap))

            StatLine("中位发布间隔", humanHours(stats.medianIntervalHours))
            StatLine("平均发布间隔", humanHours(stats.avgIntervalHours))
            StatLine("集中时段", PublicContentStats.activeWindow(stats.hourly))
            StatLine("最活跃星期", "周${weekdayNames[stats.peakWeekday]}")

            Spacer(Modifier.height(Spacing.gridGap))
            Text(
                text = "星期分布",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
            Spacer(Modifier.height(Spacing.tinyGap))

            val maxCount = (stats.weekly.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.tinyGap),
                verticalAlignment = Alignment.Bottom
            ) {
                stats.weekly.forEachIndexed { index, count ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((4 + 28 * count / maxCount).dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = weekdayNames[index],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }

            stats.notes.forEach { note ->
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "· $note",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.textMuted,
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.width(Spacing.itemGap))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 小时数转人话：超过一天用天，超过两小时用整小时窗口。 */
private fun humanHours(h: Double): String = when {
    h <= 0.0 -> "—"
    h >= 24.0 -> String.format(Locale.US, "%.1f 天", h / 24.0)
    h >= 2.0 -> String.format(Locale.US, "%.1f 小时", h)
    else -> String.format(Locale.US, "%.0f 分钟", h * 60)
}
