package com.douyin.danmaku.network

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.FrameLayout
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType

class WebViewDanmakuFetcher(
    private val activity: android.app.Activity,
    private val container: FrameLayout
) {
    
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var onDanmakuCallback: ((DanmakuMessage) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private var onDisconnectedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun init(): Boolean {
        return try {
            // 在主线程同步初始化
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
            
            webView = WebView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                    blockNetworkImage = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                addJavascriptInterface(JsBridge(), "AndroidBridge")
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (url != null && url.contains("douyin.com")) {
                            injectScript()
                            onConnectedCallback?.invoke()
                        }
                    }
                    
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.proceed()
                    }
                }
            }
            
            container.addView(webView)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun connect(roomId: String) {
        mainHandler.post {
            try {
                val url = "https://live.douyin.com/$roomId"
                webView?.loadUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
                onErrorCallback?.invoke("连接失败: ${e.message}")
            }
        }
    }
    
    private fun injectScript() {
        val script = """
            (function() {
                var OriginalWebSocket = window.WebSocket;
                window.WebSocket = function(url, protocols) {
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
                    return ws;
                };
                window.WebSocket.CONNECTING = OriginalWebSocket.CONNECTING;
                window.WebSocket.OPEN = OriginalWebSocket.OPEN;
                window.WebSocket.CLOSING = OriginalWebSocket.CLOSING;
                window.WebSocket.CLOSED = OriginalWebSocket.CLOSED;
                
                function parseMessage(str) {
                    if (str.indexOf('ChatMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        var content = matchPattern(str, /content":"([^"]+)"/);
                        if (nick && content && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('CHAT', nick, content);
                        }
                    }
                    else if (str.indexOf('MemberMessage') > -1) {
                        var nick = matchPattern(str, /nickname":"([^"]+)"/);
                        if (nick && window.AndroidBridge) {
                            AndroidBridge.onDanmaku('ENTER', nick, '进入了直播间');
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
            try {
                webView?.stopLoading()
                container.removeView(webView)
                webView?.destroy()
                webView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                "ENTER" -> DanmakuType.ENTER
                else -> DanmakuType.CHAT
            }
            onDanmakuCallback?.invoke(DanmakuMessage(
                type = danmakuType,
                nickname = nickname,
                content = content
            ))
        }
    }
}
