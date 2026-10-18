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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

private const val PREF = "first_run"
private const val KEY_USAGE_SHOWN = "usage_guide_shown"

/** 是否还需要弹一次「怎么用」引导（只弹一次）。 */
fun shouldShowUsageGuide(context: Context): Boolean =
    !context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_USAGE_SHOWN, false)

fun markUsageGuideShown(context: Context) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_USAGE_SHOWN, true).apply()
}

private data class GuideStep(
    val index: String,
    val title: String,
    val taps: String,
    val note: String = ""
)

private val STEPS = listOf(
    GuideStep(
        index = "1",
        title = "建一个文件夹",
        taps = "首页右下角 + → 起个名字 → 完成",
        note = "一个文件夹 = 一个人。名字自己起，之后平台改名也不影响。"
    ),
    GuideStep(
        index = "2",
        title = "把平台主页加进去",
        taps = "点进文件夹 → 右下角 + → 选平台 → 粘主页链接或 UID → 添加",
        note = "一个人可以同时有 B站 / 抖音 / 快手，也可以同平台挂两个号。B站建议先去「设置 → 账号与登录」登录，否则容易被限流。"
    ),
    GuideStep(
        index = "3",
        title = "看有没有更新",
        taps = "首页点「检查」，或进账号页点「检查更新」→ 底部「最近检查」看这轮结果",
        note = "B站支持后台自动查（每 60 分钟）；抖音快手要真实页面才能读，只能手动。"
    ),
    GuideStep(
        index = "4",
        title = "看内容统计",
        taps = "账号页顶部「内容统计」卡片",
        note = "发布间隔、集中时段、最活跃星期。样本少于 5 条会直接提示「仅供参考」，不要当结论。"
    ),
    GuideStep(
        index = "5",
        title = "写笔记",
        taps = "文件夹内「笔记」卡 → 写 → 点「保存」",
        note = "不点保存不落盘。这就是你自己的判断区。"
    ),
    GuideStep(
        index = "6",
        title = "登记证据",
        taps = "文件夹内「证据条目」→ 填来源 / 等级 / 备注 →「附截图」可选 → 「登记」",
        note = "自动编号 E-001、E-002，只增不改。信源等级按手册写，如 B2。"
    ),
    GuideStep(
        index = "7",
        title = "写待验证清单",
        taps = "证据页下半部分 → 写「要验证什么」+「查什么能证实/推翻」→「添加」",
        note = "之后可以标「已证实 / 已推翻 / 重置」。"
    ),
    GuideStep(
        index = "8",
        title = "导出报告",
        taps = "证据页右上「导出」→ 选保存位置",
        note = "得到四段式 Markdown：结论 / 证据 / 置信度 / 待验证。置信度要你自己填。"
    ),
    GuideStep(
        index = "9",
        title = "查自己的暴露面（可选）",
        taps = "设置 → OSINT → 自身曝光审计",
        note = "填你自己在各平台的资料，看关联度分数和关联图。只在内存里算，退出即丢。"
    )
)

/**
 * 「怎么用」页：把操作路径按顺序列出来，全部是界面上真能点到的位置。
 *
 * 和「现场手册」（方法论）分开：这份讲在这里点哪里，那份讲判断该怎么做。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("怎么用") }
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
                bottom = Spacing.sectionGap
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.gridGap)
        ) {
            item {
                Text(
                    text = "按顺序做一遍就会了。每一步都是界面上真实存在的按钮位置。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(STEPS.size) { index ->
                val step = STEPS[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = step.index,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(Spacing.itemGap))
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(Spacing.itemGap))
                        Text(
                            text = step.taps,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (step.note.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.tinyGap))
                            Text(
                                text = step.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textMuted
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "看不清的时候：设置 → OSINT → 现场手册（方法论，判断怎么做）；" +
                        "这份讲操作。两份都在应用里，不联网。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}

/**
 * 首次启动的「怎么用」提示。只弹一次，之后从设置里随时能再打开。
 */
@Composable
fun UsageGuideDialog(
    onDismiss: () -> Unit,
    onOpenGuide: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("三步就能用起来") },
        text = {
            Column {
                Text(
                    text = "1. 首页右下角 + 建一个文件夹\n" +
                        "2. 进文件夹，右下角 + 加平台主页\n" +
                        "3. 回首页点「检查」，看谁更新了",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Spacing.gridGap))
                Text(
                    text = "想记结论和证据：文件夹里的「笔记」「证据条目」。" +
                        "完整步骤在「设置 → 怎么用」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted,
                    overflow = TextOverflow.Clip
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenGuide) { Text("看完整步骤") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
