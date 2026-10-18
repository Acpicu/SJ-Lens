package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一条证据。编号只增不改：删掉的号不复用，避免报告引用错位。
 */
data class EvidenceEntry(
    val id: String,
    val source: String,
    val collectedAt: Long,
    val sha256: String,
    /** Admiralty 格式，如 B2 */
    val grade: String,
    val note: String,
    /** 本地截图路径，空表示没附 */
    val photoPath: String = ""
)

/** 一条待验证项。status：0 未验证 / 1 已证实 / 2 已推翻。 */
data class TodoItem(
    val id: String,
    val text: String,
    /** 查到什么就能证实或推翻 */
    val check: String,
    val status: Int = 0
)

/**
 * 证据与待验证清单的本机存储：按文件夹（personId）分组，SharedPreferences 存 JSON。
 *
 * 全部手工登记，不参与抓取；导出的是 Markdown 草稿。
 */
object EvidenceStore {
    private const val PREF = "sj_evidence"

    private fun listKey(personId: Long) = "ev_$personId"
    private fun seqKey(personId: Long) = "seq_$personId"
    private fun todoKey(personId: Long) = "todo_$personId"
    private fun todoSeqKey(personId: Long) = "todo_seq_$personId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- 证据 ----------

    fun list(context: Context, personId: Long): List<EvidenceEntry> {
        val raw = prefs(context).getString(listKey(personId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EvidenceEntry(
                    id = o.optString("id"),
                    source = o.optString("source"),
                    collectedAt = o.optLong("at"),
                    sha256 = o.optString("sha"),
                    grade = o.optString("grade"),
                    note = o.optString("note"),
                    photoPath = o.optString("photo")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(
        context: Context,
        personId: Long,
        source: String,
        sha256: String,
        grade: String,
        note: String,
        photoPath: String = ""
    ): List<EvidenceEntry> {
        val current = list(context, personId).toMutableList()
        val seq = prefs(context).getInt(seqKey(personId), 0) + 1
        prefs(context).edit().putInt(seqKey(personId), seq).apply()
        current += EvidenceEntry(
            id = "E-" + seq.toString().padStart(3, '0'),
            source = source.trim(),
            collectedAt = System.currentTimeMillis(),
            sha256 = sha256.trim().lowercase(Locale.US),
            grade = grade.trim().uppercase(Locale.US),
            note = note.trim(),
            photoPath = photoPath
        )
        persist(context, personId, current)
        return current
    }

    fun remove(context: Context, personId: Long, id: String): List<EvidenceEntry> {
        val current = list(context, personId).filterNot { it.id == id }
        persist(context, personId, current)
        return current
    }

    fun clear(context: Context, personId: Long) {
        prefs(context).edit().remove(listKey(personId)).remove(seqKey(personId)).apply()
    }

    private fun persist(context: Context, personId: Long, entries: List<EvidenceEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("source", e.source)
                    put("at", e.collectedAt)
                    put("sha", e.sha256)
                    put("grade", e.grade)
                    put("note", e.note)
                    put("photo", e.photoPath)
                }
            )
        }
        prefs(context).edit().putString(listKey(personId), arr.toString()).apply()
    }

    // ---------- 待验证清单 ----------

    fun todos(context: Context, personId: Long): List<TodoItem> {
        val raw = prefs(context).getString(todoKey(personId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TodoItem(
                    id = o.optString("id"),
                    text = o.optString("text"),
                    check = o.optString("check"),
                    status = o.optInt("status", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addTodo(context: Context, personId: Long, text: String, check: String): List<TodoItem> {
        val current = todos(context, personId).toMutableList()
        val seq = prefs(context).getInt(todoSeqKey(personId), 0) + 1
        prefs(context).edit().putInt(todoSeqKey(personId), seq).apply()
        current += TodoItem(
            id = "T-" + seq.toString().padStart(2, '0'),
            text = text.trim(),
            check = check.trim(),
            status = 0
        )
        persistTodos(context, personId, current)
        return current
    }

    fun setTodoStatus(
        context: Context,
        personId: Long,
        id: String,
        status: Int
    ): List<TodoItem> {
        val current = todos(context, personId).map {
            if (it.id == id) it.copy(status = status) else it
        }
        persistTodos(context, personId, current)
        return current
    }

    fun removeTodo(context: Context, personId: Long, id: String): List<TodoItem> {
        val current = todos(context, personId).filterNot { it.id == id }
        persistTodos(context, personId, current)
        return current
    }

    private fun persistTodos(context: Context, personId: Long, items: List<TodoItem>) {
        val arr = JSONArray()
        items.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("text", t.text)
                    put("check", t.check)
                    put("status", t.status)
                }
            )
        }
        prefs(context).edit().putString(todoKey(personId), arr.toString()).apply()
    }

    // ---------- 导出 ----------

    /**
     * 四段式报告草稿：结论 / 证据 / 置信度 / 待验证清单。
     *
     * 置信度不打分，留空让人自己填 —— 置信度是判断，不该由笔记工具代劳。
     */
    fun buildReport(
        personName: String,
        conclusion: String,
        entries: List<EvidenceEntry>,
        todos: List<TodoItem>
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("# 分析记录 · ").append(personName.ifBlank { "未命名" }).append("\n\n")

        sb.append("## 一、结论\n\n")
        sb.append(conclusion.ifBlank { "（未填写）" }.trim()).append("\n\n")

        sb.append("## 二、证据\n\n")
        if (entries.isEmpty()) {
            sb.append("（无）\n\n")
        } else {
            entries.forEach { e ->
                sb.append("### ").append(e.id)
                if (e.grade.isNotBlank()) sb.append("  [").append(e.grade).append(']')
                sb.append("\n\n")
                sb.append("- 采集时间：").append(fmt.format(Date(e.collectedAt))).append("\n")
                sb.append("- 来源：").append(e.source.ifBlank { "（未填）" }).append("\n")
                if (e.sha256.isNotBlank()) sb.append("- SHA-256：").append(e.sha256).append("\n")
                if (e.photoPath.isNotBlank()) sb.append("- 截图：").append(e.photoPath).append("\n")
                if (e.note.isNotBlank()) sb.append("- 备注：").append(e.note).append("\n")
                sb.append("\n")
            }
        }

        sb.append("## 三、置信度\n\n")
        sb.append("（高 / 中 / 低 + 一句理由，例如「中：只有单一信源，时间戳未互证」）\n\n")

        sb.append("## 四、待验证清单\n\n")
        if (todos.isEmpty()) {
            sb.append("（无）\n")
        } else {
            todos.forEach { t ->
                val box = when (t.status) {
                    1 -> "[x] 已证实"
                    2 -> "[x] 已推翻"
                    else -> "[ ] 未验证"
                }
                sb.append("- ").append(box).append(" ").append(t.id).append(' ')
                    .append(t.text)
                if (t.check.isNotBlank()) {
                    sb.append("（查：").append(t.check).append("）")
                }
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
