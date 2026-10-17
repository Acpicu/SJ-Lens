package com.sj.followclip.domain.model

/**
 * 平台枚举。
 *
 * 新增一个平台时只在此追加一项，其余各处都是这样的一行声明。
 */
enum class Platform(
    /** 界面展示名 */
    val displayName: String,
    /** 抖音与快手取数依赖平台页面结构，随时可能失效，界面必须标注实验性 */
    val isExperimental: Boolean,
    /** 仅 B 站走原生 HTTP，能可靠后台运行 */
    val supportAutoCheck: Boolean
) {
    DOUYIN("抖音", isExperimental = true, supportAutoCheck = false),
    KUAISHOU("快手", isExperimental = true, supportAutoCheck = false),
    BILI("B站", isExperimental = false, supportAutoCheck = true)
}

enum class CheckMode { AUTO, MANUAL_CONFIRM }

/**
 * 人物：同一个创作者在多个平台上的合集。
 *
 * 这是首页卡片对应的实体。一个人可以同时有 B 站、抖音、快手账号，
 * 点进人物卡片后再按平台分别看各自的账号情况。
 */
data class Person(
    val id: Long,
    /** 备注名，由自己起，不受平台改名影响 */
    val remarkName: String,
    val avatarUrl: String? = null,
    /** 这个人在各平台的账号；可能只有一个，也可能三个都有 */
    val accounts: List<Account> = emptyList()
)

/**
 * 某人在某个平台上的一个账号。
 *
 * 注意「一个人在同一平台可以有多个账号」：比如 B 站上既有关注工作室号，
 * 也有关注他的生活号。所以这里必须有自己的 [id]，不能拿平台当唯一键。
 */
data class Account(
    val id: Long,
    val platform: Platform,
    /** 该账号的主页链接或 UID，抓取时用；空表示还没填 */
    val homepageUrl: String = "",
    /** 账号别名，用来区分同平台的多个号，如「工作室号」「生活号」；留空则显示平台名 */
    val label: String = "",
    /** 该账号上没看过的新作品数 */
    val unreadCount: Int = 0,
    /** 该账号上次抓取成功时间（毫秒）；0 表示还没抓过 */
    val lastFetchAt: Long = 0L,
    val followerCount: Long? = null,
    val videoCount: Int? = null,
    /** 每隔多少天提醒去看一次（抖音/快手看日常用）；0 表示不提醒 */
    val remindEveryDays: Int = 0,
    /** 上次提醒时间，毫秒 */
    val lastRemindAt: Long = 0L
) {
    /** 界面上这一行显示什么：有别名用别名，没别名用平台名。 */
    val displayTitle: String get() = label.ifBlank { platform.displayName }
}

/** 一个人所有平台的未读总数，用于首页卡片角标。 */
val Person.totalUnread: Int get() = accounts.sumOf { it.unreadCount }

/** 最近一次抓取时间：取各平台里最新的那次。 */
val Person.lastFetchAt: Long get() = accounts.maxOfOrNull { it.lastFetchAt } ?: 0L

/** 作品。 */
data class Video(
    val id: Long,
    /** 平台侧作品唯一标识，去重的唯一依据 */
    val platformVideoId: String,
    val title: String,
    val publishedAt: Long,
    val durationMs: Long? = null,
    /** 应用侧是否已读取 */
    val seen: Boolean = false
)

/** 待确认页的一个分组：一个人一组。 */
data class PendingGroup(
    val personId: Long,
    val personName: String,
    val platform: Platform,
    val lastFetchAt: Long,
    val items: List<Video>
)
