import pathlib
l = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\ledger\LedgerScreen.kt")
lines = l.read_text(encoding="utf-8").split("\n")
# 439 行是多余的 @Composable（紧跟在另一个注解/声明后）；474 行的 LedgerFormFields 缺 @Composable
if lines[438].strip() == "@Composable":
    del lines[438]
# 重新定位 LedgerFormFields
idx = next(i for i, x in enumerate(lines) if x.startswith("private fun LedgerFormFields("))
lines.insert(idx, "@Composable")
l.write_text("\n".join(lines), encoding="utf-8")
print("fixed annotations")
