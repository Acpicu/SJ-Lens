package com.sj.followclip.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sj.followclip.data.local.LedgerRecord
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.Confidence
import com.sj.followclip.data.local.RecordKind
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.domain.model.totalUnread
import com.sj.followclip.ui.preview.FakeData
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.dotColor
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.AvatarStore
import com.sj.followclip.util.formatCount
import com.sj.followclip.util.relativeTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 人物（文件夹）内部：这个人 + 他在各平台的账号。
 *
 * 一个人在同一平台可以有多个账号（主号、生活号、工作室号……），
 * 所以每条账号都有自己的 id，列表也按 id 做 key。
 *
 * 交互：点账号进该账号的作品页；**长按账号可以删除**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person?,
    onBack: () -> Unit,
    onAccountClick: (Account) -> Unit,
    onDeleteAccount: (Long) -> Unit,
    onOpenLedger: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 账号列表直接取传入的数据，删除通过回调交给上层改状态，界面自动刷新
    val accounts = person?.accounts.orEmpty()
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
    }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = person?.remarkName ?: "未找到",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (person == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这个文件夹可能已被删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            item {
                PersonHeader(person = person, accountCount = accounts.size)
            }

            item {
                LedgerCard(
                    personId = person.id,
                    onOpenAll = { onOpenLedger(person.id) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.itemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "平台账号",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                    Spacer(Modifier.width(Spacing.itemGap))
                    Text(
                        text = "长按可删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        text = "还没有添加任何平台账号。点右下角的 + 加一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }

            items(items = accounts, key = { it.id }) { account ->
                AccountCard(
                    account = account,
                    ledgerRefs = ledgerRefs[account.id] ?: 0,
                    onClick = { onAccountClick(account) },
                    onLongClick = { pendingDelete = account }
                )
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个平台账号？") },
            text = {
                Text(
                    "将移除「${target.displayTitle}」以及它在本地记录的已读状态。\n" +
                        "平台上的内容不受影响，之后可以重新添加。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount(target.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 人物头部：头像、备注名、账号数与新内容总数。 */
@Composable
private fun PersonHeader(person: Person, accountCount: Int) {
    val context = LocalContext.current
    val avatar = remember(person.id) { AvatarStore.load(context, person.id) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.gridGap),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Spacing.avatarLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) {
                        Image(
                            bitmap = avatar.asImageBitmap(),
                            contentDescription = "自定义头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = person.remarkName.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.gridGap))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.remarkName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = "$accountCount 个账号 · ${person.totalUnread} 条新内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }
        }
    }
}

/** 单个平台账号卡片。点进作品页，长按删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    account: Account,
    ledgerRefs: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = Spacing.hairline,
            color = if (account.unreadCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(Spacing.unreadDot)
                            .clip(CircleShape)
                            .background(account.platform.dotColor)
                    )
                    Spacer(Modifier.width(Spacing.tinyGap))
                    Text(
                        text = account.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(Spacing.tinyGap))
                    Text(
                        text = if (account.platform.isExperimental) {
                            "· ${account.platform.displayName} 实验性"
                        } else {
                            "· ${account.platform.displayName}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                    if (account.unreadCount > 0) {
                        Spacer(Modifier.width(Spacing.itemGap))
                        Text(
                            text = "${account.unreadCount} 条新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.tinyGap))

                val stats = buildList {
                    account.followerCount?.let { add("${formatCount(it)} 粉丝") }
                    account.videoCount?.let { add("${formatCount(it.toLong())} 作品") }
                    add("上次抓取 ${relativeTime(account.lastFetchAt)}")
                }
                Text(
                    text = stats.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )

                if (ledgerRefs > 0) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = "数据本引用 $ledgerRefs 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (account.platform == Platform.KUAISHOU) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = "快手只在应用前台检查（后台会跳过）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                // 主页链接直接露出来：红队最常确认的第一件事就是「当初填的是哪个链接」
                if (account.homepageUrl.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = account.homepageUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.textMuted
            )
        }
    }
}

/**
 * 数据本卡：已登记多少条、多少条已证实，最近三条就地可见，想到就能补一句。
 *
 * 完整登记（出处 / 信源评级 / 置信度 / 截图）在数据本页——
 * 这里只保证「想到就记」这条路不断，避免随手记变成随手丢。
 */
@Composable
private fun LedgerCard(personId: Long, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    var records by remember(personId) { mutableStateOf(LedgerStore.list(context, personId)) }
    var text by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAll),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            val stats = LedgerStore.stats(records)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "数据本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = "${records.size} 条 · 已证实 ${stats.confirmed} · 待验证 ${stats.pending}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.textMuted
                )
            }

            if (stats.conflicts.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "冲突 ${stats.conflicts.size} 处：" +
                        stats.conflicts.joinToString("、") { it.field },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(Spacing.itemGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("一句话记下看到的") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Spacing.itemGap))
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        records = LedgerStore.add(
                            context = context,
                            personId = personId,
                            record = LedgerRecord(
                                id = "",
                                kind = RecordKind.OTHER,
                                fieldName = "速记",
                                value = text,
                                confidence = Confidence.LOW
                            )
                        )
                        hint = "已记入数据本"
                        text = ""
                    }
                ) { Text("记") }
            }

            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            records.take(3).forEach { record ->
                Spacer(Modifier.height(Spacing.itemGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Spacing.tinyGap))
                    Text(
                        text = fmt.format(Date(record.at)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
                Text(
                    text = record.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (records.isEmpty()) {
                Spacer(Modifier.height(Spacing.itemGap))
                Text(
                    text = "还没有记录。先写一句「抖音号叫某某，简介提到某学校」，出处稍后补。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "文件夹内部")
@Composable
private fun PersonDetailScreenPreview() {
    SJFollowClipTheme {
        PersonDetailScreen(
            person = FakeData.persons[3],
            onBack = {},
            onAccountClick = {},
            onDeleteAccount = {},
            onOpenLedger = {}
        )
    }
}
