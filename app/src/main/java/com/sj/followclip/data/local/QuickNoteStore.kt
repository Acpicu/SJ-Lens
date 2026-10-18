package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条速记：时间 + 一句话 + 类型。** */
data class QuickNote(
    val id: String,
    val at: Long,
    /** 发布 / 改名 / 删除 / 观察 —— 只是分类，不填就是观察 */
    val kind: String,
    val text: String
)

/**
 * 速记本：一个人一个列表，一条就一句话，点一下就存。
 *
 * 和「证据条目」分工：速记是随手记（不需要哈希和等级），
 * 证据条目是正式引用（要编号、来源、SHA-256）。先速记，需要引用时再升级成证据。
 */
object QuickNoteStore {
    private const val PREF = "sj_quicknotes"

    private fun listKey(personId: Long) = "qn_$personId"
    private fun seqKey(personId: Long) = "qnseq_$personId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun list(context: Context, personId: Long): List<QuickNote> {
        val raw = prefs(context).getString(listKey(personId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QuickNote(
                    id = o.optString("id"),
                    at = o.optLong("at"),
                    kind = o.optString("kind"),
                    text = o.optString("text")
                )
            }
        }.getOrDefault(emptyList()).sortedByDescending { it.at }
    }

    fun add(context: Context, personId: Long, text: String, kind: String = "观察"): List<QuickNote> {
        if (text.isBlank()) return list(context, personId)
        val current = list(context, personId).toMutableList()
        val seq = prefs(context).getInt(seqKey(personId), 0) + 1
        prefs(context).edit().putInt(seqKey(personId), seq).apply()
        current += QuickNote(
            id = "N-" + seq.toString().padStart(3, '0'),
            at = System.currentTimeMillis(),
            kind = kind,
            text = text.trim()
        )
        persist(context, personId, current)
        return list(context, personId)
    }

    fun remove(context: Context, personId: Long, id: String): List<QuickNote> {
        val current = list(context, personId).filterNot { it.id == id }
        persist(context, personId, current)
        return current
    }

    fun clear(context: Context, personId: Long) {
        prefs(context).edit().remove(listKey(personId)).remove(seqKey(personId)).apply()
    }

    private fun persist(context: Context, personId: Long, notes: List<QuickNote>) {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("at", n.at)
                    put("kind", n.kind)
                    put("text", n.text)
                }
            )
        }
        prefs(context).edit().putString(listKey(personId), arr.toString()).apply()
    }
}
