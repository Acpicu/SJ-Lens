package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONObject

/** 上一次看到的公开资料快照。作品数用 -1 表示「不知道」。 */
data class StoredProfile(
    val workCount: Int = -1,
    val nickname: String = "",
    val signature: String = "",
    /** 头像地址；换了头像就是换了头像，红队很关心 */
    val avatarUrl: String = "",
    /** 上次成功抓取的时间戳，用于命中缓存 */
    val at: Long = 0L
)

/** 两次快照之间的差异。红队关心的就这三件事：发了多少、叫什么、简介写了什么。 */
data class ProfileDelta(
    val workAdded: Int = 0,
    val renamedFrom: String? = null,
    val renamedTo: String? = null,
    val bioFrom: String? = null,
    val bioTo: String? = null,
    val avatarFrom: String? = null,
    val avatarTo: String? = null,
    /** 第一次见到这个账号：只建基线，不报警 */
    val baseline: Boolean = false,
    /** 作品数变少了（删作品 / 改私密），同样值得记一笔 */
    val workRemoved: Int = 0
) {
    val hasChange: Boolean
        get() = workAdded > 0 || workRemoved > 0 || renamedTo != null || bioTo != null || avatarTo != null
}

/**
 * 账号主页快照的本机存储：按账号 id 存一份 JSON。
 *
 * 与 [VideoSnapshotStore] 分工：那个存作品 ID / 作品总数用于列表比对，
 * 这个存「主页层面的身份信号」，用来抓改名、改简介这类更新检测看不到的变化。
 */
object WebProfileStore {

    private const val PREF = "web_profiles"

    fun load(context: Context, accountId: Long): StoredProfile? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("p_$accountId", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            StoredProfile(
                workCount = o.optInt("count", -1),
                nickname = o.optString("nick"),
                signature = o.optString("sig"),
                avatarUrl = o.optString("avatar"),
                at = o.optLong("at")
            )
        }.getOrNull()
    }

    fun save(context: Context, accountId: Long, profile: StoredProfile) {
        runCatching {
            val o = JSONObject()
                .put("count", profile.workCount)
                .put("nick", profile.nickname)
                .put("sig", profile.signature)
                .put("avatar", profile.avatarUrl)
                .put("at", if (profile.at > 0L) profile.at else System.currentTimeMillis())
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("p_$accountId", o.toString()).apply()
        }
    }

    /** 缓存命中：返回 ttl 以内抓到的快照，过期或没有则 null。 */
    fun loadFresh(context: Context, accountId: Long, ttlMs: Long): StoredProfile? {
        val cached = load(context, accountId) ?: return null
        if (cached.at <= 0L) return null
        return cached.takeIf { System.currentTimeMillis() - it.at <= ttlMs }
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** 比对旧快照与本次结果，得出该报什么。旧快照为空 → 只建基线。 */
    fun diff(old: StoredProfile?, now: StoredProfile): ProfileDelta {
        if (old == null) return ProfileDelta(baseline = true)

        var added = 0
        var removed = 0
        if (old.workCount >= 0 && now.workCount >= 0) {
            val delta = now.workCount - old.workCount
            if (delta > 0) added = delta else if (delta < 0) removed = -delta
        }
        val renamed = old.nickname.isNotBlank() && now.nickname.isNotBlank() &&
            old.nickname != now.nickname
        val bioChanged = old.signature.isNotBlank() && now.signature.isNotBlank() &&
            old.signature != now.signature
        // 头像比对：先剔掉 ?xxxx 这种每次都变的签名参数，否则每轮都会被判成"换头像"
        fun normAvatar(u: String): String {
            val cut = u.indexOf('?')
            return (if (cut >= 0) u.substring(0, cut) else u).substringAfterLast('/').trim()
        }
        val avatarChanged = old.avatarUrl.isNotBlank() && now.avatarUrl.isNotBlank() &&
            normAvatar(old.avatarUrl) != normAvatar(now.avatarUrl)

        return ProfileDelta(
            workAdded = added,
            workRemoved = removed,
            renamedFrom = if (renamed) old.nickname else null,
            renamedTo = if (renamed) now.nickname else null,
            bioFrom = if (bioChanged) old.signature else null,
            bioTo = if (bioChanged) now.signature else null,
            avatarFrom = if (avatarChanged) old.avatarUrl else null,
            avatarTo = if (avatarChanged) now.avatarUrl else null
        )
    }
}
