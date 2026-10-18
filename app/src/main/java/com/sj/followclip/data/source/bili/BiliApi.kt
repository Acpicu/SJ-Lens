package com.sj.followclip.data.source.bili

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** 从 B 站拉到的一条作品。 */
data class BiliVideo(
    val bvid: String,
    val title: String,
    val coverUrl: String?,
    val publishedAt: Long,
    val durationMs: Long?,
    val webUrl: String,
    val appSchemeUrl: String
)

/**
 * B 站数据源：原生 HTTP + WBI 签名，零第三方依赖，且**不使用用户账号**。
 *
 * 为什么会被风控，以及这里的应对（都是踩坑换来的）：
 *
 * 1. **每次启动都是"新访客"必被限** —— 以前 buvid3 只放内存，杀进程就换新身份。
 *    现在把整套 Cookie 持久化，同一台设备始终是同一个"老访客"，可信度高得多。
 * 2. **完整 Cookie 比单个 buvid3 有效** —— 真实浏览器会带 buvid3 / buvid4 / b_nut / _uuid 等一整套。
 *    这里先访问一次首页，把服务端下发的 Set-Cookie 全部吸收进来，缺 b_nut 就补一个时间戳。
 * 3. **短时间内反复请求必被限** —— 加了冷却：命中风控后一段时间内直接失败并提示，
 *    不再继续打接口，避免把身份彻底打黑。
 * 4. **缺设备指纹参数会 -352** —— dm_img_* 四个参数必须带。
 *
 * 说明：全程匿名，不涉及任何用户登录态与个人信息。
 */
object BiliSource {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val TIMEOUT_MS = 15_000
    private const val PREF = "bili_session"

    /** 被 B站 拒绝访问时的统一提示。
     *
     * 实测：未登录访问 B站 很容易被拦（412 / -352）。
     * 这是 B站 自己的运维要求，不是本应用的问题，所以直接告诉用户去登录。
     */
    private const val LIMIT_HINT =
        "B站 还没让你访问：它的运维硬性要求是登录后才稳定。" +
            "请到「设置 → 账号与登录」登录 B站 账号。" +
            "部分安全 IP 可能无需登录也能访问；如果你第一次访问就被限制，那就是需要登录了。"
    /** 命中风控后的冷却时长：既保护身份，也避免用户连点。 */
    private const val COOLDOWN_MS = 5 * 60_000L

    private val MIXIN_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cookies: MutableMap<String, String>? = null

    @Volatile
    private var cachedMixinKey: String? = null

    @Volatile
    private var cachedMixinAt: Long = 0L

    /** 由 AppUiState 在启动时调用一次，提供持久化所需的 Context。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 抓取某 UP 主的作品列表。失败时返回带中文说明的异常，界面直接展示。 */
    suspend fun fetchVideos(uid: Long): Result<List<BiliVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureNotCoolingDown()
            val cookie = ensureCookieHeader()
            val mixin = ensureMixinKey(cookie)

            val params = linkedMapOf(
                "mid" to uid.toString(),
                "ps" to "30",
                "pn" to "1",
                "order" to "pubdate",
                "platform" to "web",
                "web_location" to "1550101",
                // B 站前端会额外带这四个设备指纹参数，缺了接口会回 -352
                "dm_img_list" to "[]",
                "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
                "dm_cover_img_str" to "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwICgweDAwMDAzRTlCKSBEaXJlY3QzRDExIHZzXzVfMCBwc181XzApLCBvciBzaW1pbGFy",
                "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}",
                "wts" to (System.currentTimeMillis() / 1000).toString()
            )

            val query = params.entries
                .sortedBy { it.key }
                .joinToString("&") { "${it.key}=${wbiEncode(it.value)}" }
            val wRid = md5(query + mixin)

            val body = httpGet(
                url = "https://api.bilibili.com/x/space/wbi/arc/search?$query&w_rid=$wRid",
                cookie = cookie,
                referer = "https://space.bilibili.com/$uid/video"
            )

            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code == -352 || code == -412) {
                startCooldown()
                throw IllegalStateException(LIMIT_HINT)
            }
            if (code != 0) {
                throw IllegalStateException("B站返回 code=$code：${root.optString("message").ifEmpty { "接口拒绝" }}")
            }

            val vlist = root.getJSONObject("data").getJSONObject("list").getJSONArray("vlist")
            buildList {
                for (i in 0 until vlist.length()) {
                    val o = vlist.getJSONObject(i)
                    val bvid = o.optString("bvid")
                    if (bvid.isEmpty()) continue
                    add(
                        BiliVideo(
                            bvid = bvid,
                            title = o.optString("title"),
                            coverUrl = o.optString("pic").replace("http://", "https://").ifEmpty { null },
                            publishedAt = o.optLong("created") * 1000L,
                            durationMs = parseLength(o.optString("length")),
                            webUrl = "https://www.bilibili.com/video/$bvid",
                            appSchemeUrl = "bilibili://video/$bvid"
                        )
                    )
                }
            }
        }
    }

    /**
     * 从用户输入里取 UID。支持纯数字、space.bilibili.com/123456、以及分享文本里的链接。
     */
    fun extractUid(input: String): Long? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() }) return text.toLongOrNull()
        Regex("""(?:space\.bilibili\.com|bilibili\.com/space|m\.bilibili\.com/space)/(\d+)""")
            .find(text)?.let {
            return it.groupValues[1].toLongOrNull()
        }
        Regex("""(?:^|[^\d])(\d{4,12})(?:[^\d]|$)""").find(text)?.let {
            return it.groupValues[1].toLongOrNull()
        }
        return null
    }

    // ---------- Cookie 管理 ----------

    /** 组装完整 Cookie 头；首次会访问一次首页吸收服务端下发的整套 Cookie。 */
    private fun ensureCookieHeader(): String {
        val ctx = appContext ?: return ""
        val jar = cookies ?: loadCookies(ctx).also { cookies = it }

        if (jar["buvid3"].isNullOrEmpty()) {
            warmUp(ctx, jar)
        }
        if (jar["buvid3"].isNullOrEmpty()) {
            fetchFingerprint(ctx, jar)
        }
        // b_nut 是"首次访问时间"，真实浏览器一定会带；缺了补一个合理的过去时间戳
        if (jar["b_nut"].isNullOrEmpty()) {
            jar["b_nut"] = (System.currentTimeMillis() / 1000 - 86_400L).toString()
        }
        saveCookies(ctx, jar)
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 访问一次首页，把 Set-Cookie 全部吸收。这是"变成老访客"的关键一步。 */
    private fun warmUp(ctx: Context, jar: MutableMap<String, String>) {
        runCatching {
            httpGet(
                url = "https://www.bilibili.com",
                cookie = jar.entries.joinToString("; ") { "${it.key}=${it.value}" },
                referer = "https://www.bilibili.com",
                intoJar = jar
            )
        }
    }

    /** 兜底：指纹接口单独取 buvid3。 */
    private fun fetchFingerprint(ctx: Context, jar: MutableMap<String, String>) {
        runCatching {
            val body = httpGet(
                url = "https://api.bilibili.com/x/frontend/finger/spi",
                cookie = jar.entries.joinToString("; ") { "${it.key}=${it.value}" },
                referer = "https://www.bilibili.com",
                intoJar = jar
            )
            val data = JSONObject(body).optJSONObject("data") ?: return
            data.optString("b_3").takeIf { it.isNotEmpty() }?.let { jar["buvid3"] = it }
            data.optString("b_4").takeIf { it.isNotEmpty() }?.let { jar["buvid4"] = it }
        }
    }

    private fun loadCookies(ctx: Context): MutableMap<String, String> {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString("cookies", null) ?: return mutableMapOf()
        return runCatching {
            val o = JSONObject(raw)
            mutableMapOf<String, String>().apply {
                o.keys().forEach { k -> put(k, o.optString(k)) }
            }
        }.getOrDefault(mutableMapOf())
    }

    private fun saveCookies(ctx: Context, jar: Map<String, String>) {
        runCatching {
            val o = JSONObject()
            jar.forEach { (k, v) -> o.put(k, v) }
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("cookies", o.toString()).apply()
        }
    }

    // ---------- 登录态 ----------

    /**
     * 把 WebView 登录后拿到的整串 Cookie 合并进会话并持久化。
     * 登录后请求会带上 SESSDATA，B 站对已登录用户的频率限制宽容得多。
     */
    fun absorbLoginCookies(rawHeader: String): Boolean {
        val ctx = appContext ?: return false
        val jar = cookies ?: loadCookies(ctx).also { cookies = it }
        rawHeader.split(';').forEach { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (name.isNotEmpty() && value.isNotEmpty()) jar[name] = value
        }
        saveCookies(ctx, jar)
        return jar["SESSDATA"].isNullOrEmpty().not()
    }

    /** 抓取 UP 主的动态（图文 / 转发 / 短内容）。与投稿分开调用。 */
    suspend fun fetchDynamics(uid: Long): Result<List<BiliDynamic>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureNotCoolingDown()
            val cookie = ensureCookieHeader()
            val mixin = ensureMixinKey(cookie)

            val params = linkedMapOf(
                "host_mid" to uid.toString(),
                "features" to "itemOpusStyle",
                "web_location" to "333.1387",
                "dm_img_list" to "[]",
                "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
                "dm_cover_img_str" to "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwICgweDAwMDAzRTlCKSBEaXJlY3QzRDExIHZzXzVfMCBwc181XzApLCBvciBzaW1pbGFy",
                "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}",
                "wts" to (System.currentTimeMillis() / 1000).toString()
            )

            val query = params.entries
                .sortedBy { it.key }
                .joinToString("&") { "${it.key}=${wbiEncode(it.value)}" }
            val wRid = md5(query + mixin)

            val body = httpGet(
                url = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?$query&w_rid=$wRid",
                cookie = cookie,
                referer = "https://space.bilibili.com/$uid/dynamic"
            )

            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code == -352 || code == -412) {
                startCooldown()
                throw IllegalStateException(LIMIT_HINT)
            }
            if (code != 0) {
                throw IllegalStateException("B站动态 code=$code：${root.optString("message").ifEmpty { "接口拒绝" }}")
            }

            val items = root.getJSONObject("data").optJSONArray("items") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val modules = o.optJSONObject("modules") ?: continue
                    val dynamic = modules.optJSONObject("module_dynamic") ?: continue
                    val author = modules.optJSONObject("module_author")

                    val desc = dynamic.optJSONObject("desc")?.optString("text").orEmpty()
                    val major = dynamic.optJSONObject("major")
                    val archive = major?.optJSONObject("archive")
                    // 转发自己视频时正文可能为空，用视频标题兜底，否则这条没内容可显示
                    val text = desc.ifBlank { archive?.optString("title").orEmpty() }
                    if (text.isBlank()) continue

                    val cover = major?.optJSONObject("draw")
                        ?.optJSONArray("items")
                        ?.optJSONObject(0)
                        ?.optString("src")
                        ?: archive?.optString("cover")

                    add(
                        BiliDynamic(
                            id = o.optString("id_str", i.toString()),
                            text = text,
                            coverUrl = cover?.replace("http://", "https://")?.ifEmpty { null },
                            publishedAt = (author?.optLong("pub_ts") ?: 0L) * 1000L
                        )
                    )
                }
            }
        }
    }

    /** 当前是否已登录（本地是否存在 SESSDATA）。 */
    fun isLoggedIn(): Boolean {
        val ctx = appContext ?: return false
        val jar = cookies ?: loadCookies(ctx)
        return !jar["SESSDATA"].isNullOrEmpty()
    }

    /** 退出登录：清掉登录态 Cookie 与冷却，回到匿名访问。 */
    fun logout() {
        val ctx = appContext ?: return
        val jar = cookies ?: loadCookies(ctx).also { cookies = it }
        listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5").forEach { jar.remove(it) }
        saveCookies(ctx, jar)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("cooldown_until").apply()
    }

    /** 清掉冷却（登录成功或用户手动重试时用）。 */
    fun clearCooldown() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove("cooldown_until").apply()
    }
    // ---------- 冷却 ----------

    private fun ensureNotCoolingDown() {
        val ctx = appContext ?: return
        val until = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong("cooldown_until", 0L)
        val left = until - System.currentTimeMillis()
        if (left > 0) {
            val minutes = (left / 60_000L) + 1
            throw IllegalStateException("刚才被 B站 限流了，还要等 $minutes 分钟。这是为了保护不被封，别急")
        }
    }

    private fun startCooldown() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putLong("cooldown_until", System.currentTimeMillis() + COOLDOWN_MS)
            .apply()
    }

    // ---------- 底层请求 ----------

    private fun ensureMixinKey(cookie: String): String {
        val now = System.currentTimeMillis()
        cachedMixinKey?.let { if (now - cachedMixinAt < 10 * 60_000L) return it }

        val body = httpGet("https://api.bilibili.com/x/web-interface/nav", cookie, "https://www.bilibili.com")
        val wbi = JSONObject(body).getJSONObject("data").getJSONObject("wbi_img")
        val imgKey = wbi.getString("img_url").substringAfterLast('/').substringBefore('.')
        val subKey = wbi.getString("sub_url").substringAfterLast('/').substringBefore('.')
        val raw = imgKey + subKey

        val mixin = buildString {
            MIXIN_TAB.forEach { idx -> if (idx < raw.length) append(raw[idx]) }
        }.take(32)

        cachedMixinKey = mixin
        cachedMixinAt = now
        return mixin
    }

    /**
     * 一次 GET。带齐浏览器头与 Cookie；
     * 传入 intoJar 时顺手把响应的 Set-Cookie 吸收进去。
     */
    private fun httpGet(
        url: String,
        cookie: String,
        referer: String,
        intoJar: MutableMap<String, String>? = null
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            conn.setRequestProperty("Origin", "https://space.bilibili.com")
            conn.setRequestProperty("Referer", referer)
            if (cookie.isNotEmpty()) conn.setRequestProperty("Cookie", cookie)

            val code = conn.responseCode

            if (intoJar != null) {
                conn.headerFields.forEach { (key, values) ->
                    if (key != null && key.equals("Set-Cookie", ignoreCase = true)) {
                        values?.forEach { rawSetCookie ->
                            val pair = rawSetCookie.substringBefore(';')
                            val name = pair.substringBefore('=').trim()
                            val value = pair.substringAfter('=', "").trim()
                            if (name.isNotEmpty() && value.isNotEmpty()) intoJar[name] = value
                        }
                    }
                }
            }

            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code == 412) {
                startCooldown()
                throw IllegalStateException("B站 风控拦截（412），已冷却 5 分钟，稍后再试")
            }
            if (code !in 200..299) throw IllegalStateException("网络请求失败（HTTP $code）")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** WBI 规定：先去掉 !'()* 这五个半角符号，再做 URL 编码。 */
    private fun wbiEncode(value: String): String {
        val filtered = value.filterNot { it in "!'()*" }
        return URLEncoder.encode(filtered, "UTF-8").replace("+", "%20")
    }

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** "12:34" 或 "1:02:03" → 毫秒；解析不了返回 null。 */
    private fun parseLength(length: String): Long? {
        if (length.isBlank()) return null
        val parts = length.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return null
        val seconds = when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> parts[0]
        }
        return seconds * 1000L
    }
}
