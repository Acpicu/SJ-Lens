import pathlib
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
lines = d.read_text(encoding="utf-8").split("\n")
out = []
for i, l in enumerate(lines):
    if l.strip() == "val ctx = context":
        out.append("    val context = LocalContext.current")
        out.append("    val ctx = context")
        continue
    if l.strip() == "val context = LocalContext.current" and i > 95 and any("val ctx = context" in x for x in out[-3:]):
        continue
    out.append(l)
d.write_text("\n".join(out), encoding="utf-8")
print("context hoisted")
