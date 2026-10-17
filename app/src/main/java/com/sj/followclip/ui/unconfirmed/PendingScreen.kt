package com.sj.followclip.ui.unconfirmed

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.ui.common.RemoteImage
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.relativeTime

/**
 * 待确认：检查更新时发现、但用户还没处理的新视频。
 *
 * 数据来自 [PendingStore]，按人分组。带封面缩略图 —— 光看标题判断不了更新了什么，
 * 一眼扫封面才是这个应用该有的效率。
 *
 * 点整行去 B 站 App 看，点右侧的勾表示「这条我看过了」，从列表移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnconfirmedScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items = PendingStore.items

    // 按人分组，保持发现顺序
    val grouped = items.groupBy { it.personId }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("待确认") },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { PendingStore.clearAll(context) }) {
                            Text("全部清空", color = MaterialTheme.colorScheme.textMuted)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有待确认的新视频。\n回首页点「检查」拉一次看看。",
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
            verticalArrangement = Arrangement.spacedBy(Spacing.tinyGap)
        ) {
            grouped.forEach { (personId, list) ->
                item(key = "h-$personId") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.gridGap, bottom = Spacing.tinyGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = list.first().personName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { PendingStore.removeByPerson(context, personId) }) {
                            Text("本组全部确认")
                        }
                    }
                }

                items(items = list, key = { it.bvid }) { pending ->
                    PendingRow(
                        pending = pending,
                        onOpen = { openContent(context, pending) },
                        onConfirm = { PendingStore.remove(context, pending.bvid) }
                    )
                }
            }
        }
    }
}

/** 一行待确认：封面 + 标题，点行去看，点勾确认掉。 */
@Composable
private fun PendingRow(
    pending: PendingItem,
    onOpen: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = Spacing.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(
            url = pending.coverUrl,
            modifier = Modifier
                .width(Spacing.coverWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Spacing.itemGap))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.textMuted
                )
            }
        }

        Spacer(Modifier.width(Spacing.gridGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pending.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = "发现于 ${relativeTime(pending.foundAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }

        IconButton(onClick = onConfirm) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "标记已确认",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 打开某个视频：优先 B 站 App，失败降级网页。 */
private fun openContent(context: Context, pending: PendingItem) {
    // 动态走 t.bilibili.com（B 站 App 接管），投稿走 bilibili:// 视频深链
    val isDynamic = pending.kind == "DYNAMIC"
    val primary = if (isDynamic) {
        "https://t.bilibili.com/${pending.bvid}"
    } else {
        "bilibili://video/${pending.bvid}"
    }
    val fallback = if (isDynamic) {
        "https://t.bilibili.com/${pending.bvid}"
    } else {
        "https://www.bilibili.com/video/${pending.bvid}"
    }

    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(primary)))
    }.isSuccess
    if (!opened) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallback))) }
    }
}
