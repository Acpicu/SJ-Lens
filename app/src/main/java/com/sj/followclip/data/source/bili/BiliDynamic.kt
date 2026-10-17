package com.sj.followclip.data.source.bili

/**
 * 一条 B 站动态。
 *
 * 动态和投稿是两种内容：投稿是正式视频，动态是图文/转发/短内容。
 * 用户关注一个人，往往两样都想看，所以在账号页里分成两个页签。
 */
data class BiliDynamic(
    val id: String,
    /** 正文；转发自己的视频时正文可能是空的，用视频标题兜底 */
    val text: String,
    /** 配图或视频封面，可能没有 */
    val coverUrl: String?,
    /** 发布时间，毫秒；取不到为 0 */
    val publishedAt: Long
)
