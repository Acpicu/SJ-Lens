package com.sj.followclip.data.source.web

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.data.source.bili.AccountCheckResult
import com.sj.followclip.domain.model.Account
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener

/**
 * 抖音 / 快手的「检查更新」：用一个隐藏 WebView 依次打开主页，从渲染结果里抠作品。
 *
 * 为什么只能在点「检查」时做、不能后台做：
 * WebView 必须挂到窗口上才会真正渲染和执行 JS，后台任务没有窗口。
 * 前台时可以把一个 1×1 的 WebView 临时塞进当前 Activity，用完就销毁，用户看不见。
 *
 * 这条路的好处是不碰平台签名：页面由平台自己渲染，我们只读结果，改版也不会立刻失效。
 */
object WebViewChecker {

    /** 每个页面等多久再抠数据。抖音首屏渲染偏慢，给足时间。 */
    private const val RENDER_WAIT_MS = 4_000L

    /** 单页总超时，避免某个页面卡死拖住整轮检查。 */
    private const val PAGE_TIMEOUT_MS = 20_000L

    /**
     * 依次检查传入的账号。
     * 单个账号失败不影响其他账号，失败的那条只是没有结果。
     */
    suspend fun checkAll(
        activity: Activity,
        jobs: List<Pair<Long, Account>>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<AccountCheckResult> = withContext(Dispatchers.Main) {
        if (jobs.isEmpty()) return@withContext emptyList()

        val webView = WebView(activity).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = (settings.userAgentString ?: "")
                .replace("; wv", "")
                .replace(" Version/4.0", "")
            // 必须可见性设为 INVISIBLE 而不是 alpha = 0：
            // alpha = 0 的 View 仍然会接收触摸事件，铺满全屏会把整个界面点死。
            // INVISIBLE 不参与绘制也不接收触摸，但依然会被测量布局，
            // 视口尺寸正常，页面照常渲染 —— 这才是我们要的「隐形但工作」。
            visibility = android.view.View.INVISIBLE
            isClickable = false
            isFocusable = false
        }

        // 必须铺满窗口（真实视口）才会渲染出内容；
        // 之前用 1×1 是错的 —— 页面直接返回空壳，所以永远抓不到。
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        runCatching { activity.addContentView(webView, params) }

        val results = mutableListOf<AccountCheckResult>()

        jobs.forEachIndexed { index, (personId, account) ->
            val url = account.homepageUrl.trim()
            var freshCount = 0

            if (url.isNotEmpty()) {
                val ids = fetchIds(webView, url)
                if (ids.isNotEmpty()) {
                    val known = VideoSnapshotStore.load(activity, account.id, "dy")
                    val fresh = if (known.isEmpty()) {
                        emptySet<String>()
                    } else {
                        ids.filter { it !in known }.toSet()
                    }
                    VideoSnapshotStore.save(activity, account.id, ids.take(60), "dy")
                    freshCount = fresh.size
                }
            }

            // 不管抓没抓到都回一条结果。
            // 否则界面拿不到「这个账号检查过了」的信号，卡片上的「上次抓取」
            // 就永远停在旧时间，看起来像没检查。
            results += AccountCheckResult(personId, account.id, freshCount)
            onProgress(index + 1, jobs.size)
        }

        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }

        results
    }

    /** 打开一个页面，等它渲染完，把作品 ID 抠出来。 */
    private suspend fun fetchIds(webView: WebView, url: String): List<String> {
        val loaded = CompletableDeferred<Unit>()

        webView.webViewClient = object : WebViewClient() {
            // 平台页面会主动跳 App 深链，跟着跳就啥也抓不到（页面变错误页）
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val u = request?.url?.toString().orEmpty()
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val u = url.orEmpty()
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!loaded.isCompleted) loaded.complete(Unit)
            }
        }

        webView.loadUrl(url)
        // 等 onPageFinished，最多等超时；超时也继续尝试抠一次（SPA 可能已经渲染）
        withTimeoutOrNull(PAGE_TIMEOUT_MS) { loaded.await() }
        delay(RENDER_WAIT_MS)

        val raw = CompletableDeferred<String?>()
        webView.evaluateJavascript(EXTRACT_JS) { value -> raw.complete(value) }
        val value = withTimeoutOrNull(5_000L) { raw.await() } ?: return emptyList()
        return parseIds(value)
    }

    /** 抖音是 /video/{数字id}，快手是 /short-video/{字母数字id}，两种都认。 */
    private const val EXTRACT_JS = """
        (function(){
          try{
            var seen = {}, list = [];
            var nodes = document.querySelectorAll('a[href*="/video/"], a[href*="short-video"]');
            for (var i = 0; i < nodes.length; i++) {
              var href = nodes[i].getAttribute('href') || '';
              var m = href.match(/\/video\/([0-9]{6,})/) || href.match(/short-video\/([0-9A-Za-z_\-]{6,})/);
              if (!m) continue;
              if (seen[m[1]]) continue;
              seen[m[1]] = 1;
              list.push(m[1]);
            }
            return JSON.stringify(list);
          }catch(e){ return '[]'; }
        })()
    """

    private fun parseIds(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return emptyList()
            val arr = JSONArray(inner)
            buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(emptyList())
    }
}
