import pathlib, re
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---------- 1) 首页标题：去掉图标，SJ 渐变 ----------
h = J/"ui/home/HomeScreen.kt"
t = h.read_text(encoding="utf-8")
t = re.sub(r'\s*Row\(verticalAlignment = Alignment\.CenterVertically\) \{\s*androidx\.compose\.foundation\.Image\((?:.|\n)*?Modifier\.size\(26\.dp\)\s*\)\s*Spacer\(Modifier\.width\(Spacing\.itemGap\)\)\s*', '\n            ', t, count=1)
# 恢复成普通 Text，但 SJ 用渐变色
old_text = re.search(r'(\s*)Text\(\s*text = if \(manageMode\) "管理文件夹" else "SJ LENS",(?:.|\n)*?\n\s*\)\n\s*\}', t)
if old_text:
    t = t[:old_text.start()] + '''
                Text(
                    text = if (manageMode) {
                        androidx.compose.ui.text.buildAnnotatedString { append("管理文件夹") }
                    } else {
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                            ) { append("SJ") }
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) { append(" LENS") }
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
            }''' + t[old_text.end():]
h.write_text(t, encoding="utf-8")

# ---------- 2) 首启直接进引导页；删掉耗电引导弹窗 ----------
a = J/"ui/AppNavigation.kt"
t = a.read_text(encoding="utf-8")
t = re.sub(r'\s*// 首次启动弹一次使用引导.*?\n\s*\}\n\n', '\n', t, flags=re.S)
t = re.sub(r'\s*// 首次启动弹一次耗电管理引导（只弹一次）.*?\n\s*\}\n\n', '\n', t, flags=re.S)
t = t.replace("import com.sj.followclip.ui.common.BatteryGuideDialog\n", "")
t = t.replace("import com.sj.followclip.ui.common.shouldShowBatteryGuide\n", "")
t = t.replace("import com.sj.followclip.ui.guide.OnboardingDialog\n", "")
t = t.replace('''            startDestination = Routes.HOME,''',
'''            // 首次启动直接进入引导页，而不是盖一层弹窗
            startDestination = if (remember { shouldShowOnboarding(context) }) Routes.GUIDE else Routes.HOME,''')
t = t.replace('''            composable(Routes.GUIDE) {
                OnboardingScreen(onBack = { navController.popBackStackSafely() })
            }''',
'''            composable(Routes.GUIDE) {
                OnboardingScreen(
                    onBack = {
                        markOnboardingShown(context)
                        if (navController.previousBackStackEntry == null) {
                            navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                        } else {
                            navController.popBackStackSafely()
                        }
                    }
                )
            }''')
a.write_text(t, encoding="utf-8")

# ---------- 3) 引导页加「开始使用」按钮 ----------
g = J/"ui/guide/Onboarding.kt"
t = g.read_text(encoding="utf-8")
t = t.replace('''            item { GuideContent() }''',
'''            item { GuideContent() }
            item {
                Spacer(Modifier.height(Spacing.sectionGap))
                androidx.compose.material3.Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始使用") }
            }''')
g.write_text(t, encoding="utf-8")

# ---------- 4) 数据本：把 SHA 与账号绑定讲清楚 ----------
l = J/"ui/ledger/LedgerScreen.kt"
t = l.read_text(encoding="utf-8")
t = t.replace('''                text = "SHA-256 ${draft.sha256.take(16)}…",''',
'''                text = "截图指纹 ${draft.sha256.take(16)}…（证明这张图交档后没被替换过）",''')
t = t.replace('''                text = "绑定到哪个号（可选）",''',
'''                text = "这条事实属于哪个平台号（可选，绑了就能从账号反查记录）",''')
l.write_text(t, encoding="utf-8")
print("ui patched")
