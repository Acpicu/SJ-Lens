package com.sj.followclip.data.source.web

import android.app.Activity
import android.view.ViewGroup
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.StoredProfile
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.data.local.WebProfileStore
import com.sj.followclip.data.source.bili.AccountCheckResult
import com.sj.followclip.data.source.bili.CheckOutcome
import com.sj.followclip.domain.model.Account
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 快手更新检测：只能靠真实渲染的 WebView（HTTP 接口全被 `__NS_sig3` 签名拦死）。
 *
 * 上一版把隐藏 WebView 铺开渲染时踩过一个坑：**页面 JS 会主动调起快手 App，
 * 把正在用应用的人直接弹走**。这一版把所有能触发外部跳转的口子都堵上：
 *
 * 1. `shouldOverrideUrlLoading` 只放行 http(s)（两个重载都拦）
 * 2. `supportMultipleWindows = false` + `javaScriptCanOpenWindowsAutomatically = false`
 * 3. `WebChromeClient.onCreateWindow` 一律返回 false，不接管新窗口
 * 4. WebView 塞在内容容器最底层、缩到 3%、近乎透明：既能被绘制（DOM 才会生成），
 *    又不会挡住界面、不吃触摸
 *
 * 抓的是页面上的「作品 N」计数 —— 和用户在账号页看到的那个数字同源。
 */
object KuaishouChecker {

    private const val RENDER_WAIT_MS = 5_000L
    private const val PAGE_TIMEOUT_MS = 18_000L

    suspend fun checkAll(
        activity: Activity,
        jobs: List<Pair<Long, Account>>,
        personNames: Map<Long, String>,
        onProgress: (done: Int, total: Int) -> Unit
    ): CheckOutcome = withContext(Dispatchers.Main) {
        if (jobs.isEmpty()) return@withContext CheckOutcome()

        val webView = WebView(activity).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // 关掉一切能主动跳出去的开关
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = (settings.userAgentString ?: "")
                .replace("; wv", "")
                .replace(" Version/4.0", "")
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 软件层：被遮挡也照样光栅化，DOM 才会完整
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val host = activity.findViewById<ViewGroup>(android.R.id.content)
        runCatching { host?.addView(webView, 0, params) }
        runCatching {
            webView.alpha = 0.015f
            webView.scaleX = 0.03f
            webView.scaleY = 0.03f
            webView.translationX = 10_000f
        }

        val results = mutableListOf<AccountCheckResult>()
        val items = mutableListOf<PendingItem>()

        jobs.forEachIndexed { index, (personId, account) ->
            val personName = personNames[personId].orEmpty()
            var fetched = 0
            var newCount = 0
            var baseline = false
            var note = ""

            if (account.homepageUrl.isBlank()) {
                note = "没填主页链接，没得查"
            } else if (isVideoLink(account.homepageUrl)) {
                note = "这个是单条作品的链接，不是主页；换成主页链接才能比对作品数"
            } else {
                val snapshot = fetchProfile(webView, account.homepageUrl)
                val count = snapshot?.count ?: -1
                if (snapshot == null || count < 0) {
                    // 真·没抓到：页面没渲染出来，不能当成「没有更新」
                    note = "页面没渲染出来（没加载完或被验证页挡住），这次不算「没有更新」"
                } else {
                    fetched = count
                    val now = StoredProfile(
                        workCount = count,
                        nickname = snapshot.name,
                        signature = snapshot.bio
                    )
                    val old = WebProfileStore.load(activity, account.id)
                    val delta = WebProfileStore.diff(old, now)
                    WebProfileStore.save(activity, account.id, now)
                    VideoSnapshotStore.save(activity, account.id, listOf(count.toString()), "count")
                    baseline = delta.baseline
                    if (baseline && count == 0) {
                        note = "这个号目前没有作品，已记基线；发第一条就会报"
                    }
                    if (delta.workAdded > 0) {
                        newCount++
                        items += PendingItem(
                            bvid = "ks-count-${account.id}-$count",
                            title = "作品数 ${old?.workCount} → $count（新增 ${delta.workAdded} 条）",
                            personId = personId,
                            personName = personName,
                            platform = account.platform.name,
                            link = account.homepageUrl,
                            kind = "PROFILE",
                            foundAt = System.currentTimeMillis()
                        )
                    }
                    if (delta.renamedTo != null) {
                        newCount++
                        items += PendingItem(
                            bvid = "ks-rename-${account.id}-${delta.renamedTo.hashCode()}",
                            title = "改名：${delta.renamedFrom} → ${delta.renamedTo}",
                            personId = personId,
                            personName = personName,
                            platform = account.platform.name,
                            link = account.homepageUrl,
                            kind = "PROFILE",
                            foundAt = System.currentTimeMillis()
                        )
                    }
                    if (delta.bioTo != null) {
                        newCount++
                        items += PendingItem(
                            bvid = "ks-bio-${account.id}-${delta.bioTo.hashCode()}",
                            title = "改简介：${delta.bioFrom} → ${delta.bioTo}",
                            personId = personId,
                            personName = personName,
                            platform = account.platform.name,
                            link = account.homepageUrl,
                            kind = "PROFILE",
                            foundAt = System.currentTimeMillis()
                        )
                    }
                }
            }

            results += AccountCheckResult(
                personId = personId,
                accountId = account.id,
                accountLabel = account.displayTitle,
                platform = account.platform,
                fetched = fetched,
                newCount = newCount,
                baseline = baseline,
                note = note
            )
            onProgress(index + 1, jobs.size)
        }

        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }

        CheckOutcome(results = results, items = items)
    }

    /** 快手分享出来的常常是单条作品链接，那种链接里没有作品总数。 */
    private fun isVideoLink(url: String): Boolean =
        url.contains("/short-video/") || url.contains("/fw/live/")

    private data class KsProfile(
        val count: Int,
        val rendered: Boolean,
        val name: String,
        val bio: String
    )

    /** 打开主页，等渲染，抠「作品 N」/ 昵称 / 简介。 */
    private suspend fun fetchProfile(webView: WebView, url: String): KsProfile? {
        val loaded = CompletableDeferred<Unit>()

        webView.webChromeClient = object : WebChromeClient() {
            // 页面想开新窗口（快手用它跳 App）——一律拒绝
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }

        webView.webViewClient = object : WebViewClient() {
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
        withTimeoutOrNull(PAGE_TIMEOUT_MS) { loaded.await() }
        delay(RENDER_WAIT_MS)

        // 快手主页是懒加载的，读不到就再等一轮
        repeat(4) { attempt ->
            val raw = CompletableDeferred<String?>()
            webView.evaluateJavascript(EXTRACT_JS) { value -> raw.complete(value) }
            val parsed = parse(withTimeoutOrNull(4_000L) { raw.await() })
            if (parsed != null && parsed.count >= 0) return parsed
            if (attempt < 3) delay(3_000L)
        }
        return null
    }

    private const val EXTRACT_JS = """
        (function(){
          try{
            var text = document.body ? document.body.innerText : '';
            var name = '';
            var h = document.querySelector('h1,[class*="userName"],[class*="user-name"],[class*="profile-name"]');
            if (h) name = (h.innerText || '').trim().split('\n')[0].slice(0, 40);
            var bio = '';
            var b = document.querySelector('[class*="userDesc"],[class*="user-desc"],[class*="biography"],[class*="desc"]');
            if (b) bio = (b.innerText || '').trim().slice(0, 200);

            // 「渲染出来了」和「抓到 0 个作品」是两件事，必须分开判：
            // 主页真的存在时，页面上一定有「作品 / 粉丝」这类栏目文字。
            var rendered = text.length > 200 && (text.indexOf('作品') >= 0 || text.indexOf('粉丝') >= 0);

            var count = -1;
            var m = text.match(/作品\s*([0-9]+(?:\.[0-9]+)?)\s*(万)?/);
            if (!m) m = text.match(/([0-9]+(?:\.[0-9]+)?)\s*(万)?\s*作品/);
            if (m) {
              count = Math.round(parseFloat(m[1]) * (m[2] ? 10000 : 1));
              rendered = true;   // 能读到「N 作品」就说明页面一定渲染出来了
            } else if (rendered) {
              // 页面正常但没有「N 作品」的数字 —— 这个号就是一条都没发
              count = 0;
            }
            return JSON.stringify({count: count, rendered: rendered, name: name, bio: bio, url: location.href});
          }catch(e){ return '{}'; }
        })()
    """

    private fun parse(raw: String?): KsProfile? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(inner)
            KsProfile(
                count = o.optInt("count", -1),
                rendered = o.optBoolean("rendered", false),
                name = o.optString("name"),
                bio = o.optString("bio")
            )
        }.getOrNull()
    }
}
