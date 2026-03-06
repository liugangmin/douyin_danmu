package com.douyin.danmaku.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.*
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType

class WebViewDanmakuFetcher(private val context: Context) {
    
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
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
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    blockNetworkImage = true
                }
                
                addJavascriptInterface(JsBridge(), "AndroidBridge")
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        injectScript()
                        onConnectedCallback?.invoke()
                    }
                    
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        onErrorCallback?.invoke("页面加载错误")
                    }
                }
            }
        }
    }
    
    fun connect(roomId: String) {
        mainHandler.post {
            val url = "https://live.douyin.com/$roomId"
            webView?.loadUrl(url)
        }
    }
    
    private fun injectScript() {
        val script = """
            (function() {
                console.log('Script injected');
                
                var OriginalWebSocket = window.WebSocket;
                
                window.WebSocket = function(url, protocols) {
                    console.log('WS URL: ' + url);
                    var ws = new OriginalWebSocket(url, protocols);
                    
                    ws.addEventListener('message', function(event) {
                        try {
                            if (event.data instanceof ArrayBuffer) {
                                var bytes = new Uint8Array(event.data);
                                var str = String.fromCharCode.apply(null, bytes);
                                parseMessage(str);
                            }
                        } catch(e) {}
                    });
                    
                    ws.addEventListener('open', function() {
                        if (window.AndroidBridge) {
                            AndroidBridge.onConnected();
                        }
                    });
                    
                    ws.addEventListener('close', function() {
                        if (window.AndroidBridge) {
                            AndroidBridge.onDisconnected();
                        }
                    });
                    
                    return ws;
                };
                
                window.WebSocket.CONNECTING = OriginalWebSocket.CONNECTING;
                window.WebSocket.OPEN = OriginalWebSocket.OPEN;
                window.WebSocket.CLOSING = OriginalWebSocket.CLOSING;
                window.WebSocket.CLOSED = OriginalWebSocket.CLOSED;
                
                function parseMessage(str) {
                    // Chat
                    if (str.indexOf('ChatMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        var content = matchPattern(str, /content":"([^"]+)"/);
                        if (nick && content && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('CHAT', nick, content);
                        }
                    }
                    // Member
                    else if (str.indexOf('MemberMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        if (nick && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('ENTER', nick, '进入了直播间');
                        }
                    }
                    // Gift
                    else if (str.indexOf('GiftMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        if (nick && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('GIFT', nick, '送出了礼物');
                        }
                    }
                    // Like
                    else if (str.indexOf('LikeMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        if (nick && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('LIKE', nick, '点了赞');
                        }
                    }
                }
                
                function matchPattern(str, pattern) {
                    var match = str.match(pattern);
                    return match ? match[1] : null;
                }
            })();
        """
        
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
        fun onDanmaku(type: String, nickname: String, content: String) {
            val danmakuType = when (type) {
                "CHAT" -> DanmakuType.CHAT
                "GIFT" -> DanmakuType.GIFT
                "ENTER" -> DanmakuType.ENTER
                "LIKE" -> DanmakuType.LIKE
                else -> DanmakuType.CHAT
            }
            
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = danmakuType,
                nickname = nickname,
                content = content
            ))
        }
        
        @JavascriptInterface
        fun onConnected() {
            onConnectedCallback?.invoke()
        }
        
        @JavascriptInterface
        fun onDisconnected() {
            onDisconnectedCallback?.invoke()
        }
    }
}
