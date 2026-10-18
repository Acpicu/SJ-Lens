package com.sj.followclip.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 检查进度条：蓝青渐变，和应用的品牌色一致。
 *
 * 用 Box + 渐变背景实现，不用系统自带的 LinearProgressIndicator：
 * 后者只能单色，且圆角/高度受限，做不出想要的效果。
 *
 * 进度变化带 400ms 补间，避免跳变显得廉价。
 */
@Composable
fun TechProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "checkProgress"
    )

    // 轨道底色：比背景稍亮一点，暗色主题下才能看出"未完成"的部分
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0xFF1E2732))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF3B9EFF),
                            Color(0xFF22D3EE),
                            Color(0xFF5BC8E8)
                        )
                    )
                )
        )
    }
}
