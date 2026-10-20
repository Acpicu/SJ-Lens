import pathlib
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
lines = d.read_text(encoding="utf-8").split("\n")
# 只保留一处 val context = LocalContext.current（最早的）
seen = False
out = []
for l in lines:
    if l.strip() == "val context = LocalContext.current":
        if seen:
            continue
        seen = True
    out.append(l)
d.write_text("\n".join(out), encoding="utf-8")
print("dup removed")
