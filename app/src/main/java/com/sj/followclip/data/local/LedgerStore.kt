package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据本的核心：一条记录 = 目标身上的**一条可陈述事实**。
 *
 * 为什么不是「自由笔记」：红队交档时被追问的是「这条哪来的、可不可信、证没证实」，
 * 所以每条记录强制带 分类 / 字段 / 值 / 出处 / 信源评级 / 置信度 / 状态。
 * 自由文本想写什么就写进字段名里，结构不丢。
 */
enum class RecordKind(val label: String, val hint: String) {
    ACCOUNT("账号", "平台 UID、昵称、主页链接"),
    IDENTITY("身份", "真实姓名、曾用名、实名线索"),
    CONTACT("联系", "手机、邮箱、微信、QQ"),
    ORG("单位", "公司、学校、班级、团队"),
    LOCATION("地点", "城市、常去场所、居住片区"),
    TIMELINE("时间", "发布时刻、作息、固定活动段"),
    RELATION("关系", "同事、同学、家人、互关"),
    CONTENT("内容", "作品、简介、口头禅、素材特征"),
    BEHAVIOR("行为", "活跃规律、常用设备、输入习惯"),
    OTHER("其他", "暂不归类");

    companion object {
        fun fromName(name: String): RecordKind =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** 记录状态：拿去交档之前，每条都该落到这三个之一。 */
object RecordStatus {
    const val PENDING = 0
    const val CONFIRMED = 1
    const val REFUTED = 2

    fun label(status: Int): String = when (status) {
        CONFIRMED -> "已证实"
        REFUTED -> "已推翻"
        else -> "待验证"
    }
}

/** 置信度：0 低 / 1 中 / 2 高。和「状态」分开——状态是结论，置信度是对这条的把握。 */
object Confidence {
    const val LOW = 0
    const val MEDIUM = 1
    const val HIGH = 2

    fun label(level: Int): String = when (level) {
        HIGH -> "高"
        MEDIUM -> "中"
        else -> "低"
    }
}

/** Admiralty 信源评级：来源可靠性 A–F + 情报可信度 1–6，如 B2。 */
object Admiralty {
    val sources = listOf("A", "B", "C", "D", "E", "F")
    val credibility = listOf("1", "2", "3", "4", "5", "6")

    private val sourceText = mapOf(
        "A" to "完全可靠",
        "B" to "通常可靠",
        "C" to "较为可靠",
        "D" to "不太可靠",
        "E" to "不可靠",
        "F" to "无法判断"
    )

    private val credibilityText = mapOf(
        "1" to "已确认",
        "2" to "可能为真",
        "3" to "可能为假",
        "4" to "存疑",
        "5" to "不可能",
        "6" to "无法判断"
    )

    /** 合法就返回规范化大写（如 b2 → B2），不合法返回空串，绝不猜。 */
    fun normalize(grade: String): String {
        val text = grade.trim().uppercase(Locale.US)
        if (text.length != 2) return ""
        val sourceOk = sources.any { it[0] == text[0] }
        val credibilityOk = credibility.any { it[0] == text[1] }
        return if (sourceOk && credibilityOk) text else ""
    }

    /** 把 B2 读成人话：通常可靠 · 可能为真。 */
    fun describe(grade: String): String {
        val normalized = normalize(grade)
        if (normalized.isEmpty()) return ""
        val source = sourceText[normalized.substring(0, 1)].orEmpty()
        val credibility = credibilityText[normalized.substring(1, 2)].orEmpty()
        return "$source · $credibility"
    }
}

/** 一条数据本记录。编号 L-001 起，只增不改：删掉的号不复用，报告引用不会错位。 */
data class LedgerRecord(
    val id: String,
    val kind: RecordKind,
    /** 字段名，如「手机号」「B站 UID」「常去健身房」。不叫 field：会撞 getter 里的 field 关键字 */
    val fieldName: String,
    /** 字段值，原样保存，不做任何清洗 */
    val value: String,
    /** 原始出处：URL / 页面标题 / 文件名。空表示看到了但没留证 */
    val sourceUrl: String = "",
    /** Admiralty 评级，如 B2；空表示未评 */
    val grade: String = "",
    val confidence: Int = Confidence.MEDIUM,
    val status: Int = RecordStatus.PENDING,
    val at: Long = 0L,
    val tags: List<String> = emptyList(),
    val note: String = "",
    /** 本地截图路径，空表示没附 */
    val shotPath: String = ""
) {
    /** 界面上这一条的主标题：有字段名用字段名，没填就用分类名。 */
    val title: String get() = fieldName.ifBlank { kind.label }

    /** 参与搜索的全部文本。 */
    val searchBlob: String
        get() = buildString {
            append(kind.label).append(' ')
            append(fieldName).append(' ')
            append(value).append(' ')
            append(sourceUrl).append(' ')
            append(grade).append(' ')
            append(note).append(' ')
            append(tags.joinToString(" "))
        }
}

/** 同一字段出现两个不同值：红队最该被追问的地方，界面必须主动标出来。 */
data class LedgerConflict(
    val field: String,
    val values: List<String>,
    val ids: List<String>
)

/** 数据本概览：计数 + 冲突 + 覆盖缺口。 */
data class LedgerStats(
    val total: Int,
    val confirmed: Int,
    val pending: Int,
    val refuted: Int,
    val conflicts: List<LedgerConflict>,
    val missing: List<String>
)

/**
 * 数据本存储：按目标（personId）分组，SharedPreferences 存 JSON。
 *
 * 不引入数据库：单人单机、记录量几百条，一次性读写毫无压力，
 * 而且导出/导入天然就是这份 JSON 本身。
 */
object LedgerStore {
    private const val PREF = "sj_ledger"

    /** 交档前该填齐的几类；缺哪类，界面直接点名。 */
    private val COVERAGE = listOf(
        RecordKind.IDENTITY to "真实身份",
        RecordKind.CONTACT to "联系方式",
        RecordKind.ORG to "单位/学校",
        RecordKind.LOCATION to "城市/场所",
        RecordKind.TIMELINE to "作息时间线",
        RecordKind.RELATION to "关系人"
    )

    private fun listKey(personId: Long) = "lg_$personId"
    private fun seqKey(personId: Long) = "lgseq_$personId"
    private fun migratedKey(personId: Long) = "lgmig_$personId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- 读 ----------

    /** 某个目标的全部记录，按时间倒序。首次调用会把老的证据/笔记迁进来。 */
    fun list(context: Context, personId: Long): List<LedgerRecord> {
        migrateLegacy(context, personId)
        return read(context, personId).sortedByDescending { it.at }
    }

    /** 全部目标：personId → 记录。用于「数据本 · 全部目标」与备份。 */
    fun all(context: Context): Map<Long, List<LedgerRecord>> {
        val result = LinkedHashMap<Long, List<LedgerRecord>>()
        prefs(context).all.keys
            .filter { it.startsWith("lg_") && it.removePrefix("lg_").toLongOrNull() != null }
            .mapNotNull { it.removePrefix("lg_").toLongOrNull() }
            .sorted()
            .forEach { personId ->
                val records = list(context, personId)
                if (records.isNotEmpty()) result[personId] = records
            }
        return result
    }

    private fun read(context: Context, personId: Long): List<LedgerRecord> {
        val raw = prefs(context).getString(listKey(personId), null) ?: return emptyList()
        return runCatching { decodeArray(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    // ---------- 写 ----------

    fun add(context: Context, personId: Long, record: LedgerRecord): List<LedgerRecord> {
        val current = read(context, personId).toMutableList()
        val seq = prefs(context).getInt(seqKey(personId), 0) + 1
        prefs(context).edit().putInt(seqKey(personId), seq).apply()
        current += record.copy(
            id = "L-" + seq.toString().padStart(3, '0'),
            fieldName = record.fieldName.trim(),
            value = record.value.trim(),
            sourceUrl = record.sourceUrl.trim(),
            grade = Admiralty.normalize(record.grade),
            note = record.note.trim(),
            tags = record.tags.map { it.trim() }.filter { it.isNotEmpty() },
            at = if (record.at > 0L) record.at else System.currentTimeMillis()
        )
        persist(context, personId, current)
        return list(context, personId)
    }

    /** 改一条：只覆盖可编辑字段，编号与采集时间保持原样。 */
    fun update(context: Context, personId: Long, record: LedgerRecord): List<LedgerRecord> {
        val current = read(context, personId).map { existing ->
            if (existing.id != record.id) {
                existing
            } else {
                record.copy(
                    fieldName = record.fieldName.trim(),
                    value = record.value.trim(),
                    sourceUrl = record.sourceUrl.trim(),
                    grade = Admiralty.normalize(record.grade),
                    note = record.note.trim(),
                    tags = record.tags.map { it.trim() }.filter { it.isNotEmpty() },
                    // 采集时间属于事实本身，改字段名/备注不该把它抹掉
                    at = if (existing.at > 0L) existing.at else System.currentTimeMillis()
                )
            }
        }
        persist(context, personId, current)
        return list(context, personId)
    }

    fun setStatus(
        context: Context,
        personId: Long,
        id: String,
        status: Int
    ): List<LedgerRecord> {
        val current = read(context, personId).map {
            if (it.id == id) it.copy(status = status) else it
        }
        persist(context, personId, current)
        return list(context, personId)
    }

    fun remove(context: Context, personId: Long, id: String): List<LedgerRecord> {
        persist(context, personId, read(context, personId).filterNot { it.id == id })
        return list(context, personId)
    }


    /** 清空全部目标的数据本（导入备份前、或用户主动清库时用）。 */
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 导入备份用：整组替换，编号按序号续接，不会和已有记录撞号。 */
    fun replaceAll(context: Context, data: Map<Long, List<LedgerRecord>>) {
        val editor = prefs(context).edit()
        data.forEach { (personId, records) ->
            editor.putString(listKey(personId), encodeArray(records).toString())
            val maxSeq = records.mapNotNull {
                it.id.removePrefix("L-").toIntOrNull()
            }.maxOrNull() ?: 0
            editor.putInt(seqKey(personId), maxSeq)
            editor.putBoolean(migratedKey(personId), true)
        }
        editor.apply()
    }

    private fun persist(context: Context, personId: Long, records: List<LedgerRecord>) {
        prefs(context).edit()
            .putString(listKey(personId), encodeArray(records).toString())
            .apply()
    }

    // ---------- 序列化 ----------

    fun encodeArray(records: List<LedgerRecord>): JSONArray {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("kind", record.kind.name)
                    put("field", record.fieldName)
                    put("value", record.value)
                    put("src", record.sourceUrl)
                    put("grade", record.grade)
                    put("conf", record.confidence)
                    put("status", record.status)
                    put("at", record.at)
                    put("tags", JSONArray(record.tags))
                    put("note", record.note)
                    put("shot", record.shotPath)
                }
            )
        }
        return array
    }

    fun decodeArray(array: JSONArray): List<LedgerRecord> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tagsArray = o.optJSONArray("tags") ?: JSONArray()
            add(
                LedgerRecord(
                    id = o.optString("id"),
                    kind = RecordKind.fromName(o.optString("kind")),
                    fieldName = o.optString("field"),
                    value = o.optString("value"),
                    sourceUrl = o.optString("src"),
                    grade = o.optString("grade"),
                    confidence = o.optInt("conf", Confidence.MEDIUM),
                    status = o.optInt("status", RecordStatus.PENDING),
                    at = o.optLong("at"),
                    tags = buildList {
                        for (j in 0 until tagsArray.length()) add(tagsArray.optString(j))
                    },
                    note = o.optString("note"),
                    shotPath = o.optString("shot")
                )
            )
        }
    }

    // ---------- 分析 ----------

    fun stats(records: List<LedgerRecord>): LedgerStats {
        val live = records.filter { it.status != RecordStatus.REFUTED }
        val conflictGroups = live
            .filter { it.fieldName.isNotBlank() && it.value.isNotBlank() }
            .groupBy { it.fieldName.trim().lowercase(Locale.US) }
            .filter { (_, group) ->
                group.map { it.value.trim().lowercase(Locale.US) }.distinct().size > 1
            }
        val conflicts = conflictGroups.map { (_, group) ->
            LedgerConflict(
                field = group.first().fieldName.trim(),
                values = group.map { it.value.trim() }.distinct(),
                ids = group.map { it.id }
            )
        }
        val covered = records.map { it.kind }.toSet()
        return LedgerStats(
            total = records.size,
            confirmed = records.count { it.status == RecordStatus.CONFIRMED },
            pending = records.count { it.status == RecordStatus.PENDING },
            refuted = records.count { it.status == RecordStatus.REFUTED },
            conflicts = conflicts,
            missing = COVERAGE.filterNot { (kind, _) -> kind in covered }.map { it.second }
        )
    }

    // ---------- 导出 ----------

    /** 单目标 Markdown 简报：交档/汇报直接贴。 */
    fun buildMarkdown(
        personName: String,
        records: List<LedgerRecord>,
        generatedAt: Long = System.currentTimeMillis()
    ): String = buildMarkdown(listOf(personName.ifBlank { "未命名" } to records), generatedAt)

    /** 多目标合并简报：每段一个目标。 */
    fun buildMarkdown(
        targets: List<Pair<String, List<LedgerRecord>>>,
        generatedAt: Long = System.currentTimeMillis()
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val allRecords = targets.flatMap { it.second }
        val stats = stats(allRecords)
        val builder = StringBuilder()

        builder.append("# 数据本\n\n")
        builder.append("- 生成时间：").append(fmt.format(Date(generatedAt))).append('\n')
        builder.append("- 目标数：").append(targets.count { it.second.isNotEmpty() }).append('\n')
        builder.append("- 记录总数：").append(stats.total)
        builder.append("（已证实 ").append(stats.confirmed)
        builder.append(" / 待验证 ").append(stats.pending)
        builder.append(" / 已推翻 ").append(stats.refuted).append("）\n\n")

        targets.filter { it.second.isNotEmpty() }.forEachIndexed { index, (name, records) ->
            val targetStats = stats(records)
            builder.append("## ").append(index + 1).append(". ")
                .append(name.ifBlank { "未命名" }).append('\n')
            builder.append("- 记录 ").append(targetStats.total)
            builder.append("（已证实 ").append(targetStats.confirmed)
            builder.append(" / 待验证 ").append(targetStats.pending)
            builder.append(" / 已推翻 ").append(targetStats.refuted).append("）\n\n")

            appendSection(builder, "已证实", records.filter { it.status == RecordStatus.CONFIRMED }, fmt)
            appendSection(builder, "待验证", records.filter { it.status == RecordStatus.PENDING }, fmt)
            appendSection(builder, "已推翻", records.filter { it.status == RecordStatus.REFUTED }, fmt)

            if (targetStats.conflicts.isNotEmpty()) {
                builder.append("### 冲突\n\n")
                targetStats.conflicts.forEach { conflict ->
                    builder.append("- 「").append(conflict.field).append("」出现 ")
                        .append(conflict.values.size).append(" 个不同值：")
                        .append(conflict.values.joinToString(" ／ "))
                        .append("（").append(conflict.ids.joinToString(", ")).append("）\n")
                }
                builder.append('\n')
            }
            val timeline = records.filter { it.at > 0L }.sortedBy { it.at }
            if (timeline.isNotEmpty()) {
                builder.append("### 时间线\n\n")
                timeline.forEach { record ->
                    builder.append("- ").append(fmt.format(Date(record.at))).append("  ")
                        .append(record.id).append(" · ").append(record.kind.label)
                        .append(" · ").append(record.title).append("：").append(record.value)
                        .append("\n")
                }
                builder.append('\n')
            }

            if (targetStats.missing.isNotEmpty()) {
                builder.append("### 缺口\n\n")
                builder.append("- 尚未登记：").append(targetStats.missing.joinToString("、")).append('\n')
                builder.append('\n')
            }
        }

        if (targets.all { it.second.isEmpty() }) {
            builder.append("（数据本为空）\n")
        }
        return builder.toString()
    }

    private fun appendSection(
        builder: StringBuilder,
        title: String,
        records: List<LedgerRecord>,
        fmt: SimpleDateFormat
    ) {
        if (records.isEmpty()) return
        builder.append("### ").append(title).append('\n')
        records.sortedBy { it.id }.forEach { record ->
            builder.append("- **").append(record.id).append(" · ")
                .append(record.kind.label).append(" · ")
                .append(record.title).append("**：").append(record.value).append('\n')
            if (record.grade.isNotBlank()) {
                builder.append("  - 评级 ").append(record.grade)
                val readable = Admiralty.describe(record.grade)
                if (readable.isNotEmpty()) builder.append("（").append(readable).append("）")
                builder.append('\n')
            }
            builder.append("  - 置信度 ").append(Confidence.label(record.confidence)).append('\n')
            if (record.sourceUrl.isNotBlank()) {
                builder.append("  - 出处 ").append(record.sourceUrl).append('\n')
            }
            if (record.tags.isNotEmpty()) {
                builder.append("  - 标签 ").append(record.tags.joinToString(" / ")).append('\n')
            }
            if (record.note.isNotBlank()) {
                builder.append("  - 备注 ").append(record.note).append('\n')
            }
            if (record.at > 0L) {
                builder.append("  - 采集 ").append(fmt.format(Date(record.at))).append('\n')
            }
            if (record.shotPath.isNotBlank()) {
                builder.append("  - 截图 ").append(record.shotPath).append('\n')
            }
        }
        builder.append('\n')
    }

    // ---------- 老数据迁移 ----------

    /**
     * 旧版本把内容拆成了三处：证据条目（sj_evidence）、速记（sj_quicknotes）、结论（sj_notes）。
     * 合并进数据本，一次一人，迁完打标记，不重复迁。原始数据保持不动，不删任何东西。
     */
    private fun migrateLegacy(context: Context, personId: Long) {
        val store = prefs(context)
        if (store.getBoolean(migratedKey(personId), false)) return
        store.edit().putBoolean(migratedKey(personId), true).apply()

        val migrated = mutableListOf<LedgerRecord>()
        var seq = store.getInt(seqKey(personId), 0)

        fun nextId(): String {
            seq += 1
            return "L-" + seq.toString().padStart(3, '0')
        }

        // 证据条目 → 结构化记录（保留原编号与哈希，写进备注）
        val evidencePrefs = context.getSharedPreferences("sj_evidence", Context.MODE_PRIVATE)
        val evidenceArray = runCatching {
            JSONArray(evidencePrefs.getString("ev_$personId", "[]"))
        }.getOrNull()
        if (evidenceArray != null) {
            for (i in 0 until evidenceArray.length()) {
                val o = evidenceArray.optJSONObject(i) ?: continue
                val legacyId = o.optString("id")
                val noteParts = buildList {
                    if (o.optString("note").isNotBlank()) add(o.optString("note"))
                    if (legacyId.isNotBlank()) add("迁移自证据条目 $legacyId")
                    if (o.optString("sha").isNotBlank()) add("SHA-256 ${o.optString("sha")}")
                }
                migrated += LedgerRecord(
                    id = nextId(),
                    kind = RecordKind.OTHER,
                    fieldName = "证据 ${legacyId.ifBlank { "-" }}",
                    value = o.optString("note").ifBlank { o.optString("source") },
                    sourceUrl = o.optString("source"),
                    grade = o.optString("grade"),
                    confidence = Confidence.MEDIUM,
                    status = RecordStatus.PENDING,
                    at = o.optLong("at"),
                    note = noteParts.joinToString("｜"),
                    shotPath = o.optString("photo")
                )
            }
        }

        // 待验证清单 → 记录，状态映射 0/1/2 一一对应
        val todoArray = runCatching {
            JSONArray(evidencePrefs.getString("todo_$personId", "[]"))
        }.getOrNull()
        if (todoArray != null) {
            for (i in 0 until todoArray.length()) {
                val o = todoArray.optJSONObject(i) ?: continue
                migrated += LedgerRecord(
                    id = nextId(),
                    kind = RecordKind.OTHER,
                    fieldName = "待验证 " + o.optString("id"),
                    value = o.optString("text"),
                    confidence = Confidence.LOW,
                    status = o.optInt("status", RecordStatus.PENDING)
                        .coerceIn(RecordStatus.PENDING, RecordStatus.REFUTED),
                    at = System.currentTimeMillis(),
                    note = o.optString("check").let {
                        if (it.isBlank()) "迁移自待验证清单" else "查：$it"
                    }
                )
            }
        }

        // 速记 → 记录，类型映射到分类
        val quickPrefs = context.getSharedPreferences("sj_quicknotes", Context.MODE_PRIVATE)
        val quickArray = runCatching {
            JSONArray(quickPrefs.getString("qn_$personId", "[]"))
        }.getOrNull()
        if (quickArray != null) {
            for (i in 0 until quickArray.length()) {
                val o = quickArray.optJSONObject(i) ?: continue
                val legacyKind = o.optString("kind")
                migrated += LedgerRecord(
                    id = nextId(),
                    kind = when (legacyKind) {
                        "发布", "删除", "改名" -> RecordKind.CONTENT
                        else -> RecordKind.OTHER
                    },
                    fieldName = legacyKind.ifBlank { "速记" },
                    value = o.optString("text"),
                    confidence = Confidence.LOW,
                    status = RecordStatus.CONFIRMED,
                    at = o.optLong("at"),
                    note = "迁移自速记"
                )
            }
        }

        // 结论 → 一条记录，字段名就叫「结论」
        val conclusion = context.getSharedPreferences("sj_notes", Context.MODE_PRIVATE)
            .getString("note_$personId", "").orEmpty()
        if (conclusion.isNotBlank()) {
            migrated += LedgerRecord(
                id = nextId(),
                kind = RecordKind.OTHER,
                fieldName = "结论",
                value = conclusion.trim(),
                confidence = Confidence.MEDIUM,
                status = RecordStatus.CONFIRMED,
                at = System.currentTimeMillis(),
                note = "迁移自笔记"
            )
        }

        if (migrated.isEmpty()) return
        val existing = read(context, personId)
        prefs(context).edit()
            .putString(listKey(personId), encodeArray(existing + migrated).toString())
            .putInt(seqKey(personId), seq)
            .apply()
    }
}
