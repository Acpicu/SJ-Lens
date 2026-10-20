package com.sj.followclip.util

import java.io.File
import java.security.MessageDigest

/** 文件 SHA-256：截图入库要留指纹，红队交档时能证明"这张图没被换过"。 */
object FileHash {
    fun sha256(file: File): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}
