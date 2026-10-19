package com.sj.followclip.ui.guide

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

/**
 * 首次启动的引导。
 *
 * 只讲四件新人一定会踩的事，不写操作步骤（那属于教程，已按定位删除）：
 * 1. 基线机制——第一轮只记状态，第二轮起才报变化
 * 2. 三个平台的差别——抖音后台能查、快手后台走 HTTP、具体条目要前台
 * 3. 数据本是干什么的
 * 4. 通知权限 + 检查可以退出应用
 */
private data class GuideBlock(val title: String, val body: String)

private val BLOCKS = listOf(
    GuideBlock(
        "① 更新判定靠基线",
        "第一次检查只把目标当前状态记下来（作品数 / 昵称 / 简介），本轮不会报更新；" +
            "从第二轮起每次跟基线比对，变了才出现在「最近检查」里。所以第一轮看到" +
            "「都是见过的」是正常的。"
    ),
    GuideBlock(
        "② 三个平台查法不同",
        "B站：公开接口，作品与动态逐条。\n" +
            "抖音：作品数 / 改名 / 改简介前台后台都能查；具体是哪几条新作品要应用在前台时补抓。\n" +
            "快手：前台用真实渲染，后台走分享页 HTTP 直取作品数。"
    ),
    GuideBlock(
        "③ 检查可以退出应用",
        "点「检查」后会挂一条常驻通知跑完，期间可以退出应用做别的事；" +
            "跑完会弹一条「检查报告已生成」的横幅，点它回到应用看明细。横幅上可以直接取消。"
    ),
    GuideBlock(
        "④ 数据本是交档用的",
        "一条记录 = 一条可陈述事实，强制带出处、信源评级（Admiralty A1–F6）、置信度与状态；" +
            "同一字段出现两个不同值会被点名为「冲突」，六类关键事实缺失会列出「缺口」，" +
            "最后可导出带时间线的 Markdown 简报。"
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

@Composable
private fun GuideContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
    ) {
        BLOCKS.forEach { block ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Spacing.cardCorner),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(Spacing.tinyGap))
                    Text(
                        text = block.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** 首次启动弹一次。只弹一次，之后从设置里随时能再打开。 */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始之前，四件事") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GuideContent()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

/** 设置 →「使用引导」：同一份内容，随时可看。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("使用引导") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                top = Spacing.gridGap,
                bottom = Spacing.sectionGap
            )
        ) {
            item {
                Text(
                    text = "这四条看懂，就不会把「第一轮不报更新」当成故障。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.padding(bottom = Spacing.gridGap)
                )
            }
            item { GuideContent() }
        }
    }
}
