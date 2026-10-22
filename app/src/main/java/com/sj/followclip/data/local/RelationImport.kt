package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONObject

/**
 * 关系网（GuangXIW）导出的关系摘要导入器。
 *
 * GuangXIW 在电脑端导出的 JSON 形如：
 * ```
 * {"kind":"guangxiw.relations","folder":"某公司","persons":[
 *   {"name":"张三","relations":[{"to":"李四","type":"同学","strength":3,"evidence":"E-003","note":""}]}
 * ]}
 * ```
 * 这里只取**当前这个人**的关系，写成 RELATION 记录进数据本，重复导入会更新而不是堆叠。
 */
object RelationImport {

    data class Relation(
        val to: String,
        val type: String,
        val strength: Int,
        val evidence: String,
        val note: String
    )

    /** 解析出某个人的关系列表；文件不合法或没有这个人时返回空表。 */
    fun parseFor(json: String, personName: String): List<Relation> {
        return runCatching {
            val root = JSONObject(json)
            if (root.optString("kind") != "guangxiw.relations") return emptyList()
            val persons = root.optJSONArray("persons") ?: return emptyList()
            val out = mutableListOf<Relation>()
            for (i in 0 until persons.length()) {
                val p = persons.optJSONObject(i) ?: continue
                val name = p.optString("name")
                if (name != personName) continue
                val rels = p.optJSONArray("relations") ?: continue
                for (j in 0 until rels.length()) {
                    val r = rels.optJSONObject(j) ?: continue
                    out += Relation(
                        to = r.optString("to"),
                        type = r.optString("type", "其他"),
                        strength = r.optInt("strength", 3),
                        evidence = r.optString("evidence"),
                        note = r.optString("note")
                    )
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    /** 写进数据本：同名同类型的旧记录先删掉，保证反复导入是「更新」不是「堆积」。 */
    fun import(context: Context, personId: Long, personName: String, json: String): Int {
        val rels = parseFor(json, personName)
        if (rels.isEmpty()) return 0
        val existing = LedgerStore.all(context)[personId].orEmpty()
        val keep = existing.filterNot { it.kind == RecordKind.RELATION && it.fieldName.startsWith("关系·") }
        LedgerStore.replaceAll(context, mapOf(personId to keep))
        rels.forEach { r ->
            val value = buildString {
                append(r.to)
                append("（强度 ").append(r.strength).append("）")
                if (r.note.isNotBlank()) append(" · ").append(r.note)
            }
            LedgerStore.add(
                context, personId,
                LedgerRecord(
                    id = "",
                    kind = RecordKind.RELATION,
                    fieldName = "关系·${r.type}",
                    value = value,
                    sourceUrl = if (r.evidence.isBlank()) "guangxiw" else "guangxiw ${r.evidence}",
                    grade = "B2",
                    confidence = Confidence.HIGH,
                    status = RecordStatus.PENDING
                )
            )
        }
        return rels.size
    }
}
