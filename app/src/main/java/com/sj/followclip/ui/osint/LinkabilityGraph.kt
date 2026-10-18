package com.sj.followclip.ui.osint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sj.followclip.data.osint.AuditPair
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 关联图：把各平台画成节点，把命中的关联画成边。
 *
 * 纯本地绘制，Canvas 手写，不引第三方图表库（保持零依赖）。
 * 边越粗表示关联权重越高；强关联用告警色，弱关联用次强调色。
 */
@Composable
fun LinkabilityGraph(
    platforms: List<String>,
    pairs: List<AuditPair>,
    modifier: Modifier = Modifier
) {
    if (platforms.size < 2) return

    val nodeColor = MaterialTheme.colorScheme.primary
    val strongColor = MaterialTheme.colorScheme.error
    val weakColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelPx = with(LocalDensity.current) { 12.sp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) / 2f - 32.dp.toPx()
        val n = platforms.size

        // 节点均匀分布在圆周上，第一个摆在正上方
        val points = List(n) { i ->
            val angle = (-90.0 + 360.0 / n * i) * PI / 180.0
            Offset(
                x = cx + (radius * cos(angle)).toFloat(),
                y = cy + (radius * sin(angle)).toFloat()
            )
        }

        // 先画边，避免压住节点
        pairs.forEach { pair ->
            val i = platforms.indexOf(pair.from)
            val j = platforms.indexOf(pair.to)
            if (i >= 0 && j >= 0) {
                val strength = (pair.weight / 30f).coerceIn(0.2f, 1f)
                drawLine(
                    color = if (pair.weight >= 30) strongColor else weakColor,
                    start = points[i],
                    end = points[j],
                    strokeWidth = (1.5f + 4f * strength) * density
                )
            }
        }

        points.forEach { point ->
            drawCircle(color = nodeColor, radius = 7.dp.toPx(), center = point)
        }

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = labelPx
            color = labelColor.toArgb()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        points.forEachIndexed { index, point ->
            drawContext.canvas.nativeCanvas.drawText(
                platforms[index],
                point.x,
                point.y - 13.dp.toPx(),
                paint
            )
        }
    }
}
