package com.sj.followclip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全应用统一字阶。
 *
 * 只覆盖真正需要造型的几个角色，其余沿用 Material 3 默认值。
 *
 * headlineMedium 专门留给首页的「SJ LENS」标题：加粗 + 大字距，
 * 做出品牌标识那种克制的存在感，而不是靠放大字号硬撑。
 */
private val Base = Typography()

val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp
    ),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Base.labelSmall.copy(letterSpacing = 0.4.sp)
)
