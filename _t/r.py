import pathlib, re
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")
s = J/"ui/settings/SettingsScreen.kt"
t = s.read_text(encoding="utf-8")
t = re.sub(r'\s*ActionRow\(\s*title = "使用引导",(?:.|\n)*?\n\s*\)\n\s*Spacer\(Modifier\.height\(Spacing\.gridGap\)\)', '', t, count=1)
s.write_text(t, encoding="utf-8")
a = J/"ui/AppNavigation.kt"
t = a.read_text(encoding="utf-8")
t = t.replace(",\n                    onOpenGuide = { navController.navigate(Routes.GUIDE) }", "")
a.write_text(t, encoding="utf-8")
print("settings entry removed")
