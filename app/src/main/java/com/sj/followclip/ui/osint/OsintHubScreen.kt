package com.sj.followclip.ui.osint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.ui.theme.Spacing

/** OSINT 工具箱：把散在设置里的情报功能独立成一栏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsintHubScreen(
    onOpenLedger: () -> Unit,
    onOpenMemo: () -> Unit,
    onOpenGraph: () -> Unit,
    onOpenAudit: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("OSINT 工具", maxLines = 1) }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal)
        ) {
            Spacer(Modifier.height(6.dp))
            HubCard("数据本 · 全部目标", "所有目标的记录汇总：计数、冲突、缺口，可跨目标搜索", onOpenLedger)
            HubCard("紧急速记", "先记下来，再补录到数据本；未完成 / 已完成两栏", onOpenMemo)
            HubCard("关系网图谱", "看目标之间的关系；紧急时也能改关系与类型", onOpenGraph)
            HubCard("关联度评估", "填一个目标在各平台的公开资料，判断几个号是不是同一个人", onOpenAudit)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HubCard(title: String, summary: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(summary, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Text("进入", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
