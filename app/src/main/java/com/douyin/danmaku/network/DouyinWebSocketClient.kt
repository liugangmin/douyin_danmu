package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.proto.PushFrame
import com.douyin.danmaku.proto.Response
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * 抖音直播WebSocket客户端
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
    
    private var heartbeatJob: Job? = null
    private var cursor: String = ""
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
                    startHeartbeat()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    // 文本消息，通常不处理
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    // 二进制消息，解析弹幕
                    try {
                        val messages = DanmakuParser.parsePushFrame(bytes.toByteArray())
                        for (msg in messages) {
                            onDanmaku(msg)
                        }
                    } catch (e: Exception) {
                        // 解析失败
                    }
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnecting = false
                    stopHeartbeat()
                    onDisconnected()
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnecting = false
                    stopHeartbeat()
                    onError(t.message ?: "连接失败")
                }
            })
        } catch (e: Exception) {
            isConnecting = false
            onError(e.message ?: "连接异常")
        }
    }
    
    private fun buildWssUrl(roomId: String, userUniqueId: String, signature: String): String {
        val timestamp = System.currentTimeMillis()
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
                "&cursor=${cursor}" +
                "&internal_ext=internal_src:dim_perceiver|wss_push_room_id:${roomId}|wss_push_did:${userUniqueId}" +
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
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }
    
    private fun startHeartbeat() {
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(10000)
                sendHeartbeat()
            }
        }
    }
    
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    private fun sendHeartbeat() {
        try {
            val heartbeat = buildHeartbeat()
            webSocket?.send(heartbeat)
        } catch (e: Exception) {
            // 发送心跳失败
        }
    }
    
    private fun buildHeartbeat(): okio.ByteString {
        val pushFrame = PushFrame.newBuilder()
            .setSeqId(System.currentTimeMillis())
            .setLogId(System.currentTimeMillis())
            .setService(1000)
            .setMethod(1001)
            .build()
        
        return okio.ByteString.of(*pushFrame.toByteArray())
    }
    
    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }
    
    fun isConnected(): Boolean {
        return webSocket != null && !isConnecting
    }
}
