package com.sj.followclip.util

import java.util.concurrent.TimeUnit

/**
 * 相对时间文案。
 *
 * 规格第十二章：文案中文直白，不使用营销语气。
 * 抓取时间尚未产生时返回「尚未抓取」，而不是留空或显示 1970 年。
 */
fun relativeTime(fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (fromMillis <= 0L) return "尚未抓取"

    val diff = nowMillis - fromMillis
    if (diff <= 0L) return "刚刚"

    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前"
        else -> "${minutes / (60 * 24 * 30)} 个月前"
    }
}

/**
 * 作品时长文案：12:34 或 1:02:03。
 * 取不到时长时（B 站部分响应需要解析「分秒」字符串）返回空串，由界面决定是否隐藏。
 */
fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return ""

    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** 数字缩写：12345 → 1.2万。用于粉丝数与作品数，取不到时不调用。 */
fun formatCount(count: Long): String = when {
    count < 10_000 -> count.toString()
    count < 100_000_000 -> "%.1f万".format(count / 10_000.0)
    else -> "%.1f亿".format(count / 100_000_000.0)
}
