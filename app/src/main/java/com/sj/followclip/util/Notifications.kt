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

    private const val CHANNEL_ID = "new_content"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "有更新",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "关注的博主有新作品时提醒"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
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
