package com.douyin.danmaku.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.douyin.danmaku.databinding.ItemDanmakuBinding
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType

/**
 * 弹幕列表适配器
 */
class DanmakuAdapter : RecyclerView.Adapter<DanmakuAdapter.DanmakuViewHolder>() {
    
    private val items = mutableListOf<DanmakuMessage>()
    private val maxSize = 500
    
    fun addMessage(message: DanmakuMessage): Int {
        items.add(message)
        if (items.size > maxSize) {
            items.removeAt(0)
            notifyItemRemoved(0)
            notifyItemRangeChanged(0, items.size)
        }
        val position = items.size - 1
        notifyItemInserted(position)
        return position
    }
    
    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DanmakuViewHolder {
        val binding = ItemDanmakuBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DanmakuViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DanmakuViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount(): Int = items.size
    
    class DanmakuViewHolder(private val binding: ItemDanmakuBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: DanmakuMessage) {
            binding.tvNickname.text = message.nickname
            binding.tvContent.text = message.content
            
            // 根据消息类型设置不同颜色（适合黑色背景）
            val color = when (message.type) {
                DanmakuType.CHAT -> 0xFFFFFFFF.toInt() // 白色
                DanmakuType.GIFT -> 0xFFFFD700.toInt() // 金色
                DanmakuType.ENTER -> 0xFF00BCD4.toInt() // 青色
                DanmakuType.LIKE -> 0xFFFF69B4.toInt() // 粉色
                DanmakuType.FOLLOW -> 0xFF4CAF50.toInt() // 绿色
                DanmakuType.FANS_CLUB -> 0xFFFFC107.toInt() // 黄色
                DanmakuType.STATS -> 0xFF9C27B0.toInt() // 紫色
            }
            
            binding.tvNickname.setTextColor(color)
        }
    }
}
