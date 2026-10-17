package com.sj.followclip.ui.detail

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.data.source.bili.BiliDynamic
import com.sj.followclip.data.source.bili.BiliSource
import com.sj.followclip.data.source.bili.BiliVideo
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.ui.common.RemoteImage
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import com.sj.followclip.util.formatDuration
import com.sj.followclip.util.relativeTime
import kotlinx.coroutines.launch

/**
 * 平台作品页：应用内直接列出该账号的最新作品，点击跳 B 站 App 观看。
 *
 * 刻意不做网页浏览（WebView）：
 * - 站点的移动版在 WebView 里不渲染，桌面版又宽得没法看；
 * - 内嵌播放器在 WebView 里体验差（没声音、手势返回会串）。
 * 所以只做「列出来 + 跳过去」，把浏览和播放交给官方 App，稳定且不折腾。
 *
 * 抓取走匿名接口（不登录用户账号），因此不存在封号风险。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformVideosScreen(
    personId: Long,
    personName: String,
    account: Account?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val platform = account?.platform
    val accountId = account?.id ?: 0L
    val isBili = platform == Platform.BILI
    val uid = remember(account?.homepageUrl) {
        account?.homepageUrl?.let { BiliSource.extractUid(it) }
    }

    var videos by remember { mutableStateOf<List<BiliVideo>>(emptyList()) }
    var newIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }

    // 动态（图文/转发）单独一套状态，和投稿互不干扰
    var showDynamics by remember { mutableStateOf(false) }
    var dynamics by remember { mutableStateOf<List<BiliDynamic>>(emptyList()) }
    var dynLoading by remember { mutableStateOf(false) }
    var dynError by remember { mutableStateOf<String?>(null) }
    var newDynCount by remember { mutableStateOf(0) }

    fun refresh() {
        val targetUid = uid ?: return
        scope.launch {
            loading = true
            error = null
            BiliSource.fetchVideos(targetUid)
                .onSuccess { list ->
                    videos = list
                    // 比对快照，算出这次新增了哪些；首次访问只建基线，不报"新"
                    val known = VideoSnapshotStore.load(context, accountId)
                    val fresh = if (known.isEmpty()) {
                        emptySet()
                    } else {
                        list.filter { it.bvid !in known }.map { it.bvid }.toSet()
                    }
                    newIds = fresh
                    VideoSnapshotStore.save(context, accountId, list.map { it.bvid }.take(60))
                    // 把新增数写回数据层，首页卡片角标会跟着亮
                    if (personId > 0L && accountId > 0L) {
                        AppUiState.setUnread(personId, accountId, fresh.size)
                    }
                }
                .onFailure { error = it.message ?: "抓取失败" }
            loading = false
            loadedOnce = true
        }
    }

    fun refreshDynamics() {
        val targetUid = uid ?: return
        scope.launch {
            dynLoading = true
            dynError = null
            BiliSource.fetchDynamics(targetUid)
                .onSuccess { list ->
                    dynamics = list
                    // 与「动态」快照比对，算出这次新增了哪些
                    val known = VideoSnapshotStore.load(context, accountId, "dyn")
                    val fresh = if (known.isEmpty()) {
                        emptyList()
                    } else {
                        list.filter { it.id !in known }
                    }
                    newDynCount = fresh.size
                    VideoSnapshotStore.save(context, accountId, list.map { it.id }.take(60), "dyn")

                    if (fresh.isNotEmpty() && personId > 0L) {
                        PendingStore.addAll(
                            context,
                            fresh.map { d ->
                                PendingItem(
                                    bvid = d.id,
                                    title = d.text.take(80),
                                    personId = personId,
                                    personName = personName,
                                    platform = "BILI",
                                    coverUrl = d.coverUrl,
                                    kind = "DYNAMIC",
                                    foundAt = System.currentTimeMillis()
                                )
                            }
                        )
                    }
                }
                .onFailure { dynError = it.message ?: "动态抓取失败" }
            dynLoading = false
        }
    }

    LaunchedEffect(uid) {
        if (isBili && uid != null && !loadedOnce) refresh()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = "${platform?.displayName.orEmpty()} · $personName",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onLogin) { Text(if (BiliSource.isLoggedIn()) "已登录" else "登录") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 投稿 / 动态 两个页签：动态是图文和短内容，和正式投稿分开看更清楚
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)
            ) {
                FilterChip(
                    selected = !showDynamics,
                    onClick = { showDynamics = false },
                    label = { Text("投稿") }
                )
                FilterChip(
                    selected = showDynamics,
                    onClick = {
                        showDynamics = true
                        if (dynamics.isEmpty() && !dynLoading) refreshDynamics()
                    },
                    label = { Text("动态") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.itemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (isBili && uid != null) refresh() },
                    enabled = isBili && uid != null && !loading
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.badgeMinHeight)
                    )
                    Spacer(Modifier.width(Spacing.itemGap))
                    Text(if (loading) "检查中…" else "检查更新")
                }

                Spacer(Modifier.width(Spacing.gridGap))

                val hint = when {
                    !isBili -> "${platform?.displayName} 暂未接入"
                    uid == null -> "还没填主页链接"
                    showDynamics && newDynCount > 0 -> "动态新增 $newDynCount 条"
                    showDynamics && dynamics.isNotEmpty() -> "共 ${dynamics.size} 条动态"
                    newIds.isNotEmpty() -> "新增 ${newIds.size} 条"
                    videos.isNotEmpty() -> "共 ${videos.size} 条"
                    else -> ""
                }
                if (hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (newIds.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.textMuted
                        }
                    )
                }
            }

            val err = error
            if (err != null && videos.isEmpty()) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.pageHorizontal)
                )
                Spacer(Modifier.height(Spacing.itemGap))
            }

            when {
                showDynamics -> DynamicsSection(
                    dynamics = dynamics,
                    loading = dynLoading,
                    error = dynError,
                    onRefresh = { if (isBili && uid != null) refreshDynamics() },
                    onOpen = { dynamic -> openDynamic(context, dynamic.id) }
                )

                loading && videos.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                videos.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uid == null || !isBili) "暂无内容" else "点上面的「检查更新」拉一次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.pageHorizontal,
                        end = Spacing.pageHorizontal,
                        bottom = Spacing.sectionGap
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
                ) {
                    items(items = videos, key = { it.bvid }) { video ->
                        VideoRow(
                            video = video,
                            isNew = video.bvid in newIds,
                            onClick = { openVideoInApp(context, video) }
                        )
                    }
                }
            }
        }
    }
}

/** 单条作品：新内容左侧有圆点，点击去 App 看。 */
@Composable
private fun VideoRow(
    video: BiliVideo,
    isNew: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.tinyGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(Spacing.unreadDot),
            contentAlignment = Alignment.Center
        ) {
            if (isNew) {
                Box(
                    modifier = Modifier
                        .size(Spacing.unreadDot)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(Modifier.width(Spacing.itemGap))

        RemoteImage(
            url = video.coverUrl,
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
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = listOf(
                    formatDuration(video.durationMs),
                    if (video.publishedAt > 0L) relativeTime(video.publishedAt) else ""
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted
            )
        }
    }
}

/** 直接打开某个视频：优先 B 站 App，失败降级网页。 */
private fun openVideoInApp(context: Context, video: BiliVideo) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.appSchemeUrl)))
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.webUrl)))
        }
    }
}

/**
 * 动态列表：图文内容为主，配图能拿到就显示。
 *
 * 动态和投稿是两种内容，混在一个列表里会很乱，所以用页签分开。
 */
@Composable
private fun DynamicsSection(
    dynamics: List<BiliDynamic>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpen: (BiliDynamic) -> Unit
) {
    when {
        loading && dynamics.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        dynamics.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = error ?: "还没有抓到动态",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.textMuted
                    },
                    modifier = Modifier.padding(horizontal = Spacing.pageHorizontal)
                )
                Spacer(Modifier.height(Spacing.itemGap))
                OutlinedButton(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "抓取中…" else "重试")
                }
            }
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                top = Spacing.itemGap,
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            items(items = dynamics, key = { it.id }) { dynamic ->
                DynamicRow(dynamic = dynamic, onClick = { onOpen(dynamic) })
            }
        }
    }
}

/**
 * 单条动态。
 *
 * 有配图才显示缩略图；纯文字动态整张卡片就是文字，
 * 不该硬塞一个播放图标进去（动态不是视频）。
 */
@Composable
private fun DynamicRow(dynamic: BiliDynamic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Spacing.cardCorner),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (dynamic.coverUrl.isNullOrBlank()) {
            Column(modifier = Modifier.padding(Spacing.gridGap)) {
                Text(
                    text = dynamic.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
                if (dynamic.publishedAt > 0L) {
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = relativeTime(dynamic.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }
        } else {
            Row(modifier = Modifier.padding(Spacing.tinyGap)) {
                RemoteImage(
                    url = dynamic.coverUrl,
                    modifier = Modifier
                        .width(Spacing.coverWidth)
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(Spacing.itemGap))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {}

                Spacer(Modifier.width(Spacing.gridGap))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dynamic.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dynamic.publishedAt > 0L) {
                        Spacer(Modifier.height(Spacing.tinyGap))
                        Text(
                            text = relativeTime(dynamic.publishedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }
        }
    }
}

/** 打开某条动态：t.bilibili.com 的链接会被 B 站 App 接管，没装才走浏览器。 */
private fun openDynamic(context: Context, id: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.bilibili.com/$id"))
        )
    }
}
