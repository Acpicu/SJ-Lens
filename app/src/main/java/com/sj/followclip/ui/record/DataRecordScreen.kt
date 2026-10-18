package com.sj.followclip.ui.record

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
 * 「记录了什么」页：把数据清单和使用场景写进应用里，不用翻文档。
 *
 * 三段：一条新作品会记下什么 / 从这些记录能看出什么 / 数据存在哪怎么删。
 */
private data class Block(val title: String, val lines: List<String>)

private val RECORD_BLOCKS = listOf(
    Block(
        title = "① 一条新作品发出来时，应用记下什么",
        lines = listOf(
            "抓取到的字段（B站）：作品 ID（bvid）、标题、封面图地址、发布时间戳、时长、网页链接、App 深链",
            "抓取到的字段（抖音/快手）：封面图地址、页面里的作品总数（aweme_count）——作品 ID 在页面 DOM 里没有",
            "比对结果：和你上次存的快照对一遍，得出「新增 N 条」",
            "回写账号：未读数、上次抓取时间、粉丝数、作品数（抓到才有）",
            "更新快照：每个账号保留最近 60 条作品 ID，动态另存一份",
            "汇总到「最近检查」：本轮每个文件夹命中了什么，每轮整体覆盖",
            "发通知（有通知权限时）：只在你开了后台检查时出现",
            "你手工补充的：证据编号 E-001 起、来源、采集时间、SHA-256、信源等级、备注、截图"
        )
    ),
    Block(
        title = "② 从这些记录能看出什么",
        lines = listOf(
            "发布节奏：相邻两次发布间隔的中位数与平均值，判断是日更、周更还是随缘发",
            "活跃时段：连续 3 小时窗口里发得最集中的那一段，通常是这个人真正空闲的时间",
            "活跃星期：一周 7 天的分布，能看出周末发还是工作日发",
            "是否批量发布：1 小时内连发多条时会给提示，避免把一次批量发布当成多次更新",
            "更新骤停：上次抓取时间与最近一条作品时间的落差，长期不动就是停更信号",
            "数据可信度：样本条数、时间跨度、有标题占比，样本不足会直接标「仅供参考」",
            "跨平台对照：同一个人两个平台都有号时，可以看两边是否在同一时段一起更新（只能到分钟级近似）",
            "变化留痕：快照只保留最近 60 条，更早的对比要靠你自己登记证据条目"
        )
    ),
    Block(
        title = "③ 数据存在哪、怎么删",
        lines = listOf(
            "文件夹与账号 → 应用私有目录 JSON；设置 →「清除全部本地数据」",
            "抓取快照 → SharedPreferences「video_snapshots」；同上",
            "最近检查结果 → SharedPreferences；设置 →「最近检查结果 → 清空」",
            "笔记 → SharedPreferences「sj_notes」；文件夹内「笔记 → 清空」",
            "证据与待验证 → SharedPreferences「sj_evidence」；证据页逐条「删除」",
            "截图 → 私有目录 evidence/<文件夹>/；随「清除全部数据」或卸载一起删",
            "文件夹头像 → 私有目录；随「清除全部数据」或卸载一起删",
            "B站登录态 → 只有 SESSDATA；设置 → B站「退出」"
        )
    ),
    Block(
        title = "④ 不记录什么",
        lines = listOf(
            "视频文件、图片本体（除文件夹头像与你手动附的证据截图）",
            "页面原始 HTML（抖音/快手用隐藏 WebView 当场读 DOM，抓完即走）",
            "你的浏览行为、点击记录、崩溃日志、使用统计——没有埋点、没有统计 SDK",
            "通讯录、短信、位置、相机——这些权限根本没有申请",
            "权限只有两个：INTERNET（抓取）与 POST_NOTIFICATIONS（通知）"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRecordScreen(
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
                title = { Text("记录了什么") }
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
                    text = "全部数据都在本机，没有任何一条上传。下面逐项写清记了什么、" +
                        "能推出什么、存在哪、怎么删。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(RECORD_BLOCKS.size) { index ->
                val block = RECORD_BLOCKS[index]
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
                Text(
                    text = "同一份内容也写在仓库的 docs/数据清单.md 里，方便对外说明。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}
