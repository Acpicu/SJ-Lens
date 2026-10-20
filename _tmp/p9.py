import pathlib, re
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
lines = d.read_text(encoding="utf-8").split("\n")
out = []
i = 0
while i < len(lines):
    out.append(lines[i])
    m = re.match(r"private fun (PersonHeader|LedgerCard)\(", lines[i].strip())
    if not m:
        i += 1
        continue
    # 找到该函数的 ")" 结束行，再在其后的 "{" 行之后插入 context 声明
    j = i
    while j < len(lines) and not lines[j].rstrip().endswith("{"):
        out.append(lines[j]) if j > i else None
        j += 1
    if j < len(lines):
        out.append(lines[j])  # "{"
        out.append("    val context = LocalContext.current")
        i = j + 1
        continue
    i += 1
d.write_text("\n".join(out), encoding="utf-8")
print("context restored in helpers")
