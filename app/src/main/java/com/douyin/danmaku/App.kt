package com.douyin.danmaku

import android.app.Application
import android.webkit.WebView

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 预先初始化WebView
        try {
            WebView.setWebContentsDebuggingEnabled(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
