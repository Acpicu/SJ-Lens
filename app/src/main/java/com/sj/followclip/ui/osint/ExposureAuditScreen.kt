package com.sj.followclip.ui.osint

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.osint.ExposureAudit
import com.sj.followclip.data.osint.LinkabilityReport
import com.sj.followclip.data.osint.RiskLevel
import com.sj.followclip.data.osint.SelfProfile
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

/** 可评估的平台。红队真正会碰到的基本就这几家。 */
private val PLATFORMS = listOf(
    "微信", "QQ", "微博", "小红书", "知乎", "贴吧", "B站", "抖音", "快手"
)

/** 一个「号」的输入行。平台同名可以有多行——同一个人同平台两个号是最常见的情况。 */
private class AuditRow(platform: String) {
    var platform by mutableStateOf(platform)
    var nickname by mutableStateOf("")
    var bio by mutableStateOf("")
    var searchable by mutableStateOf(false)
    var sameAvatar by mutableStateOf(false)

    val filled: Boolean get() = nickname.isNotBlank()
}

/**
 * 关联度评估：填目标在各平台（含同平台多个号）公开可见的资料，
 * 算「这些号能不能被认定为同一个人」，并给出下一步取证动作。
 *
 * 同平台的两个号也会互相比对：主号发作品、小号发日常，昵称头像一样时几乎必然高关联。
 *
 * 全部在内存里算，不写盘、不上传；要留证请把结论登记进数据本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposureAuditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember { mutableStateListOf(AuditRow("微信"), AuditRow("QQ")) }
    var showDetail by remember { mutableStateOf(true) }

    // 同平台多个号时加序号，报告里才分得清「微信①」「微信②」
    val labels = rows.mapIndexed { index, row ->
        val sameCount = rows.count { it.platform == row.platform }
        if (sameCount > 1) {
            val nth = rows.take(index + 1).count { it.platform == row.platform }
            row.platform + ('①' + (nth - 1))
        } else {
            row.platform
        }
    }

    val profiles = rows.mapIndexedNotNull { index, row ->
        if (!row.filled) {
            null
        } else {
            SelfProfile(
                platform = labels[index],
                nickname = row.nickname.trim(),
                bio = row.bio.trim(),
                // 勾了「头像和其他号是同一张」就喂同一个哈希，引擎会算成汉明距离 0
                avatarHash = if (row.sameAvatar) 0L else null,
                searchable = row.searchable
            )
        }
    }
    val report = ExposureAudit.audit(profiles)
    val pairs = ExposureAudit.pairs(profiles)

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
                title = { Text("关联度评估") }
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
                top = Spacing.gridGap,
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            item {
                Text(
                    text = "填目标在各平台公开可见的资料。分数越高，这些号越能被认定为同一个人。" +
                        "同一平台有两个号就再加一行 —— 小号和大号往往是最容易并上的。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(rows.size) { index ->
                AccountInputCard(
                    row = rows[index],
                    label = labels[index],
                    canDelete = rows.size > 1,
                    onDelete = { rows.removeAt(index) }
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { rows.add(AuditRow("微信")) }) {
                        Text("＋ 再加一个号")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "已填 ${profiles.size} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            if (pairs.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Spacing.cardCorner),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                            Text(
                                text = "关联图",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(Spacing.tinyGap))
                            Text(
                                text = "线越粗关联越强；单对号之间 ≥30 分就该交叉验证后再并号",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textMuted
                            )
                            LinkabilityGraph(
                                platforms = profiles.map { it.platform },
                                pairs = pairs,
                                modifier = Modifier.fillMaxWidth()
                            )
                            pairs.forEach { pair ->
                                Text(
                                    text = "${pair.label}  +${pair.weight}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pair.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.textMuted
                                )
                                Spacer(Modifier.height(Spacing.tinyGap))
                            }
                        }
                    }
                }
            }

            item {
                ReportCard(
                    report = report,
                    expanded = showDetail,
                    onToggle = { showDetail = !showDetail }
                )
            }

            item {
                Text(
                    text = "本页只在内存里计算，不进数据本；要留证请把结论登记到数据本。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

@Composable
private fun AccountInputCard(
    row: AuditRow,
    label: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("移掉", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.tinyGap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PLATFORMS.forEach { name ->
                    TextButton(onClick = { row.platform = name }) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.platform == name) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.textMuted
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.itemGap))
            OutlinedTextField(
                value = row.nickname,
                onValueChange = { row.nickname = it },
                label = { Text("昵称 / 号名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.itemGap))
            OutlinedTextField(
                value = row.bio,
                onValueChange = { row.bio = it },
                label = { Text("简介（可不填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.gridGap))
            ToggleRow(
                title = "这个号能被搜到",
                summary = "站内搜索或搜索引擎直接命中",
                checked = row.searchable,
                onCheckedChange = { row.searchable = it }
            )
            ToggleRow(
                title = "头像和其他号是同一张",
                summary = "复用同一张图最容易被串起来",
                checked = row.sameAvatar,
                onCheckedChange = { row.sameAvatar = it }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }
        Spacer(Modifier.width(Spacing.itemGap))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ReportCard(
    report: LinkabilityReport,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val levelColor = when (report.level) {
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        RiskLevel.LOW -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = report.score.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = levelColor,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = "关联度 ${report.level.text}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = levelColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(Spacing.itemGap))
            Text(
                text = "命中 ${report.factors.size} 项特征",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )

            if (expanded) {
                report.factors.forEach { f ->
                    Spacer(Modifier.height(Spacing.gridGap))
                    Text(
                        text = "· ${f.label}  +${f.weight}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = f.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }

                Spacer(Modifier.height(Spacing.sectionGap))
                Text(
                    text = "下一步动作",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.sp
                )
                report.actions.forEach { a ->
                    Spacer(Modifier.height(Spacing.itemGap))
                    Text(
                        text = "→ $a",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(Spacing.gridGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "收起明细" else "展开明细",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.itemGap)
                )
                Switch(checked = expanded, onCheckedChange = { onToggle() })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // 结果给人看/给对象自己看，走系统剪贴板，不落盘不外传
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("关联度评估", reportText(report)))
                }) {
                    Text("复制报告", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 纯文本报告，用于复制给别处；不含任何输入原文之外的信息。 */
private fun reportText(report: LinkabilityReport): String {
    val sb = StringBuilder()
    sb.append("SJ Lens 关联度评估\n")
    sb.append("关联度 ${report.score}/100（${report.level.text}）\n")
    if (report.factors.isEmpty()) {
        sb.append("未命中显著关联特征\n")
    } else {
        report.factors.forEach { sb.append("· ${it.label} +${it.weight}｜${it.detail}\n") }
    }
    sb.append("下一步动作：\n")
    report.actions.forEach { sb.append("→ $it\n") }
    return sb.toString()
}
