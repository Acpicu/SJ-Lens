package com.sj.followclip.data.local

import android.content.Context

/**
 * 笔记：每个文件夹（人物）一条自由文本，存在本机 SharedPreferences。
 *
 * 定位是「观察记录 / 待验证线索」，不参与任何抓取逻辑，也不上传。
 */
object NoteStore {
    private const val PREF = "sj_notes"
    private const val KEY_PREFIX = "note_"
    private const val TIME_PREFIX = "note_at_"

    fun load(context: Context, personId: Long): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + personId, "")
            .orEmpty()

    fun updatedAt(context: Context, personId: Long): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(TIME_PREFIX + personId, 0L)

    fun save(context: Context, personId: Long, text: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_PREFIX + personId, text)
            .putLong(TIME_PREFIX + personId, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context, personId: Long) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove(KEY_PREFIX + personId)
            .remove(TIME_PREFIX + personId)
            .apply()
    }

    fun hasNotes(context: Context, personId: Long): Boolean =
        load(context, personId).isNotBlank()
}
