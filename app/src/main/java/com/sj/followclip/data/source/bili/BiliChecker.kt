package com.sj.followclip.data.source.bili

import android.content.Context
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.PendingStore
import com.sj.followclip.data.local.VideoSnapshotStore
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform
import kotlinx.coroutines.delay

/** 一次检查里某个账号的结果。 */
data class AccountCheckResult(
    val personId: Long,
    val accountId: Long,
    /** 相比上次快照新增的作品数 */
    val newCount: Int
)

/**
 * 一键检查：把所有 B 站 账号挨个过一遍，算出各自新增了几条作品。
 *
 * 只负责「抓 + 比对」，不直接改界面状态 —— 结果交回给调用方去写，
 * 这样数据层不反过来依赖界面层。
 *
 * 每个账号之间强制间隔 [GAP_MS]：连续请求是触发 B 站 风控的头号原因，
 * 宁可慢一点，也不要被限流。登录后虽然宽容度高，也没必要去试探下限。
 */
object BiliChecker {

    /** 账号之间的间隔。 */
    private const val GAP_MS = 2_500L

    suspend fun checkAll(
        context: Context,
        persons: List<Person>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Result<List<AccountCheckResult>> {
        val jobs = persons.flatMap { person ->
            person.accounts
                .filter { it.platform == Platform.BILI }
                .map { Triple(person.id, person.remarkName, it) }
        }
        if (jobs.isEmpty()) return Result.success(emptyList())

        val results = mutableListOf<AccountCheckResult>()
        // 本轮发现的新内容先攒着，最后整体替换待确认池（只保留最近一轮）
        val roundItems = mutableListOf<PendingItem>()
        var done = 0

        for ((personId, personName, account) in jobs) {
            val uid = BiliSource.extractUid(account.homepageUrl)

            if (uid != null) {
                delay(GAP_MS)
                val fetched = BiliSource.fetchVideos(uid)
                if (fetched.isFailure) {
                    // 一旦被限流就整体停下，不要继续打接口把身份彻底打黑
                    return Result.failure(
                        fetched.exceptionOrNull() ?: IllegalStateException("检查失败")
                    )
                }
                val list = fetched.getOrThrow()
                val known = VideoSnapshotStore.load(context, account.id)
                // 首次检查只建立基线，不报「新」
                val freshVideos = if (known.isEmpty()) {
                    emptyList()
                } else {
                    list.filter { it.bvid !in known }
                }
                VideoSnapshotStore.save(context, account.id, list.map { it.bvid }.take(60))
                if (freshVideos.isNotEmpty()) {
                    roundItems += freshVideos.map { v ->
                        PendingItem(
                            bvid = v.bvid,
                            title = v.title,
                            personId = personId,
                            personName = personName,
                            platform = account.platform.name,
                            coverUrl = v.coverUrl,
                            foundAt = System.currentTimeMillis()
                        )
                    }
                }
                results += AccountCheckResult(personId, account.id, freshVideos.size)

                // 动态也一起查：否则用户会以为「检查」漏掉了动态更新。
                // 动态失败不影响投稿结果，静默跳过即可。
                val dynFetched = BiliSource.fetchDynamics(uid)
                if (dynFetched.isSuccess) {
                    val dyns = dynFetched.getOrThrow()
                    val knownDyn = VideoSnapshotStore.load(context, account.id, "dyn")
                    val freshDyn = if (knownDyn.isEmpty()) {
                        emptyList()
                    } else {
                        dyns.filter { it.id !in knownDyn }
                    }
                    VideoSnapshotStore.save(context, account.id, dyns.map { it.id }.take(60), "dyn")

                    if (freshDyn.isNotEmpty()) {
                        roundItems += freshDyn.map { d ->
                            PendingItem(
                                bvid = d.id,
                                title = d.text.take(80),
                                personId = personId,
                                personName = personName,
                                platform = account.platform.name,
                                coverUrl = d.coverUrl,
                                kind = "DYNAMIC",
                                foundAt = System.currentTimeMillis()
                            )
                        }
                        results += AccountCheckResult(personId, account.id, freshDyn.size)
                    }
                }
            }

            done++
            onProgress(done, jobs.size)
        }

        // 只保留最近一轮的结果：每次检查直接覆盖，用户不用手动清理
        PendingStore.replaceAll(context, roundItems)

        return Result.success(results)
    }
}
