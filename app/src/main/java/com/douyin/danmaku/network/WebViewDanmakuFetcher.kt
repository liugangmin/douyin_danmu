package com.douyin.danmaku.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WebView弹幕获取器
 * 通过注入JavaScript拦截WebSocket消息获取弹幕
 */
class WebViewDanmakuFetcher(private val context: Context) {
    
    private var webView: WebView? = null
    private var isInitialized = false
    private val initLatch = CountDownLatch(1)
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun init() {
        mainHandler.post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    allowContentAccess = true
                    allowFileAccess = true
                    blockNetworkImage = true // 不加载图片，节省流量
                }
                
                // 添加JavaScript接口
                addJavascriptInterface(JsBridge(), "AndroidDanmakuBridge")
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 注入弹幕拦截脚本
                        injectDanmakuScript()
                    }
                    
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        if (request?.url?.toString()?.contains("live.douyin.com") == true) {
                            onErrorCallback?.invoke("页面加载错误: ${error?.description}")
                        }
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        // 可以在这里打印控制台日志用于调试
                        return true
                    }
                }
            }
            // 标记初始化完成
            isInitialized = true
            initLatch.countDown()
        }
    }
    
    fun waitForInit(timeout: Long = 10000): Boolean {
        return initLatch.await(timeout, TimeUnit.MILLISECONDS)
    }
    
    fun connect(roomId: String) {
        mainHandler.post {
            val url = "https://live.douyin.com/$roomId"
            webView?.loadUrl(url)
        }
    }
    
    private fun injectDanmakuScript() {
        val script = """
            (function() {
                console.log('Danmaku script injected');
                
                // 保存原始WebSocket
                var OriginalWebSocket = window.WebSocket;
                var originalSend = OriginalWebSocket.prototype.send;
                
                // 重写WebSocket
                window.WebSocket = function(url, protocols) {
                    console.log('WebSocket created: ' + url);
                    var ws = new OriginalWebSocket(url, protocols);
                    
                    // 监听消息
                    ws.addEventListener('message', function(event) {
                        try {
                            if (event.data instanceof ArrayBuffer) {
                                // 二进制消息 - 可能是protobuf数据
                                var bytes = new Uint8Array(event.data);
                                
                                // 尝试解析PushFrame
                                try {
                                    parseProtobufMessage(bytes);
                                } catch(e) {
                                    // 解析失败，忽略
                                }
                            } else if (typeof event.data === 'string') {
                                // 文本消息
                                console.log('Text message: ' + event.data);
                            }
                        } catch(e) {
                            console.log('Error processing message: ' + e);
                        }
                    });
                    
                    ws.addEventListener('open', function() {
                        console.log('WebSocket connected');
                        if (window.AndroidDanmakuBridge) {
                            AndroidDanmakuBridge.onConnected();
                        }
                    });
                    
                    ws.addEventListener('close', function() {
                        console.log('WebSocket closed');
                        if (window.AndroidDanmakuBridge) {
                            AndroidDanmakuBridge.onDisconnected();
                        }
                    });
                    
                    ws.addEventListener('error', function(error) {
                        console.log('WebSocket error: ' + error);
                        if (window.AndroidDanmakuBridge) {
                            AndroidDanmakuBridge.onError('WebSocket error');
                        }
                    });
                    
                    return ws;
                };
                
                // 复制静态属性
                window.WebSocket.CONNECTING = OriginalWebSocket.CONNECTING;
                window.WebSocket.OPEN = OriginalWebSocket.OPEN;
                window.WebSocket.CLOSING = OriginalWebSocket.CLOSING;
                window.WebSocket.CLOSED = OriginalWebSocket.CLOSED;
                
                // 解析Protobuf消息
                function parseProtobufMessage(bytes) {
                    try {
                        // 简单的消息类型检测
                        var method = detectMessageType(bytes);
                        
                        if (method) {
                            // 提取弹幕信息
                            var message = extractMessage(bytes, method);
                            if (message && window.AndroidDanmakuBridge) {
                                AndroidDanmakuBridge.onDanmaku(
                                    message.type,
                                    message.nickname,
                                    message.content,
                                    message.userId || ''
                                );
                            }
                        }
                    } catch(e) {
                        // 解析失败
                    }
                }
                
                // 检测消息类型
                function detectMessageType(bytes) {
                    // 在字节数组中搜索方法名
                    var methods = ['ChatMessage', 'GiftMessage', 'MemberMessage', 'LikeMessage', 'RoomUserSeqMessage'];
                    var bytesStr = String.fromCharCode.apply(null, bytes);
                    
                    for (var i = 0; i < methods.length; i++) {
                        if (bytesStr.indexOf(methods[i]) !== -1) {
                            return methods[i];
                        }
                    }
                    return null;
                }
                
                // 提取消息内容
                function extractMessage(bytes, method) {
                    var bytesStr = String.fromCharCode.apply(null, bytes);
                    
                    if (method === 'ChatMessage') {
                        return extractChatMessage(bytesStr);
                    } else if (method === 'MemberMessage') {
                        return extractMemberMessage(bytesStr);
                    } else if (method === 'GiftMessage') {
                        return extractGiftMessage(bytesStr);
                    } else if (method === 'LikeMessage') {
                        return extractLikeMessage(bytesStr);
                    }
                    
                    return null;
                }
                
                // 提取聊天消息
                function extractChatMessage(str) {
                    var nickname = extractNickname(str);
                    var content = extractContent(str);
                    
                    if (nickname && content) {
                        return {
                            type: 'CHAT',
                            nickname: nickname,
                            content: content,
                            userId: ''
                        };
                    }
                    return null;
                }
                
                // 提取进场消息
                function extractMemberMessage(str) {
                    var nickname = extractNickname(str);
                    
                    if (nickname) {
                        return {
                            type: 'ENTER',
                            nickname: nickname,
                            content: '进入了直播间',
                            userId: ''
                        };
                    }
                    return null;
                }
                
                // 提取礼物消息
                function extractGiftMessage(str) {
                    var nickname = extractNickname(str);
                    
                    if (nickname) {
                        return {
                            type: 'GIFT',
                            nickname: nickname,
                            content: '送出了礼物',
                            userId: ''
                        };
                    }
                    return null;
                }
                
                // 提取点赞消息
                function extractLikeMessage(str) {
                    var nickname = extractNickname(str);
                    
                    if (nickname) {
                        return {
                            type: 'LIKE',
                            nickname: nickname,
                            content: '点了赞',
                            userId: ''
                        };
                    }
                    return null;
                }
                
                // 从字符串中提取昵称
                function extractNickname(str) {
                    // 尝试多种模式提取昵称
                    var patterns = [
                        /nickname":"([^"]+)"/,
                        /nick_name":"([^"]+)"/,
                        /"nick":"([^"]+)"/
                    ];
                    
                    for (var i = 0; i < patterns.length; i++) {
                        var match = str.match(patterns[i]);
                        if (match && match[1]) {
                            return match[1];
                        }
                    }
                    return null;
                }
                
                // 从字符串中提取内容
                function extractContent(str) {
                    var patterns = [
                        /content":"([^"]+)"/,
                        /"text":"([^"]+)"/
                    ];
                    
                    for (var i = 0; i < patterns.length; i++) {
                        var match = str.match(patterns[i]);
                        if (match && match[1]) {
                            return match[1];
                        }
                    }
                    return null;
                }
                
                console.log('Danmaku script ready');
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script, null)
    }
    
    fun disconnect() {
        mainHandler.post {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
        }
    }
    
    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
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
    
    inner class JsBridge {
        @JavascriptInterface
        fun onDanmaku(type: String, nickname: String, content: String, userId: String) {
            val danmakuType = when (type) {
                "CHAT" -> DanmakuType.CHAT
                "GIFT" -> DanmakuType.GIFT
                "ENTER" -> DanmakuType.ENTER
                "LIKE" -> DanmakuType.LIKE
                "FOLLOW" -> DanmakuType.FOLLOW
                "FANS_CLUB" -> DanmakuType.FANS_CLUB
                else -> DanmakuType.CHAT
            }
            
            val message = DanmakuMessage(
                type = danmakuType,
                nickname = nickname,
                content = content,
                userId = userId
            )
            
            onDanmakuCallback?.invoke(message)
        }
        
        @JavascriptInterface
        fun onConnected() {
            onConnectedCallback?.invoke()
        }
        
        @JavascriptInterface
        fun onDisconnected() {
            onDisconnectedCallback?.invoke()
        }
        
        @JavascriptInterface
        fun onError(error: String) {
            onErrorCallback?.invoke(error)
        }
    }
}
