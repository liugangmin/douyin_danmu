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
import com.douyin.danmaku.ui.DanmakuAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DanmakuAdapter
    private lateinit var roomInfoFetcher: RoomInfoFetcher
    private var webSocketClient: DouyinWebSocketClient? = null
    
    private var currentRoomInfo: RoomInfo? = null
    private var isConnected = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initViews()
        initData()
    }
    
    private fun initViews() {
        adapter = DanmakuAdapter()
        binding.rvDanmaku.layoutManager = LinearLayoutManager(this)
        binding.rvDanmaku.adapter = adapter
        
        binding.btnConnect.setOnClickListener { connect() }
        binding.btnDisconnect.setOnClickListener { disconnect() }
    }
    
    private fun initData() {
        roomInfoFetcher = RoomInfoFetcher()
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
        
        updateConnectionStatus(false, true)
        
        lifecycleScope.launch {
            try {
                val roomInfo = roomInfoFetcher.fetchRoomInfo(input)
                if (roomInfo == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "无法获取直播间信息", Toast.LENGTH_SHORT).show()
                        updateConnectionStatus(false, false)
                    }
                    return@launch
                }
                
                currentRoomInfo = roomInfo
                
                withContext(Dispatchers.Main) {
                    showRoomInfo(roomInfo)
                    
                    val userUniqueId = roomInfoFetcher.generateUserUniqueId()
                    val signature = generateSignature(roomInfo.roomId, userUniqueId)
                    
                    webSocketClient = DouyinWebSocketClient(
                        onDanmaku = { message ->
                            runOnUiThread { adapter.addMessage(message) }
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
                                Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
                                isConnected = false
                                updateConnectionStatus(false, false)
                            }
                        }
                    )
                    
                    webSocketClient?.connect(roomInfo.roomId, userUniqueId, signature)
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
        webSocketClient?.disconnect()
        webSocketClient = null
        isConnected = false
        updateConnectionStatus(false, false)
        adapter.clear()
        binding.roomInfoArea.visibility = View.GONE
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateConnectionStatus(connected: Boolean, connecting: Boolean) {
        binding.btnConnect.isEnabled = !connected && !connecting
        binding.btnDisconnect.isEnabled = connected || connecting
        
        binding.tvStatus.text = when {
            connecting -> getString(R.string.connecting)
            connected -> getString(R.string.connected)
            else -> getString(R.string.disconnected)
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
        webSocketClient?.disconnect()
    }
}
