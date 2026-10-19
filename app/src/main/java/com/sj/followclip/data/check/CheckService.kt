package com.sj.followclip.data.check

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 检查用的前台服务。
 *
 * 为什么需要它：检查要跑几十秒（B站要限速、快手要渲染），
 * 用户点完「检查」就退出应用的话，普通后台协程会被系统随时掐掉。
 * 前台服务带一条常驻通知，系统不会杀，跑完再推「检查报告已生成」。
 *
 * 注意：快手仍然需要真实窗口，应用被切到后台后窗口不可见，
 * 那一部分会被如实跳过（并在下次回到应用时自动补查）。
 */
class CheckService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.ensureChannel(this)

        // 先把自己提到前台，否则 Android 8+ 会直接杀进程
        val notification = Notifications.checkingNotification(this, 0, 1, "准备中")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (CheckController.running) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                AppUiState.init(this@CheckService)
                CheckController.execute(
                    context = this@CheckService,
                    persons = AppUiState.persons.toList(),
                    includeWeb = true
                )
            } finally {
                stopForegroundCompat()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CheckService::class.java)
                )
            }
        }
    }
}
