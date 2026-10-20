package com.sj.followclip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 应用调色板：护眼深色 + 青蓝点缀。
 *
 * 护眼的三个做法，都体现在下面这些值里：
 * 1. 底色不用纯黑，用带蓝味的深灰（#0D1117），比纯黑柔和，眼睛不用一直绷着；
 * 2. 强调色降饱和度，不用荧光绿那种刺眼的高亮；
 * 3. 正文不用纯白，用偏灰的 #E6EDF3，长时间看不发涩。
 *
 * 整体克制：彩色只出现在强调色、角标和平台点上，其余全是中性灰蓝，
 * 这样才「有高级感但不腻」。色值集中在本文件，界面只通过 MaterialTheme 取色。
 */
private val Palette = darkColorScheme(
    // 青蓝：强调色、选中态、新内容标记
    primary = Color(0xFF67D6F5),
    onPrimary = Color(0xFF04212B),
    primaryContainer = Color(0xFF123039),
    onPrimaryContainer = Color(0xFF9BDDF0),

    // 淡紫蓝：B 站平台色点
    secondary = Color(0xFFB0BCF5),
    onSecondary = Color(0xFF10162E),
    secondaryContainer = Color(0xFF1B2340),
    onSecondaryContainer = Color(0xFFC3CBFA),

    // 柔和琥珀：快手平台色点
    tertiary = Color(0xFFE8C885),
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = Color(0xFF33280F),
    onTertiaryContainer = Color(0xFFEED9A8),

    // 深灰蓝底，比纯黑柔和
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF141A21),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B232B),
    onSurfaceVariant = Color(0xFF96A4B2),

    outline = Color(0xFF27313B),
    outlineVariant = Color(0xFF212A33),

    error = Color(0xFFF08A8A),
    onError = Color(0xFF2A0808)
)

/**
 * 应用主题：固定深色。
 *
 * 不跟随系统浅色模式——这套配色的观感就是应用的标识，跟随会把它冲淡。
 */
@Composable
fun SJFollowClipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Palette,
        typography = AppTypography,
        content = content
    )
}
