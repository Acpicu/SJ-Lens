package com.sj.followclip.ui.guide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted
import kotlinx.coroutines.launch

/**
 * 首次启动的引导：逐页滑动的独立页面，看完即止。
 *
 * 只讲四件新人必踩的事，外加一页通知设置；顶部「n / 总页数」表示进度。
 * 不写操作步骤（教程已按定位删除）。
 */
private data class Page(val title: String, val lines: List<String>)

private val PAGES = listOf(
    Page(
        "更新判定靠基线",
        listOf(
            "第一次检查只记下目标当前状态（作品数 / 昵称 / 简介），这一轮不会报更新。",
            "从第二轮起每次跟基线比对，变了才出现在「最近检查」里。",
            "所以第一轮看到「都是见过的」是正常的。"
        )
    ),
    Page(
        "三个平台查法不同",
        listOf(
            "B站：公开接口，作品与动态逐条可点。",
            "抖音：作品数 / 改名 / 改简介前台后台都能查，具体哪几条要前台补抓。",
            "快手：前台真实渲染，后台走分享页 HTTP 直取作品数。"
        )
    ),
    Page(
        "检查可以退出应用",
        listOf(
            "点「检查」后由前台服务执行，可以退出应用做别的事。",
            "跑完弹「检查报告已生成」横幅，点它回到应用看明细。",
            "横幅上可以直接取消这一轮。"
        )
    ),
    Page(
        "数据本是交档用的",
        listOf(
            "一条记录 = 一条可陈述事实，强制带出处、信源评级、置信度与状态。",
            "同一字段出现两个不同值会被点名「冲突」，六类关键事实缺失会列「缺口」。",
            "可导出 Markdown 与单页 HTML 简报，含时间线。"
        )
    )
)

private const val PREF = "first_run"
private const val KEY_ONBOARDING = "onboarding_shown"

fun shouldShowOnboarding(context: Context): Boolean =
    !context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDING, false)

fun markOnboardingShown(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_ONBOARDING, true).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageCount = PAGES.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val current = pagerState.currentPage

    var notifyGranted by remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifyGranted = granted
        android.widget.Toast.makeText(
            context,
            if (granted) "通知已开启" else "没开启通知，检查完了不会有横幅提醒",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("使用引导") },
                actions = {
                    Text(
                        text = "${current + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.textMuted,
                        modifier = Modifier.padding(end = Spacing.pageHorizontal)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.pageHorizontal)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                if (index < PAGES.size) {
                    val page = PAGES[index]
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(Spacing.sectionGap))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.gridGap))
                        page.lines.forEach { line ->
                            Row(modifier = Modifier.padding(vertical = Spacing.tinyGap)) {
                                Text(
                                    text = "· ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    NotificationPage(
                        granted = notifyGranted,
                        onOpenSystemSettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onAllow = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onBatterySettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.gridGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (current > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(current - 1) }
                    }) { Text("上一页") }
                }
                Spacer(Modifier.weight(1f))
                if (current < pageCount - 1) {
                    Button(onClick = {
                        scope.launch { pagerState.animateScrollToPage(current + 1) }
                    }) { Text("下一页") }
                } else {
                    Button(onClick = onBack) { Text("开始使用") }
                }
            }
        }
    }
}

@Composable
private fun NotificationPage(
    granted: Boolean,
    onAllow: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBatterySettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(Spacing.sectionGap))
        Text(
            text = "让通知更快抵达",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(Spacing.gridGap))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Spacing.cardCorner),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                Text(
                    text = "允许通知：检查跑完要弹横幅告诉你结果。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.tinyGap))
                Text(
                    text = "关闭省电限制：系统省电策略会掐掉后台检查，允许后台运行才稳定。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(Spacing.gridGap))
        Text(
            text = if (granted) "通知：已开启（系统里可随时关）" else "通知：未开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(Spacing.gridGap))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (granted) {
                // 已授权时系统不会再弹窗，点了没反应最容易被当成"点不动"——直接给系统设置入口
                Button(onClick = onOpenSystemSettings) { Text("通知设置") }
            } else {
                Button(onClick = onAllow) { Text("允许通知") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBatterySettings) { Text("省电设置") }
        }
        Spacer(Modifier.height(Spacing.tinyGap))
        Text(
            text = "两项都可以以后在系统设置里改。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.textMuted
        )
    }
}
