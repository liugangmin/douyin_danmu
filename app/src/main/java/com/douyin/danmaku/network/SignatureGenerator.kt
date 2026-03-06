package com.douyin.danmaku.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 签名生成器
 * 通过WebView执行JavaScript生成签名
 */
class SignatureGenerator(private val context: Context) {
    
    private var webView: WebView? = null
    private var isScriptLoaded = false
    private val initLatch = CountDownLatch(1)
    
    @SuppressLint("SetJavaScriptEnabled")
    fun init() {
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isScriptLoaded = true
                    initLatch.countDown()
                }
            }
            
            // 加载签名脚本
            val script = context.assets.open("signature.js").bufferedReader().use { it.readText() }
            loadDataWithBaseURL("https://live.douyin.com", "<html><body><script>$script</script></body></html>", "text/html", "UTF-8", null)
        }
    }
    
    fun waitForInit(timeout: Long = 5000): Boolean {
        return initLatch.await(timeout, TimeUnit.MILLISECONDS)
    }
    
    fun generateLocalSignature(roomId: String, userId: String, cursor: String): String {
        // 本地生成签名
        val timestamp = System.currentTimeMillis()
        val content = "${roomId}_${userId}_${cursor}_${timestamp}"
        return md5(content)
    }
    
    private fun md5(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    fun destroy() {
        webView?.destroy()
        webView = null
    }
}
