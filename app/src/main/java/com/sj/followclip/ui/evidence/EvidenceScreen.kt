package com.sj.followclip.ui.evidence

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sj.followclip.data.local.EvidenceEntry
import com.sj.followclip.data.local.EvidenceStore
import com.sj.followclip.data.local.NoteStore
import com.sj.followclip.data.local.TodoItem
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 证据与待验证清单：手工登记，一键导出四段式报告草稿。
 *
 * 应用只负责编号、计时、算哈希、拼 Markdown；判断与结论全部由人填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceScreen(
    personId: Long,
    personName: String,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var entries by remember(personId) {
        mutableStateOf(EvidenceStore.list(context, personId))
    }
    var todos by remember(personId) {
        mutableStateOf(EvidenceStore.todos(context, personId))
    }
    var conclusion by remember(personId) { mutableStateOf(NoteStore.load(context, personId)) }

    var source by remember { mutableStateOf("") }
    var sha by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf("") }
    var photoName by remember { mutableStateOf("") }

    var todoText by remember { mutableStateOf("") }
    var todoCheck by remember { mutableStateOf("") }

    var hint by remember { mutableStateOf("") }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    // 选截图：复制进应用私有目录，顺手算 SHA-256
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = savePhoto(context, personId, uri)
            if (file != null) {
                photoPath = file.absolutePath
                photoName = file.name
                val hash = sha256Of(file)
                if (hash.isNotEmpty() && sha.isBlank()) sha = hash
                hint = "已附截图 ${file.name}"
            } else {
                hint = "截图保存失败"
            }
        }
    }

    // 导出：系统文档创建器，用户自己选位置
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            val report = EvidenceStore.buildReport(personName, conclusion, entries, todos)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(report.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            hint = if (ok) "已导出报告" else "导出失败"
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
                title = {
                    Text(
                        text = "证据 · ${personName.ifBlank { "未命名" }}",
                        maxLines = 1
                    )
                },
                actions = {
                    TextButton(
                        enabled = entries.isNotEmpty() || todos.isNotEmpty(),
                        onClick = onPreview
                    ) { Text("预览") }
                    TextButton(
                        enabled = entries.isNotEmpty() || todos.isNotEmpty(),
                        onClick = {
                            exportLauncher.launch(
                                "osint-${personName.ifBlank { "note" }}-report.md"
                            )
                        }
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

            // ---------- 结论 ----------
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
                            text = "结论（会写进报告第一段）",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        OutlinedTextField(
                            value = conclusion,
                            onValueChange = { conclusion = it },
                            label = { Text("只写证据支持得住的话，一句一条") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                        )
                        TextButton(onClick = {
                            NoteStore.save(context, personId, conclusion)
                            hint = "结论已保存到笔记"
                        }) { Text("保存结论") }
                    }
                }
            }

            // ---------- 新增证据 ----------
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
                            text = "新增证据",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.itemGap))

                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text("来源（URL / 平台 / 页面标题）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        OutlinedTextField(
                            value = sha,
                            onValueChange = { sha = it },
                            label = { Text("SHA-256（附截图会自动算）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        OutlinedTextField(
                            value = grade,
                            onValueChange = { grade = it },
                            label = { Text("信源等级（如 A1 / B2 / F6）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注 / 这条能证明什么") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { photoLauncher.launch("image/*") }) {
                                Text(if (photoPath.isBlank()) "附截图" else "换一张")
                            }
                            if (photoPath.isNotBlank()) {
                                Text(
                                    text = photoName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.textMuted
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = source.isNotBlank() || note.isNotBlank(),
                                onClick = {
                                    entries = EvidenceStore.add(
                                        context = context,
                                        personId = personId,
                                        source = source,
                                        sha256 = sha,
                                        grade = grade,
                                        note = note,
                                        photoPath = photoPath
                                    )
                                    hint = "已登记 ${entries.lastOrNull()?.id.orEmpty()}"
                                    source = ""
                                    sha = ""
                                    grade = ""
                                    note = ""
                                    photoPath = ""
                                    photoName = ""
                                }
                            ) { Text("登记") }
                        }
                    }
                }
            }

            // ---------- 证据列表 ----------
            items(entries.size) { index ->
                val e = entries[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = e.id,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            if (e.grade.isNotBlank()) {
                                Spacer(Modifier.width(Spacing.itemGap))
                                Text(
                                    text = "[${e.grade}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(Modifier.width(Spacing.itemGap))
                            Text(
                                text = fmt.format(Date(e.collectedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textMuted
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    entries = EvidenceStore.remove(context, personId, e.id)
                                }
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        EvidenceLine("来源", e.source)
                        if (e.sha256.isNotBlank()) EvidenceLine("SHA-256", e.sha256)
                        if (e.photoPath.isNotBlank()) {
                            EvidenceLine("截图", File(e.photoPath).name)
                        }
                        if (e.note.isNotBlank()) EvidenceLine("备注", e.note)
                    }
                }
            }

            // ---------- 待验证清单 ----------
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
                            text = "待验证清单",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.tinyGap))
                        Text(
                            text = "每条写清「查什么 + 查到就能证实/推翻」",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                        Spacer(Modifier.height(Spacing.itemGap))

                        OutlinedTextField(
                            value = todoText,
                            onValueChange = { todoText = it },
                            label = { Text("要验证什么") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        OutlinedTextField(
                            value = todoCheck,
                            onValueChange = { todoCheck = it },
                            label = { Text("查什么就能证实/推翻") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            enabled = todoText.isNotBlank(),
                            onClick = {
                                todos = EvidenceStore.addTodo(
                                    context, personId, todoText, todoCheck
                                )
                                todoText = ""
                                todoCheck = ""
                                hint = "已添加待验证项"
                            }
                        ) { Text("添加") }
                    }
                }
            }

            items(todos.size) { index ->
                val t = todos[index]
                TodoCard(
                    item = t,
                    onStatus = { status ->
                        todos = EvidenceStore.setTodoStatus(context, personId, t.id, status)
                    },
                    onDelete = {
                        todos = EvidenceStore.removeTodo(context, personId, t.id)
                    }
                )
            }

            item {
                Text(
                    text = "编号只增不改。截图存在应用私有目录，卸载应用会一起删除；" +
                        "报告导出为 .md 文件，位置由你自己选。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

@Composable
private fun TodoCard(
    item: TodoItem,
    onStatus: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val statusText = when (item.status) {
        1 -> "已证实"
        2 -> "已推翻"
        else -> "未验证"
    }
    val statusColor = when (item.status) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.textMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.id,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.check.isNotBlank()) {
                Text(
                    text = "查：${item.check}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onStatus(1) }) { Text("证实") }
                TextButton(onClick = { onStatus(2) }) { Text("推翻") }
                TextButton(onClick = { onStatus(0) }) { Text("重置") }
            }
        }
    }
}

@Composable
private fun EvidenceLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
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

/** 把选中的图复制进应用私有目录，返回落盘文件。 */
private fun savePhoto(context: Context, personId: Long, uri: Uri): File? = runCatching {
    val dir = File(context.filesDir, "evidence/$personId").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    if (file.length() > 0L) file else null
}.getOrNull()

/** 文件 SHA-256；算不出来返回空串，不阻塞登记。 */
private fun sha256Of(file: File): String = runCatching {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            md.update(buffer, 0, read)
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
}.getOrDefault("")
