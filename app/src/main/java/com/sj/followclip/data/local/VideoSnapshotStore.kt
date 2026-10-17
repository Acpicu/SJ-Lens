package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONArray

/**
 * 「上次看到过哪些视频」的快照，按账号 id 存。
 *
 * 只存平台作品 ID（B 站就是 BV 号）的集合，不存标题：
 * 比对只需要判断「这个 ID 见过没有」，存得越小读写越快，也越不容易出格式问题。
 *
 * 存本地 SharedPreferences，不进主 JSON：它变化频繁且只是辅助信息，
 * 丢了最多多提示一次"有新视频"，不影响订阅数据本身。
 */
object VideoSnapshotStore {

    private const val PREF = "video_snapshots"

    fun load(context: Context, accountId: Long, kind: String = "vid"): Set<String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("${kind}_$accountId", null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        }.getOrDefault(emptySet())
    }

    fun save(context: Context, accountId: Long, ids: List<String>, kind: String = "vid") {
        runCatching {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString("${kind}_$accountId", arr.toString())
                .apply()
        }
    }
}
