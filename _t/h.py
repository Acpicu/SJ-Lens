import pathlib
h = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\home\HomeScreen.kt")
lines = h.read_text(encoding="utf-8").split("\n")
# 重写 361..385（0-based 360..384）这一段：正确的 Text + 收尾大括号
start = next(i for i, l in enumerate(lines) if "buildAnnotatedString { append(\"管理文件夹\")" in l) - 1
end = next(i for i, l in enumerate(lines) if l.strip() == "}" and i > start and lines[i-1].strip() == ")")
block = '''            Text(
                text = if (manageMode) {
                    androidx.compose.ui.text.buildAnnotatedString { append("管理文件夹") }
                } else {
                    androidx.compose.ui.text.buildAnnotatedString {
                        androidx.compose.ui.text.withStyle(
                            androidx.compose.ui.text.SpanStyle(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        ) { append("SJ") }
                        androidx.compose.ui.text.withStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        ) { append(" LENS") }
                    }
                },
                style = MaterialTheme.typography.headlineMedium
            )'''
new = lines[:start] + block.split("\n") + lines[end+1:]
h.write_text("\n".join(new), encoding="utf-8")
print("header rewritten")
