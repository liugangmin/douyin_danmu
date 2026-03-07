package com.douyin.danmaku

import android.os.Bundle
import android.view.View
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
        // 设置堆栈从底部开始，新消息会在底部
        layoutManager.stackFromEnd = true
        binding.rvDanmaku.layoutManager = layoutManager
        binding.rvDanmaku.adapter = adapter
        
        binding.btnConnect.setOnClickListener { connect() }
        binding.btnDisconnect.setOnClickListener { disconnect() }
    }
    
    private fun initClient() {
        danmakuClient = DanmakuClient(this).apply {
            setOnDanmakuCallback { message ->
                runOnUiThread {
                    val position = adapter.addMessage(message)
                    // 自动滚动到最新消息
                    binding.rvDanmaku.scrollToPosition(position)
                }
            }
            
            setOnConnectedCallback {
                runOnUiThread {
                    isConnected = true
                    updateConnectionStatus(true, false)
                    Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
                }
            }
            
            setOnDisconnectedCallback {
                runOnUiThread {
                    isConnected = false
                    updateConnectionStatus(false, false)
                }
            }
            
            setOnErrorCallback { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "错误: $error", Toast.LENGTH_SHORT).show()
                    isConnected = false
                    updateConnectionStatus(false, false)
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
        
        if (isConnected) {
            Toast.makeText(this, "已经连接中，请先断开", Toast.LENGTH_SHORT).show()
            return
        }
        
        updateConnectionStatus(false, true)
        binding.roomInfoArea.visibility = View.VISIBLE
        binding.tvRoomTitle.text = "正在连接..."
        binding.tvViewerCount.text = "房间号: $input"
        
        val roomId = parseRoomId(input)
        lifecycleScope.launch {
            danmakuClient?.connect(roomId)
        }
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
    
    private fun disconnect() {
        danmakuClient?.disconnect()
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
    
    override fun onDestroy() {
        super.onDestroy()
        danmakuClient?.disconnect()
    }
}
