package com.douyin.danmaku

import android.os.Bundle
import android.view.View
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
    private var currentViewerCount: Long = 0
    
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
        
        binding.btnDisconnect.setOnClickListener {
            disconnect()
        }
        
        updateUI()
    }
    
    private fun initClient() {
        danmakuClient = DanmakuClient(this).apply {
            setOnDanmakuCallback { message ->
                runOnUiThread {
                    val position = adapter.addMessage(message)
                    if (position >= 0) {
                        binding.rvDanmaku.scrollToPosition(position)
                    }
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
            
            setOnRoomInfoCallback { roomInfo ->
                runOnUiThread {
                    showRoomInfo(roomInfo)
                }
            }
            
            setOnViewerCountCallback { count ->
                runOnUiThread {
                    updateViewerCount(count)
                }
            }
        }
    }
    
    private fun showRoomInfo(roomInfo: com.douyin.danmaku.model.RoomInfo) {
        binding.inputArea.visibility = View.GONE
        binding.roomInfoArea.visibility = View.VISIBLE
        
        val anchorName = roomInfo.anchorName.ifEmpty { "主播" }
        binding.tvAnchorName.text = "主播：$anchorName"
        binding.tvAnchorName.setTextColor(0xFFFE2C55.toInt())
        
        currentViewerCount = roomInfo.viewerCount
        binding.tvViewerCount.text = formatViewerCount(currentViewerCount)
    }
    
    private fun updateViewerCount(count: Long) {
        currentViewerCount = count
        binding.tvViewerCount.text = formatViewerCount(count)
    }
    
    private fun hideRoomInfo() {
        binding.inputArea.visibility = View.VISIBLE
        binding.roomInfoArea.visibility = View.GONE
        currentViewerCount = 0
    }
    
    private fun formatViewerCount(count: Long): String {
        return when {
            count >= 10000 -> String.format("%.1f万", count / 10000.0)
            count >= 1000 -> String.format("%.1f千", count / 1000.0)
            else -> "${count}人在线"
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
        hideRoomInfo()
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
        binding.btnConnect.text = if (isConnected) getString(R.string.disconnect) else getString(R.string.connect)
        
        when {
            isConnecting -> {
                binding.tvStatus.text = getString(R.string.connecting)
                binding.tvStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_connecting, 0, 0, 0)
                binding.tvStatus.setTextColor(0xFF888888.toInt())
            }
            isConnected -> {
                binding.tvStatus.text = getString(R.string.connected)
                binding.tvStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_connected, 0, 0, 0)
                binding.tvStatus.setTextColor(0xFF00FF00.toInt())
            }
            else -> {
                binding.tvStatus.text = getString(R.string.disconnected)
                binding.tvStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.status_disconnected, 0, 0, 0)
                binding.tvStatus.setTextColor(0xFF888888.toInt())
            }
        }
        
        binding.etRoomId.isEnabled = !isConnected && !isConnecting
        binding.btnConnect.isEnabled = !isConnecting
    }
    
    override fun onDestroy() {
        super.onDestroy()
        danmakuClient?.disconnect()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
