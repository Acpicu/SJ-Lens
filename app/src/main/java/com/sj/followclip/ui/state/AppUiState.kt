package com.sj.followclip.ui.state

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.sj.followclip.data.local.JsonStore
import com.sj.followclip.data.local.LedgerStore
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.local.WebProfileStore
import com.sj.followclip.data.source.bili.BiliSource
import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform

/**
 * 界面状态中枢：内存列表 + 每次改动同步落盘。
 *
 * - 用 SnapshotStateList 承载，任何改动都让读它的界面自动重组；
 * - 每次增删改后立刻写回 JSON 文件，杀进程也不丢；
 * - 启动时先读 JSON，读不到（首次安装）才用假数据铺满并落盘。
 */
object AppUiState {

    val persons = mutableStateListOf<Person>()

    private var appContext: Context? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        BiliSource.init(context)
        PendingStore.init(context)

        // 首次启动就是空的，由用户自己建文件夹；
        // 假数据只留在 @Preview 里，绝不进真实运行环境。
        val backup = JsonStore.loadBackup(context)
        if (backup != null && backup.persons.isNotEmpty()) {
            persons.addAll(backup.persons)
            if (backup.ledger.isNotEmpty()) LedgerStore.replaceAll(context, backup.ledger)
        }
    }

    private fun persist() {
        appContext?.let { ctx ->
            JsonStore.save(ctx, persons.toList(), LedgerStore.all(ctx))
        }
    }

    fun addPerson(remarkName: String): Person {
        val newId = (persons.maxOfOrNull { it.id } ?: 0L) + 1
        val person = Person(id = newId, remarkName = remarkName.trim(), accounts = emptyList())
        persons.add(0, person)
        persist()
        return person
    }

    fun addAccount(personId: Long, platform: Platform, label: String, homepageUrl: String): Boolean {
        val index = persons.indexOfFirst { it.id == personId }
        if (index < 0) return false

        val person = persons[index]
        val newAccountId = (persons.flatMap { it.accounts }.maxOfOrNull { it.id } ?: 100L) + 1
        val account = Account(
            id = newAccountId,
            platform = platform,
            label = label.trim(),
            homepageUrl = homepageUrl.trim(),
            unreadCount = 0,
            lastFetchAt = System.currentTimeMillis()
        )
        persons[index] = person.copy(accounts = person.accounts + account)
        persist()
        return true
    }

    fun removePerson(personId: Long) {
        persons.removeAll { it.id == personId }
        persist()
    }

    fun removeAccount(personId: Long, accountId: Long) {
        val index = persons.indexOfFirst { it.id == personId }
        if (index < 0) return
        val person = persons[index]
        persons[index] = person.copy(
            accounts = person.accounts.filterNot { it.id == accountId }
        )
        persist()
    }

    /** 导出成 JSON 文本，供用户自己保存备份。数据本记录一起带走。 */
    fun exportJson(context: Context): String =
        JsonStore.encode(persons.toList(), LedgerStore.all(context))

    /**
     * 从 JSON 文本恢复数据（文件夹 + 数据本）。
     * 返回导入的文件夹数量；解析失败返回 -1，由界面提示用户。
     */
    fun importJson(context: Context, text: String): Int {
        val backup = JsonStore.decodeBackup(text) ?: return -1
        if (backup.persons.isEmpty()) return 0
        persons.clear()
        persons.addAll(backup.persons)
        // 覆盖式恢复：先清空旧数据本，避免上一份目标的记录残留
        LedgerStore.clearAll(context)
        WebProfileStore.clearAll(context)
        if (backup.ledger.isNotEmpty()) LedgerStore.replaceAll(context, backup.ledger)
        persist()
        return backup.persons.size
    }

    /** 清空全部本地数据：文件夹、账号、待确认、抓取快照、数据本、旧版笔记与证据。 */
    fun clearAllData(context: Context) {
        persons.clear()
        persist()
        PendingStore.clearAll(context)
        context.getSharedPreferences("video_snapshots", Context.MODE_PRIVATE)
            .edit().clear().apply()
        LedgerStore.clearAll(context)
        WebProfileStore.clearAll(context)
        // 旧版三处数据也跟着清：升级过的机器上可能还留着，别让用户以为清干净了
        context.getSharedPreferences("sj_notes", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("sj_evidence", Context.MODE_PRIVATE)
            .edit().clear().apply()
        context.getSharedPreferences("sj_quicknotes", Context.MODE_PRIVATE)
            .edit().clear().apply()
        runCatching {
            java.io.File(context.filesDir, "evidence").deleteRecursively()
        }
        runCatching {
            java.io.File(context.filesDir, "ledger").deleteRecursively()
        }
    }

    /** 设置某个账号的「几天提醒一次」。days = 0 表示关掉。 */    fun setReminder(personId: Long, accountId: Long, days: Int) {
        val index = persons.indexOfFirst { it.id == personId }
        if (index < 0) return
        val person = persons[index]
        persons[index] = person.copy(
            accounts = person.accounts.map {
                if (it.id == accountId) it.copy(remindEveryDays = days) else it
            }
        )
        persist()
    }

    /** 记录一次已提醒，避免同一天重复提醒。 */
    fun markReminded(personId: Long, accountId: Long) {
        val index = persons.indexOfFirst { it.id == personId }
        if (index < 0) return
        val person = persons[index]
        persons[index] = person.copy(
            accounts = person.accounts.map {
                if (it.id == accountId) it.copy(lastRemindAt = System.currentTimeMillis()) else it
            }
        )
        persist()
    }

    /** 拖动排序后调用：把第 from 个挪到第 to 个位置并落盘。 */
    fun movePerson(from: Int, to: Int) {
        if (from == to) return
        if (from !in persons.indices || to !in persons.indices) return
        val item = persons.removeAt(from)
        persons.add(to, item)
        persist()
    }
    /** 更新某个账号的未读数，首页卡片角标会跟着变。同时落盘。 */
    fun setUnread(personId: Long, accountId: Long, count: Int) {
        val index = persons.indexOfFirst { it.id == personId }
        if (index < 0) return
        val person = persons[index]
        val updated = person.accounts.map {
            if (it.id == accountId) it.copy(unreadCount = count, lastFetchAt = System.currentTimeMillis()) else it
        }
        persons[index] = person.copy(accounts = updated)
        persist()
    }
    fun findPerson(personId: Long): Person? = persons.firstOrNull { it.id == personId }
}
