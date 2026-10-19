package com.sj.followclip.data.check

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.source.bili.AccountCheckResult
import com.sj.followclip.domain.model.Person
import com.sj.followclip.ui.state.AppUiState
import com.sj.followclip.util.CurrentActivity
import com.sj.followclip.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 检查会话。**跑在应用级协程里，不挂在任何一个界面上。**
 *
 * 为什么必须这样：之前检查逻辑写在 `HomeScreen` 的 `rememberCoroutineScope` 里，
 * 一切到设置页，Home 的组合被销毁 → 协程被取消 → 检查断在半路，
 * 表现就是「点检查，切到设置，就再也等不到结果了」。
 *
 * 现在状态放在这个单例里，任何界面都能读；检查自己跑完、自己发通知、自己写「最近检查」。
 */
object CheckController {

    /** 应用级作用域：只随进程结束而结束。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var running by mutableStateOf(false)
        private set

    var done by mutableStateOf(0)
        private set

    var total by mutableStateOf(0)
        private set

    var label by mutableStateOf("")
        private set

    var statusLine by mutableStateOf("")
        private set

    /** 上一轮的逐账号明细；界面随时可以打开来看。 */
    var lastResults by mutableStateOf<List<AccountCheckResult>>(emptyList())
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    /** 界面消费掉明细后置 false，避免每次回到首页都弹。 */
    var showDetail by mutableStateOf(false)

    private var job: Job? = null
    private var lastManual = false

    val progress: Float
        get() = if (total <= 0) 0f else done.toFloat() / total

    /**
     * 开始一轮检查。已在跑时直接忽略（连点不会叠加请求，也不会被风控）。
     *
     * @param includeWeb 手动检查为 true；进应用自动跑那一轮也只查 B站 + 抖音
     */
    fun start(context: Context, persons: List<Person>, includeWeb: Boolean) {
        if (running) return
        if (persons.isEmpty()) {
            statusLine = "还没有可检查的卡片"
            return
        }

        val appContext = context.applicationContext
        val snapshotOfPersons = persons.toList()
        lastManual = includeWeb

        job = scope.launch {
            running = true
            done = 0
            total = snapshotOfPersons.sumOf { it.accounts.size }.coerceAtLeast(1)
            label = ""
            statusLine = "检查中…"
            Notifications.startChecking(appContext, total)

            val outcome = CheckRunner.run(
                context = appContext,
                persons = snapshotOfPersons,
                // 快手要真实窗口：拿当前前台 Activity，没拿到就如实跳过
                activity = CurrentActivity.get(),
                includeWeb = includeWeb
            ) { d, all, current ->
                done = d
                total = all
                label = current
                statusLine = "$current $d/$all"
                Notifications.updateChecking(appContext, d, all, current)
            }

            PendingStore.replaceAll(appContext, outcome.items.sortedByDescending { it.foundAt })
            outcome.results.forEach { r -> AppUiState.setUnread(r.personId, r.accountId, r.newCount) }

            Notifications.stopChecking(appContext)

            val added = outcome.results.sumOf { it.newCount }
            lastResults = outcome.results
            lastError = outcome.error
            statusLine = outcome.error ?: if (added > 0) "本次新增 $added 条" else "没有新更新"
            // 手动那一轮才弹明细；自动那一轮只更新状态文字
            showDetail = includeWeb && outcome.results.isNotEmpty()

            running = false
            delay(3_000)
            if (!running) statusLine = ""
        }
    }


    /** 手动重跑：不弹明细，只是把上一轮结果留在界面里。 */
    fun wasManual(): Boolean = lastManual
}
