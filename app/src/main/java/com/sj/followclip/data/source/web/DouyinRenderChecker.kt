package com.sj.followclip.data.source.web

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.VideoSnapshotStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener

/**
 * 抖音「具体哪几条新作品」：渲染主页，从 DOM 里抽 `/video/{id}` 链接。
 *
 * 为什么绕开接口：`aweme/post` 要 `a_bogus` 签名（页面 JS 环境算出来的），
 * 客户端重写算法维护成本极高。而主页 DOM 里本来就摆着作品卡的链接，
 * 渲染一次即可拿到 id + 封面，**不需要任何签名**。
 *
 * 分工（重要，避免把真计数冲掉）：
 * - 作品总数、昵称、简介 → 仍由 [HttpProfileChecker] 的接口给（准、可后台）
 * - 具体作品 id → 这条渲染路径给（仅前台，用于"哪几条是新的"）
 */
object DouyinRenderChecker {

    private const val FIRST_WAIT_MS = 3_000L
    private const val ROUNDS = 5
    private const val GAP_MS = 2_000L

    /** 渲染一次主页，返回发现的 (作品id → 封面) 列表；失败返回空。 */
    suspend fun fetchVideos(activity: Activity, url: String): Map<String, String> =
        withContext(Dispatchers.Main) {
            val webView = WebView(activity).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.blockNetworkImage = true
                settings.userAgentString = (settings.userAgentString ?: "")
                    .replace("; wv", "")
                    .replace(" Version/4.0", "")
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean = false   // 抖音页面会试图调起自己的 App，一律拒绝
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val u = request?.url?.toString().orEmpty()
                        return !(u.startsWith("http://") || u.startsWith("https://"))
                    }

                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, u: String?): Boolean {
                        val s = u.orEmpty()
                        return !(s.startsWith("http://") || s.startsWith("https://"))
                    }
                }
            }

            val host = activity.findViewById<ViewGroup>(android.R.id.content)
            runCatching { host?.addView(webView, 0, ViewGroup.LayoutParams(-1, -1)) }
            runCatching {
                webView.alpha = 0.015f
                webView.scaleX = 0.03f
                webView.scaleY = 0.03f
                webView.translationX = 10_000f
            }

            try {
                webView.loadUrl(url)
                delay(FIRST_WAIT_MS)

                var best = emptyMap<String, String>()
                repeat(ROUNDS) {
                    val raw = CompletableDeferred<String?>()
                    webView.evaluateJavascript(EXTRACT_JS) { v -> raw.complete(v) }
                    val parsed = parse(withTimeoutOrNull(4_000L) { raw.await() })
                    if (parsed.size > best.size) best = parsed
                    delay(GAP_MS)
                }
                best
            } finally {
                runCatching {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.stopLoading()
                    webView.clearCache(true)
                    webView.destroy()
                }
            }
        }

    /**
     * 抽作品卡 → 与快照比对 → 返回「新作品」条目。
     * 首次（快照为空）只建基线，不报新。
     */
    fun newItems(
        activity: Activity,
        accountId: Long,
        personId: Long,
        personName: String,
        platform: String,
        videos: Map<String, String>
    ): List<PendingItem> {
        if (videos.isEmpty()) return emptyList()
        val known = VideoSnapshotStore.load(activity, accountId, "dy")
        val fresh = if (known.isEmpty()) emptyList() else videos.keys.filter { it !in known }
        VideoSnapshotStore.merge(activity, accountId, videos.keys.toList(), "dy")
        return fresh.map { id ->
            PendingItem(
                bvid = id,
                title = "新作品 · 视频 $id",
                personId = personId,
                personName = personName,
                platform = platform,
                coverUrl = videos[id],
                link = "https://www.douyin.com/video/$id",
                kind = "VIDEO",
                foundAt = System.currentTimeMillis()
            )
        }
    }

    private const val EXTRACT_JS = """
        (function(){
          try{
            var out = [], seen = {};
            var nodes = document.querySelectorAll('a[href*="/video/"]');
            for (var i = 0; i < nodes.length && out.length < 40; i++) {
              var a = nodes[i];
              var m = (a.getAttribute('href') || '').match(/\/video\/([0-9]{6,})/);
              if (!m) continue;
              var id = m[1];
              if (seen[id]) continue;
              seen[id] = 1;
              var card = a.closest('li') || a.parentElement || a;
              var img = card.querySelector ? card.querySelector('img') : null;
              var cover = img ? (img.getAttribute('src') || '') : '';
              out.push({ id: id, cover: cover });
            }
            return JSON.stringify(out);
          }catch(e){ return '[]'; }
        })()
    """

    private fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyMap()
        return runCatching {
            val inner = JSONTokener(raw).nextValue() as? String ?: return emptyMap()
            val arr = JSONArray(inner)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isNotBlank()) {
                        put(id, o.optString("cover").replace("http://", "https://"))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
}
