package com.sj.followclip.ui.preview

import com.sj.followclip.domain.model.Account
import com.sj.followclip.domain.model.PendingGroup
import com.sj.followclip.domain.model.Person
import com.sj.followclip.domain.model.Platform
import com.sj.followclip.domain.model.Video
import java.util.concurrent.TimeUnit

/**
 * 假数据。
 *
 * 只服务界面预览与截图回归，不进真实数据链路。
 * 时间统一用「当前时刻减去偏移」实时算，保证截图里永远是「2 小时前」这种新鲜文案。
 */
object FakeData {

    private val MINUTE = TimeUnit.MINUTES.toMillis(1)
    private val HOUR = TimeUnit.HOURS.toMillis(1)
    private val DAY = TimeUnit.DAYS.toMillis(1)

    private fun now(): Long = System.currentTimeMillis()

    private val TITLES = listOf(
        "提车三个月，这几个功能我一次都没用过",
        "十万块预算，第一台车到底怎么选？把账算给你看",
        "跑了一趟高速，油耗实测结果有点意外",
        "保养被坑了三百块，这四项其实完全可以不做",
        "这条街开了二十年的老店，只剩最后三个月",
        "厨房里最没用的三样东西，我全买了",
        "拆了一台两万块的机器，里面比想象中简单",
        "花一周把旧手机改成监控，成本不到五十块",
        "这个细节没人在意，但它决定了整道菜的成败",
        "我用了三年才明白，工具不是越多越好"
    )

    /**
     * 人物列表。刻意覆盖几种情况：
     * 单平台、双平台、同平台多个号、全部没更新、以及刚添加还没抓过。
     */
    val persons: List<Person> = listOf(
            Person(
                id = 1,
                remarkName = "老师好我叫何同学",
                accounts = listOf(
                    Account(id = 101, platform = Platform.BILI, unreadCount = 3,
                        lastFetchAt = now() - 1 * DAY, followerCount = 12_800_000, videoCount = 96),
                    Account(id = 102, platform = Platform.DOUYIN, unreadCount = 1,
                        lastFetchAt = now() - 2 * HOUR, followerCount = 9_400_000, videoCount = 210)
                )
            ),
            Person(
                id = 2,
                remarkName = "影视飓风",
                accounts = listOf(
                    Account(id = 103, platform = Platform.BILI, homepageUrl = "https://space.bilibili.com/946974", unreadCount = 5,
                        lastFetchAt = now() - 30 * MINUTE, followerCount = 8_420_000, videoCount = 412)
                )
            ),
            Person(
                id = 3,
                remarkName = "阿强说车",
                accounts = listOf(
                    Account(id = 104, platform = Platform.DOUYIN, unreadCount = 3,
                        lastFetchAt = now() - 2 * HOUR, followerCount = 1_260_000, videoCount = 838),
                    Account(id = 105, platform = Platform.KUAISHOU, unreadCount = 0,
                        lastFetchAt = now() - 6 * HOUR, followerCount = 640_000, videoCount = 502)
                )
            ),
            // 同一个人在同一平台有两个号：主号 + 生活号
            Person(
                id = 4,
                remarkName = "老饭骨",
                accounts = listOf(
                    Account(id = 106, platform = Platform.BILI, label = "主号", unreadCount = 0,
                        lastFetchAt = now() - 1 * DAY, followerCount = 2_100_000, videoCount = 380),
                    Account(id = 107, platform = Platform.BILI, label = "生活号", unreadCount = 4,
                        lastFetchAt = now() - 3 * HOUR, followerCount = 86_000, videoCount = 41),
                    Account(id = 108, platform = Platform.DOUYIN, label = "工作室号", unreadCount = 2,
                        lastFetchAt = now() - 8 * HOUR, followerCount = 5_600_000, videoCount = 1_204),
                    Account(id = 109, platform = Platform.KUAISHOU, unreadCount = 9,
                        lastFetchAt = now() - 6 * HOUR, followerCount = 3_050_000, videoCount = 640)
                )
            ),
            Person(
                id = 5,
                remarkName = "刚添加还没抓过",
                accounts = listOf(
                    Account(id = 110, platform = Platform.BILI, unreadCount = 0, lastFetchAt = 0L)
                )
            )
        )

    /** 某个人在某个平台上的作品列表。按人物与平台确定性生成，保证每次进都一致。 */
    fun videosFor(personId: Long, platform: Platform): List<Video> {
        val seed = personId * 100 + platform.ordinal * 10
        return List(6) { index ->
            val pick = ((seed + index) % TITLES.size).toInt()
            Video(
                id = seed + index,
                platformVideoId = "${platform.name.lowercase()}_${personId}_$index",
                title = TITLES[pick],
                publishedAt = now() - (index + 1) * 9 * HOUR,
                durationMs = (3 + pick) * 60_000L + pick * 1_100L,
                seen = index > 1
            )
        }
    }

    /**
     * 待确认页用数据：按博主分组。
     * 其中一组刻意做成「抓取时间超过 24 小时」，用来验证灰态不可用提示。
     */
    val pendingGroups: List<PendingGroup> = listOf(
            PendingGroup(
                personId = 4,
                personName = "老饭骨",
                platform = Platform.KUAISHOU,
                lastFetchAt = now() - 6 * HOUR,
                items = List(7) { index ->
                    Video(
                        id = 100L + index,
                        platformVideoId = "ks_pending_$index",
                        title = TITLES[index % TITLES.size],
                        publishedAt = now() - (index + 1) * 5 * HOUR,
                        durationMs = (3 + index) * 60_000L
                    )
                }
            ),
            PendingGroup(
                personId = 3,
                personName = "阿强说车",
                platform = Platform.DOUYIN,
                lastFetchAt = now() - 2 * HOUR,
                items = List(3) { index ->
                    Video(
                        id = 200L + index,
                        platformVideoId = "dy_pending_$index",
                        title = TITLES[(index + 3) % TITLES.size],
                        publishedAt = now() - (index + 1) * 8 * HOUR,
                        durationMs = (5 + index) * 60_000L
                    )
                }
            ),
            PendingGroup(
                personId = 1,
                personName = "老师好我叫何同学",
                platform = Platform.BILI,
                // 超过 24 小时：分组标题旁显示灰色过期提示
                lastFetchAt = now() - 2 * DAY,
                items = List(4) { index ->
                    Video(
                        id = 300L + index,
                        platformVideoId = "bili_stale_$index",
                        title = TITLES[(index + 6) % TITLES.size],
                        publishedAt = now() - (index + 1) * 12 * HOUR,
                        durationMs = (2 + index) * 60_000L
                    )
                }
            )
        )


}
