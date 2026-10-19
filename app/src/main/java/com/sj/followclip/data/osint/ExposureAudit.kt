package com.sj.followclip.data.osint

/**
 * 跨平台关联度评估引擎。
 *
 * 输入：一个目标在各平台**公开可见**的资料（昵称、简介、头像哈希、能否被搜到）。
 * 输出：这些号被认定为同一个人的把握有多大，以及下一步该去取什么证。
 *
 * 本文件是纯 Kotlin，不 import android.*，可以直接在 JVM 单测里跑。
 */
data class SelfProfile(
    /** 平台展示名，用于报告里指出是哪两个平台相同 */
    val platform: String,
    val nickname: String,
    val bio: String = "",
    /** 头像感知哈希（dHash 64 位）；由 UI 层用 Bitmap 采样后传进来 */
    val avatarHash: Long? = null,
    /** 该主页能否被平台站内搜索或搜索引擎直接命中；null = 没测 */
    val searchable: Boolean? = null
)

enum class RiskLevel(val text: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高")
}

/** 一条被命中的关联特征。 */
data class LinkabilityFactor(
    val label: String,
    val weight: Int,
    val detail: String
)

data class LinkabilityReport(
    /** 0..100，越高越容易被认成同一个人 */
    val score: Int,
    val level: RiskLevel,
    val factors: List<LinkabilityFactor>,
    val actions: List<String>
) {
    val isClean: Boolean get() = score < 20
}

/** 两个平台之间的一条关联，用于画关系图。 */
data class AuditPair(
    val from: String,
    val to: String,
    val weight: Int,
    val reason: String
) {
    val label: String get() = "$from ↔ $to"
}

object ExposureAudit {

    private const val W_NICK_SAME = 30
    private const val W_NICK_CONTAINS = 18
    private const val W_NICK_EDIT1 = 12
    private const val W_AVATAR_SAME = 25
    private const val W_AVATAR_NEAR = 15
    private const val W_BIO_TOKEN = 8
    private const val W_BIO_CAP = 16
    private const val W_SEARCHABLE = 10

    /** 头像汉明距离超过这个位数就认为不是同一张图 */
    private const val AVATAR_NEAR_BITS = 8

    fun audit(profiles: List<SelfProfile>): LinkabilityReport {
        val list = profiles.filter { it.nickname.isNotBlank() }
        if (list.size < 2) {
            return LinkabilityReport(
                score = 0,
                level = RiskLevel.LOW,
                factors = emptyList(),
                actions = listOf("至少填两个平台的资料才能算跨平台关联；单平台没有关联度可言")
            )
        }

        val factors = mutableListOf<LinkabilityFactor>()

        // ---------- 1. 昵称相似度 ----------
        var nickW = 0
        var nickDetail = ""
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                val na = normalize(a.nickname)
                val nb = normalize(b.nickname)
                if (na.isEmpty() || nb.isEmpty()) continue
                val w = when {
                    na == nb -> W_NICK_SAME
                    na.contains(nb) || nb.contains(na) -> W_NICK_CONTAINS
                    editDistance(na, nb) <= 1 -> W_NICK_EDIT1
                    else -> 0
                }
                if (w > nickW) {
                    nickW = w
                    nickDetail = "${a.platform} / ${b.platform}：「${a.nickname}」对「${b.nickname}」"
                }
            }
        }
        if (nickW > 0) factors += LinkabilityFactor("昵称高度相似", nickW, nickDetail)

        // ---------- 2. 头像复用 ----------
        var avatarW = 0
        var avatarDetail = ""
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val ha = list[i].avatarHash ?: continue
                val hb = list[j].avatarHash ?: continue
                val d = java.lang.Long.bitCount(ha xor hb)
                val w = when {
                    d == 0 -> W_AVATAR_SAME
                    d <= AVATAR_NEAR_BITS -> W_AVATAR_NEAR
                    else -> 0
                }
                if (w > avatarW) {
                    avatarW = w
                    avatarDetail = "${list[i].platform} / ${list[j].platform}：汉明距离 $d 位"
                }
            }
        }
        if (avatarW > 0) factors += LinkabilityFactor("头像是同一张图", avatarW, avatarDetail)

        // ---------- 3. 简介共用特征词 ----------
        val bioHits = mutableListOf<String>()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val common = tokens(list[i].bio).intersect(tokens(list[j].bio))
                if (common.isNotEmpty()) {
                    bioHits += "${list[i].platform}/${list[j].platform}: " +
                        common.take(3).joinToString("、")
                }
            }
        }
        if (bioHits.isNotEmpty()) {
            val bioW = minOf(W_BIO_TOKEN * bioHits.size, W_BIO_CAP)
            factors += LinkabilityFactor("简介共用特征词", bioW, bioHits.joinToString("；"))
        }

        // ---------- 4. 站外可检索 ----------
        val searchableCount = list.count { it.searchable == true }
        if (searchableCount >= 2) {
            factors += LinkabilityFactor(
                "站外可直接搜到", W_SEARCHABLE,
                "$searchableCount 个平台的主页能被搜索引擎或站内搜索直接命中"
            )
        } else if (searchableCount == 1) {
            factors += LinkabilityFactor(
                "站外可直接搜到", W_SEARCHABLE / 2,
                "1 个平台的主页能被直接命中"
            )
        }

        val score = factors.sumOf { it.weight }.coerceAtMost(100)
        val level = when {
            score < 25 -> RiskLevel.LOW
            score < 60 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }

        // ---------- 5. 下一步取证动作 ----------
        val actions = mutableListOf<String>()
        if (nickW >= W_NICK_SAME) {
            actions += "昵称完全一致：截图归档并按 A 级处理，这一条足以支撑「同一人」的结论"
        } else if (nickW > 0) {
            actions += "昵称只差一层（包含或一字之差）：换平台再搜同款昵称，看有没有第三、第四个号"
        }
        if (avatarW >= W_AVATAR_SAME) {
            actions += "同一张头像：存原图并算 dHash，这张图还能拿去别的平台反查"
        } else if (avatarW > 0) {
            actions += "头像近似：按图搜图再确认一次，别急着并号"
        }
        if (bioHits.isNotEmpty()) {
            actions += "简介共用的词单独登记，逐词去搜——学校 / 公司 / 城市通常就藏在这些词里"
        }
        if (searchableCount > 0) {
            actions += "已确认可被搜索引擎命中：把命中页 URL 记进数据本，作为交叉验证的一环"
        }
        if (actions.isEmpty()) {
            actions += "样本不足：先补齐目标在各平台的昵称与简介，再回来算"
        }

        return LinkabilityReport(score, level, factors, actions)
    }

    /**
     * 平台两两之间的关联明细，供关系图使用。
     *
     * 与 [audit] 同源（同一批私有权重与归一化规则），只是把结果按「边」拆开列出。
     */
    fun pairs(profiles: List<SelfProfile>): List<AuditPair> {
        val list = profiles.filter { it.nickname.isNotBlank() }
        val out = mutableListOf<AuditPair>()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val a = list[i]
                val b = list[j]
                var weight = 0
                val reasons = mutableListOf<String>()

                val na = normalize(a.nickname)
                val nb = normalize(b.nickname)
                if (na.isNotEmpty() && nb.isNotEmpty()) {
                    when {
                        na == nb -> {
                            weight += W_NICK_SAME
                            reasons += "昵称相同"
                        }

                        na.contains(nb) || nb.contains(na) -> {
                            weight += W_NICK_CONTAINS
                            reasons += "昵称包含"
                        }

                        editDistance(na, nb) <= 1 -> {
                            weight += W_NICK_EDIT1
                            reasons += "昵称差一字"
                        }
                    }
                }

                val ha = a.avatarHash
                val hb = b.avatarHash
                if (ha != null && hb != null) {
                    val d = java.lang.Long.bitCount(ha xor hb)
                    when {
                        d == 0 -> {
                            weight += W_AVATAR_SAME
                            reasons += "同一头像"
                        }

                        d <= AVATAR_NEAR_BITS -> {
                            weight += W_AVATAR_NEAR
                            reasons += "头像近似"
                        }
                    }
                }

                val common = tokens(a.bio).intersect(tokens(b.bio))
                if (common.isNotEmpty()) {
                    weight += W_BIO_TOKEN
                    reasons += "简介词：" + common.take(2).joinToString("、")
                }

                if (a.searchable == true && b.searchable == true) {
                    weight += W_SEARCHABLE
                    reasons += "都可搜到"
                }

                if (weight > 0) {
                    out += AuditPair(a.platform, b.platform, weight, reasons.joinToString(" · "))
                }
            }
        }
        return out
    }

    /** 小写化并只保留字母与数字，消除空格、下划线、全角标点的干扰。 */
    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    /** 简介切词：非字母数字都当分隔符，丢掉单字符。 */
    private fun tokens(s: String): Set<String> =
        s.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
            .toSet()

    /** 标准 Levenshtein 距离，按钮级数据量用 O(n*m) 足够。 */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(
                    prev[j] + 1,
                    cur[j - 1] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
