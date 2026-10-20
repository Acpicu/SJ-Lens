import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---- 3) 录入表单：截图算 SHA + ACCOUNT 记录绑定账号 ----
p = J/"ui/ledger/LedgerScreen.kt"
t = p.read_text(encoding="utf-8")
t = t.replace('''    var shotPath by mutableStateOf(record?.shotPath.orEmpty())''',
'''    var shotPath by mutableStateOf(record?.shotPath.orEmpty())
    var sha256 by mutableStateOf(record?.sha256.orEmpty())
    var accountRef by mutableStateOf(record?.accountRef ?: 0L)''')
t = t.replace('''    shotPath = shotPath
)''','''    shotPath = shotPath,
    sha256 = sha256,
    accountRef = accountRef
)''')
t = t.replace('''    shotPath = ""
    shotName = ""
}''','''    shotPath = ""
    shotName = ""
    sha256 = ""
    accountRef = 0L
}''')
# 选完图后自动算指纹
t = t.replace('''                draft.shotName = file.name
                hint = "已附截图 ${file.name}"''',
'''                draft.shotName = file.name
                draft.sha256 = com.sj.followclip.util.FileHash.sha256(file)
                hint = "已附截图 ${file.name}（已算 SHA-256）"''')
t = t.replace('''                editDraft?.shotPath = file.absolutePath
                editDraft?.shotName = file.name''',
'''                editDraft?.shotPath = file.absolutePath
                editDraft?.shotName = file.name
                editDraft?.sha256 = com.sj.followclip.util.FileHash.sha256(file)''')
# 表单里显示 SHA 与账号绑定
t = t.replace('''        if (onPickPhoto != null) {''',
'''        if (draft.sha256.isNotBlank()) {
            Text(
                text = "SHA-256 ${draft.sha256.take(16)}…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        if (onPickPhoto != null) {''')
p.write_text(t, encoding="utf-8")

# ---- 4) 卡片显示 SHA / 关联账号 ----
q = J/"ui/ledger/LedgerParts.kt"
t = q.read_text(encoding="utf-8")
t = t.replace('''            if (record.note.isNotBlank()) {''',
'''            if (record.sha256.isNotBlank()) {
                Text(
                    text = "SHA-256 ${record.sha256.take(16)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (record.note.isNotBlank()) {''')
q.write_text(t, encoding="utf-8")

# ---- 5) 报告页：增加导出 HTML ----
r = J/"ui/ledger/LedgerReportScreen.kt"
t = r.read_text(encoding="utf-8")
t = t.replace('''    val exportLauncher = rememberLauncherForActivityResult(''',
'''    val htmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = LedgerStore.buildHtml(personName, records)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(''')
t = t.replace('''                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            exportLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}-report.md"
                            )
                        }
                    ) { Text("导出") }''',
'''                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            exportLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}-report.md"
                            )
                        }
                    ) { Text("MD") }
                    TextButton(
                        enabled = records.isNotEmpty(),
                        onClick = {
                            htmlLauncher.launch(
                                "ledger-${personName.ifBlank { "target" }}-report.html"
                            )
                        }
                    ) { Text("HTML") }''')
r.write_text(t, encoding="utf-8")
print("form + card + html export patched")
