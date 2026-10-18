package com.sj.followclip.ui.notes

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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.QuickNoteStore
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KINDS = listOf("发布", "改名", "删除", "观察")

private val WHAT_TO_RECORD = listOf(
    "发布：哪天发了什么内容、几条 —— 一句话就够，比如「10/19 发了 1 条日常，晚上 9 点」",
    "改名/换头像：改前的名字写下来，之后可以对照搜索结果的变化",
    "删除：确认哪条消失了，这是最有信息量的一条",
    "时间：只在异常的时间发（凌晨、连续多天）才值得单独记",
    "观察：任何你觉得不对劲、但还说不清的地方 —— 先记，别急着下结论"
)

/**
 * 速记本：一条一句话，输入完点「记」就存，带时间戳。
 *
 * 不做必填项、不做哈希、不做等级 —— 那些在「证据条目」里，需要引用时才用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    personId: Long,
    personName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var notes by remember(personId) { mutableStateOf(QuickNoteStore.list(context, personId)) }
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("观察") }
    var showWhat by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

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
                title = { Text("速记 · ${personName.ifBlank { "未命名" }}", maxLines = 1) },
                actions = {
                    TextButton(onClick = { showWhat = !showWhat }) { Text("记什么") }
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
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            if (showWhat) {
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
                                text = "OSINT 笔记记这五类就够",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(Spacing.itemGap))
                            WHAT_TO_RECORD.forEach { line ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "· ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 输入区：一行 + 一个按钮 ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("一句话记下来") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.tinyGap)
                        ) {
                            KINDS.forEach { k ->
                                FilterChip(
                                    selected = kind == k,
                                    onClick = { kind = k },
                                    label = { Text(k, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = text.isNotBlank(),
                                onClick = {
                                    notes = QuickNoteStore.add(context, personId, text, kind)
                                    text = ""
                                }
                            ) { Text("记") }
                            Text(
                                text = "共 ${notes.size} 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textMuted
                            )
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                item {
                    Text(
                        text = "还没有记录。点右上「记什么」看该记哪五类。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            items(notes.size) { index ->
                val note = notes[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.cardPadding),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = note.kind,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(Spacing.itemGap))
                                Text(
                                    text = fmt.format(Date(note.at)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.textMuted
                                )
                            }
                            Spacer(Modifier.height(Spacing.tinyGap))
                            Text(
                                text = note.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(
                            onClick = {
                                notes = QuickNoteStore.remove(context, personId, note.id)
                            }
                        ) { Text("删", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
