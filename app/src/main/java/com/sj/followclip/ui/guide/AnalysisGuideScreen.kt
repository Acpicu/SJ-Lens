package com.sj.followclip.ui.guide

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.ui.theme.Spacing
import com.sj.followclip.ui.theme.textMuted

/**
 * 「怎么分析」页：把四份数据的分工、能得出什么、可信度、以及一个完整例子写死在应用里。
 *
 * 这一页只讲判断方法，不涉及身份推断 —— 结论永远由人来写。
 */
private data class Block(val title: String, val lines: List<String>)

private val ANALYSIS_BLOCKS = listOf(
    Block(
        title = "① 四份数据各管什么",
        lines = listOf(
            "平台更新记录 → 回答「有没有发新东西」。靠快照比对，B站可靠，抖音快手靠作品计数，会差 1–2 条",
            "内容统计 → 回答「多久发一次、什么时间发」。中位间隔 + 3 小时窗口峰值 + 一周分布",
            "速记 → 记「机器看不出来的事」：改名、删内容、异常时间。一句话，随手记",
            "证据条目 → 只有要进报告的那几条才升级成证据，补来源、SHA-256、信源等级",
            "待验证清单 → 放还没结论的问题，每条写清「查什么就能证实/推翻」"
        )
    ),
    Block(
        title = "② 速记和证据是什么关系",
        lines = listOf(
            "先记速记：一句话，不填来源、不算哈希，三秒一条",
            "要用到它了，再去证据条目登记：来源填链接或页面标题，等级按 Admiralty 写（如 B2），需要留图就「附截图」，哈希会自动算",
            "速记是草稿，证据是引用。草稿可以删，引用删了编号不复用",
            "判断标准：这条会不会出现在报告里？会 → 登记证据；不会 → 留着速记就够"
        )
    ),
    Block(
        title = "③ 用平台数据能得出什么（附可信度）",
        lines = listOf(
            "有没有发新内容 → 快照比对 → 高（B站）/ 中（抖音快手靠计数）",
            "多久发一次 → 相邻间隔中位数 → 中，样本要 ≥5 条",
            "一般在几点发 → 连续 3 小时窗口峰值 → 中",
            "是不是批量发 → 1 小时内连发条数 → 高",
            "有没有停更 → 上次抓取时间 vs 最近作品时间 → 中",
            "两个平台是否同步发 → 同时间段对照 → 低（只能到分钟级，且抓取有时间差）",
            "改过名吗 → 平台不保留历史，只能靠你的速记 → 取决于你记没记"
        )
    ),
    Block(
        title = "④ 笔记本身不「分析」，它的用法是这三条",
        lines = listOf(
            "按类型数：这个月记了几条「删除」？删除变密本身就是信号",
            "按时间排：把速记按日期连起来，找变化点——哪天开始不发、哪天开始凌晨发",
            "找矛盾：速记写着「发了」但平台计数没变，说明是非公开可见、已删除，或者你看错了号",
            "软件只提供排序、类型筛选、时间戳。结论由你自己写进「结论」框"
        )
    ),
    Block(
        title = "⑤ 一个完整例子：从 0 到报告",
        lines = listOf(
            "第 1 天：加账号 → 点检查 → 统计卡显示中位间隔 3.2 天，集中在 20:00–23:00",
            "第 2 天：速记「10/19 发了 1 条，晚上 9 点」",
            "第 5 天：速记「改名：原名 A，现名 B」→ 打开证据条目登记 E-001，来源填主页链接，等级 B2，附截图",
            "第 9 天：速记「9 月底那条没了」→ 待验证加一条 T-01：查 9/30 那条是否还在（在=我看错，不在=确实删了）",
            "第 10 天：结论写「10 月改名并删旧内容，发布间隔从 3 天拉长到 10 天」→ 证据页「预览」核对 → 导出 .md",
            "全程软件只做了四件事：抓字段、算间隔、排时间、拼报告。其余都是你写的"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisGuideScreen(
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
                title = { Text("怎么分析") }
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
                    text = "这一页回答三个问题：四份数据各管什么、能得出什么结论、笔记怎么用。" +
                        "每一条都标了可信度，别把低可信度当结论。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(ANALYSIS_BLOCKS.size) { index ->
                val block = ANALYSIS_BLOCKS[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Text(
                            text = block.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        block.lines.forEach { line ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "· ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(0.dp))
                    Text(
                        text = "配套：设置 → OSINT →「怎么用」讲操作、「记录了什么」讲字段、「现场手册」讲纪律。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textMuted
                    )
                }
            }
        }
    }
}
