package com.sj.followclip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sj.followclip.ui.AppNavigation
import com.sj.followclip.ui.theme.SJFollowClipTheme

/**
 * 应用唯一 Activity（规格第三章：单 Activity + Navigation Compose）。
 *
 * 页面切换、底部导航与悬浮按钮都在 [AppNavigation] 内部完成，
 * 这里只负责铺主题与一层底色 Surface。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SJFollowClipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
