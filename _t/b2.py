import pathlib
h = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\home\HomeScreen.kt")
t = h.read_text(encoding="utf-8")
# 撤掉 Box 包裹（结构改动过大），光带改为不用
t = t.replace('''    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {''',
'''    Column(modifier = modifier.fillMaxSize()) {''', 1)
start = t.find("        // 标题区一层极淡光带（垫在内容下方）")
if start > 0:
    end = t.find("        AppHeader(", start)
    t = t[:start] + t[end:]
# 补 background / Brush import
for imp in ["import androidx.compose.foundation.background", "import androidx.compose.ui.graphics.Brush"]:
    if imp not in t:
        t = t.replace("import androidx.compose.foundation.clickable", imp + "\nimport androidx.compose.foundation.clickable", 1)
h.write_text(t, encoding="utf-8")
print("reverted box wrap")
