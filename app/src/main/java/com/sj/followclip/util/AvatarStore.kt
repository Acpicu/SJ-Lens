package com.sj.followclip.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 自定义头像的本地存储。
 *
 * 文件名固定为 avatar_<personId>.jpg，放在应用私有目录里。
 * 这样一个功能既不需要数据库、也不需要新增依赖。
 *
 * 关键点：**存的时候就裁成正方形**。
 * 相册里的图多半是竖图或横图，如果只按宽高比存原图、到界面再想办法裁，
 * 很容易出现压扁、变形、或者圆里套一个长方形的观感。
 * 存成正方形之后，界面只要把圆角一剪就一定是正圆，不会出意外。
 */
object AvatarStore {

    private const val DIR_NAME = "avatars"

    /** 存成 256×256。头像最大只显示 48dp，再大纯属浪费。 */
    private const val TARGET_EDGE_PX = 256

    private fun avatarFile(context: Context, personId: Long): File {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        return File(dir, "avatar_$personId.jpg")
    }

    fun hasAvatar(context: Context, personId: Long): Boolean =
        avatarFile(context, personId).exists()

    /** 读取已保存的头像，没有则返回 null。解码失败按「没有头像」处理。 */
    fun load(context: Context, personId: Long): Bitmap? {
        val file = avatarFile(context, personId)
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /**
     * 只解码相册选中的图，不落盘。
     * 新建文件夹还没生成 id，没法存文件，所以先用这个做界面预览。
     */
    fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /**
     * 把相册选中的图片**居中裁成正方形**再缩到 256×256 存下来。
     *
     * 全程兜底：读不到、解码失败、写盘失败都只返回 false，
     * 结果就是「头像没换成」，不能让主流程崩。
     */
    fun save(context: Context, personId: Long, uri: Uri): Boolean = runCatching {
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return false

        val square = centerSquare(original)
        val finalBitmap = if (square.width > TARGET_EDGE_PX) {
            Bitmap.createScaledBitmap(square, TARGET_EDGE_PX, TARGET_EDGE_PX, true)
        } else {
            square
        }

        avatarFile(context, personId).outputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        true
    }.getOrDefault(false)

    /**
     * 直接存一张已经裁好的正方形位图。
     * 用户在裁切界面调好之后走这个入口，不再重复裁剪。
     */
    fun save(context: Context, personId: Long, bitmap: Bitmap): Boolean = runCatching {
        val finalBitmap = if (bitmap.width > TARGET_EDGE_PX) {
            Bitmap.createScaledBitmap(bitmap, TARGET_EDGE_PX, TARGET_EDGE_PX, true)
        } else {
            bitmap
        }
        avatarFile(context, personId).outputStream().use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        true
    }.getOrDefault(false)

    /** 从中间截出最大的正方形。 */
    private fun centerSquare(source: Bitmap): Bitmap {
        val edge = minOf(source.width, source.height)
        if (edge <= 0) return source
        val left = (source.width - edge) / 2
        val top = (source.height - edge) / 2
        return Bitmap.createBitmap(source, left, top, edge, edge)
    }
}
