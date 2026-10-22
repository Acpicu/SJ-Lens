package com.sj.followclip.data.check

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.work.CheckWorker
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
    /**
     * 真正干活的地方。放在前台服务里跑：用户点完检查就退出应用去做别的事，
     * 这一轮照样跑完并推送「检查报告已生成」。
     */
    suspend fun execute(
        context: Context,
        persons: List<Person>,
        includeWeb: Boolean,
        baselineOnly: Boolean = false
    ) {
        val appContext = context.applicationContext
        val snapshotOfPersons = persons.toList()
        lastManual = includeWeb

        running = true
        done = 0
        total = snapshotOfPersons.sumOf { it.accounts.size }.coerceAtLeast(1)
        label = ""
        statusLine = "检查中…"
        Notifications.startChecking(appContext, total)

        val outcome = CheckRunner.run(
            context = appContext,
            persons = snapshotOfPersons,
            // 快手要真实窗口：应用在前台才拿得到 Activity
            activity = CurrentActivity.get(),
            includeWeb = includeWeb
        ) { d, all, current ->
            done = d
            total = all
            label = current
            statusLine = "$current $d/$all"
            Notifications.updateChecking(appContext, d, all, current)
        }

        if (baselineOnly) {
            // 只建基线：把本轮结果丢掉，角标清零，下次检查才有「变化」可言
            PendingStore.clearAll(appContext)
            outcome.results.forEach { r -> AppUiState.setUnread(r.personId, r.accountId, 0) }
        } else {
            // 合并而不是覆盖：用户还没看过的旧条目不能被这一轮冲掉
            PendingStore.merge(appContext, outcome.items)
            outcome.results.forEach { r -> AppUiState.setUnread(r.personId, r.accountId, r.newCount) }
        }

        Notifications.stopChecking(appContext)

        val added = outcome.results.sumOf { it.newCount }
        lastResults = outcome.results
        lastError = outcome.error
        statusLine = when {
            baselineOnly -> "基线已建立（${outcome.results.count { it.fetched > 0 }} 个账号）"
            outcome.error != null -> outcome.error!!
            added > 0 -> "本次新增 $added 条"
            else -> "没有新更新"
        }
        showDetail = !baselineOnly && includeWeb && outcome.results.isNotEmpty()

        // 前台服务跑的一轮：完成就给一条通知，用户退出应用也能知道
        Notifications.notifyCheckDone(appContext, added)

        // 后台没有窗口时快手会被跳过：留标记，下次回到应用自动补查
        if (outcome.results.any { it.note.contains("后台没有窗口") }) {
            CheckWorker.markKuaishouPending(appContext)
        }

        running = false
        delay(3_000)
        if (!running) statusLine = ""
    }

    /** 只建基线：把当前状态记下来，不报更新、不动角标。 */
    fun startBaseline(context: Context, persons: List<Person>) {
        if (running) return
        if (persons.isEmpty()) {
            statusLine = "还没有可检查的卡片"
            return
        }
        CheckService.start(context, baselineOnly = true)
    }

    /** 点一下检查：交给前台服务，退出应用也不会被打断。 */
    fun start(context: Context, persons: List<Person>, includeWeb: Boolean) {
        if (running) return
        if (persons.isEmpty()) {
            statusLine = "还没有可检查的卡片"
            return
        }
        val started = CheckService.start(context)
        if (!started) {
            // 系统不让起前台服务（部分 ROM / 后台限制）：退回应用内协程，
            // 并把原因写在状态行，用户不会以为"点了没反应"
            statusLine = "后台服务被系统拦下，改用前台执行"
            scope.launch { execute(context, persons, includeWeb) }
        }
    }

    /** 上一轮是不是手动触发的（手动才弹逐账号明细）。 */
    fun wasManual(): Boolean = lastManual
}
