package com.sj.followclip.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sj.followclip.MainActivity
import com.sj.followclip.R

/**
 * 通知。
 *
 * 只发一种：发现新内容。不做分组渠道、不做免打扰（那是后续版本的事），
 * 先保证「有新作品能提醒到人」这一件事。
 */
object Notifications {

    private const val CHANNEL_ID = "new_content_v2"
    private const val NOTIFICATION_ID = 1001

    /** 检查进行中的常驻横幅：独立渠道，低打扰、不可滑走。 */
    private const val CHANNEL_CHECKING = "checking"
    private const val CHECKING_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "有更新/检查完成",
                NotificationManager.IMPORTANCE_HIGH   // HIGH 才会弹横幅（悬浮通知）
            ).apply {
                description = "检查完成、发现新内容时弹出横幅"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHECKING,
                "检查中",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "SJ 正在检查所有卡片的更新状态" }
        )
    }

    /**
     * 检查开始的常驻横幅：「SJ 正在检查 N 个卡片的更新状态」。
     *
     * 常驻（ongoing）+ 进度条：用户随时能看到机器在干活，
     * 也让「到底检查了没有」这件事不再靠猜。
     */
    fun startChecking(context: Context, total: Int) {
        ensureChannel(context)
        updateChecking(context, 0, total, "准备中")
    }

    /** 前台服务启动时要传一个 Notification，这里复用同一套内容。 */
    fun checkingNotification(context: Context, done: Int, total: Int, label: String): android.app.Notification =
        buildChecking(context, done, total, label)

    fun updateChecking(context: Context, done: Int, total: Int, label: String) {
        runCatching {
            NotificationManagerCompat.from(context).notify(CHECKING_ID, buildChecking(context, done, total, label))
        }
    }

    private fun buildChecking(context: Context, done: Int, total: Int, label: String): android.app.Notification {
        val safeTotal = total.coerceAtLeast(1)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHECKING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SJ 正在检查 ${total} 个卡片的更新状态")
            .setContentText(
                if (label.isBlank()) "$done / $safeTotal" else "$label · $done / $safeTotal"
            )
            .setProgress(safeTotal, done.coerceIn(0, safeTotal), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .build()

        return notification
    }

    /** 后台检查完成：给一条可点的结果通知（前台检查有明细弹窗，不重复打扰）。 */
    fun notifyCheckDone(context: Context, added: Int) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("检查报告已生成")
            .setContentText(if (added > 0) "本轮发现 $added 条变化，点开看明细" else "本轮没有新变化")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 2, notification)
        }
    }

    /** 检查结束：常驻横幅撤掉。 */
    fun stopChecking(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(CHECKING_ID)
        }
    }

    /**
     * 发一条「有新内容」通知，点击进入应用。
     *
     * 权限被拒时静默返回：宁可没有通知，也不能因为通知失败让后台任务崩掉。
     */
    fun notifyNewContent(context: Context, count: Int) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("有 $count 条新内容")
            .setContentText("点开看看是谁更新了")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 提醒去看某个博主的日常（抖音/快手网页看不到日常，只能靠提醒）。 */
    fun notifyReminder(context: Context, names: List<String>) {
        ensureChannel(context)
        if (names.isEmpty()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val who = if (names.size == 1) names.first() else "${names.size} 个人"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("该看看 $who 的日常了")
            .setContentText("点开跳到 App 里看最新内容")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID + 1, notification)
        }
    }
}
