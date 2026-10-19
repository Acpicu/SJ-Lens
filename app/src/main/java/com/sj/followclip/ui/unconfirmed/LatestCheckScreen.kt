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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
 * 最近一轮检查的结果。
 *
 * 每次检查**整体覆盖**，不需要手动确认或清理 —— 打开看到的永远是
 * 「这一次检查发现了什么」，不会越堆越多。
 *
 * 条目来自一整轮检查：B站 的投稿与动态，加上抖音/快手 页面里抽到的作品卡片。
 * 抓不到内容的账号不会假装「没有更新」，原因写在检查结果弹窗里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestCheckScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items = PendingStore.items
    val grouped = items.groupBy { it.personId }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("最近检查") }
            )
        }
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.pageHorizontal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有检查结果。\n回首页点「检查」，或者等一次自动检查。",
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
                        Text(
                            text = "${list.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }

                items(items = list, key = { it.bvid }) { item ->
                    ResultRow(item = item, onClick = { openContent(context, item) })
                }
            }
        }
    }
}

/** 一行结果：封面 + 标题 + 时间，点整行去 App 看。 */
@Composable
private fun ResultRow(item: PendingItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(
            url = item.coverUrl,
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
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = buildString {
                    append(
                        when (item.platform) {
                            "BILI" -> "B站"
                            "DOUYIN" -> "抖音"
                            "KUAISHOU" -> "快手"
                            else -> item.platform
                        }
                    )
                    append(" · ")
                    append(
                        when (item.kind) {
                            "DYNAMIC" -> "动态"
                            "PROFILE" -> "主页变化"
                            else -> "作品"
                        }
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }
    }
}

/**
 * 打开内容：优先用检查时记下的地址（抖音/快手作品的真实链接），
 * B站 投稿走 App 深链、动态走 t.bilibili.com。
 */
private fun openContent(context: Context, item: PendingItem) {
    val candidates = buildList {
        if (item.link.isNotBlank()) add(item.link)
        when {
            item.kind == "DYNAMIC" -> {
                add("https://t.bilibili.com/${item.bvid}")
            }
            item.platform == "BILI" -> {
                add("bilibili://video/${item.bvid}")
                add("https://www.bilibili.com/video/${item.bvid}")
            }
            item.platform == "DOUYIN" -> {
                add("snssdk1128://aweme/detail/${item.bvid}")
                add("https://www.douyin.com/video/${item.bvid}")
            }
            item.platform == "KUAISHOU" -> {
                add("kwai://work/${item.bvid}")
                add("https://www.kuaishou.com/short-video/${item.bvid}")
            }
        }
    }
    candidates.forEach { target ->
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }.isSuccess
        if (opened) return
    }
}
