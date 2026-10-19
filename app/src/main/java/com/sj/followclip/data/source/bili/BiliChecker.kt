package com.sj.followclip.data.source.bili

import android.content.Context
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform
import kotlinx.coroutines.delay

/** 一次检查里某个账号的结果。界面用来说明「这次到底抓到了什么」。 */
data class AccountCheckResult(
    val personId: Long,
    val accountId: Long,
    val accountLabel: String,
    val platform: Platform,
    /** 这次抓到多少条（0 表示页面/接口没给东西，不是「没有更新」） */
    val fetched: Int = 0,
    /** 相比上次快照新增的作品数 */
    val newCount: Int = 0,
    /** 首次检查：只建立基线，按设计不报新增 */
    val baseline: Boolean = false,
    /** 失败或跳过时的原因，正常为空 */
    val note: String = ""
)

/** 一轮检查的产物：逐账号结果 + 本轮发现的新内容。 */
data class CheckOutcome(
    val results: List<AccountCheckResult> = emptyList(),
    val items: List<PendingItem> = emptyList(),
    /** 整体中断的原因（如被风控），局部失败写在各账号的 note 里 */
    val error: String? = null
)

/**
 * 一键检查：把所有 B 站账号挨个过一遍，算出各自新增了几条作品。
 *
 * 只负责「抓 + 比对」，**不写任何存储**（快照除外）：结果交回给调用方统一落盘，
 * 这样「最近检查」才是完整的一轮（B站 + 抖音 + 快手），不会被后跑的阶段覆盖掉。
 *
 * 每个账号之间强制间隔 [GAP_MS]：连续请求是触发 B 站 风控的头号原因。
 */
object BiliChecker {

    private const val GAP_MS = 2_500L

    suspend fun checkAll(
        context: Context,
        persons: List<Person>,
        onProgress: (done: Int, total: Int) -> Unit
    ): CheckOutcome {
        val jobs = persons.flatMap { person ->
            person.accounts
                .filter { it.platform == Platform.BILI }
                .map { Triple(person.id, person.remarkName, it) }
        }
        if (jobs.isEmpty()) return CheckOutcome()

        val results = mutableListOf<AccountCheckResult>()
        val roundItems = mutableListOf<PendingItem>()
        var done = 0

        for ((personId, personName, account) in jobs) {
            val label = account.displayTitle
            val uid = BiliSource.extractUid(account.homepageUrl)

            if (uid == null) {
                results += AccountCheckResult(
                    personId = personId,
                    accountId = account.id,
                    accountLabel = label,
                    platform = account.platform,
                    note = "主页链接里找不到 UID，没人可查。检查「添加账号」里填的链接"
                )
                done++
                onProgress(done, jobs.size)
                continue
            }

            delay(GAP_MS)
            val fetched = BiliSource.fetchVideos(uid)
            if (fetched.isFailure) {
                // 一旦被限流就整体停下，不要继续打接口把身份彻底打黑
                return CheckOutcome(
                    results = results,
                    items = roundItems,
                    error = fetched.exceptionOrNull()?.message ?: "B站检查失败"
                )
            }

            val list = fetched.getOrThrow()
            val known = VideoSnapshotStore.load(context, account.id)
            val baseline = known.isEmpty()
            val fresh = if (baseline) emptyList() else list.filter { it.bvid !in known }

            // 抓空不写快照；有内容就并进旧快照（不是整体替换）
            VideoSnapshotStore.merge(context, account.id, list.map { it.bvid })

            roundItems += fresh.map { v ->
                PendingItem(
                    bvid = v.bvid,
                    title = v.title,
                    personId = personId,
                    personName = personName,
                    platform = account.platform.name,
                    coverUrl = v.coverUrl,
                    link = v.webUrl,
                    kind = "VIDEO",
                    foundAt = System.currentTimeMillis()
                )
            }

            var note = ""
            var freshDynCount = 0
            // 动态也一起查：否则用户会以为「检查」漏掉了动态更新。
            // 动态失败不影响投稿结果，静默跳过。
            val dynResult = BiliSource.fetchDynamics(uid)
            if (dynResult.isSuccess) {
                val dyns = dynResult.getOrThrow()
                val knownDyn = VideoSnapshotStore.load(context, account.id, "dyn")
                val dynBaseline = knownDyn.isEmpty()
                val freshDyn = if (dynBaseline) {
                    emptyList()
                } else {
                    dyns.filter { it.id !in knownDyn }
                }
                VideoSnapshotStore.merge(context, account.id, dyns.map { it.id }, "dyn")
                freshDynCount = freshDyn.size
                roundItems += freshDyn.map { d ->
                    PendingItem(
                        bvid = d.id,
                        title = d.text.take(80),
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        coverUrl = d.coverUrl,
                        link = "https://t.bilibili.com/${d.id}",
                        kind = "DYNAMIC",
                        foundAt = System.currentTimeMillis()
                    )
                }
            } else {
                note = "动态没抓到（不影响投稿）"
            }

            results += AccountCheckResult(
                personId = personId,
                accountId = account.id,
                accountLabel = label,
                platform = account.platform,
                fetched = list.size,
                newCount = fresh.size + freshDynCount,
                baseline = baseline,
                note = note
            )

            done++
            onProgress(done, jobs.size)
        }

        return CheckOutcome(results = results, items = roundItems)
    }
}
