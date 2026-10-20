import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")
# Tokens: 卡片圆角 18 -> 20，新增描边色引用
p = J/"ui/theme/Tokens.kt"
t = p.read_text(encoding="utf-8")
t = t.replace("val cardCorner = 18.dp", "val cardCorner = 20.dp")
p.write_text(t, encoding="utf-8")
# 首页人卡片：加 hairline 描边
c = J/"ui/home/PersonCard.kt"
t = c.read_text(encoding="utf-8")
if "BorderStroke(" not in t:
    t = t.replace("colors = CardDefaults.cardColors(",
                  "border = androidx.compose.foundation.BorderStroke(\n        width = Spacing.hairline,\n        color = MaterialTheme.colorScheme.outlineVariant\n    ),\n    colors = CardDefaults.cardColors(", 1)
c.write_text(t, encoding="utf-8")
print("polish applied")
