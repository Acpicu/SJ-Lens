import pathlib
d = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip\ui\detail\PersonDetailScreen.kt")
t = d.read_text(encoding="utf-8")

# 在 PersonDetailScreen 体内取 context 并算反向引用计数
t = t.replace('''    val accounts = person?.accounts.orEmpty()''',
'''    val accounts = person?.accounts.orEmpty()
    val context = LocalContext.current
    // 每个平台号被数据本引用了几条（账号归并的反向索引）
    val ledgerRefs = remember(person?.id) {
        runCatching {
            com.sj.followclip.data.local.LedgerStore.list(context, person?.id ?: 0L)
                .map { it.accountRef }
                .filter { it > 0L }
                .groupingBy { it }
                .eachCount()
        }.getOrDefault(emptyMap())
    }''', 1)

# 调用点传入引用数
t = t.replace('''                AccountCard(
                    account = account,
                    onClick = { onAccountClick(account) },''',
'''                AccountCard(
                    account = account,
                    ledgerRefs = ledgerRefs[account.id] ?: 0,
                    onClick = { onAccountClick(account) },''', 1)

# 卡片签名与展示
t = t.replace('''private fun AccountCard(
    account: Account,
    onClick: () -> Unit,''',
'''private fun AccountCard(
    account: Account,
    ledgerRefs: Int,
    onClick: () -> Unit,''', 1)
t = t.replace('''                if (account.platform == Platform.KUAISHOU) {''',
'''                if (ledgerRefs > 0) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = "数据本引用 $ledgerRefs 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (account.platform == Platform.KUAISHOU) {''', 1)
d.write_text(t, encoding="utf-8")
print("person detail patched cleanly")
