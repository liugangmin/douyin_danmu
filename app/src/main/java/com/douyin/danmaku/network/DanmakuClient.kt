package com.douyin.danmaku.network

import android.content.Context
import android.util.Log
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import com.douyin.danmaku.proto.*
import com.douyin.danmaku.utils.SignatureGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

class DanmakuClient(private val context: Context) {
    
    companion object {
        private const val TAG = "DanmakuClient"
    }
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val isConnected = AtomicBoolean(false)
    private var heartbeatRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    private var ttwid: String? = null
    private var roomId: String? = null
    private var signatureGenerator: SignatureGenerator? = null
    
    suspend fun connect(webRoomId: String) {
        withContext(Dispatchers.IO) {
            try {
                disconnect()
                    
                Log.d(TAG, "开始连接直播间: $webRoomId")
                    
                // 0. 初始化签名生成器
                if (signatureGenerator == null) {
                    signatureGenerator = SignatureGenerator(context)
                    val initSuccess = signatureGenerator!!.init()
                    Log.d(TAG, "签名生成器初始化: $initSuccess")
                    if (!initSuccess) {
                        withContext(Dispatchers.Main) {
                            onErrorCallback?.invoke("签名生成器初始化失败")
                        }
                        return@withContext
                    }
                }
                    
                // 1. 获取ttwid
                ttwid = fetchTtwid(webRoomId)
                Log.d(TAG, "获取ttwid: $ttwid")
                    
                // 2. 获取真实roomId
                roomId = fetchRoomId(webRoomId, ttwid)
                Log.d(TAG, "获取roomId: $roomId")
                    
                if (roomId.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        onErrorCallback?.invoke("无法获取直播间信息，请检查房间号是否正确")
                    }
                    return@withContext
                }
                    
                // 3. 连接WebSocket
                connectWebSocket()
                    
            } catch (e: Exception) {
                Log.e(TAG, "连接失败", e)
                withContext(Dispatchers.Main) {
                    onErrorCallback?.invoke("连接失败: ${e.message}")
                }
            }
        }
    }
        
    private fun fetchTtwid(webRoomId: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://live.douyin.com/$webRoomId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                .build()
                
            val response = client.newCall(request).execute()
            response.headers("Set-Cookie").forEach { cookie ->
                if (cookie.startsWith("ttwid=")) {
                    return cookie.substringAfter("ttwid=").substringBefore(";")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取ttwid失败", e)
            null
        }
    }
        
    private fun fetchRoomId(webRoomId: String, ttwid: String?): String? {
        return try {
            val msToken = generateMsToken()
            val request = Request.Builder()
                .url("https://live.douyin.com/$webRoomId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
                .header("Cookie", "ttwid=${ttwid ?: ""}; msToken=$msToken; __ac_nonce=0123407cc00a9e438deb4")
                .build()
                
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
                
            // 提取roomId - 多种匹配模式
            val patterns = listOf(
                Regex("""roomId\\":\\"(\d+)\\""""),
                Regex(""""roomId":\s*"?(\d+)"?"""),
                Regex("""ROOM_ID\s*=\s*['"]?(\d+)['"]?""")
            )
                
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取roomId失败", e)
            null
        }
    }
        
    private fun generateMsToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..182).map { chars.random() }.joinToString("")
    }
        
    private fun generateUserUniqueId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (100000000..999999999).random()
        return "$timestamp$random"
    }
        
    private fun connectWebSocket() {
        val userUniqueId = generateUserUniqueId()
            
        val wsUrlBase = buildString {
            append("wss://webcast100-ws-web-lq.douyin.com/webcast/im/push/v2/?")
            append("app_name=douyin_web&version_code=180800&webcast_sdk_version=1.0.14-beta.0")
            append("&update_version_code=1.0.14-beta.0&compress=gzip&device_platform=web")
            append("&cookie_enabled=true&screen_width=1536&screen_height=864")
            append("&browser_language=zh-CN&browser_platform=Win32&browser_name=Mozilla")
            append("&browser_version=5.0%20(Windows%20NT%2010.0;%20Win64;%20x64)%20AppleWebKit/537.36")
            append("&browser_online=true&tz_name=Asia/Shanghai")
            append("&host=https://live.douyin.com&aid=6383&live_id=1&did_rule=3&endpoint=live_pc&support_wrds=1")
            append("&user_unique_id=$userUniqueId&im_path=/webcast/im/fetch/&identity=audience")
            append("&need_persist_msg_count=15&room_id=${roomId}&heartbeatDuration=0")
        }
            
        // 生成签名
        var wsUrl = wsUrlBase
        try {
            val signature = signatureGenerator?.generateSignature(wsUrlBase)
            Log.d(TAG, "生成签名: $signature")
            if (!signature.isNullOrEmpty()) {
                wsUrl = "$wsUrlBase&signature=$signature"
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成签名失败", e)
            // 继续尝试不带签名连接
        }
            
        Log.d(TAG, "WebSocket URL: $wsUrl")
            
        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .header("Cookie", "ttwid=${ttwid ?: ""}")
            .build()
            
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接成功")
                isConnected.set(true)
                startHeartbeat()
                onConnectedCallback?.invoke()
            }
                
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    parseMessage(bytes.toByteArray())
                } catch (e: Exception) {
                    Log.e(TAG, "解析消息失败", e)
                }
            }
                
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }
                
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 关闭: $code - $reason")
                isConnected.set(false)
                stopHeartbeat()
                onDisconnectedCallback?.invoke()
            }
                
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 失败", t)
                isConnected.set(false)
                stopHeartbeat()
                onErrorCallback?.invoke("连接失败: ${t.message}")
            }
        })
    }
    
    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected.get()) {
                    try {
                        val heartbeat = PushFrame.newBuilder()
                            .setPayloadType("hb")
                            .build()
                            .toByteArray()
                        webSocket?.send(heartbeat.toByteString())
                        Log.d(TAG, "发送心跳包")
                    } catch (e: Exception) {
                        Log.e(TAG, "发送心跳失败", e)
                    }
                    handler.postDelayed(this, 10000)
                }
            }
        }
        handler.postDelayed(heartbeatRunnable!!, 10000)
    }
    
    private fun stopHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
    }
    
    private fun parseMessage(data: ByteArray) {
        try {
            val pushFrame = PushFrame.parseFrom(data)
            val payload = pushFrame.payload.toByteArray()
            
            if (payload.isEmpty()) return
            
            // GZIP解压
            val decompressed = GZIPInputStream(payload.inputStream()).readBytes()
            
            val response = Response.parseFrom(decompressed)
            
            // 发送ACK
            if (response.needAck && response.internalExt.isNotEmpty()) {
                val ack = PushFrame.newBuilder()
                    .setLogId(pushFrame.logId)
                    .setPayloadType("ack")
                    .setPayload(com.google.protobuf.ByteString.copyFromUtf8(response.internalExt))
                    .build()
                    .toByteArray()
                webSocket?.send(ack.toByteString())
            }
            
            // 解析消息
            response.messagesListList.forEach { msg ->
                val method = msg.method
                Log.d(TAG, "收到消息类型: $method")
                
                try {
                    when {
                        method.contains("ChatMessage") -> parseChatMessage(msg.payload.toByteArray())
                        method.contains("GiftMessage") -> parseGiftMessage(msg.payload.toByteArray())
                        method.contains("MemberMessage") -> parseMemberMessage(msg.payload.toByteArray())
                        method.contains("LikeMessage") -> parseLikeMessage(msg.payload.toByteArray())
                        method.contains("SocialMessage") -> parseSocialMessage(msg.payload.toByteArray())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析 $method 失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析PushFrame失败", e)
        }
    }
    
    private fun parseChatMessage(data: ByteArray) {
        val msg = ChatMessage.parseFrom(data)
        val user = msg.user
        if (user != null && msg.content.isNotEmpty()) {
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = DanmakuType.CHAT,
                nickname = user.nickName,
                content = msg.content,
                userId = user.id.toString()
            ))
        }
    }
    
    private fun parseGiftMessage(data: ByteArray) {
        val msg = GiftMessage.parseFrom(data)
        val user = msg.user
        val giftName = if (msg.hasGift()) msg.gift.name else "礼物"
        if (user != null) {
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = DanmakuType.GIFT,
                nickname = user.nickName,
                content = "送出了 $giftName x${msg.comboCount}",
                userId = user.id.toString()
            ))
        }
    }
    
    private fun parseMemberMessage(data: ByteArray) {
        val msg = MemberMessage.parseFrom(data)
        val user = msg.user
        if (user != null) {
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = DanmakuType.ENTER,
                nickname = user.nickName,
                content = "进入了直播间",
                userId = user.id.toString()
            ))
        }
    }
    
    private fun parseLikeMessage(data: ByteArray) {
        val msg = LikeMessage.parseFrom(data)
        val user = msg.user
        if (user != null) {
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = DanmakuType.LIKE,
                nickname = user.nickName,
                content = "点了${msg.count}个赞",
                userId = user.id.toString()
            ))
        }
    }
    
    private fun parseSocialMessage(data: ByteArray) {
        val msg = SocialMessage.parseFrom(data)
        val user = msg.user
        if (user != null && msg.action == 1L) {
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = DanmakuType.FOLLOW,
                nickname = user.nickName,
                content = "关注了主播",
                userId = user.id.toString()
            ))
        }
    }
    
    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        isConnected.set(false)
        signatureGenerator?.destroy()
        signatureGenerator = null
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
