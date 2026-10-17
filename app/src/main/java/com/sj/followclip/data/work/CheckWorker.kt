package com.sj.followclip.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sj.followclip.data.source.bili.BiliChecker
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

        val biliResult = BiliChecker.checkAll(applicationContext, persons) { _, _ -> }
        biliResult.onSuccess { results ->
            val added = results.sumOf { it.newCount }
            if (added > 0) {
                Notifications.notifyNewContent(applicationContext, added)
            }
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

        return biliResult.fold(
            onSuccess = { Result.success() },
            onFailure = {
                // 限流或网络问题：交给系统退避重试，不立即再打接口
                Result.retry()
            }
        )
    }
}
