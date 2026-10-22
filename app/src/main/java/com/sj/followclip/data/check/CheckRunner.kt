package com.sj.followclip.data.check

import android.app.Activity
import android.content.Context
import com.sj.followclip.data.local.PendingItem
import com.sj.followclip.data.local.WebProfileStore
import com.sj.followclip.data.local.StoredProfile
import com.sj.followclip.data.source.bili.AccountCheckResult
import com.sj.followclip.data.source.bili.BiliChecker
import com.sj.followclip.data.source.bili.CheckOutcome
import com.sj.followclip.data.source.web.HttpProfileChecker
import com.sj.followclip.data.source.web.KuaishouChecker
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform

/**
 * 一轮检查的完整执行器。前台（首页「检查」）和后台（WorkManager）都走这里，
 * 保证两条路径的口径完全一致 —— 否则「前台说有更新、后台说没有」这种事会反复出现。
 *
 * 三个平台三条路：
 * - **B站**：公开接口（WBI 签名），能拿到作品与动态条目
 * - **抖音**：纯 HTTP 探主页（`iesdouyin` 的 user/info），拿作品总数 / 昵称 / 简介，
 *   **不需要 WebView，所以后台也能跑**
 * - **快手**：只能靠真实渲染的 WebView，纯 HTTP 一律被签名拦住；没有 Activity 时如实跳过
 */
object CheckRunner {

    /** 结果缓存有效期：10 分钟内的重复检查不再打网络。 */
    private const val CACHE_TTL_MS = 10 * 60_000L

    /**
     * @param activity 前台才有；为 null 时快手会被跳过（后台任务的常态）
     * @param includeWeb 是否连抖音/快手一起查（自动检查只查 B站 + 抖音，快）
     */
    suspend fun run(
        context: Context,
        persons: List<Person>,
        activity: Activity?,
        includeWeb: Boolean,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): CheckOutcome {

        val biliTotal = persons.sumOf { p -> p.accounts.count { it.platform == Platform.BILI } }
        val httpJobs = if (includeWeb) {
            persons.flatMap { p ->
                p.accounts
                    .filter {
                        it.platform != Platform.BILI &&
                            it.platform != Platform.KUAISHOU &&
                            it.platform != Platform.QQZONE      // QQ空间要登录，没有可匿名读的接口
                    }
                    .filter { it.homepageUrl.isNotBlank() }
                    .map { p.id to it }
            }
        } else {
            emptyList()
        }
        val kuaishouJobs = if (includeWeb && activity != null) {
            persons.flatMap { p ->
                p.accounts
                    .filter { it.platform == Platform.KUAISHOU && it.homepageUrl.isNotBlank() }
                    .map { p.id to it }
            }
        } else {
            emptyList()
        }

        val total = (biliTotal + httpJobs.size + kuaishouJobs.size).coerceAtLeast(1)
        val results = mutableListOf<AccountCheckResult>()
        val items = mutableListOf<PendingItem>()
        var error: String? = null
        var done = 0

        // ---------- 1) B站 ----------
        if (biliTotal > 0) {
            val bili = BiliChecker.checkAll(context, persons) { d, _ ->
                onProgress(done + d, total, "B站")
            }
            results += bili.results
            items += bili.items
            bili.error?.let { error = it }
            done += bili.results.size
        }

        // ---------- 2) 抖音（纯 HTTP） ----------
        for ((personId, account) in httpJobs) {
            val personName = persons.firstOrNull { it.id == personId }?.remarkName ?: ""

            // 缓存优先：10 分钟内查过就直接复用，省一次网络往返（也省心）
            val cached = WebProfileStore.loadFresh(context, account.id, CACHE_TTL_MS)
            if (cached != null) {
                results += AccountCheckResult(
                    personId = personId,
                    accountId = account.id,
                    accountLabel = account.displayTitle,
                    platform = account.platform,
                    fetched = cached.workCount.coerceAtLeast(0),
                    newCount = 0,
                    note = "缓存（10 分钟内刚查过）"
                )
                done++
                onProgress(done, total, account.platform.displayName)
                continue
            }

            // 和 B站 一样给个间隔：连续打接口是风控最爱抓的模式
            kotlinx.coroutines.delay(900)
            val fetched = HttpProfileChecker.fetch(account.platform, account.homepageUrl)
            if (fetched.isFailure) {
                results += AccountCheckResult(
                    personId = personId,
                    accountId = account.id,
                    accountLabel = account.displayTitle,
                    platform = account.platform,
                    note = fetched.exceptionOrNull()?.message ?: "HTTP 探测失败"
                )
            } else {
                val snapshot = fetched.getOrThrow()
                val now = StoredProfile(
                    workCount = snapshot.workCount ?: -1,
                    nickname = snapshot.nickname,
                    signature = snapshot.signature,
                    avatarUrl = snapshot.avatarUrl.orEmpty()
                )
                val old = WebProfileStore.load(context, account.id)
                val delta = WebProfileStore.diff(old, now)
                WebProfileStore.save(context, account.id, now)
                if (snapshot.workCount != null) {
                    com.sj.followclip.data.local.VideoSnapshotStore.save(
                        context, account.id, listOf(snapshot.workCount.toString()), "count"
                    )
                }

                val newItems = mutableListOf<PendingItem>()
                if (delta.workAdded > 0) {
                    newItems += PendingItem(
                        bvid = "profile-${account.id}-${snapshot.workCount}",
                        title = "作品数 ${old?.workCount} → ${snapshot.workCount}（新增 ${delta.workAdded} 条）",
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        coverUrl = snapshot.avatarUrl,
                        link = snapshot.canonicalUrl,
                        kind = "PROFILE",
                        foundAt = System.currentTimeMillis()
                    )
                }
                if (delta.renamedTo != null) {
                    newItems += PendingItem(
                        bvid = "rename-${account.id}-${delta.renamedTo.hashCode()}",
                        title = "改名：${delta.renamedFrom} → ${delta.renamedTo}",
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        coverUrl = snapshot.avatarUrl,
                        link = snapshot.canonicalUrl,
                        kind = "PROFILE",
                        foundAt = System.currentTimeMillis()
                    )
                }
                if (delta.avatarTo != null) {
                    newItems += PendingItem(
                        bvid = "avatar-${account.id}-${delta.avatarTo.hashCode()}",
                        title = "换头像：${account.displayTitle}",
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        coverUrl = snapshot.avatarUrl,
                        link = snapshot.canonicalUrl,
                        kind = "PROFILE",
                        foundAt = System.currentTimeMillis()
                    )
                }
                if (delta.bioTo != null) {
                    newItems += PendingItem(
                        bvid = "bio-${account.id}-${delta.bioTo.hashCode()}",
                        title = "改简介：${delta.bioFrom} → ${delta.bioTo}",
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        coverUrl = snapshot.avatarUrl,
                        link = snapshot.canonicalUrl,
                        kind = "PROFILE",
                        foundAt = System.currentTimeMillis()
                    )
                }
                items += newItems

                // 前台补一步：渲染主页抽具体作品 id，才有"哪几条是新的"和可点进 App 的链接
                if (activity != null) {
                    val rendered = com.sj.followclip.data.source.web.DouyinRenderChecker
                        .fetchVideos(activity, account.homepageUrl)
                    items += com.sj.followclip.data.source.web.DouyinRenderChecker.newItems(
                        activity = activity,
                        accountId = account.id,
                        personId = personId,
                        personName = personName,
                        platform = account.platform.name,
                        videos = rendered
                    )
                }

                results += AccountCheckResult(
                    personId = personId,
                    accountId = account.id,
                    accountLabel = account.displayTitle,
                    platform = account.platform,
                    fetched = snapshot.workCount ?: 0,
                    newCount = newItems.size,
                    baseline = delta.baseline,
                    note = when {
                        !delta.baseline -> ""
                        snapshot.workCount == 0 ->
                            "这个号目前没有作品，已记基线；发第一条就会报"
                        else -> "首次检查已建基线：作品 ${snapshot.workCount}"
                    }
                )
            }
            done++
            onProgress(done, total, account.platform.displayName)
        }

        // ---------- 3) 快手：只能真实渲染，前台有窗口时跑 ----------
        if (kuaishouJobs.isNotEmpty() && activity != null) {
            val ks = KuaishouChecker.checkAll(
                activity = activity,
                jobs = kuaishouJobs,
                personNames = persons.associate { it.id to it.remarkName }
            ) { d, _ ->
                onProgress(done + d, total, "快手")
            }
            results += ks.results
            items += ks.items
        } else if (includeWeb) {
            // QQ空间：只能点开看，不做自动比对，避免给出"没有更新"的错觉
            persons.forEach { person ->
                person.accounts
                    .filter { it.platform == Platform.QQZONE }
                    .forEach { account ->
                        results += AccountCheckResult(
                            personId = person.id,
                            accountId = account.id,
                            accountLabel = account.displayTitle,
                            platform = Platform.QQZONE,
                            note = "QQ空间需登录，点开账号页查看；不做自动更新比对"
                        )
                    }
            }
            // 后台没有窗口也能查：走分享落地页的 HTTP 直取（见 KuaishouHttpProbe）
            persons.forEach { person ->
                person.accounts
                    .filter { it.platform == Platform.KUAISHOU && it.homepageUrl.isNotBlank() }
                    .forEach { account ->
                        kotlinx.coroutines.delay(900)
                        val count = com.sj.followclip.data.source.web.KuaishouHttpProbe
                            .count(account.homepageUrl)
                        if (count == null) {
                            results += AccountCheckResult(
                                personId = person.id,
                                accountId = account.id,
                                accountLabel = account.displayTitle,
                                platform = Platform.KUAISHOU,
                                note = "这轮没取到作品数（分享页没给「作品 N」），下次打开应用会补查"
                            )
                        } else {
                            val cached = WebProfileStore.load(context, account.id)
                            val now = StoredProfile(
                                workCount = count,
                                // HTTP 只有数字：昵称/简介沿用上次，别清空
                                nickname = cached?.nickname.orEmpty(),
                                signature = cached?.signature.orEmpty()
                            )
                            val delta = WebProfileStore.diff(cached, now)
                            WebProfileStore.save(context, account.id, now)
                            var newCount = 0
                            if (delta.workAdded > 0) {
                                newCount++
                                items += PendingItem(
                                    bvid = "ks-http-${account.id}-$count",
                                    title = "作品数 ${cached?.workCount} → $count（新增 ${delta.workAdded} 条）",
                                    personId = person.id,
                                    personName = person.remarkName,
                                    platform = Platform.KUAISHOU.name,
                                    link = account.homepageUrl,
                                    kind = "PROFILE",
                                    foundAt = System.currentTimeMillis()
                                )
                            }
                            results += AccountCheckResult(
                                personId = person.id,
                                accountId = account.id,
                                accountLabel = account.displayTitle,
                                platform = Platform.KUAISHOU,
                                fetched = count,
                                newCount = newCount,
                                baseline = delta.baseline,
                                note = if (delta.baseline) "首次检查已建基线：作品 $count" else "HTTP 直取"
                            )
                        }
                    }
            }
        }

        return CheckOutcome(results = results, items = items, error = error)
    }
}
