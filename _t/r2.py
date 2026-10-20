import pathlib
s = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\settings\SettingsScreen.kt")
t = s.read_text(encoding="utf-8")
t = t.replace("    onOpenLedger: () -> Unit,\n    onOpenGuide: () -> Unit,", "    onOpenLedger: () -> Unit,")
t = t.replace("            onOpenLedger = {},\n            onOpenGuide = {}", "            onOpenLedger = {}")
s.write_text(t, encoding="utf-8")
print("param removed")
