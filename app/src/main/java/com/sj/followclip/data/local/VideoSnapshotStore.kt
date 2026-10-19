package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONArray

/**
 * 「上次看到过哪些作品」的快照，按账号 id 存。
 *
 * 只存平台作品 ID 的集合，不存标题：
 * 比对只需要判断「这个 ID 见过没有」，存得越小读写越快，也越不容易出格式问题。
 *
 * 存本地 SharedPreferences，不进主 JSON：它变化频繁且只是辅助信息。
 *
 * **两条铁律**（都是「发了新作品却不报更新」的真实原因）：
 * 1. 抓空了就**不许写快照** —— 空列表会把基线抹掉，下一次检查就变成重新建基线，
 *    期间发的新作品永远报不出来。
 * 2. 新抓到的 ID 要**并进**旧快照，不能整体替换 —— 列表只取首页 30 条，
 *    旧作品被挤出去之后，下次检查会把它当成「新作品」反复报（假阳性）。
 */
object VideoSnapshotStore {

    private const val PREF = "video_snapshots"

    /** 每个账号最多记这么多 ID：够覆盖首页 + 最近的动态，不会无限长。 */
    private const val MAX_IDS = 300

    fun load(context: Context, accountId: Long, kind: String = "vid"): Set<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("${kind}_$accountId", null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(emptySet())
    }

    /** 整体覆盖。只在「确认这次抓到了东西」时用。 */
    fun save(context: Context, accountId: Long, ids: List<String>, kind: String = "vid") {
        if (ids.isEmpty()) return
        runCatching {
            val arr = JSONArray()
            ids.take(MAX_IDS).forEach { arr.put(it) }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString("${kind}_$accountId", arr.toString())
                .apply()
        }
    }

    /**
     * 合并写入：新 ID 在前，旧 ID 补齐，去重后封顶 [MAX_IDS]。
     * 检查完一律走这个，不要走 [save]。
     */
    fun merge(context: Context, accountId: Long, ids: List<String>, kind: String = "vid") {
        if (ids.isEmpty()) return
        val merged = LinkedHashSet<String>(ids.size + 64)
        ids.forEach { if (it.isNotBlank()) merged.add(it) }
        load(context, accountId, kind).forEach { merged.add(it) }
        save(context, accountId, merged.toList(), kind)
    }
}
