package com.douyin.danmaku.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SignatureGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "SignatureGenerator"
        private const val TIMEOUT_SECONDS = 10L
    }
    
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initLatch = CountDownLatch(1)
    private var isInitialized = false
    
    fun init(): Boolean {
        Log.d(TAG, "开始初始化签名生成器")
        
        val initResult = AtomicReference<Boolean>(false)
        
        mainHandler.post {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(TAG, "JS加载完成")
                            isInitialized = true
                            initResult.set(true)
                            initLatch.countDown()
                        }
                        
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            Log.e(TAG, "WebView错误: $description")
                            initLatch.countDown()
                        }
                    }
                }
                
                // 加载sign.js
                val jsContent = context.assets.open("sign.js").bufferedReader().use { it.readText() }
                Log.d(TAG, "sign.js内容长度: ${jsContent.length}")
                
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
                
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
                initLatch.countDown()
            }
        }
        
        // 等待初始化完成
        val success = initLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) && initResult.get()
        Log.d(TAG, "初始化结果: $success")
        return success
    }
    
    /**
     * 生成签名
     */
    fun generateSignature(wss: String): String? {
        if (!isInitialized) {
            Log.e(TAG, "签名生成器未初始化")
            return null
        }
        
        val md5Param = calculateMd5Param(wss)
        Log.d(TAG, "MD5参数: $md5Param")
        
        val resultLatch = CountDownLatch(1)
        val signatureResult = AtomicReference<String?>(null)
        
        mainHandler.post {
            try {
                val jsCode = "javascript:try { window.signatureResult = get_sign('$md5Param'); } catch(e) { window.signatureResult = 'error:' + e.message; }"
                
                webView?.evaluateJavascript(jsCode) {
                    webView?.evaluateJavascript("javascript:window.signatureResult") { sigResult ->
                        Log.d(TAG, "签名结果: $sigResult")
                        
                        val signature = sigResult?.trim('"') ?: ""
                        if (!signature.startsWith("error:") && signature != "null" && signature.isNotEmpty()) {
                            signatureResult.set(signature)
                        }
                        resultLatch.countDown()
                    }
                }
                
                // 超时处理
                mainHandler.postDelayed({
                    resultLatch.countDown()
                }, 5000)
                
            } catch (e: Exception) {
                Log.e(TAG, "执行JS失败", e)
                resultLatch.countDown()
            }
        }
        
        resultLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return signatureResult.get()
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
        
        val tplParams = params.map { param ->
            "$param=${paramMap[param] ?: ""}"
        }
        val param = tplParams.joinToString(",")
        
        val md5 = MessageDigest.getInstance("MD5")
        val digest = md5.digest(param.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
            isInitialized = false
        }
    }
}
