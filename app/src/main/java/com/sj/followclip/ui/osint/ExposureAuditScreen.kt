package com.sj.followclip.ui.osint

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

/**
 * 曝光审计页：把自己在各平台的公开资料填进来，算「别人有多容易把这些号串成同一个人」。
 *
 * 主体是本机主人自己。填三项即可出分，不联网、不申请任何权限、不读通讯录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposureAuditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val platforms = listOf("B站", "抖音", "快手")
    val nick = remember { mutableStateListOf("", "", "") }
    val bio = remember { mutableStateListOf("", "", "") }
    val searchable = remember { mutableStateListOf(false, false, false) }
    val sameAvatar = remember { mutableStateListOf(false, false, false) }
    var showDetail by remember { mutableStateOf(true) }

    val profiles = platforms.indices.mapNotNull { i ->
        if (nick[i].isBlank()) {
            null
        } else {
            SelfProfile(
                platform = platforms[i],
                nickname = nick[i].trim(),
                bio = bio[i].trim(),
                // 勾了「头像是同一张图」就喂同一个哈希，引擎会算成汉明距离 0
                avatarHash = if (sameAvatar[i]) 0L else null,
                searchable = searchable[i]
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("曝光审计") }
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
                Text(
                    text = "填你自己在各平台的公开资料。分数表示别人把这些号认成同一个人的难易程度，越高越容易。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(platforms.size) { i ->
                AuditInputCard(
                    platform = platforms[i],
                    nickname = nick[i],
                    onNickname = { nick[i] = it },
                    bio = bio[i],
                    onBio = { bio[i] = it },
                    searchable = searchable[i],
                    onSearchable = { searchable[i] = it },
                    sameAvatar = sameAvatar[i],
                    onSameAvatar = { sameAvatar[i] = it }
                )
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
                                text = "关联图（自查版）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(Spacing.tinyGap))
                            Text(
                                text = "线越粗关联越强；红色 = 强关联（≥30 分）",
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
                    text = "数据只在本页内存里计算，关闭页面即丢；不写磁盘、不上传。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

@Composable
private fun AuditInputCard(
    platform: String,
    nickname: String,
    onNickname: (String) -> Unit,
    bio: String,
    onBio: (String) -> Unit,
    searchable: Boolean,
    onSearchable: (Boolean) -> Unit,
    sameAvatar: Boolean,
    onSameAvatar: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Text(
                text = platform,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(Spacing.itemGap))
            OutlinedTextField(
                value = nickname,
                onValueChange = onNickname,
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.itemGap))
            OutlinedTextField(
                value = bio,
                onValueChange = onBio,
                label = { Text("简介（可不填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.gridGap))
            ToggleRow(
                title = "这个主页能被搜到",
                summary = "站内搜索或搜索引擎直接命中",
                checked = searchable,
                onCheckedChange = onSearchable
            )
            ToggleRow(
                title = "头像和其他平台是同一张",
                summary = "复用同一张图最容易被串起来",
                checked = sameAvatar,
                onCheckedChange = onSameAvatar
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
                    text = "收敛建议",
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
                    cm.setPrimaryClip(ClipData.newPlainText("曝光审计", reportText(report)))
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
    sb.append("SJ Lens 曝光审计\n")
    sb.append("关联度 ${report.score}/100（${report.level.text}）\n")
    if (report.factors.isEmpty()) {
        sb.append("未命中显著关联特征\n")
    } else {
        report.factors.forEach { sb.append("· ${it.label} +${it.weight}｜${it.detail}\n") }
    }
    sb.append("收敛建议：\n")
    report.actions.forEach { sb.append("→ $it\n") }
    return sb.toString()
}
