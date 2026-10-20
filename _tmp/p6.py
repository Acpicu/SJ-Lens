import pathlib
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
t = d.read_text(encoding="utf-8")
# 把 context 提到 remember 之外（composable 作用域内先取值）
t = t.replace('''    val ledgerCounts = remember(person?.id) {
        val ctx = LocalContext.current
        runCatching {''','''    val ctx = context
    val ledgerCounts = remember(person?.id, ctx) {
        runCatching {''')
d.write_text(t, encoding="utf-8")
print("ok")
