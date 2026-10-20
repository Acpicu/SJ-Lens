import pathlib
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
t = d.read_text(encoding="utf-8")
t = t.replace('''    val ledgerCounts = remember(person?.id) {
        val ctx = context
        runCatching {''','''    val ledgerCounts = remember(person?.id) {
        val ctx = LocalContext.current
        runCatching {''')
d.write_text(t, encoding="utf-8")
print("fixed")
