package com.douyin.danmaku

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.douyin.danmaku.databinding.ActivityMainBinding
import com.douyin.danmaku.network.DanmakuClient
import com.douyin.danmaku.ui.DanmakuAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DanmakuAdapter
    private var danmakuClient: DanmakuClient? = null
    
    private var isConnected = false
    private var isConnecting = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        initClient()
    }
    
    private fun initViews() {
        adapter = DanmakuAdapter()
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.rvDanmaku.layoutManager = layoutManager
        binding.rvDanmaku.adapter = adapter
        
        binding.btnConnect.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                connect()
            }
        }
        
        updateUI()
    }
    
    private fun initClient() {
        danmakuClient = DanmakuClient(this).apply {
            setOnDanmakuCallback { message ->
                runOnUiThread {
                    val position = adapter.addMessage(message)
                    binding.rvDanmaku.scrollToPosition(position)
                }
            }
            
            setOnConnectedCallback {
                runOnUiThread {
                    isConnected = true
                    isConnecting = false
                    updateUI()
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
                }
            }
            
            setOnDisconnectedCallback {
                runOnUiThread {
                    isConnected = false
                    isConnecting = false
                    updateUI()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            
            setOnErrorCallback { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
                    isConnected = false
                    isConnecting = false
                    updateUI()
                }
            }
        }
    }
    
    private fun connect() {
        val input = binding.etRoomId.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入直播间链接或房间号", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isConnected || isConnecting) {
            return
        }
        
        isConnecting = true
        updateUI()
        
        val roomId = parseRoomId(input)
        lifecycleScope.launch {
            danmakuClient?.connect(roomId)
        }
    }
    
    private fun disconnect() {
        danmakuClient?.disconnect()
        isConnected = false
        isConnecting = false
        adapter.clear()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateUI()
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show()
    }
    
    private fun parseRoomId(input: String): String {
        val patterns = listOf(
            Regex("live\\.douyin\\.com/(\\d+)"),
            Regex("live\\.douyin\\.com/([a-zA-Z0-9_-]+)"),
            Regex("v\\.douyin\\.com/([a-zA-Z0-9]+)"),
            Regex("douyin\\.com/live/([a-zA-Z0-9_-]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return input
    }
    
    private fun updateUI() {
        // 更新按钮文字
        binding.btnConnect.text = if (isConnected) {
            getString(R.string.disconnect)
        } else {
            getString(R.string.connect)
        }
        
        // 更新状态文字
        binding.tvStatus.text = when {
            isConnecting -> getString(R.string.connecting)
            isConnected -> getString(R.string.connected)
            else -> getString(R.string.disconnected)
        }
        
        // 输入框在连接时禁用
        binding.etRoomId.isEnabled = !isConnected && !isConnecting
    }
    
    override fun onDestroy() {
        super.onDestroy()
        danmakuClient?.disconnect()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
