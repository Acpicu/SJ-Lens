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
                    .filter { it.platform != Platform.BILI && it.platform != Platform.KUAISHOU }
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
                    signature = snapshot.signature
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
            // 后台没有窗口：如实记一笔，别让「没查」看起来像「没有更新」
            persons.forEach { person ->
                person.accounts
                    .filter { it.platform == Platform.KUAISHOU && it.homepageUrl.isNotBlank() }
                    .forEach { account ->
                        results += AccountCheckResult(
                            personId = person.id,
                            accountId = account.id,
                            accountLabel = account.displayTitle,
                            platform = Platform.KUAISHOU,
                            note = "后台没有窗口，快手要前台才查得了；下次打开应用会自动补查"
                        )
                    }
            }
        }

        return CheckOutcome(results = results, items = items, error = error)
    }
}
