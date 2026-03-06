package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import com.douyin.danmaku.proto.ChatMessage
import com.douyin.danmaku.proto.GiftMessage
import com.douyin.danmaku.proto.LikeMessage
import com.douyin.danmaku.proto.MemberMessage
import com.douyin.danmaku.proto.FansClubMessage
import com.douyin.danmaku.proto.RoomUserSeqMessage
import com.douyin.danmaku.proto.PushFrame
import com.douyin.danmaku.proto.Response

/**
 * 弹幕消息解析器
 */
object DanmakuParser {
    
    fun parse(payload: ByteArray, method: String): DanmakuMessage? {
        return try {
            when {
                method.contains("ChatMessage") -> parseChatMessage(payload)
                method.contains("GiftMessage") -> parseGiftMessage(payload)
                method.contains("MemberMessage") -> parseMemberMessage(payload)
                method.contains("LikeMessage") -> parseLikeMessage(payload)
                method.contains("RoomUserSeqMessage") -> parseStatsMessage(payload)
                method.contains("FansClubMessage") -> parseFansClubMessage(payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseChatMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val chatMessage = ChatMessage.parseFrom(payload)
            val user = chatMessage.user ?: return null
            val content = chatMessage.content
            if (content.isEmpty()) return null
            
            DanmakuMessage(
                type = DanmakuType.CHAT,
                nickname = user.nickname,
                content = content,
                userId = user.id.toString()
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseGiftMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val giftMessage = GiftMessage.parseFrom(payload)
            val user = giftMessage.user ?: return null
            
            DanmakuMessage(
                type = DanmakuType.GIFT,
                nickname = user.nickname,
                content = "送出了礼物",
                userId = user.id.toString()
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseMemberMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val memberMessage = MemberMessage.parseFrom(payload)
            val user = memberMessage.user ?: return null
            
            DanmakuMessage(
                type = DanmakuType.ENTER,
                nickname = user.nickname,
                content = "进入了直播间",
                userId = user.id.toString()
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseLikeMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val likeMessage = LikeMessage.parseFrom(payload)
            val user = likeMessage.user ?: return null
            val count = likeMessage.count
            
            DanmakuMessage(
                type = DanmakuType.LIKE,
                nickname = user.nickname,
                content = "点了${count}个赞",
                userId = user.id.toString()
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseStatsMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val statsMessage = RoomUserSeqMessage.parseFrom(payload)
            val total = statsMessage.totalUser
            val popularity = statsMessage.popularity
            
            DanmakuMessage(
                type = DanmakuType.STATS,
                nickname = "系统",
                content = "当前观看: ${formatNumber(popularity)}, 累计: ${formatNumber(total)}"
            )
        } catch (e: Exception) { null }
    }
    
    private fun parseFansClubMessage(payload: ByteArray): DanmakuMessage? {
        return try {
            val fansClubMessage = FansClubMessage.parseFrom(payload)
            val user = fansClubMessage.user ?: return null
            
            DanmakuMessage(
                type = DanmakuType.FANS_CLUB,
                nickname = user.nickname,
                content = "成为粉丝团成员",
                userId = user.id.toString()
            )
        } catch (e: Exception) { null }
    }
    
    private fun formatNumber(num: Long): String {
        return if (num >= 10000) {
            String.format("%.1f万", num / 10000.0)
        } else {
            num.toString()
        }
    }
    
    fun parsePushFrame(data: ByteArray): List<DanmakuMessage> {
        val messages = mutableListOf<DanmakuMessage>()
        
        try {
            val pushFrame = PushFrame.parseFrom(data)
            val payload = pushFrame.payload
            
            if (payload != null && payload.isNotEmpty()) {
                val response = Response.parseFrom(payload)
                
                for (message in response.messagesList) {
                    val method = message.method ?: continue
                    val msgPayload = message.payload ?: continue
                    
                    parse(msgPayload.toByteArray(), method)?.let {
                        messages.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败
        }
        
        return messages
    }
}
