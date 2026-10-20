import pathlib
J = pathlib.Path(r"D:\桌面\SJ Lens\app\src\main\java\com\sj\followclip")

# ---- 1) 记录新增 sha256 / accountRef ----
p = J/"data/local/LedgerStore.kt"
t = p.read_text(encoding="utf-8")
t = t.replace('''    /** 本地截图路径，空表示没附 */
    val shotPath: String = ""''',
'''    /** 本地截图路径，空表示没附 */
    val shotPath: String = "",
    /** 截图指纹，交档时用来证明这张图没被换过 */
    val sha256: String = "",
    /** 关联到哪个平台账号（数据本里的 ACCOUNT 记录用） */
    val accountRef: Long = 0L''')
t = t.replace('''                    put("shot", record.shotPath)''',
'''                    put("shot", record.shotPath)
                    put("sha", record.sha256)
                    put("acc", record.accountRef)''')
t = t.replace('''                    shotPath = o.optString("shot")''',
'''                    shotPath = o.optString("shot"),
                    sha256 = o.optString("sha"),
                    accountRef = o.optLong("acc")''')
# 报告里补 SHA 与关联账号
t = t.replace('''            if (record.shotPath.isNotBlank()) {
                builder.append("  - 截图 ").append(record.shotPath).append('\\n')
            }''',
'''            if (record.sha256.isNotBlank()) {
                builder.append("  - 截图 SHA-256 ").append(record.sha256).append('\\n')
            }
            if (record.shotPath.isNotBlank()) {
                builder.append("  - 截图 ").append(record.shotPath).append('\\n')
            }''')
p.write_text(t, encoding="utf-8")
print("store patched")
