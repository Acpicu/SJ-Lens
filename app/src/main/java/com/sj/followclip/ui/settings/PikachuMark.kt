package com.sj.followclip.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 手绘的皮卡丘脸。
 *
 * 不用图片资源：纯粹用 Canvas 画，随尺寸缩放都清晰，
 * 也不增加安装包体积（一张 PNG 少说几十 KB）。
 */
@Composable
fun PikachuMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val yellow = Color(0xFFFFD93B)
        val black = Color(0xFF2B2B2B)
        val red = Color(0xFFE8453C)

        // 两只耳朵
        drawPath(
            Path().apply {
                moveTo(w * 0.34f, h * 0.34f)
                lineTo(w * 0.12f, h * 0.02f)
                lineTo(w * 0.46f, h * 0.22f)
                close()
            },
            yellow
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.66f, h * 0.34f)
                lineTo(w * 0.88f, h * 0.02f)
                lineTo(w * 0.54f, h * 0.22f)
                close()
            },
            yellow
        )
        // 耳尖的黑色
        drawPath(
            Path().apply {
                moveTo(w * 0.12f, h * 0.02f)
                lineTo(w * 0.24f, h * 0.16f)
                lineTo(w * 0.16f, h * 0.20f)
                close()
            },
            black
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.88f, h * 0.02f)
                lineTo(w * 0.76f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.20f)
                close()
            },
            black
        )

        // 脸
        drawCircle(yellow, radius = w * 0.33f, center = Offset(w * 0.5f, h * 0.58f))

        // 眼睛
        drawCircle(black, radius = w * 0.055f, center = Offset(w * 0.39f, h * 0.54f))
        drawCircle(black, radius = w * 0.055f, center = Offset(w * 0.61f, h * 0.54f))
        drawCircle(Color.White, radius = w * 0.018f, center = Offset(w * 0.375f, h * 0.525f))
        drawCircle(Color.White, radius = w * 0.018f, center = Offset(w * 0.595f, h * 0.525f))

        // 腮红
        drawCircle(red, radius = w * 0.068f, center = Offset(w * 0.28f, h * 0.66f))
        drawCircle(red, radius = w * 0.068f, center = Offset(w * 0.72f, h * 0.66f))

        // 嘴
        drawPath(
            Path().apply {
                moveTo(w * 0.45f, h * 0.63f)
                quadraticTo(w * 0.5f, h * 0.69f, w * 0.55f, h * 0.63f)
            },
            black,
            style = Stroke(width = w * 0.022f)
        )
    }
}
