package com.sj.followclip.data.local

import android.content.Context
import org.json.JSONObject

/** 上一次看到的公开资料快照。作品数用 -1 表示「不知道」。 */
data class StoredProfile(
    val workCount: Int = -1,
    val nickname: String = "",
    val signature: String = ""
)

/** 两次快照之间的差异。红队关心的就这三件事：发了多少、叫什么、简介写了什么。 */
data class ProfileDelta(
    val workAdded: Int = 0,
    val renamedFrom: String? = null,
    val renamedTo: String? = null,
    val bioFrom: String? = null,
    val bioTo: String? = null,
    /** 第一次见到这个账号：只建基线，不报警 */
    val baseline: Boolean = false,
    /** 作品数变少了（删作品 / 改私密），同样值得记一笔 */
    val workRemoved: Int = 0
) {
    val hasChange: Boolean
        get() = workAdded > 0 || workRemoved > 0 || renamedTo != null || bioTo != null
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
                signature = o.optString("sig")
            )
        }.getOrNull()
    }

    fun save(context: Context, accountId: Long, profile: StoredProfile) {
        runCatching {
            val o = JSONObject()
                .put("count", profile.workCount)
                .put("nick", profile.nickname)
                .put("sig", profile.signature)
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("p_$accountId", o.toString()).apply()
        }
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

        return ProfileDelta(
            workAdded = added,
            workRemoved = removed,
            renamedFrom = if (renamed) old.nickname else null,
            renamedTo = if (renamed) now.nickname else null,
            bioFrom = if (bioChanged) old.signature else null,
            bioTo = if (bioChanged) now.signature else null
        )
    }
}
