package com.sj.followclip.data.local

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** 一条「待确认」的新内容：检查时发现、用户还没处理。 */
data class PendingItem(
    val bvid: String,
    val title: String,
    val personId: Long,
    val personName: String,
    val platform: String,
    val coverUrl: String? = null,
    /** 直接可打开的地址；空则按平台规则拼 */
    val link: String = "",
    /** VIDEO 或 DYNAMIC：决定点开的是什么 */
    val kind: String = "VIDEO",
    val foundAt: Long
)

/**
 * 待确认的新内容。
 *
 * 检查结果是**合并**进去的：新一轮发现的新内容追加到前面，旧里还没被用户看过的会保留，
 * 避免「刚看到一条更新，下一秒刷新就没了」。上限 300 条，超出丢最旧的。
 *
 * 用 SnapshotStateList 承载，界面读它会自动重组；每次写入都落盘，杀进程不丢。
 */
object PendingStore {

    private const val PREF = "pending_items"
    private const val KEY_SEEN_AT = "seen_at"
    private const val MAX_ITEMS = 300

    val items = mutableStateListOf<PendingItem>()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        items.clear()
        items.addAll(load(context))
    }

    /** 用本轮结果整体替换；同一轮内按 id 去重。 */
    fun replaceAll(context: Context, incoming: List<PendingItem>) {
        items.clear()
        val seen = mutableSetOf<String>()
        incoming.forEach { item ->
            if (seen.add(item.bvid)) items.add(item)
        }
        persist(context)
    }

    /** 合并：本轮新内容放最前，旧内容里未见过的保留，按 bvid 去重，最多 300 条。 */
    fun merge(context: Context, incoming: List<PendingItem>) {
        val old = items.toList()
        val result = ArrayList<PendingItem>(incoming.size + old.size)
        val seen = mutableSetOf<String>()
        incoming.sortedByDescending { it.foundAt }.forEach { if (seen.add(it.bvid)) result.add(it) }
        old.forEach { if (seen.add(it.bvid)) result.add(it) }
        items.clear()
        items.addAll(result.take(MAX_ITEMS))
        persist(context)
    }

    /** 上次打开「最近检查」的时间；早于它的条目算已看。 */
    fun seenAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_SEEN_AT, 0L)

    /** 打开「最近检查」时调用：把当前时间记为已看。 */
    fun markSeen(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SEEN_AT, System.currentTimeMillis()).apply()
    }

    /** 未看条数：按时间戳算，合并后不会把旧条目重新算成未看。 */
    fun unseenCount(context: Context): Int {
        val at = seenAt(context)
        return items.count { it.foundAt > at }
    }

    fun clearAll(context: Context) {
        items.clear()
        persist(context)
    }

    private fun load(context: Context): List<PendingItem> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PendingItem(
                            bvid = o.optString("bvid"),
                            title = o.optString("title"),
                            personId = o.optLong("personId"),
                            personName = o.optString("personName"),
                            platform = o.optString("platform", "BILI"),
                            coverUrl = o.optString("cover").ifEmpty { null },
                            link = o.optString("link"),
                            kind = o.optString("kind", "VIDEO"),
                            foundAt = o.optLong("foundAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context) {
        runCatching {
            val arr = JSONArray()
            items.forEach { item ->
                val o = JSONObject()
                o.put("bvid", item.bvid)
                o.put("title", item.title)
                o.put("personId", item.personId)
                o.put("personName", item.personName)
                o.put("platform", item.platform)
                o.put("cover", item.coverUrl ?: "")
                o.put("link", item.link)
                o.put("kind", item.kind)
                o.put("foundAt", item.foundAt)
                arr.put(o)
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("items", arr.toString()).apply()
        }
    }
}
