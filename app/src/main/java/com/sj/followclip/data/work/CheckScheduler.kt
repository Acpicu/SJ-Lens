package com.sj.followclip.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 自动检查的排期。
 *
 * 用 WorkManager 而不是 AlarmManager / 常驻服务：
 * 系统级排期、能跟随 Doze 与网络状态、不需要前台服务权限，最省电也最省事。
 *
 * 周期取系统允许的最小值（15 分钟）以上的一小时：
 * 更密对「看更新」没有实际收益，反而更容易被平台限流。
 */
object CheckScheduler {

    private const val WORK_NAME = "auto_check_periodic"

    fun schedule(context: Context) {
        // 不再做 60 分钟周期的后台轮询：
        // 现在改成「打开应用自动查一轮」，由前台服务保证退出应用也能跑完并通知。
        runCatching {
            androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
