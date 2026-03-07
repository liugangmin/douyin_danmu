package com.douyin.danmaku.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.douyin.danmaku.databinding.ItemDanmakuBinding
import com.douyin.danmaku.model.DanmakuMessage
import com.douyin.danmaku.model.DanmakuType
import com.douyin.danmaku.model.EmojiInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class DanmakuAdapter : RecyclerView.Adapter<DanmakuAdapter.DanmakuViewHolder>() {
    
    private val items = mutableListOf<DanmakuMessage>()
    private val maxSize = 500
    
    private val client = OkHttpClient.Builder().build()
    private val emojiCache = ConcurrentHashMap<String, Bitmap>()
    private val failedEmojis = ConcurrentHashMap<String, Boolean>()
    
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
    
    inner class DanmakuViewHolder(private val binding: ItemDanmakuBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: DanmakuMessage) {
            val context = binding.root.context
            
            when (message.type) {
                DanmakuType.CHAT -> {
                    binding.tvNickname.text = "${message.nickname}："
                    binding.tvNickname.setTextColor(0xFF2196F3.toInt())
                    
                    val content = message.content
                    
                    if (message.emojis.isNotEmpty()) {
                        parseAndSetEmojiText(context, content, message.emojis, binding.tvContent.textSize)
                    } else if (containsEmojiPattern(content)) {
                        parseAndLoadEmojiFromPattern(context, content, binding.tvContent.textSize)
                    } else {
                        binding.tvContent.text = content
                    }
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
        
        private fun containsEmojiPattern(text: String): Boolean {
            return text.contains("[") && text.contains("]")
        }
        
        private fun parseAndLoadEmojiFromPattern(context: android.content.Context, text: String, textSize: Float) {
            val spannable = SpannableString(text)
            val emojiSize = (textSize * 1.3).toInt()
            val pattern = Regex("\\[([^\\[\\]]+)\\]")
            val matches = pattern.findAll(text).toList()
            
            if (matches.isEmpty()) {
                binding.tvContent.text = text
                return
            }
            
            binding.tvContent.text = text
            
            CoroutineScope(Dispatchers.Main).launch {
                var hasUpdate = false
                for (match in matches) {
                    val emojiCode = match.groupValues[1]
                    
                    if (failedEmojis.containsKey(emojiCode)) {
                        continue
                    }
                    
                    val bitmap = loadEmojiWithFallback(context, emojiCode)
                    if (bitmap != null) {
                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, emojiSize, emojiSize, true)
                        val span = ImageSpan(context, scaledBitmap, ImageSpan.ALIGN_BASELINE)
                        spannable.setSpan(span, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        hasUpdate = true
                    } else {
                        failedEmojis[emojiCode] = true
                    }
                }
                if (hasUpdate) {
                    binding.tvContent.text = spannable
                }
            }
        }
        
        private fun parseAndSetEmojiText(
            context: android.content.Context, 
            text: String, 
            emojis: List<EmojiInfo>,
            textSize: Float
        ) {
            val spannable = SpannableString(text)
            val emojiSize = (textSize * 1.3).toInt()
            
            binding.tvContent.text = text
            
            CoroutineScope(Dispatchers.Main).launch {
                for (emoji in emojis) {
                    val emojiText = emoji.text
                    val startIndex = text.indexOf(emojiText)
                    if (startIndex >= 0 && emoji.url.isNotEmpty()) {
                        val bitmap = loadEmojiBitmap(context, emoji.url)
                        if (bitmap != null) {
                            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, emojiSize, emojiSize, true)
                            val span = ImageSpan(context, scaledBitmap, ImageSpan.ALIGN_BASELINE)
                            spannable.setSpan(span, startIndex, startIndex + emojiText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                binding.tvContent.text = spannable
            }
        }
        
        private suspend fun loadEmojiWithFallback(context: android.content.Context, code: String): Bitmap? {
            val urls = getEmojiUrls(code)
            
            for (url in urls) {
                val bitmap = loadEmojiBitmap(context, url)
                if (bitmap != null) {
                    return bitmap
                }
            }
            return null
        }
        
        private fun getEmojiUrls(code: String): List<String> {
            val encoded = URLEncoder.encode(code, "UTF-8")
            return listOf(
                "https://p3-webcast.douyinpic.com/img/webcast/emoji/$encoded.png",
                "https://p3-webcast.douyinpic.com/img/webcast/emoji_normal/$encoded.png",
                "https://p3-webcast.douyinpic.com/img/webcast/emoji_new/$encoded.png",
                "https://lf3-static.bytednsdoc.com/obj/eden-cn/hjeh7pldnulm/$encoded.png",
                "https://p9-webcast.douyinpic.com/img/webcast/emoji/$encoded.png",
                "https://p6-webcast.douyinpic.com/img/webcast/emoji/$encoded.png"
            )
        }
        
        private suspend fun loadEmojiBitmap(context: android.content.Context, url: String): Bitmap? {
            emojiCache[url]?.let { return it }
            
            return withContext(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "emoji_${url.hashCode()}")
                    if (cacheFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                        if (bitmap != null) {
                            emojiCache[url] = bitmap
                        }
                        return@withContext bitmap
                    }
                    
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes() ?: return@withContext null
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            emojiCache[url] = bitmap
                            try {
                                cacheFile.writeBytes(bytes)
                            } catch (e: Exception) {
                            }
                        }
                        bitmap
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
