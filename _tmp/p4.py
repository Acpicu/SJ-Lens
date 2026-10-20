import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---- 6) 账号归并：ACCOUNT 类记录可绑定到具体平台账号 ----
p = J/"ui/ledger/LedgerScreen.kt"
t = p.read_text(encoding="utf-8")
t = t.replace('''                            LedgerFormFields(
                                draft = draft,
                                onPickPhoto = { photoLauncher.launch("image/*") }
                            )''',
'''                            LedgerFormFields(
                                draft = draft,
                                accounts = accounts,
                                onPickPhoto = { photoLauncher.launch("image/*") }
                            )''')
t = t.replace('''                    LedgerFormFields(
                        draft = editDraft,
                        onPickPhoto = { editPhotoLauncher.launch("image/*") }
                    )''',
'''                    LedgerFormFields(
                        draft = editDraft,
                        accounts = accounts,
                        onPickPhoto = { editPhotoLauncher.launch("image/*") }
                    )''')
t = t.replace('''private fun LedgerFormFields(
    draft: LedgerDraft,
    onPickPhoto: (() -> Unit)?
) {''',
'''private fun LedgerFormFields(
    draft: LedgerDraft,
    accounts: List<com.sj.followclip.domain.model.Account>,
    onPickPhoto: (() -> Unit)?
) {''')
# 在"分类提示"之后插入绑定账号行
t = t.replace('''        Text(
            text = draft.kind.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted
        )''',
'''        Text(
            text = draft.kind.hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted
        )

        // 「账号」类记录可以绑到目标下的某个平台号：以后一条事实能追溯到具体账号
        if (draft.kind == RecordKind.ACCOUNT && accounts.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = "绑定到哪个号（可选）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            ChoiceChips(
                options = listOf(0L) + accounts.map { it.id },
                selected = draft.accountRef,
                label = { id ->
                    if (id == 0L) "不绑定"
                    else accounts.firstOrNull { it.id == id }?.displayTitle ?: "号 $id"
                },
                onSelect = { draft.accountRef = it }
            )
        }''')
# 读账号列表
t = t.replace('''    val draft = remember(personId) { LedgerDraft() }''',
'''    val accounts = AppUiState.findPerson(personId)?.accounts.orEmpty()
    val draft = remember(personId) { LedgerDraft() }''')
t = t.replace("import com.sj.followclip.ui.theme.Spacing",
              "import com.sj.followclip.ui.state.AppUiState\nimport com.sj.followclip.ui.theme.Spacing")
p.write_text(t, encoding="utf-8")

# ---- 7) 目标详情页：账号卡片显示被数据本引用几条 ----
d = J/"ui/detail/PersonDetailScreen.kt"
t = d.read_text(encoding="utf-8")
t = t.replace('''            items(items = accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,''',
'''            items(items = accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    ledgerRefs = ledgerCounts[account.id] ?: 0,''')
t = t.replace('''private fun AccountCard(
    account: Account,
    onClick: () -> Unit,''',
'''private fun AccountCard(
    account: Account,
    ledgerRefs: Int,
    onClick: () -> Unit,''')
t = t.replace('''                if (account.platform == Platform.KUAISHOU) {''',
'''                if (ledgerRefs > 0) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = "数据本引用 $ledgerRefs 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (account.platform == Platform.KUAISHOU) {''')
t = t.replace('''    val accounts = person?.accounts.orEmpty()''',
'''    val accounts = person?.accounts.orEmpty()
    // 每个平台号被数据本引用了几条（账号归并的反向索引）
    val ledgerCounts = remember(person?.id) {
        val ctx = context
        runCatching {
            com.sj.followclip.data.local.LedgerStore.list(ctx, person?.id ?: 0L)
                .map { it.accountRef }
                .filter { it > 0L }
                .groupingBy { it }
                .eachCount()
        }.getOrDefault(emptyMap())
    }''')
t = t.replace('''    var pendingDelete by remember { mutableStateOf<Account?>(null) }''',
'''    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    val context = LocalContext.current''')
d.write_text(t, encoding="utf-8")
print("account binding wired")
