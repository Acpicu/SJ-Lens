package com.sj.followclip.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.domain.model.Account
import com.sj.followclip.ui.common.RemoteImage
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 一条从网页里抠出来的作品。 */
private data class WebItem(val id: String, val cover: String, val title: String)

/**
 * 抖音 / 快手：底层 WebView 按真实尺寸渲染当数据源，上层用原生界面排版。
 *
 * 关键点：WebView **不能缩到 1×1**。视口太小时站点会直接渲染成空壳，
 * DOM 里什么都抓不到（这就是之前白屏的原因）。这里让它铺满、正常渲染，
 * 只是被上层的原生界面完全盖住，用户看不见。
 *
 * 抓取只读 DOM，不碰接口签名 —— 页面由平台自己的 JS 渲染，改版也不会立刻失效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlatformWebView(
    personId: Long,
    personName: String,
    account: Account?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val platform = account?.platform
    val accountId = account?.id ?: 0L
    val targetUrl = account?.homepageUrl?.trim().orEmpty()
    val referer = remember(platform) {
        when (platform?.name) {
            "DOUYIN" -> "https://www.douyin.com/"
            "KUAISHOU" -> "https://www.kuaishou.com/"
            else -> "https://www.bilibili.com"
        }
    }

    var items by remember(accountId) { mutableStateOf<List<WebItem>>(emptyList()) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var newCount by remember(accountId) { mutableIntStateOf(0) }
    var workCount by remember(accountId) { mutableIntStateOf(0) }
    var lastSeenCount by remember(accountId) { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var debug by remember(accountId) { mutableStateOf("") }

    BackHandler { onBack() }

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
                    IconButton(onClick = {
                        loading = true
                        webView?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(Spacing.avatar / 2)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 底层：真实尺寸的数据源。铺满渲染，被上层界面完全遮住。
            if (targetUrl.isNotBlank()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = (settings.userAgentString ?: "")
                                .replace("; wv", "")
                                .replace(" Version/4.0", "")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val wv = view ?: return
                                    // 页面异步渲染，多试几轮再抠
                                    wv.postDelayed({ loading = false }, 9500L)
                                listOf(1500L, 3500L, 6000L, 9000L).forEach { delayMs ->
                                        wv.postDelayed({
                                            wv.evaluateJavascript(EXTRACT_JS) { raw ->
                                                android.util.Log.d("SJExtract", "raw=" + (raw ?: "null").take(160))
                                                val parsed = parseItems(raw)
                                                debug = parsed.debug
                                                val parsedItems = parsed.items
                                                if (parsedItems.isNotEmpty()) {
                                                    items = parsedItems
                                                    loading = false
                                                    if (accountId > 0L) {
                                                        val known = VideoSnapshotStore.load(
                                                            ctx, accountId, "dy"
                                                        )
                                                        val fresh = if (known.isEmpty()) {
                                                            emptySet<String>()
                                                        } else {
                                                            parsedItems.map { it.id }
                                                                .filter { it !in known }
                                                                .toSet()
                                                        }
                                                        newCount = fresh.size
                                                        VideoSnapshotStore.save(
                                                            ctx, accountId,
                                                            parsedItems.map { it.id }.take(60), "dy"
                                                        )
                                                        if (personId > 0L) {
                                                            AppUiState.setUnread(
                                                                personId, accountId, fresh.size
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }, delayMs)
                                    }
                                }
                            }
                            loadUrl(targetUrl)
                            webView = this

                            // 不能只靠 onPageFinished：这类 SPA 常常永远不回调它，
                            // 结果就是一次都抽不到（和当初 B站 页面加载超时同一个坑）。
                            // 用定时器兜底，到点就抽，抽到即停。
                            // 不再模拟滑动：会误触打开视频，副作用太大。
                            // 作品总数由页面头部数字判断，见 EXTRACT_JS 的 w 字段。
                            listOf(2000L, 3500L, 5000L, 8000L, 12000L).forEach { delayMs ->
                                postDelayed({
                                    evaluateJavascript(EXTRACT_JS) { raw ->                                        val parsed = parseItems(raw)
                                        debug = parsed.debug
                                        if (parsed.workCount > 0) {
                                            // 只有连续两次读到同一个数字才算稳定。
                                            // 单次抖动（28 → 27 → 28）会造成误报，必须先过滤。
                                            val stable = parsed.workCount == lastSeenCount
                                            lastSeenCount = parsed.workCount
                                            workCount = parsed.workCount

                                            if (stable && accountId > 0L) {
                                                val prev = VideoSnapshotStore
                                                    .load(ctx, accountId, "dyn")
                                                    .mapNotNull { it.toIntOrNull() }
                                                    .maxOrNull() ?: 0
                                                newCount = if (prev in 1 until parsed.workCount) {
                                                    parsed.workCount - prev
                                                } else {
                                                    0
                                                }
                                                VideoSnapshotStore.save(
                                                    ctx, accountId,
                                                    listOf(parsed.workCount.toString()),
                                                    "dyn"
                                                )
                                                if (personId > 0L) {
                                                    AppUiState.setUnread(
                                                        personId, accountId, newCount
                                                    )
                                                }
                                            }
                                        }

                                        if (parsed.items.size > items.size) {
                                            items = parsed.items
                                            loading = false
                                        }
                                    }
                                }, delayMs)
                            }
                        }
                    },
                    update = { },
                    onRelease = { it.stopLoading() }
                )
            }

            // 上层：原生界面，不透明背景把 WebView 完全盖住
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (targetUrl.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "这个账号还没填主页链接。\n回到文件夹里删掉后重新添加，填上主页链接即可。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textMuted,
                            modifier = Modifier.padding(Spacing.pageHorizontal)
                        )
                    }
                    return@Column
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.tinyGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            newCount > 0 -> "检测到 $newCount 条新作品"
                            workCount > 0 -> "主页数据 $workCount 条 · 已载入 ${items.size} 张预览"
                            items.isNotEmpty() -> "已载入 ${items.size} 张封面"
                            loading -> "正在读取主页所有作品数量…"
                            debug.isNotEmpty() -> debug
                            else -> "点右上角刷新"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (newCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.textMuted
                        }
                    )

                    Spacer(Modifier.weight(1f))

                    OutlinedButton(onClick = { openRealApp(context, account) }) {
                        Text(
                            text = when (platform?.name) {
                                "DOUYIN" -> "去抖音看日常"
                                "KUAISHOU" -> "去快手看动态"
                                else -> "去${platform?.displayName.orEmpty()}看主页"
                            },
                            maxLines = 1
                        )
                    }
                }

                // 每个账号都提示：平台计数不保证准确，用户要自己进主页核对
                if (!loading && workCount > 0) {
                    Text(
                        text = "主页数据可能不稳，请自行进主页确认作品数量。" +
                            "多出的条目可能是「互关好友可见」的草稿箱内容，" +
                            "不会是私密作品。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted,
                        modifier = Modifier.padding(
                            start = Spacing.pageHorizontal,
                            end = Spacing.pageHorizontal,
                            top = Spacing.tinyGap
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.pageHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "提醒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                    listOf(0 to "关", 3 to "3天", 7 to "7天", 14 to "14天").forEach { (days, label) ->
                        FilterChip(
                            selected = (account?.remindEveryDays ?: 0) == days,
                            onClick = {
                                if (personId > 0L && accountId > 0L) {
                                    AppUiState.setReminder(personId, accountId, days)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                when {
                    loading && items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }

                    items.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没解析到作品（$debug）。可能是页面结构变了，或需要先登录一次。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.textMuted,
                            modifier = Modifier.padding(Spacing.pageHorizontal)
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.pageHorizontal,
                            end = Spacing.pageHorizontal,
                            top = Spacing.itemGap,
                            bottom = Spacing.sectionGap
                        ),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.tinyGap),
                        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)
                    ) {
                        items(items = items, key = { it.id }) { item ->
                            WebItemCell(
                                item = item,
                                referer = referer,
                                onClick = { openRealApp(context, account) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 单张作品卡：封面 + 标题，模仿平台主页的网格观感。 */
@Composable
private fun WebItemCell(
    item: WebItem,
    referer: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.itemGap))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(Spacing.itemGap))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (item.cover.isNotBlank()) {
                RemoteImage(
                    url = item.cover,
                    referer = referer,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        if (item.title.isNotBlank()) {
            Spacer(Modifier.height(Spacing.tinyGap))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 滚到底触发懒加载。
 *
 * 注意：不能只滚 window —— 抖音这类页面用的是**内层 div 滚动**，
 * 滚 window 完全没效果（这就是之前反复刷新都只有 14 条的原因）。
 * 这里同时滚根节点，并自动找出页面上最高的可滚动容器再滚它。
 */
/**
 * 给 WebView 模拟一次「手指上滑」。
 *
 * 为什么不用 JS 设 scrollTop：抖音的滚动是它自己接管的手势逻辑，
 * 设 scrollTop 它不认，页面纹丝不动（所以之前永远是首屏 14 条）。
 * 直接派发真实的 MotionEvent 序列，走的是完整输入链路，和真手指几乎一样。
 */
/**
 * 抓作品。
 *
 * 实测结论：抖音主页的 DOM 里**没有作品 ID**（10 万字页面里一个 aweme_id 都没有），
 * ID 只存在它的 JS 内存里，DOM 抓取拿不到。所以改用**封面图 URL 作为身份标识**：
 * 有新作品就必然出现新封面，用封面集合的差集算「新增 N 条」完全够用。
 *
 * 这也让方案更抗改版：不再依赖任何接口字段名，只依赖"页面上有图片"这件事实。
 */
private const val EXTRACT_JS = """
(function(){
  try{
    var list = [], seen = {};
    var imgs = document.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var im = imgs[i];
      var s = im.currentSrc || im.getAttribute('src') || im.getAttribute('data-src') || '';
      if (!s || s.indexOf('http') !== 0) continue;
      var low = s.toLowerCase();
      if (low.indexOf('douyinpic') < 0 && low.indexOf('douyinvod') < 0
          && low.indexOf('kuaishou') < 0 && low.indexOf('kwimgs') < 0
          && low.indexOf('kspkg') < 0 && low.indexOf('yximgs') < 0
          && low.indexOf('byteimg') < 0) continue;

      // 过滤头像：头像通常在 header 里，或者接近正方形；封面是 3:4 竖图
      if (im.closest('header')) continue;
      var iw = im.naturalWidth || 0, ih = im.naturalHeight || 0;
      if (iw > 0 && ih > 0 && Math.abs(iw - ih) < iw * 0.12) continue;

      // 身份标识只取「文件名主干」：
      // 完整 URL 带一次性签名参数，每次都不一样，直接比对会误报新作品。
      var key = s.split('?')[0].split('/').pop();
      key = key.split('~')[0].split('.')[0];
      if (!key || key.length < 6) key = s.split('?')[0];
      if (seen[key]) continue;
      seen[key] = 1;

      var t = im.getAttribute('alt') || '';
      list.push({ i: key, c: s, t: t });
    }
    // 作品总数：优先取页面内嵌数据里的 aweme_count（不依赖排版，最稳），
    // 取不到再退回页面上「作品 27」这种文字。
    var workCount = 0;
    var src = (document.documentElement.innerHTML || '');
    var am = src.match(/"aweme_count"\s*:\s*([0-9]+)/);
    if (am) workCount = parseInt(am[1], 10) || 0;

    if (workCount === 0) {
      var t2 = (document.body && document.body.innerText) ? document.body.innerText : '';
      var wm = t2.match(/作品\s*[：:]?\s*([0-9]+)/) || t2.match(/([0-9]+)\s*作品/);
      if (wm) workCount = parseInt(wm[1], 10) || 0;
    }

    return JSON.stringify({
      n: list.length,
      w: workCount,
      h: document.documentElement.innerHTML.length,
      s: (document.body ? (document.body.innerText || "") : "").slice(0, 400),
      l: list
    });
  }catch(e){
    return JSON.stringify({ n: 0, w: 0, h: 0, s: "", l: [] });
  }
})()
"""



private data class ParseResult(
    val items: List<WebItem>,
    val workCount: Int,
    val debug: String,
    val rawText: String = ""
)

private fun parseItems(raw: String?): ParseResult {
    if (raw.isNullOrBlank() || raw == "null") return ParseResult(emptyList(), 0, "无返回")
    return runCatching {
        val inner = JSONTokener(raw).nextValue() as? String
            ?: return ParseResult(emptyList(), 0, "空结果")
        val obj = JSONObject(inner)
        val arr = obj.optJSONArray("l") ?: JSONArray()
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("i")
                if (id.isEmpty()) continue
                add(WebItem(id = id, cover = o.optString("c"), title = o.optString("t")))
            }
        }
        ParseResult(list, obj.optInt("w"), "图${obj.optInt("n")} 作品${obj.optInt("w")}", obj.optString("s"))
    }.getOrDefault(ParseResult(emptyList(), 0, "解析异常"))
}


/** 跳真实 App 的用户主页 —— 那里才有「日常」。 */
/**
 * 跳到真实的 App 里看。
 *
 * 抖音只有一家，直接给 scheme；快手有「快手」和「快手极速版」两个包，
 * 依次尝试各自的 scheme，都唤起不了就退回网页链接 —— 网页链接同样会被
 * 这两个 App 接管，系统会弹出选择器，用户自己挑用哪个。
 */
private fun openRealApp(context: Context, account: Account?) {
    val url = account?.homepageUrl.orEmpty()
    if (url.isBlank()) return

    val schemes = when (account?.platform?.name) {
        "DOUYIN" -> {
            val secUid = Regex("""/user/([A-Za-z0-9_\-]+)""").find(url)?.groupValues?.get(1)
            listOfNotNull(secUid?.let { "snssdk1128://user/profile/$it" })
        }

        // 快手的 kwai:// 协议没有公开文档，实测唤不起，直接用网页打开。
        // 网页版内容是全的，只是没有原生 App 那么顺滑。
        "KUAISHOU" -> emptyList()

        else -> emptyList()
    }

    for (scheme in schemes) {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(scheme)))
        }.isSuccess
        if (ok) return
    }

    // 兜底：网页链接，快手/极速版/浏览器由系统选择
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}


