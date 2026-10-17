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
        val request = PeriodicWorkRequestBuilder<CheckWorker>(60, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        // KEEP：重复调用不会重置已有排期，避免每次开应用都把计时清零
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
