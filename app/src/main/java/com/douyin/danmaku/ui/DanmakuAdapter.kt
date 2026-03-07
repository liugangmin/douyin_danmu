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
        if (message.type == DanmakuType.LIKE) {
            return -1
        }
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
            val context = binding.root.context
            
            when (message.type) {
                DanmakuType.CHAT -> {
                    binding.tvNickname.text = "${message.nickname}："
                    binding.tvNickname.setTextColor(0xFF2196F3.toInt())
                    binding.tvContent.text = message.content
                    binding.tvContent.setTextColor(0xFFFFFFFF.toInt())
                }
                DanmakuType.GIFT -> {
                    binding.tvNickname.text = message.nickname
                    binding.tvNickname.setTextColor(0xFFFFD700.toInt())
                    binding.tvContent.text = " ${message.content}"
                    binding.tvContent.setTextColor(0xFFFFD700.toInt())
                }
                DanmakuType.ENTER -> {
                    binding.tvNickname.text = message.nickname
                    binding.tvNickname.setTextColor(0xFF00BCD4.toInt())
                    binding.tvContent.text = " ${message.content}"
                    binding.tvContent.setTextColor(0xFF00BCD4.toInt())
                }
                DanmakuType.FOLLOW -> {
                    binding.tvNickname.text = message.nickname
                    binding.tvNickname.setTextColor(0xFF4CAF50.toInt())
                    binding.tvContent.text = " ${message.content}"
                    binding.tvContent.setTextColor(0xFF4CAF50.toInt())
                }
                DanmakuType.FANS_CLUB -> {
                    binding.tvNickname.text = message.nickname
                    binding.tvNickname.setTextColor(0xFFFFC107.toInt())
                    binding.tvContent.text = " ${message.content}"
                    binding.tvContent.setTextColor(0xFFFFC107.toInt())
                }
                DanmakuType.STATS -> {
                    binding.tvNickname.text = ""
                    binding.tvContent.text = message.content
                    binding.tvContent.setTextColor(0xFF9C27B0.toInt())
                }
                DanmakuType.LIKE -> {
                    binding.tvNickname.text = ""
                    binding.tvContent.text = ""
                }
            }
        }
    }
}
