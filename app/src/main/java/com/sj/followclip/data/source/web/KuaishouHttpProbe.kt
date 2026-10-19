package com.sj.followclip.data.source.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 快手「后台也能查」的简单思路。
 *
 * 绕开 `__NS_sig3`：快手的**分享落地页**（`v.kuaishou.com/xxx` 302 之后那个
 * `/fw/user/{id}` 页面）是服务端渲染的，HTML 里直接带着「作品 42」这样的文案。
 * 实测：HTTP GET 该页面，正则 `作品\s*(\d+)` 就能拿到作品总数，
 * 和 WebView 渲染出来的数字完全一致。
 *
 * 所以后台（没有窗口、无法渲染）时走这条路；前台仍然优先用渲染路径，
 * 因为渲染还能顺手拿到昵称与简介。
 */
object KuaishouHttpProbe {

    private const val TIMEOUT_MS = 9_000
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    private val COUNT_PATTERNS = listOf(
        Regex("""作品\s*(\d+)"""),
        Regex("""(\d+)\s*个作品"""),
        Regex("""作品[^0-9]{0,20}(\d+)""")
    )

    /** 返回作品总数；拿不到返回 null（调用方据此保持原基线，不误判为 0）。 */
    suspend fun count(homepageUrl: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(homepageUrl.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val html = conn.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(400_000)
                    val read = reader.read(buffer)
                    if (read > 0) String(buffer, 0, read) else ""
                }
                if (html.isBlank()) return@runCatching null
                COUNT_PATTERNS.firstNotNullOfOrNull { pattern ->
                    pattern.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
