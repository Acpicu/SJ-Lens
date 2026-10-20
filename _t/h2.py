import pathlib
h = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\home\HomeScreen.kt")
src = h.read_text(encoding="utf-8")
start = src.find("private fun AppHeader(")
assert start > 0
# 从 start 起做花括号配平，找到函数结束
i = src.find("{", start)
depth = 0
end = None
while i < len(src):
    if src[i] == "{": depth += 1
    elif src[i] == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            break
    i += 1
block = '''private fun AppHeader(
    manageMode: Boolean,
    checking: Boolean,
    status: String,
    onToggleManage: () -> Unit,
    onCheckAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                top = Spacing.gridGap,
                bottom = Spacing.gridGap
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (manageMode) {
                    buildAnnotatedString { append("管理文件夹") }
                } else {
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        ) { append("SJ") }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append(" LENS")
                        }
                    }
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = when {
                    manageMode -> "点卡片右上角的删除按钮"
                    checking -> status.ifEmpty { "检查中…" }
                    status.isNotEmpty() -> status
                    else -> "OSINT"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = if (manageMode || checking || status.isNotEmpty()) 0.sp else 3.sp
            )
        }

        TextButton(onClick = onCheckAll, enabled = !checking) {
            Text(if (checking) "…" else "检查")
        }
        TextButton(onClick = onToggleManage) {
            Text(if (manageMode) "完成" else "管理")
        }
    }
}'''
src = src[:start] + block + src[end:]
# 补 import
for imp in [
    "import androidx.compose.ui.graphics.Brush",
    "import androidx.compose.ui.text.SpanStyle",
    "import androidx.compose.ui.text.buildAnnotatedString",
    "import androidx.compose.ui.text.withStyle",
]:
    if imp not in src:
        src = src.replace("import androidx.compose.ui.Modifier", imp + "\nimport androidx.compose.ui.Modifier", 1)
h.write_text(src, encoding="utf-8")
print("AppHeader rewritten")
