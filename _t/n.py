import pathlib
p = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\guide\Onboarding.kt")
t = p.read_text(encoding="utf-8")

# 1) 记状态 + 点完刷新 + 已授权就跳系统设置
t = t.replace('''    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }''',
'''    var notifyGranted by remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifyGranted = granted
        android.widget.Toast.makeText(
            context,
            if (granted) "通知已开启" else "没开启通知，检查完了不会有横幅提醒",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }''')

# 2) 通知页传入状态与两个动作
t = t.replace('''                    NotificationPage(
                        onAllow = {''',
'''                    NotificationPage(
                        granted = notifyGranted,
                        onOpenSystemSettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onAllow = {''')

# 3) 通知页按钮按状态说话
t = t.replace('''private fun NotificationPage(onAllow: () -> Unit, onBatterySettings: () -> Unit) {''',
'''private fun NotificationPage(
    granted: Boolean,
    onAllow: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBatterySettings: () -> Unit
) {''')
t = t.replace('''        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAllow) { Text("允许通知") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBatterySettings) { Text("省电设置") }
        }''',
'''        Text(
            text = if (granted) "通知：已开启（系统里可随时关）" else "通知：未开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Spacing.gridGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (granted) {
                // 已授权时系统不会再弹窗，点了没反应最容易被当成"点不动"——直接给系统设置入口
                Button(onClick = onOpenSystemSettings) { Text("通知设置") }
            } else {
                Button(onClick = onAllow) { Text("允许通知") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBatterySettings) { Text("省电设置") }
        }''')

# 4) 补 import
for imp in ["import androidx.compose.runtime.getValue",
            "import androidx.compose.runtime.mutableStateOf",
            "import androidx.compose.runtime.remember",
            "import androidx.compose.runtime.setValue"]:
    if imp not in t:
        t = t.replace("import androidx.compose.runtime.rememberCoroutineScope",
                      imp + "\nimport androidx.compose.runtime.rememberCoroutineScope", 1)
p.write_text(t, encoding="utf-8")
print("notification page improved")
