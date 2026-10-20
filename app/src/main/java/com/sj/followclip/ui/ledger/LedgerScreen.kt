package com.sj.followclip.ui.ledger

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.local.Admiralty
import com.sj.followclip.data.local.Confidence
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.RecordKind
import com.sj.followclip.data.local.RecordStatus
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import java.io.File

/** 登记/编辑表单的草稿。放在一个类里，避免二十个 var 在 composable 之间来回传。 */
internal class LedgerDraft(record: LedgerRecord? = null) {
    var kind by mutableStateOf(record?.kind ?: RecordKind.ACCOUNT)
    var fieldName by mutableStateOf(TextFieldValue(record?.fieldName.orEmpty()))
    var value by mutableStateOf(TextFieldValue(record?.value.orEmpty()))
    var sourceUrl by mutableStateOf(TextFieldValue(record?.sourceUrl.orEmpty()))
    var letter by mutableStateOf(record?.grade?.take(1)?.ifBlank { "B" } ?: "B")
    var digit by mutableStateOf(record?.grade?.takeLast(1)?.ifBlank { "2" } ?: "2")
    var confidence by mutableStateOf(record?.confidence ?: Confidence.MEDIUM)
    var status by mutableStateOf(record?.status ?: RecordStatus.PENDING)
    var tags by mutableStateOf(TextFieldValue(record?.tags?.joinToString(",").orEmpty()))
    var note by mutableStateOf(TextFieldValue(record?.note.orEmpty()))
    var shotPath by mutableStateOf(record?.shotPath.orEmpty())
    var sha256 by mutableStateOf(record?.sha256.orEmpty())
    var accountRef by mutableStateOf(record?.accountRef ?: 0L)
    var shotName by mutableStateOf(record?.shotPath?.takeIf { it.isNotBlank() }?.let { File(it).name }.orEmpty())

    val grade: String get() = letter + digit
    val tagList: List<String>
        get() = tags.text.split(',', '，', ' ')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }

    /** 只有填了值才有资格入档：空记录会把统计全污染。 */
    val canSubmit: Boolean get() = value.text.isNotBlank()
}

/**
 * 数据本 · 单个目标。
 *
 * 一条记录强制带出处、评级、置信度、状态——红队交档时被追问的就是这四样。
 * 顶部是概览（冲突和缺口直接点名），中间是登记，下面可搜索/按状态与分类筛。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    personId: Long,
    personName: String,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var records by remember(personId) { mutableStateOf(LedgerStore.list(context, personId)) }
    var statusFilter by remember { mutableStateOf(-1) }
    var kindFilter by remember { mutableStateOf<RecordKind?>(null) }
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerRecord?>(null) }
    var hint by remember { mutableStateOf("") }
    var quickValue by remember { mutableStateOf(TextFieldValue("")) }

    val accounts = AppUiState.findPerson(personId)?.accounts.orEmpty()
    val draft = remember(personId) { LedgerDraft() }
    val editDraft = remember(editing) { editing?.let { LedgerDraft(it) } }

    val stats = LedgerStore.stats(records)
    val filtered = records.filter { record ->
        (statusFilter < 0 || record.status == statusFilter) &&
            (kindFilter == null || record.kind == kindFilter) &&
            (query.isBlank() || record.searchBlob.contains(query.trim(), ignoreCase = true))
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = saveShot(context, personId, uri)
            if (file != null) {
                draft.shotPath = file.absolutePath
                draft.shotName = file.name
                draft.sha256 = com.sj.followclip.util.FileHash.sha256(file)
                hint = "已附截图 ${file.name}（已算 SHA-256）"
            } else {
                hint = "截图保存失败"
            }
        }
    }

    // 编辑既有记录时换截图：单独一个选择器，写进编辑草稿，别污染登记表单
    val editPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = saveShot(context, personId, uri)
            if (file != null) {
                editDraft?.shotPath = file.absolutePath
                editDraft?.shotName = file.name
                editDraft?.sha256 = com.sj.followclip.util.FileHash.sha256(file)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = LedgerStore.buildMarkdown(personName, records)
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            hint = if (ok) "已导出数据本" else "导出失败"
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
                title = {
                    Text(
                        text = "数据本 · ${personName.ifBlank { "未命名" }}",
                        maxLines = 1
                    )
                },
                actions = {
                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = onOpenReport
                    ) { Text("报告") }
                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            exportLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}.md"
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
                // 顶部留白：否则第一张卡会贴着标题栏，像被压住一样
                top = Spacing.gridGap,
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

            item { LedgerStatsCard(stats) }

            // ---------- 快速登记：一行就够 ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        DraftTextField(
                            label = "一句话登记（先记下，再补出处）",
                            value = quickValue,
                            onValue = { quickValue = it }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = quickValue.text.isNotBlank(),
                                onClick = {
                                    records = LedgerStore.add(
                                        context = context,
                                        personId = personId,
                                        record = LedgerRecord(
                                            id = "",
                                            kind = RecordKind.OTHER,
                                            fieldName = "速记",
                                            value = quickValue.text,
                                            confidence = Confidence.LOW
                                        )
                                    )
                                    hint = "已登记 ${records.firstOrNull()?.id.orEmpty()}"
                                    quickValue = TextFieldValue("")
                                }
                            ) { Text("记") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showForm = !showForm }) {
                                Text(if (showForm) "收起表单" else "完整登记")
                            }
                        }
                    }
                }
            }

            // ---------- 完整登记表单 ----------
            if (showForm) {
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
                                text = "完整登记",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(Spacing.itemGap))
                            LedgerFormFields(
                                draft = draft,
                                accounts = accounts,
                                onPickPhoto = { photoLauncher.launch("image/*") }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    enabled = draft.canSubmit,
                                    onClick = {
                                        records = LedgerStore.add(
                                            context = context,
                                            personId = personId,
                                            record = draft.toRecord()
                                        )
                                        hint = "已登记 ${records.firstOrNull()?.id.orEmpty()}"
                                        draft.reset()
                                        showForm = false
                                    }
                                ) { Text("入档") }
                                TextButton(
                                    onClick = {
                                        draft.reset()
                                        showForm = false
                                    }
                                ) { Text("清空") }
                            }
                        }
                    }
                }
            }

            // ---------- 筛选 ----------
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
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("搜字段 / 值 / 备注 / 标签") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        ChoiceChips(
                            options = listOf(-1, RecordStatus.CONFIRMED, RecordStatus.PENDING, RecordStatus.REFUTED),
                            selected = statusFilter,
                            label = { if (it < 0) "全部" else RecordStatus.label(it) },
                            onSelect = { statusFilter = it }
                        )
                        Spacer(Modifier.height(Spacing.tinyGap))
                        ChoiceChips(
                            options = listOf<RecordKind?>(null) + RecordKind.entries,
                            selected = kindFilter,
                            label = { it?.label ?: "全部分类" },
                            onSelect = { kindFilter = it }
                        )
                        Spacer(Modifier.height(Spacing.tinyGap))
                        Text(
                            text = "显示 ${filtered.size} / ${records.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Text(
                        text = "还没有记录。先写一句「抖音主页叫某某，简介提到某学校」，再慢慢补出处和评级。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "当前筛选下没有记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            items(filtered.size) { index ->
                val record = filtered[index]
                LedgerRecordCard(
                    record = record,
                    onStatus = { status ->
                        records = LedgerStore.setStatus(context, personId, record.id, status)
                    },
                    onEdit = { editing = record },
                    onDelete = {
                        records = LedgerStore.remove(context, personId, record.id)
                        hint = "已删除 ${record.id}"
                    }
                )
            }

            if (records.isNotEmpty()) {
                item {
                    Text(
                        text = "编号 L-001 起只增不改；删掉的号不复用。" +
                            "同一字段出现两个不同值会在这里点名为「冲突」，先去核实再交档。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }
        }
    }

    val target = editing
    if (target != null && editDraft != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("改 ${target.id}", maxLines = 1) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    LedgerFormFields(
                        draft = editDraft,
                        accounts = accounts,
                        onPickPhoto = { editPhotoLauncher.launch("image/*") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editDraft.canSubmit,
                    onClick = {
                        records = LedgerStore.update(
                            context = context,
                            personId = personId,
                            record = editDraft.toRecord().copy(id = target.id)
                        )
                        hint = "已更新 ${target.id}"
                        editing = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
}

/** 表单字段本体。登记与编辑共用一份，避免两处逻辑走样。 */
/**
 * 受控文本框。
 *
 * 为什么不用普通 String 版本：中文输入法打全角标点（“”）时是一段「组合态」，
 * 只回传 String 会丢掉选区与组合信息，表现为「引号没打出来，光标还往前跳」。
 * 用 TextFieldValue 保管文本 + 选区，输入法状态就不会被我们的重组冲掉。
 */
/**
 * 受控文本框：状态直接由调用方的 TextFieldValue 保管。
 *
 * 之前用「本地 remember + 回写 String」，中文输入法打全角引号（“）时会因为
 * 组合态被重建而丢字、光标回跳；把 TextFieldValue 一路托管给上层就不会。
 */
@Composable
private fun DraftTextField(
    label: String,
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LedgerFormFields(
    draft: LedgerDraft,
    accounts: List<com.sj.followclip.domain.model.Account>,
    onPickPhoto: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ChoiceChips(
            options = RecordKind.entries,
            selected = draft.kind,
            label = { it.label },
            onSelect = { draft.kind = it }
        )
        Text(
            text = draft.kind.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted
        )

        // 「账号」类记录可以绑到目标下的某个平台号：以后一条事实能追溯到具体账号
        if (draft.kind == RecordKind.ACCOUNT && accounts.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = "这条事实属于哪个平台号（可选，绑了就能从账号反查记录）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            ChoiceChips(
                options = listOf(0L) + accounts.map { it.id },
                selected = draft.accountRef,
                label = { id ->
                    if (id == 0L) "不绑定"
                    else accounts.firstOrNull { it.id == id }?.displayTitle ?: "号 $id"
                },
                onSelect = { draft.accountRef = it }
            )
        }

        Spacer(Modifier.height(Spacing.itemGap))
        DraftTextField(
            label = "字段名（如 手机号 / B站 UID）",
            value = draft.fieldName,
            onValue = { draft.fieldName = it },
            singleLine = true,
        )
        Spacer(Modifier.height(Spacing.itemGap))
        DraftTextField(
            label = "值（原样抄，别整理成结论）",
            value = draft.value,
            onValue = { draft.value = it },
            minLines = 3,
        )
        Spacer(Modifier.height(Spacing.itemGap))
        DraftTextField(
            label = "出处（URL / 页面标题 / 文件名）",
            value = draft.sourceUrl,
            onValue = { draft.sourceUrl = it },
            singleLine = true,
        )

        Spacer(Modifier.height(Spacing.itemGap))
        Text(
            text = "信源评级",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ChoiceChips(
            options = Admiralty.sources,
            selected = draft.letter,
            label = { it },
            onSelect = { draft.letter = it }
        )
        ChoiceChips(
            options = Admiralty.credibility,
            selected = draft.digit,
            label = { it },
            onSelect = { draft.digit = it }
        )
        GradeHint(draft.grade)

        Spacer(Modifier.height(Spacing.itemGap))
        Text(
            text = "置信度",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ChoiceChips(
            options = listOf(Confidence.LOW, Confidence.MEDIUM, Confidence.HIGH),
            selected = draft.confidence,
            label = { Confidence.label(it) },
            onSelect = { draft.confidence = it }
        )
        Spacer(Modifier.height(Spacing.itemGap))
        Text(
            text = "状态",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ChoiceChips(
            options = listOf(RecordStatus.PENDING, RecordStatus.CONFIRMED, RecordStatus.REFUTED),
            selected = draft.status,
            label = { RecordStatus.label(it) },
            onSelect = { draft.status = it }
        )

        Spacer(Modifier.height(Spacing.itemGap))
        DraftTextField(
            label = "标签，逗号分隔",
            value = draft.tags,
            onValue = { draft.tags = it },
            singleLine = true,
        )
        Spacer(Modifier.height(Spacing.itemGap))
        DraftTextField(
            label = "备注（这条能推出什么 / 还差什么）",
            value = draft.note,
            onValue = { draft.note = it },
            minLines = 2,
        )

        if (draft.sha256.isNotBlank()) {
            Text(
                text = "截图指纹 ${draft.sha256.take(16)}…（证明这张图交档后没被替换过）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        if (onPickPhoto != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickPhoto) {
                    Text(if (draft.shotPath.isBlank()) "附截图" else "换一张")
                }
                if (draft.shotName.isNotBlank()) {
                    Text(
                        text = draft.shotName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }
        }
    }
}

/** 草稿 → 记录。编号交给存储层，这里一律留空。 */
internal fun LedgerDraft.toRecord(): LedgerRecord = LedgerRecord(
    id = "",
    kind = kind,
    fieldName = fieldName.text,
    value = value.text,
    sourceUrl = sourceUrl.text,
    grade = grade,
    confidence = confidence,
    status = status,
    tags = tagList,
    note = note.text,
    shotPath = shotPath,
    sha256 = sha256,
    accountRef = accountRef
)

internal fun LedgerDraft.reset() {
    kind = RecordKind.ACCOUNT
    fieldName = TextFieldValue("")
    value = TextFieldValue("")
    sourceUrl = TextFieldValue("")
    letter = "B"
    digit = "2"
    confidence = Confidence.MEDIUM
    status = RecordStatus.PENDING
    tags = TextFieldValue("")
    note = TextFieldValue("")
    shotPath = ""
    shotName = ""
    sha256 = ""
    accountRef = 0L
}



/** 截图复制进应用私有目录，返回落盘文件。 */
private fun saveShot(context: Context, personId: Long, uri: Uri): File? = runCatching {
    val dir = File(context.filesDir, "ledger/$personId").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    if (file.length() > 0L) file else null
}.getOrNull()


