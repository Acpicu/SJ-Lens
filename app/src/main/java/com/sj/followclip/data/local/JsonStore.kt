package com.sj.followclip.data.local

import android.content.Context
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据落盘：把文件夹（人物）和它下面的平台账号存成一份 JSON 文件。
 *
 * 为什么不用 Room：
 * 1. 单人工具，数据量就几十个文件夹，JSON 读写一次全量，毫无压力；
 * 2. 不引入 KSP / 注解处理器，少一层版本匹配风险；
 * 3. 导出/导入天然就是「文件本身」，不需要额外迁移逻辑。
 *
 * 头像不在这里存：头像按人物 id 存成 avatar_<id>.jpg（见 AvatarStore），
 * 这里只存账号本身的数据。
 */
object JsonStore {

    private const val FILE_NAME = "folders.json"

    /** 导出用的格式版本，以后改结构时靠它做兼容。 */
    private const val FORMAT_VERSION = 1

    /** 把人物列表编码成可读 JSON，供备份/导出使用。 */
    fun encode(persons: List<Person>, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONArray()
        persons.forEach { person ->
            val accounts = JSONArray()
            person.accounts.forEach { account ->
                val ao = JSONObject()
                ao.put("id", account.id)
                ao.put("platform", account.platform.name)
                ao.put("label", account.label)
                ao.put("url", account.homepageUrl)
                ao.put("unread", account.unreadCount)
                ao.put("lastFetchAt", account.lastFetchAt)
                account.followerCount?.let { ao.put("followers", it) }
                account.videoCount?.let { ao.put("videos", it) }
                ao.put("remind", account.remindEveryDays)
                ao.put("lastRemind", account.lastRemindAt)
                accounts.put(ao)
            }
            val po = JSONObject()
            po.put("id", person.id)
            po.put("name", person.remarkName)
            po.put("accounts", accounts)
            root.put(po)
        }

        val wrapper = JSONObject()
        wrapper.put("app", "SJ Lens")
        wrapper.put("version", FORMAT_VERSION)
        wrapper.put("exportedAt", exportedAt)
        wrapper.put("persons", root)
        return wrapper.toString(2)
    }

    /**
     * 从 JSON 还原人物列表。
     * 兼容两种格式：带外层包装的导出文件，以及早期直接存数组的 folders.json。
     */
    fun decode(text: String): List<Person>? = runCatching {
        val trimmed = text.trim()
        val root = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).getJSONArray("persons")
        }

        buildList {
            for (i in 0 until root.length()) {
                val po = root.getJSONObject(i)
                val accountsArr = po.optJSONArray("accounts") ?: JSONArray()
                val accounts = buildList {
                    for (j in 0 until accountsArr.length()) {
                        val ao = accountsArr.getJSONObject(j)
                        add(
                            Account(
                                id = ao.getLong("id"),
                                platform = runCatching { Platform.valueOf(ao.getString("platform")) }
                                    .getOrDefault(Platform.BILI),
                                label = ao.optString("label"),
                                homepageUrl = ao.optString("url"),
                                unreadCount = ao.optInt("unread"),
                                lastFetchAt = ao.optLong("lastFetchAt"),
                                followerCount = ao.takeIf { it.has("followers") }?.getLong("followers"),
                                videoCount = ao.takeIf { it.has("videos") }?.getInt("videos"),
                                remindEveryDays = ao.optInt("remind"),
                                lastRemindAt = ao.optLong("lastRemind")
                            )
                        )
                    }
                }
                add(
                    Person(
                        id = po.getLong("id"),
                        remarkName = po.getString("name"),
                        accounts = accounts
                    )
                )
            }
        }
    }.getOrNull()

    fun save(context: Context, persons: List<Person>) {
        java.io.File(context.filesDir, FILE_NAME).writeText(encode(persons))
    }

    /** 读不到、或格式损坏时返回 null。 */
    fun load(context: Context): List<Person>? = runCatching {
        val file = java.io.File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val loaded = decode(file.readText()) ?: return null
        loaded.ifEmpty { null }
    }.getOrNull()
}
