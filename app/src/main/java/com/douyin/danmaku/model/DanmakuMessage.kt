package com.douyin.danmaku.model

data class DanmakuMessage(
    val id: Long = System.currentTimeMillis(),
    val type: DanmakuType,
    val nickname: String,
    val content: String,
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val emojis: List<EmojiInfo> = emptyList()
)

data class EmojiInfo(
    val text: String,
    val url: String
)

enum class DanmakuType {
    CHAT,           // 聊天弹幕
    GIFT,           // 礼物
    ENTER,          // 进场
    LIKE,           // 点赞
    FOLLOW,         // 关注
    FANS_CLUB,      // 粉丝团
    STATS           // 统计信息
}
