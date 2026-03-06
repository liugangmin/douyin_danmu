package com.douyin.danmaku.network

import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import tech.ordinaryroad.live.chat.client.commons.base.listener.IBaseConnectionListener
import tech.ordinaryroad.live.chat.client.douyin.client.DouyinLiveChatClient
import tech.ordinaryroad.live.chat.client.douyin.config.DouyinLiveChatClientConfig
import tech.ordinaryroad.live.chat.client.douyin.listener.IDouyinMsgListener
import tech.ordinaryroad.live.chat.client.douyin.msg.DouyinDanmuMsg
import tech.ordinaryroad.live.chat.client.douyin.msg.DouyinEnterRoomMsg
import tech.ordinaryroad.live.chat.client.douyin.msg.DouyinGiftMsg
import tech.ordinaryroad.live.chat.client.douyin.msg.DouyinLikeMsg
import tech.ordinaryroad.live.chat.client.douyin.netty.handler.DouyinBinaryFrameHandler
import java.util.concurrent.Executors

class DanmakuClient {
    
    private var client: DouyinLiveChatClient? = null
    private val executor = Executors.newCachedThreadPool()
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    fun connect(roomId: String) {
        executor.execute {
            try {
                disconnect()
                
                val config = DouyinLiveChatClientConfig.builder()
                    .roomId(roomId)
                    .build()
                
                client = DouyinLiveChatClient(config, object : IDouyinMsgListener {
                    override fun onDanmuMsg(
                        binaryFrameHandler: DouyinBinaryFrameHandler?,
                        msg: DouyinDanmuMsg?
                    ) {
                        msg?.let {
                            val nickname = it.username ?: ""
                            val content = it.content ?: ""
                            if (nickname.isNotEmpty() && content.isNotEmpty()) {
                                onDanmakuCallback?.invoke(DanmakuMessage(
                                    type = DanmakuType.CHAT,
                                    nickname = nickname,
                                    content = content,
                                    userId = it.uid?.toString() ?: ""
                                ))
                            }
                        }
                    }
                    
                    override fun onEnterRoomMsg(msg: DouyinEnterRoomMsg?) {
                        msg?.let {
                            val nickname = it.username ?: ""
                            if (nickname.isNotEmpty()) {
                                onDanmakuCallback?.invoke(DanmakuMessage(
                                    type = DanmakuType.ENTER,
                                    nickname = nickname,
                                    content = "进入了直播间",
                                    userId = it.uid?.toString() ?: ""
                                ))
                            }
                        }
                    }
                    
                    override fun onGiftMsg(
                        binaryFrameHandler: DouyinBinaryFrameHandler?,
                        msg: DouyinGiftMsg?
                    ) {
                        msg?.let {
                            val nickname = it.username ?: ""
                            val giftName = it.giftName ?: "礼物"
                            if (nickname.isNotEmpty()) {
                                onDanmakuCallback?.invoke(DanmakuMessage(
                                    type = DanmakuType.GIFT,
                                    nickname = nickname,
                                    content = "送出了 $giftName",
                                    userId = it.uid?.toString() ?: ""
                                ))
                            }
                        }
                    }
                    
                    override fun onLikeMsg(
                        binaryFrameHandler: DouyinBinaryFrameHandler?,
                        msg: DouyinLikeMsg?
                    ) {
                        msg?.let {
                            val nickname = it.username ?: ""
                            if (nickname.isNotEmpty()) {
                                onDanmakuCallback?.invoke(DanmakuMessage(
                                    type = DanmakuType.LIKE,
                                    nickname = nickname,
                                    content = "点了赞",
                                    userId = it.uid?.toString() ?: ""
                                ))
                            }
                        }
                    }
                })
                
                client?.addConnectionListener(object : IBaseConnectionListener {
                    override fun onConnected() {
                        onConnectedCallback?.invoke()
                    }
                    
                    override fun onDisconnected() {
                        onDisconnectedCallback?.invoke()
                    }
                    
                    override fun onError(cause: Throwable?) {
                        onErrorCallback?.invoke(cause?.message ?: "连接错误")
                    }
                })
                
                client?.connect()
            } catch (e: Exception) {
                e.printStackTrace()
                onErrorCallback?.invoke("连接失败: ${e.message}")
            }
        }
    }
    
    fun disconnect() {
        try {
            client?.disconnect()
            client = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun destroy() {
        disconnect()
        executor.shutdown()
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
