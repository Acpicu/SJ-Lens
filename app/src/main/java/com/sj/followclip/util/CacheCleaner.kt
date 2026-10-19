package com.sj.followclip.util

import android.content.Context
import android.webkit.WebView
import java.io.File

/**
 * 缓存清道夫。
 *
 * 体积大户只有一个：快手检查用的 WebView（Chromium 的 HTTP 缓存 + 代码缓存），
 * 用几次就能堆到几百 MB。所以：
 * 1. 每次快手检查结束后立刻 `clearCache(true)`；
 * 2. 设置页给一个「清理缓存」入口，随时能一键清干净；
 * 3. 只清缓存，不动数据本、截图与登录态。
 */
object CacheCleaner {

    /** 缓存目录：WebView 的 app_webview 才是大头。 */
    private fun targets(context: Context): List<File> = listOfNotNull(
        context.cacheDir,
        context.noBackupFilesDir?.let { File(it, "WebView") },
        context.filesDir?.parentFile?.let { File(it, "app_webview") },
        context.filesDir?.parentFile?.let { File(it, "app_textures") }
    ).distinctBy { it.absolutePath }

    /** 当前缓存占用（字节）。算不出来就返回 0，不阻塞界面。 */
    fun sizeBytes(context: Context): Long = runCatching {
        targets(context).sumOf { dir -> if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }
    }.getOrDefault(0L)

    /** 清缓存；返回清掉了多少字节。 */
    fun clear(context: Context): Long {
        val before = sizeBytes(context)
        runCatching {
            targets(context).forEach { dir ->
                if (dir.exists()) dir.walkBottomUp().forEach { node ->
                    if (node.isFile) node.delete() else if (node != dir) node.delete()
                }
            }
        }
        runCatching {
            WebView(context.applicationContext).apply {
                clearCache(true)
                clearFormData()
                clearHistory()
                destroy()
            }
        }
        return (before - sizeBytes(context)).coerceAtLeast(0L)
    }



    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format("%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
