package com.douyin.danmaku

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.douyin.danmaku.databinding.ActivityMainBinding
import com.douyin.danmaku.model.RoomInfo
import com.douyin.danmaku.network.DouyinWebSocketClient
import com.douyin.danmaku.network.RoomInfoFetcher
import com.douyin.danmaku.network.WebViewDanmakuFetcher
import com.douyin.danmaku.ui.DanmakuAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DanmakuAdapter
    private lateinit var roomInfoFetcher: RoomInfoFetcher
    private lateinit var webSocketClient: DouyinWebSocketClient
    private lateinit var webViewFetcher: WebViewDanmakuFetcher
    
    private var currentRoomInfo: RoomInfo? = null
    private var isConnected = false
    private var useWebView = false // 使用WebView模式
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        initData()
    }
    
    private fun initViews() {
        // 设置RecyclerView
        adapter = DanmakuAdapter()
        binding.rvDanmaku.layoutManager = LinearLayoutManager(this)
        binding.rvDanmaku.adapter = adapter
        
        // 设置按钮点击事件
        binding.btnConnect.setOnClickListener {
            connect()
        }
        
        binding.btnDisconnect.setOnClickListener {
            disconnect()
        }
    }
    
    private fun initData() {
        roomInfoFetcher = RoomInfoFetcher()
        
        // 初始化WebSocket客户端
        webSocketClient = DouyinWebSocketClient(
            onDanmaku = { message ->
                runOnUiThread {
                    adapter.addMessage(message)
                }
            },
            onConnected = {
                runOnUiThread {
                    isConnected = true
                    updateConnectionStatus(true, false)
                    Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
                }
            },
            onDisconnected = {
                runOnUiThread {
                    isConnected = false
                    updateConnectionStatus(false, false)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "连接错误: $error", Toast.LENGTH_SHORT).show()
                    isConnected = false
                    updateConnectionStatus(false, false)
                }
            }
        )
        
        // 初始化WebView获取器
        webViewFetcher = WebViewDanmakuFetcher(this)
        webViewFetcher.init()
        webViewFetcher.setOnDanmakuCallback { message ->
            runOnUiThread {
                adapter.addMessage(message)
            }
        }
        webViewFetcher.setOnConnectedCallback {
            runOnUiThread {
                isConnected = true
                updateConnectionStatus(true, false)
                Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }
        }
        webViewFetcher.setOnDisconnectedCallback {
            runOnUiThread {
                isConnected = false
                updateConnectionStatus(false, false)
            }
        }
        webViewFetcher.setOnErrorCallback { error ->
            runOnUiThread {
                Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun connect() {
        val input = binding.etRoomId.text.toString().trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入直播间链接或房间号", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isConnected) {
            Toast.makeText(this, "已经连接中，请先断开", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示连接中状态
        updateConnectionStatus(false, true)
        
        lifecycleScope.launch {
            try {
                // 获取直播间信息
                val roomInfo = roomInfoFetcher.fetchRoomInfo(input)
                if (roomInfo == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "无法获取直播间信息，请检查输入是否正确", Toast.LENGTH_SHORT).show()
                        updateConnectionStatus(false, false)
                    }
                    return@launch
                }
                
                currentRoomInfo = roomInfo
                
                withContext(Dispatchers.Main) {
                    // 显示直播间信息
                    showRoomInfo(roomInfo)
                    
                    // 生成用户ID
                    val userUniqueId = roomInfoFetcher.generateUserUniqueId()
                    
                    // 使用WebSocket直连模式
                    val signature = generateSignature(roomInfo.roomId, userUniqueId)
                    webSocketClient.connect(roomInfo.roomId, userUniqueId, signature)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateConnectionStatus(false, false)
                }
            }
        }
    }
    
    private fun generateSignature(roomId: String, userId: String): String {
        val timestamp = System.currentTimeMillis()
        val content = "${roomId}_${userId}__$timestamp"
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(content.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun disconnect() {
        webSocketClient.disconnect()
        webViewFetcher.disconnect()
        isConnected = false
        updateConnectionStatus(false, false)
        
        // 清空弹幕列表
        adapter.clear()
        
        // 隐藏直播间信息
        binding.roomInfoArea.visibility = View.GONE
        
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateConnectionStatus(connected: Boolean, connecting: Boolean) {
        binding.btnConnect.isEnabled = !connected && !connecting
        binding.btnDisconnect.isEnabled = connected || connecting
        
        when {
            connecting -> {
                binding.tvStatus.text = getString(R.string.connecting)
            }
            connected -> {
                binding.tvStatus.text = getString(R.string.connected)
            }
            else -> {
                binding.tvStatus.text = getString(R.string.disconnected)
            }
        }
    }
    
    private fun showRoomInfo(roomInfo: RoomInfo) {
        binding.roomInfoArea.visibility = View.VISIBLE
        binding.tvRoomTitle.text = if (roomInfo.title.isNotEmpty()) {
            roomInfo.title
        } else {
            "${roomInfo.anchorName}的直播间"
        }
        binding.tvViewerCount.text = "房间号: ${roomInfo.webRoomId}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.disconnect()
        webViewFetcher.destroy()
    }
}
