package com.sj.followclip.ui.manual

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
 * 现场手册：OSINT 方法论与纪律，离线内置，不联网。
 *
 * 收的是「怎么做才不出错」的部分：证据链、信源分级、交叉验证、常见误判、操作安全、边界。
 * 不收录针对具体个人的取数流程。
 */
private data class ManualSection(
    val title: String,
    val lines: List<String>
)

private val SECTIONS = listOf(
    ManualSection(
        title = "1 · 证据链与编号",
        lines = listOf(
            "每条结论后面必须跟一个证据编号（E-001、E-002……），编号只增不改。",
            "证据对象三件套：原始文件 + SHA-256 + 采集时间。截图不算原始证据，要存页面/接口原始返回。",
            "同一事实只记一条证据；多条证据支持同一结论时并列编号，不要合成一条。",
            "删掉的证据保留编号并标注作废原因，不要复用编号。"
        )
    ),
    ManualSection(
        title = "2 · 信源可靠性分级（Admiralty）",
        lines = listOf(
            "可靠性 A：完全可靠（官方原始发布、第一手记录）",
            "可靠性 B：通常可靠（有编辑流程的媒体、机构报告）",
            "可靠性 C：较可靠（有实名的从业者、可追溯的公开账号）",
            "可靠性 D：不太可靠（匿名转述、二手汇总）",
            "可靠性 E：不可靠（明显立场化、无法核实的爆料）",
            "可靠性 F：无法判断（缺失出处，默认按 F 处理）",
            "可信度 1–6：1 已由其他独立源证实 / 2 很可能 / 3 可能 / 4 存疑 / 5 不可能 / 6 无法判断",
            "记录格式：B2 表示通常可靠但尚未独立证实。"
        )
    ),
    ManualSection(
        title = "3 · 三源法则",
        lines = listOf(
            "第一源：原始出处。找到内容最初发布的位置，不是转载。",
            "第二源：独立来源。必须与第一源没有引用关系，转述同一爆料不算独立。",
            "时间源：时间戳互证。发布时间、编辑时间、平台抓取时间三者对不上就先挂起结论。",
            "任何一条对不上，结论降级为「待验证」，不要写进结论段。"
        )
    ),
    ManualSection(
        title = "4 · 常见误判清单",
        lines = listOf(
            "同名不同人：昵称相同是最弱信号，单独不能作为同一人的依据。",
            "头像盗用：头像可以被任何人复制，感知哈希相同只说明图相同，不说明人相同。",
            "算法碰撞：感知哈希在纯色、渐变、文字图上碰撞率高，必须人工目视复核。",
            "时钟推断：发帖时间只能推到时区，推不到城市；且用户可能手动改时间、跨时区旅行。",
            "互关可见：非公开内容被算进计数，会让「更新了」的判断假阳性。",
            "语言指纹：措辞习惯会随场合改变，不能当作稳定标识。"
        )
    ),
    ManualSection(
        title = "5 · 操作安全（OPSEC）",
        lines = listOf(
            "分析用环境与日常账号隔离：不用本人常用账号去看目标主页，避免留下访客痕迹与推荐关联。",
            "不互动：不点赞、不关注、不评论、不转发。互动会直接暴露你是关注者。",
            "不下载可执行文件：样本一律在隔离环境处理，不在手机上打开。",
            "不把分析结果发到公开群组或社交平台，截图打码也可能被反向检索。",
            "本机数据按敏感数据对待：导出前脱敏，用完即清。"
        )
    ),
    ManualSection(
        title = "6 · 边界",
        lines = listOf(
            "只处理公众可访问的信息：不绕过登录、不破解、不抓私域（私信、好友、关注列表、非公开内容）。",
            "不做人身定位：不通过照片背景、Wi-Fi 列表、基站信息推断位置。",
            "不做人脸比对：不用人脸识别库跨平台匹配。",
            "不建长期档案：不对具体个人持续累积资料、不做心理画像。",
            "被分析者的权利优先：对方要求停止，就停止并清除已收集内容。",
            "公开不等于可随意使用：个人信息保护法对「已公开信息」的使用同样有边界。"
        )
    ),
    ManualSection(
        title = "7 · 报告写法",
        lines = listOf(
            "结构固定四段：结论 / 证据 / 置信度 / 待验证清单。",
            "结论只写证据支持得住的话，一句一条，不做推测性延伸。",
            "置信度用等级（高/中/低）+ 一句理由，例如「中：只有单一信源，时间戳未互证」。",
            "待验证清单要写出「下一步查什么、查到什么就能证实/推翻」，不要写「继续关注」。",
            "所有截图标注采集时间与来源地址，链接失效也要保留快照哈希。"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldManualScreen(
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
                title = { Text("现场手册") }
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
                    text = "方法论与纪律，离线内置。收的是「怎么做才不出错」，不是「怎么查某个人」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            items(SECTIONS.size) { index ->
                val section = SECTIONS[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Spacing.cardCorner),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(Spacing.itemGap))
                        section.lines.forEach { line ->
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
        }
    }
}
