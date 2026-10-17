package com.sj.followclip.data.local

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** 一条「待确认」的新视频：检查时发现、用户还没处理。 */
data class PendingItem(
    val bvid: String,
    val title: String,
    val personId: Long,
    val personName: String,
    val platform: String,
    val coverUrl: String? = null,
    /** VIDEO 或 DYNAMIC：决定点击后打开视频还是动态页 */
    val kind: String = "VIDEO",
    val foundAt: Long
)

/**
 * 待确认池：检查更新时发现的新视频先堆在这里，供「待确认」页展示。
 *
 * 用 SnapshotStateList 承载，界面读它会自动重组；
 * 每次增删都落盘，杀进程不丢。按 bvid 去重，重复检查不会堆积。
 */
object PendingStore {

    private const val PREF = "pending_items"

    val items = mutableStateListOf<PendingItem>()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        items.clear()
        items.addAll(load(context))
    }

    /** 批量加入新视频；已存在的 bvid 跳过。 */
    fun addAll(context: Context, incoming: List<PendingItem>) {
        if (incoming.isEmpty()) return
        val existing = items.map { it.bvid }.toMutableSet()
        var changed = false
        incoming.forEach { item ->
            if (existing.add(item.bvid)) {
                items.add(item)
                changed = true
            }
        }
        if (changed) persist(context)
    }

    fun remove(context: Context, bvid: String) {
        val removed = items.removeAll { it.bvid == bvid }
        if (removed) persist(context)
    }

    /** 整组确认：把某个人的待确认清空。 */
    fun removeByPerson(context: Context, personId: Long) {
        val removed = items.removeAll { it.personId == personId }
        if (removed) persist(context)
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
                o.put("kind", item.kind)
                o.put("foundAt", item.foundAt)
                arr.put(o)
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("items", arr.toString()).apply()
        }
    }
}
