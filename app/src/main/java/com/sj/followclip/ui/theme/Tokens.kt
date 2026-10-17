package com.sj.followclip.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sj.followclip.domain.model.Platform

/**
 * 设计令牌。界面代码不写十六进制色值，一律从这里取。
 */

/** 有新内容：实心数字角标，用主题强调色（终端绿）。 */
val ColorScheme.badgeUnread: Color get() = primary

/** 辅助信息与不可用提示。 */
val ColorScheme.textMuted: Color get() = onSurfaceVariant

/** 平台色点：三个平台各占一个色位，保证一眼能分。 */
val Platform.dotColor: Color
    @Composable get() = when (this) {
        Platform.DOUYIN -> MaterialTheme.colorScheme.primary
        Platform.KUAISHOU -> MaterialTheme.colorScheme.tertiary
        Platform.BILI -> MaterialTheme.colorScheme.secondary
    }

/** 间距与尺寸，全部是 4 的倍数。 */
object Spacing {
    /** 页面左右边距 */
    val pageHorizontal = 16.dp

    /** 卡片内边距 */
    val cardPadding = 16.dp

    /** 网格间距 */
    val gridGap = 12.dp

    /** 卡片圆角 */
    val cardCorner = 18.dp

    /** 区块之间 */
    val sectionGap = 24.dp

    /** 同一行元素之间 */
    val itemGap = 8.dp

    /** 紧邻元素之间（如色点与平台名） */
    val tinyGap = 4.dp

    /** 头像直径 */
    val avatar = 48.dp

    /** 人物页头部的大头像直径 */
    val avatarLarge = 64.dp

    /** 新建文件夹页面的大头像直径 */
    val avatarFolder = 96.dp

    /** 角标最小高度 */
    val badgeMinHeight = 20.dp

    /** 未读圆点直径 */
    val unreadDot = 8.dp

    /** 作品列表里的封面宽度，高度按 16:9 换算 */
    val coverWidth = 120.dp

    /** 一像素细线，用于卡片描边 */
    val hairline = 1.dp
}
