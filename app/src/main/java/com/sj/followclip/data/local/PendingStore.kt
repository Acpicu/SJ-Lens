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
 * 最近一轮检查的结果。
 *
 * **只保留最近一次检查发现的新内容**，每次检查整体覆盖，用户不需要手动清理。
 * 这样「待确认」页永远只反映当前状态，不会堆成陈年旧货。
 *
 * 用 SnapshotStateList 承载，界面读它会自动重组；每次写入都落盘，杀进程不丢。
 */
object PendingStore {

    private const val PREF = "pending_items"
    private const val KEY_SEEN = "seen_count"

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

    /** 上次打开「最近检查」时已经看过的条数；用来算底部导航的未看角标。 */
    fun seenCount(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_SEEN, 0)

    /** 打开「最近检查」时调用：把当前条数记为已看。 */
    fun markSeen(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SEEN, items.size).apply()
    }

    /** 未看条数。 */
    fun unseenCount(context: Context): Int = (items.size - seenCount(context)).coerceAtLeast(0)

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
