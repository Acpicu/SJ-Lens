package com.sj.followclip.data.local

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** 一条紧急速记。done=false 表示还没补录到数据本。 */
data class Memo(
    val id: String,
    val text: String,
    val at: Long,
    val done: Boolean = false
)

/**
 * 紧急速记：只存在应用私有目录（SharedPreferences），不写外部存储、不走任何同步。
 * 用途是「先记下来，之后再补录到数据本」。
 */
object QuickMemoStore {

    private const val PREF = "quick_memo"
    private const val KEY = "items"

    val items = mutableStateListOf<Memo>()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        items.clear()
        items.addAll(read(context))
    }

    fun add(context: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val memo = Memo(
            id = System.currentTimeMillis().toString(36) + "-" + (System.nanoTime() % 4096).toString(36),
            text = t, at = System.currentTimeMillis()
        )
        val snapshot = items.toList()
        items.clear()
        items.add(memo)
        items.addAll(snapshot)
        persist(context)
    }

    fun toggle(context: Context, id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(done = !items[i].done); persist(context) }
    }

    /** 改时间：补录昨天的事就把这个调回去。 */
    fun setAt(context: Context, id: String, at: Long) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = items[i].copy(at = at); persist(context) }
    }

    fun remove(context: Context, id: String) {
        items.removeAll { it.id == id }
        persist(context)
    }

    fun clearDone(context: Context) {
        items.removeAll { it.done }
        persist(context)
    }

    private fun read(context: Context): List<Memo> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Memo(o.optString("id"), o.optString("text"), o.optLong("at"), o.optBoolean("done")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context) {
        runCatching {
            val arr = JSONArray()
            items.forEach {
                arr.put(JSONObject().put("id", it.id).put("text", it.text).put("at", it.at).put("done", it.done))
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        }
    }
}
