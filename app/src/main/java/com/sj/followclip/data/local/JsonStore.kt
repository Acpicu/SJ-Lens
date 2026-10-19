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
    private const val FORMAT_VERSION = 2

    /** 一份完整备份：目标（含平台账号）+ 各自的数据本记录。 */
    data class Backup(
        val persons: List<Person>,
        val ledger: Map<Long, List<LedgerRecord>>
    )

    /** 把人物列表编码成可读 JSON，供备份/导出使用。数据本记录嵌在每个人物对象里。 */
    fun encode(
        persons: List<Person>,
        ledger: Map<Long, List<LedgerRecord>> = emptyMap(),
        exportedAt: Long = System.currentTimeMillis()
    ): String {
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
            val records = ledger[person.id].orEmpty()
            if (records.isNotEmpty()) {
                po.put("ledger", LedgerStore.encodeArray(records))
            }
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
     * 从 JSON 还原完整备份：人物列表 + 数据本记录。
     * 兼容两种格式：带外层包装的导出文件，以及早期直接存数组的 folders.json（没有 ledger 字段）。
     */
    fun decodeBackup(text: String): Backup? = runCatching {
        val trimmed = text.trim()
        val root = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).getJSONArray("persons")
        }

        val persons = mutableListOf<Person>()
        val ledger = LinkedHashMap<Long, List<LedgerRecord>>()

        for (i in 0 until root.length()) {
            val po = root.getJSONObject(i)
            val personId = po.getLong("id")
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
            persons += Person(
                id = personId,
                remarkName = po.getString("name"),
                accounts = accounts
            )
            po.optJSONArray("ledger")?.let { array ->
                val records = LedgerStore.decodeArray(array)
                if (records.isNotEmpty()) ledger[personId] = records
            }
        }
        Backup(persons = persons, ledger = ledger)
    }.getOrNull()

    /** 只要人物列表时用这个（老调用点保持可用）。 */
    fun decode(text: String): List<Person>? = decodeBackup(text)?.persons

    fun save(
        context: Context,
        persons: List<Person>,
        ledger: Map<Long, List<LedgerRecord>> = emptyMap()
    ) {
        java.io.File(context.filesDir, FILE_NAME).writeText(encode(persons, ledger))
    }

    /** 读不到、或格式损坏时返回 null。 */
    fun loadBackup(context: Context): Backup? = runCatching {
        val file = java.io.File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val backup = decodeBackup(file.readText()) ?: return null
        backup.takeIf { it.persons.isNotEmpty() }
    }.getOrNull()

    fun load(context: Context): List<Person>? = loadBackup(context)?.persons
}
