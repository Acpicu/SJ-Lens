import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# 1) 调色：加深侧壁、提亮强调色，整体更有层次
p = J/"ui/theme/Theme.kt"
t = p.read_text(encoding="utf-8")
t = t.replace("primary = Color(0xFF5BC8E8)", "primary = Color(0xFF67D6F5)")
t = t.replace("surface = Color(0xFF151B22)", "surface = Color(0xFF141A21)")
t = t.replace("surfaceVariant = Color(0xFF1C242C)", "surfaceVariant = Color(0xFF1B232B)")
t = t.replace("outline = Color(0xFF2B3540)", "outline = Color(0xFF27313B)")
t = t.replace("outlineVariant = Color(0xFF1D252D)", "outlineVariant = Color(0xFF212A33)")
t = t.replace("secondary = Color(0xFFA8B4F0)", "secondary = Color(0xFFB0BCF5)")
t = t.replace("tertiary = Color(0xFFE0BE7A)", "tertiary = Color(0xFFE8C885)")
p.write_text(t, encoding="utf-8")

# 2) 首页标题区加一层极淡渐变光带，缓解纯深色单调
h = J/"ui/home/HomeScreen.kt"
t = h.read_text(encoding="utf-8")
anchor = '''        AppHeader(
            manageMode = manageMode,'''
if "radialGradient(" not in t:
    t = t.replace(anchor, '''        // 标题区一层极淡光带：纯深色容易看腻，加一点点层次就够了
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                    )
                )
        )

''' + anchor, 1)
    # 让 header 浮在光带之上：把 AppHeader 之后的内容整体不变，仅调整结构 -> 改为在 Column 顶部叠加
    t = t.replace('''    Column(modifier = modifier.fillMaxSize()) {''',
'''    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {''', 1)
    t = t.replace('''        // 标题区一层极淡光带''', '''        // 标题区一层极淡光带（垫在内容下方）''')
h.write_text(t, encoding="utf-8")
print("theme + header tint")
