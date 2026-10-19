package com.sj.followclip.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.check.CheckRunner
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.util.Notifications

/**
 * 后台自动检查：定期把所有 B 站 账号查一遍，有新内容就发通知。
 *
 * 说明：
 * - 只在有网络时执行；被限流时返回 retry，交给系统按退避策略重排，不硬打接口。
 * - 国内 ROM 普遍会限制后台，需要用户在系统里给「电池优化白名单 / 自启动」，
 *   设置页会提示，不授予就只在前台手动检查。
 */
class CheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // 后台进程里状态可能是空的，先确保数据加载好
        AppUiState.init(applicationContext)

        val persons = AppUiState.persons.toList()
        if (persons.isEmpty()) return Result.success()

        Notifications.startChecking(applicationContext, persons.sumOf { it.accounts.size }.coerceAtLeast(1))

        val outcome = CheckRunner.run(
            context = applicationContext,
            persons = persons,
            activity = null,          // 后台没有窗口：快手会如实跳过，B站/抖音照常查
            includeWeb = true
        ) { done, total, label ->
            Notifications.updateChecking(applicationContext, done, total, label)
        }

        // 后台这一轮同样把结果写进「最近检查」，否则用户回到应用看到的是空的
        if (outcome.error == null) {
            PendingStore.replaceAll(applicationContext, outcome.items.sortedByDescending { it.foundAt })
        }
        outcome.results.forEach { r ->
            AppUiState.setUnread(r.personId, r.accountId, r.newCount)
        }
        Notifications.stopChecking(applicationContext)
        val added = outcome.results.sumOf { it.newCount }
        // 后台这一轮用户看不到界面：给一条「检查报告已生成」，点开就能回到应用看明细
        Notifications.notifyCheckDone(applicationContext, added)

        // 后台没有窗口，快手这一轮是跳过的：留个标记，用户回到前台立刻补查
        if (outcome.results.any { it.note.contains("快手要前台") }) {
            markKuaishouPending(applicationContext)
        }

        // 定期提醒：抖音/快手网页看不到「日常」，只能按用户设定的间隔催他去看一眼
        val now = System.currentTimeMillis()
        val dueNames = mutableListOf<String>()
        persons.forEach { person ->
            person.accounts.forEach { account ->
                if (account.remindEveryDays <= 0) return@forEach
                val due = now - account.lastRemindAt >= account.remindEveryDays * 86_400_000L
                if (!due) return@forEach
                dueNames += person.remarkName
                AppUiState.markReminded(person.id, account.id)
            }
        }
        if (dueNames.isNotEmpty()) {
            Notifications.notifyReminder(applicationContext, dueNames)
        }

        // 限流或网络问题：交给系统退避重试，不立即再打接口
        return if (outcome.error != null) Result.retry() else Result.success()
    }

    companion object {
        private const val PREF = "check_flags"
        private const val KEY_KUAISHOU_PENDING = "kuaishou_pending"

        private fun markKuaishouPending(context: Context) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_KUAISHOU_PENDING, true).apply()
        }

        /** 有没有「后台跳过快手」的待补查标记。 */
        fun consumeKuaishouPending(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val pending = prefs.getBoolean(KEY_KUAISHOU_PENDING, false)
            if (pending) prefs.edit().putBoolean(KEY_KUAISHOU_PENDING, false).apply()
            return pending
        }
    }
}
