package com.sj.followclip.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * 闪电标识：关于页的作者标签用。
 *
 * 纯 Canvas 画的矢量折线，不依赖图片资源，任何尺寸都清晰；
 * 之前那个手绘皮卡丘在深色底上又糊又不可爱，换成闪电更贴这台机器的气质。
 */
@Composable
fun BoltMark(
    size: Dp = 36.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bolt = Path().apply {
            moveTo(w * 0.60f, h * 0.02f)
            lineTo(w * 0.14f, h * 0.58f)
            lineTo(w * 0.46f, h * 0.58f)
            lineTo(w * 0.36f, h * 0.98f)
            lineTo(w * 0.86f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.38f)
            close()
        }
        drawPath(path = bolt, color = color)
    }
}

/** 给不需要渐变的场景用的纯色闪电（保留一个明显的出口，方便以后加光晕）。 */
@Composable
fun BoltMarkGlow(
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bolt = Path().apply {
            moveTo(w * 0.60f, h * 0.02f)
            lineTo(w * 0.14f, h * 0.58f)
            lineTo(w * 0.46f, h * 0.58f)
            lineTo(w * 0.36f, h * 0.98f)
            lineTo(w * 0.86f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.38f)
            close()
        }
        drawPath(
            path = bolt,
            brush = Brush.linearGradient(
                colors = listOf(scheme.primary, scheme.secondary),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
    }
}
