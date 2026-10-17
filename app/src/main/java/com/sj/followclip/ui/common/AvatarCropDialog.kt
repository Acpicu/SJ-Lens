package com.sj.followclip.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.roundToInt

/** 取景框边长。 */
private val CropBoxSize = 280.dp

/** 缩放上限。1x 是「刚好铺满取景框」，不放大就没有黑边。 */
private const val MaxUserScale = 4f

/**
 * 头像裁切对话框：用户自己拖、自己缩，决定裁哪一块。
 *
 * 做法说明（为什么这样算）：
 * - 图片用 ContentScale.Crop 铺满取景框，此时基准缩放 = 取景框边长 / 图片短边；
 * - 用户的手势只在此之上再叠一层缩放与平移，所以「界面看到的」和「最后裁出来的」
 *   用的是同一套变换参数，不会出现所见非所得；
 * - 平移会被夹住，保证图片永远盖满取景框，不会露出黑边；
 * - 确认时按同一套参数反算原图上的矩形，裁下来再缩到 256×256。
 *
 * 全程是纯界面计算，不涉及网络与存储。
 */
@Composable
fun AvatarCropDialog(
    source: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val boxPx = with(LocalDensity.current) { CropBoxSize.toPx() }

    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 基准缩放：图片短边铺满取景框
    val baseScale = boxPx / min(source.width, source.height).toFloat()

    /** 夹住平移量，保证图片始终盖满取景框。 */
    fun clampOffset(candidate: Offset, scale: Float): Offset {
        val totalScale = baseScale * scale
        val scaledW = source.width * totalScale
        val scaledH = source.height * totalScale
        val maxX = ((scaledW - boxPx) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - boxPx) / 2f).coerceAtLeast(0f)
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY)
        )
    }

    /** 用当前变换把取景框里的内容裁成一张正方形位图。 */
    fun cropToBitmap(): Bitmap {
        val totalScale = baseScale * userScale
        val sourceSide = boxPx / totalScale
        val centerX = source.width / 2f - offset.x / totalScale
        val centerY = source.height / 2f - offset.y / totalScale

        val side = sourceSide.roundToInt().coerceAtLeast(1)
        val left = (centerX - sourceSide / 2f).roundToInt()
            .coerceIn(0, (source.width - side).coerceAtLeast(0))
        val top = (centerY - sourceSide / 2f).roundToInt()
            .coerceIn(0, (source.height - side).coerceAtLeast(0))

        val safeW = min(side, source.width - left)
        val safeH = min(side, source.height - top)
        val cropped = Bitmap.createBitmap(source, left, top, safeW, safeH)
        return Bitmap.createScaledBitmap(cropped, 256, 256, true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E12))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "调整头像",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "拖动换位置，双指缩放调大小",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(CropBoxSize)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (userScale * zoom).coerceIn(1f, MaxUserScale)
                            userScale = newScale
                            // 缩放后再夹一次，避免缩小后留下黑边
                            offset = clampOffset(offset + pan, newScale)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = "待裁切头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = userScale,
                            scaleY = userScale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { onConfirm(cropToBitmap()) }) {
                    Text("确定")
                }
            }
        }
    }
}
