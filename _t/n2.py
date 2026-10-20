import pathlib
p = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\guide\Onboarding.kt")
t = p.read_text(encoding="utf-8")
if "import androidx.compose.runtime.remember\n" not in t:
    t = t.replace("import androidx.compose.runtime.Composable",
                  "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue", 1)
p.write_text(t, encoding="utf-8")
print("imports fixed")
