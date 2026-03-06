package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import com.douyin.danmaku.proto.*
import com.google.protobuf.InvalidProtocolBufferException

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
        } catch (e: InvalidProtocolBufferException) {
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseChatMessage(payload: ByteArray): DanmakuMessage? {
        val chatMessage = ChatMessage.parseFrom(payload)
        val user = chatMessage.user ?: return null
        val content = chatMessage.content
        
        if (content.isNullOrBlank()) return null
        
        return DanmakuMessage(
            type = DanmakuType.CHAT,
            nickname = user.nickname ?: "匿名用户",
            content = content,
            userId = user.id.toString()
        )
    }
    
    private fun parseGiftMessage(payload: ByteArray): DanmakuMessage? {
        val giftMessage = GiftMessage.parseFrom(payload)
        val user = giftMessage.user ?: return null
        
        return DanmakuMessage(
            type = DanmakuType.GIFT,
            nickname = user.nickname ?: "匿名用户",
            content = "送出了礼物",
            userId = user.id.toString()
        )
    }
    
    private fun parseMemberMessage(payload: ByteArray): DanmakuMessage? {
        val memberMessage = MemberMessage.parseFrom(payload)
        val user = memberMessage.user ?: return null
        
        return DanmakuMessage(
            type = DanmakuType.ENTER,
            nickname = user.nickname ?: "匿名用户",
            content = "进入了直播间",
            userId = user.id.toString()
        )
    }
    
    private fun parseLikeMessage(payload: ByteArray): DanmakuMessage? {
        val likeMessage = LikeMessage.parseFrom(payload)
        val user = likeMessage.user ?: return null
        val count = likeMessage.count
        
        return DanmakuMessage(
            type = DanmakuType.LIKE,
            nickname = user.nickname ?: "匿名用户",
            content = "点了${count}个赞",
            userId = user.id.toString()
        )
    }
    
    private fun parseStatsMessage(payload: ByteArray): DanmakuMessage? {
        val statsMessage = RoomUserSeqMessage.parseFrom(payload)
        val total = statsMessage.totalUser
        val popularity = statsMessage.popularity
        
        return DanmakuMessage(
            type = DanmakuType.STATS,
            nickname = "系统",
            content = "当前观看人数: ${formatNumber(popularity)}, 累计观看人数: ${formatNumber(total)}"
        )
    }
    
    private fun parseFansClubMessage(payload: ByteArray): DanmakuMessage? {
        val fansClubMessage = FansClubMessage.parseFrom(payload)
        val user = fansClubMessage.user ?: return null
        
        return DanmakuMessage(
            type = DanmakuType.FANS_CLUB,
            nickname = user.nickname ?: "匿名用户",
            content = "成为粉丝团成员",
            userId = user.id.toString()
        )
    }
    
    private fun formatNumber(num: Long): String {
        return when {
            num >= 10000 -> String.format("%.1f万", num / 10000.0)
            else -> num.toString()
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
