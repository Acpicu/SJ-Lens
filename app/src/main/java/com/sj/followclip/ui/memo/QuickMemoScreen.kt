package com.sj.followclip.ui.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.QuickMemoStore
import com.sj.followclip.ui.theme.Spacing

/**
 * 紧急速记：先记，后补录。
 * 两条清单——「未完成添加」和「已完成添加」；点条目切换状态，长按删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { QuickMemoStore.init(context) }
    var draft by remember { mutableStateOf("") }
    var dateFor by remember { mutableStateOf<com.sj.followclip.data.local.Memo?>(null) }
    val all = QuickMemoStore.items
    val todo = all.filter { !it.done }
    val done = all.filter { it.done }

    Scaffold(topBar = {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            title = { Text("紧急速记", maxLines = 1) },
            actions = {
                TextButton(onClick = {
                    val dir = context.getExternalFilesDir(null)
                    val f = java.io.File(dir, "sj-memos.json")
                    val arr = org.json.JSONArray()
                    QuickMemoStore.items.forEach {
                        arr.put(org.json.JSONObject().put("text", it.text).put("at", it.at).put("done", it.done))
                    }
                    runCatching { f.writeText(arr.toString()) }
                    android.widget.Toast.makeText(context, "已导出 ${f.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                }) { Text("导出给电脑") }
                if (done.isNotEmpty()) {
                    TextButton(onClick = { QuickMemoStore.clearDone(context) }) { Text("清已完成") }
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val target2 = dateFor
            if (target2 != null) MemoDateDialog(target2) { dateFor = null }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("一句话，先记下来") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { QuickMemoStore.add(context, draft); draft = "" }) { Text("记") }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    SectionTitle("未完成添加", todo.size)
                }
                if (todo.isEmpty()) {
                    item { EmptyLine("暂无。记下的东西先躺在这里。") }
                }
                items(todo, key = { it.id }) { m ->
                    MemoRow(m.text, m.at, false,
                        onClick = { QuickMemoStore.toggle(context, m.id) },
                        onLong = { dateFor = m })
                }
                item { SectionTitle("已完成添加", done.size) }
                if (done.isEmpty()) {
                    item { EmptyLine("补录到数据本之后，点条目移到这里。") }
                }
                items(done, key = { it.id }) { m ->
                    MemoRow(m.text, m.at, true,
                        onClick = { QuickMemoStore.toggle(context, m.id) },
                        onLong = { dateFor = m })
                }
            }
        }
    }
}

@Composable
private fun MemoDateDialog(memo: com.sj.followclip.data.local.Memo,
                           onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fmt = remember(memo.at) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }
    var text by remember(memo.id) { mutableStateOf(fmt.format(java.util.Date(memo.at))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间与操作") },
        text = {
            Column {
                Text("改时间可以补录之前发生的事，格式 2026-10-21 14:30",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ts = runCatching { fmt.parse(text.trim())?.time }.getOrNull()
                if (ts != null) QuickMemoStore.setAt(context, memo.id, ts)
                onDismiss()
            }) { Text("保存时间") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { QuickMemoStore.remove(context, memo.id); onDismiss() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun SectionTitle(text: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("$count", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoRow(text: String, at: Long, done: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val t = remember(at) {
        val d = java.util.Date(at)
        val f = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        f.format(d)
    }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (done) "✓" else "○", color = if (done) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null)
            Text(t, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

}
