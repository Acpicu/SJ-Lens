package com.sj.followclip.util

/**
 * 平台地址归一化。
 *
 * 踩过的坑：从抖音「分享」里复制出来的不是网页链接，而是 App 深链
 * （`snssdk1128://user/profile?...`）。WebView 加载它只会报
 * `ERR_UNKNOWN_URL_SCHEME`，页面一片空白 —— 而界面只会显示「没解析到作品」，
 * 让人误以为是解析规则的问题，实际上根本没加载任何网页。
 *
 * 这里把深链尽量还原成网页地址；还原不出来就返回 null，
 * 由界面明确告诉用户「这个地址网页打不开」，而不是继续装作在抓取。
 */
object PlatformUrls {

    /** 判断是不是 http(s) 地址。 */
    fun isWebUrl(raw: String): Boolean {
        val t = raw.trim()
        return t.startsWith("http://") || t.startsWith("https://")
    }

    /**
     * 把用户填的地址转成 WebView 能打开的地址。
     *
     * 支持：
     * - 正常网页链接：原样返回
     * - 抖音深链：从查询参数里抠 sec_uid / sec_user_id，拼回主页地址
     *
     * 转不出来返回 null（调用方负责提示用户重新填）。
     */
    fun toWebUrl(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (isWebUrl(text)) return text

        // snssdk1128://user/profile?sec_uid=MS4wLjAB... → 网页主页
        Regex("""sec_uid=([A-Za-z0-9_\-]+)""").find(text)?.let {
            return "https://www.douyin.com/user/${it.groupValues[1]}"
        }
        Regex("""sec_user_id=([A-Za-z0-9_\-]+)""").find(text)?.let {
            return "https://www.douyin.com/user/${it.groupValues[1]}"
        }
        // 快手深链里一般带 userId / photoId
        Regex("""userId=([A-Za-z0-9_\-]+)""").find(text)?.let {
            return "https://www.kuaishou.com/profile/${it.groupValues[1]}"
        }

        return null
    }
}
