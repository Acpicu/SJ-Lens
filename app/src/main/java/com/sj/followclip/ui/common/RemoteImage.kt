package com.sj.followclip.ui.common

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简网络图片加载 + 内存缓存，零第三方依赖。
 *
 * 为什么自己写：只为显示封面图就引入 Coil / Glide 这类库，体积和依赖面都不划算。
 * 这里用 LruCache 存已解码的位图，滑回已看过的位置不再重复下载。
 *
 * 注意：B 站的图床对 Referer 有校验，不带会拿到 403，所以固定带上。
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    referer: String = "https://www.bilibili.com",
    fallback: @Composable BoxScope.() -> Unit = {}
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { ImageCache.get(it) }) }

    if (bitmap == null && !url.isNullOrBlank()) {
        LaunchedEffect(url) {
            // 必须切到 IO 线程：下载 + 解码都在主线程跑会把界面卡死
            val loaded = withContext(Dispatchers.IO) { ImageCache.load(url, referer) }
            if (loaded != null) bitmap = loaded
        }
    }

    Box(modifier = modifier) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            fallback()
        }
    }
}

/** 位图内存缓存。大小按可用内存的 1/8 算，超出自动淘汰最久未用的。 */
private object ImageCache {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 解码目标宽度。封面显示宽度约 120dp，按 3 倍屏算 360px 足够清晰。 */
    private const val TARGET_WIDTH_PX = 360

    private val cache = object : LruCache<String, ImageBitmap>(cacheSize()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun cacheSize(): Int {
        val max = Runtime.getRuntime().maxMemory()
        // 只给图片 1/16 的内存，宁可多淘汰也不要撑爆堆导致卡顿
        return (max / 16).toInt().coerceIn(2 * 1024 * 1024, 16 * 1024 * 1024)
    }

    /**
     * 按目标宽度降采样解码。
     * 封面在界面上只有 120dp 宽，直接解原图（可能 1000+ px）纯属浪费内存和 CPU，
     * 这也是之前卡顿的主要原因之一。
     */
    private fun decodeSampled(bytes: ByteArray, reqWidth: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqWidth) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun get(url: String): ImageBitmap? = cache.get(url)

    /** 下载并解码。任何失败都返回 null，由调用方显示占位图。 */
    fun load(url: String, referer: String): ImageBitmap? {
        cache.get(url)?.let { return it }

        val conn = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull()
            ?: return null
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", UA)
            // B 站图床校验 Referer，缺了会 403
            conn.setRequestProperty("Referer", referer)
            if (conn.responseCode !in 200..299) return null

            val bytes = conn.inputStream.use { it.readBytes() }
            val bitmap = decodeSampled(bytes, TARGET_WIDTH_PX)?.asImageBitmap() ?: return null
            cache.put(url, bitmap)
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
