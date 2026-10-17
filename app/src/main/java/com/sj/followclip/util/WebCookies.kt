package com.sj.followclip.util

import android.webkit.CookieManager

/**
 * 平台网页登录态的读写。
 *
 * 抖音和快手没有开放接口，登录只能在我们自己的 WebView 里完成，
 * 登录态就是 CookieManager 里的那堆 cookie。
 * 注意：这是**应用内**的登录，和手机上装的抖音 App 是两套身份，互不影响。
 */
object WebCookies {

    private const val DOUYIN = "https://www.douyin.com"
    private const val KUAISHOU = "https://www.kuaishou.com"

    fun douyinLoggedIn(): Boolean = hasCookie(DOUYIN)

    fun kuaishouLoggedIn(): Boolean = hasCookie(KUAISHOU)

    private fun hasCookie(url: String): Boolean {
        val cookie = CookieManager.getInstance().getCookie(url)
        return !cookie.isNullOrBlank()
    }

    /** 清掉所有平台的网页登录态（B站的 Cookie 单独由 BiliSource 管理）。 */
    fun clearAll(onDone: () -> Unit = {}) {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies {
            manager.flush()
            onDone()
        }
    }
}
