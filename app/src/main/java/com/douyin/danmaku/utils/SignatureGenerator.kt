package com.douyin.danmaku.utils

import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

class SignatureGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "SignatureGenerator"
    }
    
    private var webView: WebView? = null
    private var isInitialized = false
    private val initLatch = CountDownLatch(1)
    private val signatureResult = AtomicReference<String?>(null)
    
    suspend fun init(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "开始初始化签名生成器")
                
                // 在主线程创建WebView
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        webView = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    Log.d(TAG, "JS加载完成")
                                    isInitialized = true
                                    initLatch.countDown()
                                    continuation.resume(true) {}
                                }
                                
                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    Log.e(TAG, "WebView错误: $description")
                                    initLatch.countDown()
                                    if (!continuation.isCompleted) {
                                        continuation.resumeWithException(Exception("WebView初始化失败: $description"))
                                    }
                                }
                            }
                        }
                        
                        // 加载sign.js
                        val jsContent = context.assets.open("sign.js").bufferedReader().use { it.readText() }
                        Log.d(TAG, "sign.js内容长度: ${jsContent.length}")
                        
                        // 注入必要的全局变量
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head><meta charset="utf-8"></head>
                            <body>
                            <script>
                            $jsContent
                            </script>
                            </body>
                            </html>
                        """.trimIndent()
                        
                        webView?.loadDataWithBaseURL(
                            "https://www.douyin.com",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                        
                        // 超时处理
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!continuation.isCompleted) {
                                if (isInitialized) {
                                    continuation.resume(true) {}
                                } else {
                                    continuation.resumeWithException(Exception("初始化超时"))
                                }
                            }
                        }, 5000)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "初始化失败", e)
                        continuation.resumeWithException(e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化异常", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * 生成签名
     * @param wss WebSocket URL
     * @return 签名字符串
     */
    suspend fun generateSignature(wss: String): String {
        return suspendCancellableCoroutine { continuation ->
            if (!isInitialized) {
                continuation.resumeWithException(Exception("签名生成器未初始化"))
                return@suspendCancellableCoroutine
            }
            
            try {
                // 计算参数MD5
                val md5Param = calculateMd5Param(wss)
                Log.d(TAG, "MD5参数: $md5Param")
                
                // 在主线程执行JS
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        val jsCode = "javascript:try { window.signatureResult = get_sign('$md5Param'); } catch(e) { window.signatureResult = 'error:' + e.message; }"
                        
                        webView?.evaluateJavascript(jsCode) { result ->
                            Log.d(TAG, "JS执行结果: $result")
                            
                            // 获取签名结果
                            webView?.evaluateJavascript("javascript:window.signatureResult") { sigResult ->
                                Log.d(TAG, "签名结果: $sigResult")
                                
                                // 去掉引号
                                var signature = sigResult?.trim('"') ?: ""
                                
                                if (signature.startsWith("error:")) {
                                    continuation.resumeWithException(Exception(signature))
                                } else if (signature.isNotEmpty() && signature != "null") {
                                    continuation.resume(signature) {}
                                } else {
                                    continuation.resumeWithException(Exception("签名生成失败"))
                                }
                            }
                        }
                        
                        // 超时处理
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!continuation.isCompleted) {
                                continuation.resumeWithException(Exception("签名生成超时"))
                            }
                        }, 3000)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "执行JS失败", e)
                        continuation.resumeWithException(e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "生成签名失败", e)
                continuation.resumeWithException(e)
            }
        }
    }
    
    /**
     * 计算MD5参数
     */
    private fun calculateMd5Param(wss: String): String {
        val params = listOf(
            "live_id", "aid", "version_code", "webcast_sdk_version",
            "room_id", "sub_room_id", "sub_channel_id", "did_rule",
            "user_unique_id", "device_platform", "device_type", "ac",
            "identity"
        )
        
        // 解析URL参数
        val queryStart = wss.indexOf("?")
        if (queryStart < 0) return ""
        
        val query = wss.substring(queryStart + 1)
        val paramMap = mutableMapOf<String, String>()
        
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                paramMap[parts[0]] = java.net.URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        
        // 构建参数字符串
        val tplParams = params.map { param ->
            "$it=${paramMap[param] ?: ""}"
        }
        val param = tplParams.joinToString(",")
        
        // 计算MD5
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(param.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun destroy() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
            isInitialized = false
        }
    }
}
