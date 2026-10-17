package com.sj.followclip.ui.detail

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.sj.followclip.data.source.bili.BiliSource
import com.sj.followclip.ui.theme.textMuted
import kotlinx.coroutines.delay

/**
 * B 站登录：用 WebView 打开官方登录页，用户扫码或输入账号即可。
 *
 * 为什么要登录：匿名访问时 B 站 对 API 的频率限制很严，翻两下就 412；
 * 带上登录态的 SESSDATA 之后，正常浏览的请求会被当成真实用户。
 *
 * 安全说明（也是给用户的承诺）：
 * - 账号密码只输入到 B 站 自己的页面里，应用不接触、不记录；
 * - 拿到的 Cookie 只存本机，不上传任何地方；
 * - 提供「退出登录」随时清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BiliLoginScreen(
    onBack: () -> Unit,
    onLoginDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }

    // 轮询 Cookie：出现 SESSDATA 就说明登录成功
    LaunchedEffect(Unit) {
        while (done == 0) {
            delay(1500)
            val cookie = runCatching {
                CookieManager.getInstance().getCookie("https://www.bilibili.com")
            }.getOrNull().orEmpty()
            if (cookie.contains("SESSDATA=")) {
                if (BiliSource.absorbLoginCookies(cookie)) {
                    BiliSource.clearCooldown()
                    done = 1
                    onLoginDone()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("登录 B站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 退出登录：清掉本机保存的登录态，回到匿名访问
                    TextButton(
                        onClick = {
                            BiliSource.logout()
                            onBack()
                        }
                    ) {
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = "登录只为提高接口频率上限。密码只输入到 B 站 自己页面，应用不接触。\nCookie 只存本机，可随时退出登录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textMuted,
                modifier = Modifier.padding(
                    horizontal = com.sj.followclip.ui.theme.Spacing.pageHorizontal,
                    vertical = com.sj.followclip.ui.theme.Spacing.itemGap
                )
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                url: String?
                            ): Boolean = false
                        }
                        // 登录页用桌面 UA，扫码登录界面更完整
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        loadUrl("https://passport.bilibili.com/login")
                    }
                }
            )
        }
    }
}
