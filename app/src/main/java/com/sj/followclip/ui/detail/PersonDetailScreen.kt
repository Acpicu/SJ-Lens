package com.sj.followclip.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.totalUnread
import com.sj.followclip.ui.preview.FakeData
import com.sj.followclip.ui.theme.SJFollowClipTheme
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.dotColor
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.AvatarStore
import com.sj.followclip.util.formatCount
import com.sj.followclip.util.relativeTime

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
    modifier: Modifier = Modifier
) {
    // 账号列表直接取传入的数据，删除通过回调交给上层改状态，界面自动刷新
    val accounts = person?.accounts.orEmpty()
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
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.textMuted
            )
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
            onDeleteAccount = {}
        )
    }
}
