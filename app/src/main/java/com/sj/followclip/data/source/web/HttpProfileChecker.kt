package com.sj.followclip.data.source.web

import com.sj.followclip.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一个账号在平台上**匿名 HTTP 就能拿到**的公开快照。
 *
 * 只有确认过不需要签名、不需要登录的字段才放进这里 —— 宁可少，不可假。
 */
data class ProfileSnapshot(
    /** 作品/投稿总数：更新检测的核心信号（数字变大就是发了新东西） */
    val workCount: Int? = null,
    val nickname: String = "",
    val signature: String = "",
    val followerCount: Long? = null,
    /** 短链解析后的规范主页地址 */
    val canonicalUrl: String = "",
    val avatarUrl: String? = null
)

/**
 * 纯 HTTP 的主页探测。**不开 WebView，因此后台任务里也能跑。**
 *
 * 现状（2026-10 实测）：
 * - 抖音：短链 302 到 `iesdouyin.com/share/user/{sec_uid}`，
 *   `web/api/v2/user/info?sec_uid=` 未签名即可返回 `aweme_count` / 昵称 / 简介 / 粉丝数 —— 可用。
 * - 快手：`rest/wd/user/profile` 一律返回 `result:2`（要签名），
 *   `www.kuaishou.com` 的 profile 页给的是验证页 —— 纯 HTTP 拿不到，只能走真实渲染的 WebView。
 */
object HttpProfileChecker {

    private const val TIMEOUT_MS = 12_000
    private const val UA_MOBILE =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    /** 这个平台能不能纯 HTTP 查。快手目前不行。 */
    fun supportsHttp(platform: Platform): Boolean = platform == Platform.DOUYIN

    suspend fun fetch(platform: Platform, homepageUrl: String): Result<ProfileSnapshot> {
        if (!supportsHttp(platform)) {
            return Result.failure(IllegalStateException("这个平台需要真实页面，只能在应用内检查"))
        }
        // 网络必须挪到 IO 线程：主线程直接发请求会被 NetworkOnMainThreadException 掐掉，
        // 而 runCatching 会把它吞成「找不到 sec_uid」这种误导性提示。
        return withContext(Dispatchers.IO) {
            when (platform) {
                Platform.DOUYIN -> fetchDouyin(homepageUrl)
                else -> Result.failure(IllegalStateException("暂不支持纯 HTTP 检查"))
            }
        }
    }

    // ---------- 抖音 ----------

    private fun fetchDouyin(homepageUrl: String): Result<ProfileSnapshot> = runCatching {
        val secUid = secUidOf(homepageUrl)
            ?: throw IllegalStateException("链接里找不到 sec_uid，重新粘一次分享链接试试")

        val body = httpGet("https://www.iesdouyin.com/web/api/v2/user/info/?sec_uid=$secUid")
        val root = JSONObject(body)
        val code = root.optInt("status_code", -1)
        if (code != 0) {
            throw IllegalStateException("抖音返回 status_code=$code（可能被限流或需要登录）")
        }
        val info = root.optJSONObject("user_info")
            ?: throw IllegalStateException("抖音没返回 user_info")
        ProfileSnapshot(
            workCount = info.optInt("aweme_count", -1).takeIf { it >= 0 },
            nickname = info.optString("nickname"),
            signature = info.optString("signature"),
            followerCount = info.optLong("mplatform_followers_count", -1L)
                .takeIf { it >= 0 }
                ?: info.optLong("follower_count", -1L).takeIf { it >= 0 },
            canonicalUrl = "https://www.douyin.com/user/$secUid",
            avatarUrl = info.optJSONObject("avatar_thumb")
                ?.optJSONArray("url_list")
                ?.optString(0)
                ?.replace("http://", "https://")
                ?.ifEmpty { null }
        )
    }

    /**
     * 从各种形态的链接里取 sec_uid：
     * `v.douyin.com/xxx` 短链走一次 HEAD 拿 302 的 Location，
     * 长链直接正则抠。
     */
    private fun secUidOf(raw: String): String? {
        val direct = SEC_UID_REGEX.find(raw)?.groupValues?.get(1)
        if (!direct.isNullOrEmpty()) return direct

        val landing = followLanding(raw) ?: return null
        SEC_UID_REGEX.find(landing.first)?.groupValues?.get(1)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return SEC_UID_REGEX.find(landing.second)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    private val SEC_UID_REGEX = Regex("""(?:sec_uid=|/user/)(MS4wLjAB[A-Za-z0-9_-]{10,})""")

    /** 跟随重定向，返回（最终地址, 正文前段）。短链必须走这一步才拿得到 sec_uid。 */
    private fun followLanding(url: String): Pair<String, String>? = runCatching {
        val conn = (URL(url.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA_MOBILE)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { reader ->
                val buffer = CharArray(200_000)
                val read = reader.read(buffer)
                if (read > 0) String(buffer, 0, read) else ""
            }.orEmpty()
            conn.url.toString() to body
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA_MOBILE)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Referer", "https://www.iesdouyin.com/")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("抖音请求失败（HTTP $code）")
            if (body.isBlank()) throw IllegalStateException("抖音返回空内容（可能被限流）")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
