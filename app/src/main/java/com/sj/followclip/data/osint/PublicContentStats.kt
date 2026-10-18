package com.sj.followclip.data.osint

import java.util.Calendar
import java.util.TimeZone

/**
 * 公开内容统计引擎。
 *
 * 输入：已经从公开列表抓到的条目时间戳（就是应用里已有的 Video）。
 * 输出：发布间隔、星期分布、时段分布、数据完整度提示。
 *
 * 只做统计，不做身份推断，不新增任何网络请求。
 */
data class PostSample(
    /** 毫秒时间戳，平台公开展示的发布时间 */
    val publishedAt: Long,
    val title: String = "",
    val durationMs: Long? = null
)

data class ContentStats(
    val count: Int,
    /** 首尾样本跨度，天 */
    val spanDays: Long,
    val avgIntervalHours: Double,
    val medianIntervalHours: Double,
    /** 周一 = 0 … 周日 = 6 */
    val weekly: List<Int>,
    /** 0..23 点，本机时区 */
    val hourly: List<Int>,
    val peakWeekday: Int,
    val peakHour: Int,
    /** 有标题的条目占比；低于 0.5 说明抓到的多是纯图/纯视频卡片 */
    val titleRatio: Double,
    val notes: List<String>
) {
    val isEmpty: Boolean get() = count == 0
}

object PublicContentStats {

    fun analyze(
        samples: List<PostSample>,
        tz: TimeZone = TimeZone.getDefault()
    ): ContentStats {
        val valid = samples.filter { it.publishedAt > 0L }.sortedBy { it.publishedAt }
        if (valid.isEmpty()) {
            return ContentStats(
                count = 0,
                spanDays = 0,
                avgIntervalHours = 0.0,
                medianIntervalHours = 0.0,
                weekly = List(7) { 0 },
                hourly = List(24) { 0 },
                peakWeekday = 0,
                peakHour = 0,
                titleRatio = 0.0,
                notes = listOf("本轮没有采到带时间戳的公开条目，统计不可用")
            )
        }

        val weekly = IntArray(7)
        val hourly = IntArray(24)
        val cal = Calendar.getInstance(tz)
        valid.forEach {
            cal.timeInMillis = it.publishedAt
            // Calendar.MONDAY = 2 … SUNDAY = 1，转成周一为 0
            weekly[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] += 1
            hourly[cal.get(Calendar.HOUR_OF_DAY)] += 1
        }

        val spanDays = (valid.last().publishedAt - valid.first().publishedAt) / 86_400_000L

        val gaps = ArrayList<Long>(valid.size.coerceAtLeast(1))
        for (i in 1 until valid.size) {
            gaps += valid[i].publishedAt - valid[i - 1].publishedAt
        }
        val avgH = if (gaps.isEmpty()) 0.0 else gaps.sum().toDouble() / gaps.size / 3_600_000.0
        val medianH = if (gaps.isEmpty()) {
            0.0
        } else {
            val sorted = gaps.sorted()
            val mid = sorted.size / 2
            val v = if (sorted.size % 2 == 1) {
                sorted[mid].toDouble()
            } else {
                (sorted[mid - 1] + sorted[mid]) / 2.0
            }
            v / 3_600_000.0
        }

        val notes = mutableListOf<String>()
        if (valid.size < 5) notes += "样本少于 5 条，分布结论仅供参考"
        if (valid.size > 1 && spanDays < 7) notes += "时间跨度不足 7 天，星期分布不可用"
        if (avgH > 0.0 && avgH < 1.0) notes += "存在 1 小时内连发，可能混入同一次批量发布"
        if (valid.size == 1) notes += "只有 1 条样本，间隔与分布无意义"

        return ContentStats(
            count = valid.size,
            spanDays = spanDays,
            avgIntervalHours = round2(avgH),
            medianIntervalHours = round2(medianH),
            weekly = weekly.toList(),
            hourly = hourly.toList(),
            peakWeekday = argmax(weekly),
            peakHour = argmax(hourly),
            titleRatio = round2(valid.count { it.title.isNotBlank() }.toDouble() / valid.size),
            notes = notes
        )
    }

    /** 发布最集中的连续 3 小时窗口，用于「这个人一般在几点发」的一句话结论。 */
    fun activeWindow(hourly: List<Int>): String {
        if (hourly.size != 24 || hourly.all { it == 0 }) return "样本不足"
        var bestStart = 0
        var bestSum = -1
        for (start in 0 until 24) {
            val sum = (0 until 3).sumOf { hourly[(start + it) % 24] }
            if (sum > bestSum) {
                bestSum = sum
                bestStart = start
            }
        }
        val end = (bestStart + 3) % 24
        return String.format(java.util.Locale.US, "%02d:00-%02d:00", bestStart, end)
    }

    private fun argmax(a: IntArray): Int {
        var best = 0
        for (i in a.indices) if (a[i] > a[best]) best = i
        return best
    }

    private fun round2(v: Double): Double =
        Math.round(v * 100.0) / 100.0
}
