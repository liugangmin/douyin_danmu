package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import com.douyin.danmaku.proto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DanmakuClient {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val isConnected = AtomicBoolean(false)
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    suspend fun connect(webRoomId: String) {
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                
                // 获取直播间信息
                val roomInfo = fetchRoomInfo(webRoomId)
                if (roomInfo == null) {
                    withContext(Dispatchers.Main) {
                        onErrorCallback?.invoke("无法获取直播间信息")
                    }
                    return@withContext
                }
                
                // 连接WebSocket
                connectWebSocket(roomInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onErrorCallback?.invoke("连接失败: ${e.message}")
                }
            }
        }
    }
    
    private data class RoomInfo(
        val roomId: String,
        val userUniqueId: String
    )
    
    private fun fetchRoomInfo(webRoomId: String): RoomInfo? {
        return try {
            val url = "https://live.douyin.com/$webRoomId"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val html = response.body?.string() ?: return null
            
            // 提取roomId
            val roomIdPattern = Regex("\"roomId\"\\s*:\\s*\"?(\\d+)\"?")
            val roomIdMatch = roomIdPattern.find(html)
            val roomId = roomIdMatch?.groupValues?.get(1) ?: return null
            
            // 生成userUniqueId
            val userUniqueId = System.currentTimeMillis().toString() + (0..999999).random().toString().padStart(6, '0')
            
            RoomInfo(roomId, userUniqueId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun connectWebSocket(roomInfo: RoomInfo) {
        val wsUrl = buildWebSocketUrl(roomInfo.roomId, roomInfo.userUniqueId)
        
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                onConnectedCallback?.invoke()
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    parseMessage(bytes.toByteArray())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                onDisconnectedCallback?.invoke()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                onErrorCallback?.invoke(t.message ?: "连接失败")
            }
        })
    }
    
    private fun buildWebSocketUrl(roomId: String, userUniqueId: String): String {
        val ttwid = generateRandomString(32)
        return "wss://webcast5-ws-web-lf.douyin.com/webcast/im/push/v2/?" +
                "app_id=1128&" +
                "device_platform=web&" +
                "language=zh-CN&" +
                "enter_from=web_live&" +
                "room_id=$roomId&" +
                "user_unique_id=$userUniqueId&" +
                "ttwid=$ttwid"
    }
    
    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
    
    private fun parseMessage(data: ByteArray) {
        try {
            val pushFrame = PushFrame.parseFrom(data)
            val payload = pushFrame.payload.toByteArray()
            val response = Response.parseFrom(payload)
            
            for (message in response.messagesList) {
                val method = message.method
                val msgPayload = message.payload.toByteArray()
                
                when {
                    method.contains("ChatMessage") -> {
                        val chatMsg = ChatMessage.parseFrom(msgPayload)
                        val user = chatMsg.user
                        if (user != null && chatMsg.content.isNotEmpty()) {
                            onDanmakuCallback?.invoke(DanmakuMessage(
                                type = DanmakuType.CHAT,
                                nickname = user.nickname,
                                content = chatMsg.content,
                                userId = user.id.toString()
                            ))
                        }
                    }
                    method.contains("MemberMessage") -> {
                        val memberMsg = MemberMessage.parseFrom(msgPayload)
                        val user = memberMsg.user
                        if (user != null) {
                            onDanmakuCallback?.invoke(DanmakuMessage(
                                type = DanmakuType.ENTER,
                                nickname = user.nickname,
                                content = "进入了直播间",
                                userId = user.id.toString()
                            ))
                        }
                    }
                    method.contains("GiftMessage") -> {
                        val giftMsg = GiftMessage.parseFrom(msgPayload)
                        val user = giftMsg.user
                        if (user != null) {
                            onDanmakuCallback?.invoke(DanmakuMessage(
                                type = DanmakuType.GIFT,
                                nickname = user.nickname,
                                content = "送出了 ${giftMsg.giftName.ifEmpty { "礼物" }}",
                                userId = user.id.toString()
                            ))
                        }
                    }
                    method.contains("LikeMessage") -> {
                        val likeMsg = LikeMessage.parseFrom(msgPayload)
                        val user = likeMsg.user
                        if (user != null) {
                            onDanmakuCallback?.invoke(DanmakuMessage(
                                type = DanmakuType.LIKE,
                                nickname = user.nickname,
                                content = "点了赞",
                                userId = user.id.toString()
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected.set(false)
    }
    
    fun setOnDanmakuCallback(callback: (DanmakuMessage) -> Unit) {
        onDanmakuCallback = callback
    }
    
    fun setOnConnectedCallback(callback: () -> Unit) {
        onConnectedCallback = callback
    }
    
    fun setOnDisconnectedCallback(callback: () -> Unit) {
        onDisconnectedCallback = callback
    }
    
    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
    }
}
