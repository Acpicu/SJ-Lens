import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---- 2) LedgerStore: HTML 简报 ----
p = J/"data/local/LedgerStore.kt"
t = p.read_text(encoding="utf-8")
anchor = "    // ---------- 老数据迁移 ----------"
html = '''    /** 交档用的单页 HTML：比赛/汇报时直接丢浏览器里演示，不再依赖 Markdown 渲染。 */
    fun buildHtml(
        personName: String,
        records: List<LedgerRecord>,
        generatedAt: Long = System.currentTimeMillis()
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val stats = stats(records)
        val esc = { s: String -> s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") }
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\\"zh-CN\\"><head><meta charset=\\"utf-8\\">")
        sb.append("<meta name=\\"viewport\\" content=\\"width=device-width,initial-scale=1\\">")
        sb.append("<title>数据本 · ").append(esc(personName.ifBlank { "未命名" })).append("</title>")
        sb.append("<style>")
        sb.append("body{margin:0;background:#0d1117;color:#e6edf3;font:15px/1.6 -apple-system,Segoe UI,Roboto,sans-serif}")
        sb.append("header{padding:28px 20px;border-bottom:1px solid #1d252d}")
        sb.append("h1{margin:0;font-size:22px;letter-spacing:2px}")
        sb.append("h2{margin:28px 20px 8px;font-size:15px;color:#5bc8e8;letter-spacing:1px}")
        sb.append(".meta{color:#96a4b2;font-size:12px;margin-top:6px}")
        sb.append(".card{margin:10px 20px;padding:14px 16px;background:#151b22;border:1px solid #1d252d;border-radius:14px}")
        sb.append(".id{color:#5bc8e8;font-size:12px}.k{color:#96a4b2;font-size:12px}")
        sb.append(".v{font-size:15px;margin:2px 0 6px}.tag{display:inline-block;margin-right:6px;padding:1px 8px;border-radius:9px;background:#123039;color:#9bddf0;font-size:11px}")
        sb.append(".warn{color:#f08a8a}.ok{color:#5bc8e8}.tl{color:#96a4b2;font-size:12px}")
        sb.append("</style></head><body><header><h1>数据本 · ").append(esc(personName.ifBlank { "未命名" })).append("</h1>")
        sb.append("<div class=\\"meta\\">生成于 ").append(fmt.format(Date(generatedAt)))
        sb.append(" · 记录 ").append(stats.total).append(" 条（已证实 ").append(stats.confirmed)
        sb.append(" / 待验证 ").append(stats.pending).append(" / 已推翻 ").append(stats.refuted).append("）</div></header>")

        if (stats.conflicts.isNotEmpty()) {
            sb.append("<h2>冲突</h2>")
            stats.conflicts.forEach { c ->
                sb.append("<div class=\\"card\\"><span class=\\"warn\\">「").append(esc(c.field))
                    .append("」出现 ").append(c.values.size).append(" 个不同值：")
                    .append(esc(c.values.joinToString(" ／ "))).append("</span></div>")
            }
        }
        if (stats.missing.isNotEmpty()) {
            sb.append("<h2>缺口</h2><div class=\\"card\\">")
            stats.missing.forEach { sb.append("<span class=\\"tag\\">").append(esc(it)).append("</span>") }
            sb.append("</div>")
        }

        listOf(
            "已证实" to RecordStatus.CONFIRMED,
            "待验证" to RecordStatus.PENDING,
            "已推翻" to RecordStatus.REFUTED
        ).forEach { (label, status) ->
            val list = records.filter { it.status == status }.sortedBy { it.id }
            if (list.isEmpty()) return@forEach
            sb.append("<h2>").append(label).append("（").append(list.size).append("）</h2>")
            list.forEach { r ->
                sb.append("<div class=\\"card\\"><div><span class=\\"id\\">").append(r.id)
                    .append("</span> <span class=\\"k\\">").append(esc(r.kind.label))
                    .append(" · ").append(esc(r.title)).append("</span>")
                if (r.grade.isNotBlank()) {
                    sb.append(" <span class=\\"tag\\">").append(esc(r.grade)).append("</span>")
                }
                sb.append("</div><div class=\\"v\\">").append(esc(r.value)).append("</div>")
                sb.append("<div class=\\"k\\">置信度 ").append(Confidence.label(r.confidence))
                if (r.at > 0L) sb.append(" · ").append(fmt.format(Date(r.at)))
                if (r.sourceUrl.isNotBlank()) sb.append(" · 出处 ").append(esc(r.sourceUrl))
                if (r.tags.isNotEmpty()) sb.append(" · ").append(esc(r.tags.joinToString(" ") { "#$it" }))
                sb.append("</div>")
                if (r.note.isNotBlank()) sb.append("<div class=\\"k\\">").append(esc(r.note)).append("</div>")
                if (r.sha256.isNotBlank()) sb.append("<div class=\\"k\\">截图 SHA-256 ").append(esc(r.sha256)).append("</div>")
                sb.append("</div>")
            }
        }

        val timeline = records.filter { it.at > 0L }.sortedBy { it.at }
        if (timeline.isNotEmpty()) {
            sb.append("<h2>时间线</h2><div class=\\"card\\">")
            timeline.forEach { r ->
                sb.append("<div class=\\"tl\\">").append(fmt.format(Date(r.at))).append(" · ")
                    .append(r.id).append(" · ").append(esc(r.title)).append("：")
                    .append(esc(r.value)).append("</div>")
            }
            sb.append("</div>")
        }

        sb.append("<div class=\\"meta\\" style=\\"margin:24px 20px\\">SJ Lens · 数据本导出 · 仅供授权范围内使用</div>")
        sb.append("</body></html>")
        return sb.toString()
    }

''' + anchor
t = t.replace(anchor, html, 1)
p.write_text(t, encoding="utf-8")
print("buildHtml added")
