import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---------- 1) 标题：SJ 改成多色渐变 ----------
h = J/"ui/home/HomeScreen.kt"
t = h.read_text(encoding="utf-8")
t = t.replace('''                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )''',
'''                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )''')
h.write_text(t, encoding="utf-8")

# ---------- 2) 数据本输入框改成 TextFieldValue（保住中文输入法的标点组合态） ----------
l = J/"ui/ledger/LedgerScreen.kt"
t = l.read_text(encoding="utf-8")

# 2a) 加一个受控文本框组件
if "private fun DraftTextField(" not in t:
    anchor = "private fun LedgerFormFields("
    helper = '''/**
 * 受控文本框。
 *
 * 为什么不用普通 String 版本：中文输入法打全角标点（“”）时是一段「组合态」，
 * 只回传 String 会丢掉选区与组合信息，表现为「引号没打出来，光标还往前跳」。
 * 用 TextFieldValue 保管文本 + 选区，输入法状态就不会被我们的重组冲掉。
 */
@Composable
private fun DraftTextField(
    label: String,
    initial: String,
    onValue: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    var tv by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    LaunchedEffect(initial) {
        if (tv.text != initial) {
            tv = tv.copy(text = initial, selection = TextRange(initial.length))
        }
    }
    OutlinedTextField(
        value = tv,
        onValueChange = { next ->
            tv = next
            onValue(next.text)
        },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

''' + anchor
    t = t.replace(anchor, helper, 1)

import re
def swap(field, label, single=True, minl=None):
    global t
    pat = re.compile(r'OutlinedTextField\(\s*value = draft\.' + field + r',\s*onValueChange = \{ draft\.' + field + r' = it \},\s*label = \{ Text\("(?:[^"]*)"\) \},\s*(singleLine = true,\s*)?modifier = Modifier\.fillMaxWidth\(\)\s*\)')
    def repl(m):
        parts = ['DraftTextField(', '            label = "%s",' % label, '            initial = draft.%s,' % field, '            onValue = { draft.%s = it },' % field]
        if single:
            parts.append('            singleLine = true,')
        if minl:
            parts.append('            minLines = %d,' % minl)
        parts.append('        )')
        return "\n".join(parts)
    t2, n = pat.subn(repl, t)
    if n: t = t2
    return n

n1 = swap("fieldName", "字段名（如 手机号 / B站 UID）")
n2 = swap("value", "值（原样抄，别整理成结论）", single=False, minl=3)
n3 = swap("sourceUrl", "出处（URL / 页面标题 / 文件名）")
n4 = swap("tags", "标签，逗号分隔")
n5 = swap("note", "备注（这条能推出什么 / 还差什么）", single=False, minl=2)
print("swapped", n1, n2, n3, n4, n5)

# 2b) 顶部快速登记框也换掉
t = re.sub(r'OutlinedTextField\(\s*value = quickText,\s*onValueChange = \{ quickText = it \},\s*label = \{ Text\("一句话登记（先记下，再补出处）"\) \},\s*singleLine = true,\s*modifier = Modifier\.fillMaxWidth\(\)\s*\)',
'''DraftTextField(
                            label = "一句话登记（先记下，再补出处）",
                            initial = quickText,
                            onValue = { quickText = it }
                        )''', t)

# 2c) 补 import
for imp in ["import androidx.compose.runtime.LaunchedEffect",
            "import androidx.compose.ui.text.TextRange",
            "import androidx.compose.ui.text.input.TextFieldValue"]:
    if imp not in t:
        t = t.replace("import androidx.compose.ui.unit.dp", imp + "\nimport androidx.compose.ui.unit.dp", 1)
l.write_text(t, encoding="utf-8")
print("inputs replaced")
