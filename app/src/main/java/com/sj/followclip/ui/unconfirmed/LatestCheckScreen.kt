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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    // 进来即视为看过：底部导航的角标随之清零
    LaunchedEffect(items.size) { PendingStore.markSeen(context) }
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
            item(key = "baseline-note") {
                Text(
                    text = "检查靠基线：第一次检查只把当前状态记下来（作品数 / 昵称 / 简介），" +
                        "所以第一轮不会报更新；从第二轮起每次跟基线比对，变了才出现在这里。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.padding(top = Spacing.gridGap, bottom = Spacing.tinyGap)
                )
            }

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

/** 文字类变化（主页改名 / 改简介 / 作品数 / 纯文字动态）走文字卡片，不配视频封面。 */
private fun isTextItem(item: PendingItem): Boolean =
    item.kind == "PROFILE" || item.kind == "DYNAMIC" || item.coverUrl.isNullOrBlank()

/** 一行结果：有封面的走图文行，纯文字变化走文字卡片。 */
@Composable
private fun ResultRow(item: PendingItem, onClick: () -> Unit) {
    if (isTextItem(item)) {
        TextResultCard(item = item, onClick = onClick)
        return
    }
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
                            "IMAGE" -> "图文"
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
 * 文字卡片：整条变化的原文直接可读。
 *
 * 主页改名、改简介、作品数变化这类信息本身就是一个句子，
 * 套上 16:9 的视频封面反而看不清内容 —— 红队要看的是「变了什么」。
 */
@Composable
private fun TextResultCard(item: PendingItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (item.kind) {
                        "PROFILE" -> "主页变化"
                        "DYNAMIC" -> "动态"
                        "IMAGE" -> "图文作品"
                        else -> "文字更新"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    text = when (item.platform) {
                        "BILI" -> "B站"
                        "DOUYIN" -> "抖音"
                        "KUAISHOU" -> "快手"
                        else -> item.platform
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = relativeTime(item.foundAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 打开内容：优先用检查时记下的地址（抖音/快手作品的真实链接），
 * B站 投稿走 App 深链、动态走 t.bilibili.com。
 */
private fun openContent(context: Context, item: PendingItem) {
    // 每个平台给多个候选：先试能直接进 App 内页面的深链，再退网页。
    // 各家深链版本差异大，单靠一个 scheme 必然有踩空的时候。
    val candidates = buildList {
        when (item.platform) {
            "BILI" -> {
                if (item.kind == "DYNAMIC") add("https://t.bilibili.com/${item.bvid}")
                else add("bilibili://video/${item.bvid}")
            }

            "DOUYIN" -> {
                val secUid = Regex("""/user/(MS4wLjAB[A-Za-z0-9_-]+)""")
                    .find(item.link)?.groupValues?.get(1)
                if (item.kind != "PROFILE" && item.bvid.length > 8) {
                    add("snssdk1128://aweme/detail/${item.bvid}")
                    add("snssdk1128://video/${item.bvid}")
                }
                if (secUid != null) {
                    add("snssdk1128://user/profile/$secUid")
                    add("snssdk1128://user/$secUid")
                }
            }

            "KUAISHOU" -> {
                val videoId = Regex("""/short-video/([A-Za-z0-9_-]+)""")
                    .find(item.link)?.groupValues?.get(1)
                val userId = Regex("""/(?:fw/user|profile)/([A-Za-z0-9_-]+)""")
                    .find(item.link)?.groupValues?.get(1)
                if (videoId != null) {
                    add("kwai://work/$videoId")
                    add("kwai://short-video/$videoId")
                }
                if (userId != null) {
                    add("kwai://profile/$userId")
                    add("kwai://user/profile/$userId")
                    add("kwai://home/user/$userId")
                }
            }
        }
        // 网页兜底：主页地址 + 检查时记下的原始链接
        when (item.platform) {
            "DOUYIN" -> Regex("""/user/(MS4wLjAB[A-Za-z0-9_-]+)""").find(item.link)
                ?.groupValues?.get(1)?.let { add("https://www.douyin.com/user/$it") }
            "KUAISHOU" -> Regex("""/(?:fw/user|profile)/([A-Za-z0-9_-]+)""").find(item.link)
                ?.groupValues?.get(1)?.let { add("https://www.kuaishou.com/profile/$it") }
        }
        if (item.link.isNotBlank()) add(item.link)
        if (item.platform == "BILI") add("https://www.bilibili.com/video/${item.bvid}")
    }

    // 网页地址优先：深链经常"启动了但页面空白"，网页至少能看到内容。
    val web = candidates.filter { it.startsWith("http://") || it.startsWith("https://") }
    val app = candidates.filter { !it.startsWith("http://") && !it.startsWith("https://") }

    fun open(target: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
    }.isSuccess

    web.forEach { if (open(it)) return }
    app.forEach { if (open(it)) return }

    // 兜底：连链接都没有时给一个平台主页，别让用户点了个空白
    val fallback = when (item.platform) {
        "DOUYIN" -> "https://www.douyin.com/"
        "KUAISHOU" -> "https://www.kuaishou.com/"
        "BILI" -> "https://www.bilibili.com/"
        else -> null
    }
    if (fallback != null) open(fallback)
}
