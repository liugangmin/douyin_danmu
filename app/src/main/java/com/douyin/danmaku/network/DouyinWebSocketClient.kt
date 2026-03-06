package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * 抖音直播WebSocket客户端（简化版）
 */
class DouyinWebSocketClient(
    private val onDanmaku: (DanmakuMessage) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var isConnecting = false
    
    fun connect(roomId: String, userUniqueId: String, signature: String) {
        if (isConnecting) return
        isConnecting = true
        
        try {
            val url = buildWssUrl(roomId, userUniqueId, signature)
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .build()
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnecting = false
                    onConnected()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    // 文本消息
                    parseTextMessage(text)
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    // 二进制消息，解析弹幕
                    parseBinaryMessage(bytes.toByteArray())
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnecting = false
                    onDisconnected()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnecting = false
                    onError(t.message ?: "连接失败")
                }
            })
        } catch (e: Exception) {
            isConnecting = false
            onError(e.message ?: "连接异常")
        }
    }
    
    private fun buildWssUrl(roomId: String, userUniqueId: String, signature: String): String {
        return "wss://webcast5-ws-web-hl.douyin.com/webcast/im/push/v2/" +
                "?app_id=1128" +
                "&device_platform=web" +
                "&language=zh-CN" +
                "&enter_from=web_live" +
                "&cookie_enabled=true" +
                "&browser_language=zh-CN" +
                "&browser_platform=MacIntel" +
                "&browser_name=Chrome" +
                "&browser_version=120.0.0.0" +
                "&browser_online=true" +
                "&host=https://live.douyin.com" +
                "&im_path=/webcast/im/push/v2/" +
                "&identity=audience" +
                "&room_id=${roomId}" +
                "&user_unique_id=${userUniqueId}" +
                "&signature=${signature}" +
                "&ttwid=${generateTtwid()}"
    }
    
    private fun generateTtwid(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
    
    private fun parseTextMessage(text: String) {
        // 简单解析文本消息
        if (text.contains("nickname") && text.contains("content")) {
            val nickname = extractValue(text, "nickname")
            val content = extractValue(text, "content")
            if (nickname != null && content != null) {
                onDanmaku(DanmakuMessage(
                    type = DanmakuType.CHAT,
                    nickname = nickname,
                    content = content
                ))
            }
        }
    }
    
    private fun parseBinaryMessage(data: ByteArray) {
        // 简单解析二进制消息
        try {
            val str = String(data, Charsets.UTF_8)
            
            // 检测消息类型
            when {
                str.contains("ChatMessage") -> {
                    val nickname = extractValue(str, "nickname")
                    val content = extractValue(str, "content")
                    if (nickname != null && content != null) {
                        onDanmaku(DanmakuMessage(
                            type = DanmakuType.CHAT,
                            nickname = nickname,
                            content = content
                        ))
                    }
                }
                str.contains("MemberMessage") -> {
                    val nickname = extractValue(str, "nickname")
                    if (nickname != null) {
                        onDanmaku(DanmakuMessage(
                            type = DanmakuType.ENTER,
                            nickname = nickname,
                            content = "进入了直播间"
                        ))
                    }
                }
                str.contains("GiftMessage") -> {
                    val nickname = extractValue(str, "nickname")
                    if (nickname != null) {
                        onDanmaku(DanmakuMessage(
                            type = DanmakuType.GIFT,
                            nickname = nickname,
                            content = "送出了礼物"
                        ))
                    }
                }
                str.contains("LikeMessage") -> {
                    val nickname = extractValue(str, "nickname")
                    if (nickname != null) {
                        onDanmaku(DanmakuMessage(
                            type = DanmakuType.LIKE,
                            nickname = nickname,
                            content = "点了赞"
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失败，忽略
        }
    }
    
    private fun extractValue(str: String, key: String): String? {
        val patterns = listOf(
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\""),
            Regex("$key\":\"([^\"]+)\""),
            Regex("$key=([^,\\]]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(str)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
    
    fun isConnected(): Boolean {
        return webSocket != null && !isConnecting
    }
}
