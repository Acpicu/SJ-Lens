import pathlib
l = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\ledger\LedgerScreen.kt")
t = l.read_text(encoding="utf-8")
# 去掉重复的 @Composable 注解（保留 Doc 后的第一个）
t = t.replace(""" */
@Composable
@Composable
private fun DraftTextField(""", """ */
@Composable
private fun DraftTextField(""")
t = t.replace("""@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftTextField(""", """@Composable
private fun DraftTextField(""")
l.write_text(t, encoding="utf-8")
print("annotations cleaned")
