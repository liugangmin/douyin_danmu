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
    
    private val emojiMap = mapOf(
        "微笑" to "😊",
        "撇嘴" to "😒",
        "色" to "😍",
        "发呆" to "😳",
        "得意" to "😏",
        "流泪" to "😢",
        "害羞" to "😳",
        "闭嘴" to "😶",
        "睡" to "😴",
        "大哭" to "😭",
        "尴尬" to "😅",
        "发怒" to "😠",
        "调皮" to "😜",
        "呲牙" to "😁",
        "惊讶" to "😲",
        "难过" to "😔",
        "酷" to "😎",
        "冷汗" to "😰",
        "抓狂" to "😫",
        "吐" to "🤮",
        "偷笑" to "🤭",
        "可爱" to "🥰",
        "白眼" to "🙄",
        "傲慢" to "😤",
        "饥饿" to "🤤",
        "困" to "😪",
        "惊恐" to "😱",
        "流汗" to "💦",
        "憨笑" to "😄",
        "大兵" to "💂",
        "奋斗" to "💪",
        "咒骂" to "🤬",
        "疑问" to "❓",
        "嘘" to "🤫",
        "晕" to "😵",
        "折磨" to "😩",
        "衰" to "😞",
        "骷髅" to "💀",
        "敲打" to "🔨",
        "再见" to "👋",
        "擦汗" to "😅",
        "抠鼻" to "🤏",
        "鼓掌" to "👏",
        "糗大了" to "🙈",
        "坏笑" to "🤭",
        "左哼哼" to "😤",
        "右哼哼" to "😤",
        "哈欠" to "🥱",
        "鄙视" to "😒",
        "委屈" to "🥺",
        "快哭了" to "😢",
        "阴险" to "😈",
        "亲亲" to "😘",
        "吓" to "😨",
        "可怜" to "🥺",
        "菜刀" to "🔪",
        "西瓜" to "🍉",
        "啤酒" to "🍺",
        "篮球" to "🏀",
        "乒乓" to "🏓",
        "咖啡" to "☕",
        "饭" to "🍚",
        "猪头" to "🐷",
        "玫瑰" to "🌹",
        "凋谢" to "🥀",
        "示爱" to "❤️",
        "爱心" to "❤️",
        "心碎" to "💔",
        "蛋糕" to "🎂",
        "闪电" to "⚡",
        "炸弹" to "💣",
        "刀" to "🔪",
        "足球" to "⚽",
        "瓢虫" to "🐞",
        "便便" to "💩",
        "月亮" to "🌙",
        "太阳" to "☀️",
        "礼物" to "🎁",
        "拥抱" to "🤗",
        "强" to "👍",
        "弱" to "👎",
        "握手" to "🤝",
        "胜利" to "✌️",
        "抱拳" to "👊",
        "勾引" to "👉",
        "拳头" to "👊",
        "差劲" to "👎",
        "爱你" to "🤟",
        "NO" to "🙅",
        "OK" to "👌",
        "爱情" to "❤️",
        "飞吻" to "😘",
        "跳跳" to "蹦蹦跳跳",
        "发抖" to "🫨",
        "怄火" to "🔥",
        "转圈" to "🔄",
        "磕头" to "🙇",
        "回头" to "🔙",
        "跳绳" to "🪢",
        "挥手" to "👋",
        "激动" to "🤩",
        "街舞" to "🕺",
        "献吻" to "💋",
        "左太极" to "☯️",
        "右太极" to "☯️",
        "双喜" to "囍",
        "鞭炮" to "🧨",
        "灯笼" to "🏮",
        "发财" to "💰",
        "K歌" to "🎤",
        "购物" to "🛒",
        "邮件" to "📧",
        "帅" to "😎",
        "喝彩" to "🎉",
        "祈祷" to "🙏",
        "爆筋" to "💪",
        "棒棒糖" to "🍭",
        "喝奶" to "🥛",
        "下面" to "🍜",
        "香蕉" to "🍌",
        "飞机" to "✈️",
        "开车" to "🚗",
        "高铁" to "🚄",
        "动车" to "🚄",
        "火车" to "🚂",
        "公交" to "🚌",
        "单车" to "🚲",
        "摩托" to "🏍️",
        "轮船" to "🚢",
        "火箭" to "🚀",
        "沙发" to "🛋️",
        "药" to "💊",
        "手机" to "📱",
        "电话" to "📞",
        "电脑" to "💻",
        "电视" to "📺",
        "书" to "📖",
        "游戏" to "🎮",
        "音乐" to "🎵",
        "电影" to "🎬",
        "相机" to "📷",
        "时钟" to "🕐",
        "雨伞" to "☂️",
        "气球" to "🎈",
        "戒指" to "💍",
        "口红" to "💄",
        "钻石" to "💎",
        "皇冠" to "👑",
        "奖杯" to "🏆",
        "奖牌" to "🏅",
        "红包" to "🧧",
        "福" to "🧧",
        "烟花" to "🎆",
        "打call" to "📞",
        "崇拜" to "🙏",
        "比心" to "❤️",
        "点赞" to "👍",
        "笑哭" to "😂",
        "摊手" to "🤷",
        "捂脸" to "🤦",
        "加油" to "💪",
        "汗" to "😅",
        "天啊" to "😱",
        "Emm" to "🤔",
        "社会社会" to "😎",
        "旺柴" to "🐕",
        "好的" to "👌",
        "哇" to "🤩",
        "翻白眼" to "🙄",
        "666" to "👍",
        "让我看看" to "👀",
        "叹气" to "😔",
        "苦涩" to "😫",
        "裂开" to "💔",
        "嘴唇" to "👄",
        "喜欢" to "❤️",
        "爱心" to "❤️",
        "心" to "❤️",
        "玫瑰" to "🌹",
        "花" to "🌸",
        "太阳" to "☀️",
        "月亮" to "🌙",
        "星星" to "⭐",
        "彩虹" to "🌈",
        "云" to "☁️",
        "雨" to "🌧️",
        "雪" to "❄️",
        "火" to "🔥",
        "水" to "💧",
        "风" to "💨",
        "山" to "⛰️",
        "海" to "🌊"
    )
    
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
                        val convertedText = convertEmojiText(content)
                        binding.tvContent.text = convertedText
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
        
        private fun convertEmojiText(text: String): String {
            var result = text
            val pattern = Regex("\\[([^\\[\\]]+)\\]")
            pattern.findAll(text).forEach { match ->
                val emojiCode = match.groupValues[1]
                val emoji = emojiMap[emojiCode]
                if (emoji != null) {
                    result = result.replace(match.value, emoji)
                }
            }
            return result
        }
        
        private fun parseAndSetEmojiText(
            context: android.content.Context, 
            text: String, 
            emojis: List<EmojiInfo>,
            textSize: Float
        ) {
            var convertedText = text
            for (emoji in emojis) {
                if (emoji.text.isNotEmpty() && emoji.url.isNotEmpty()) {
                    val localEmoji = emojiMap[emoji.text]
                    if (localEmoji != null) {
                        convertedText = convertedText.replace(emoji.text, localEmoji)
                    }
                }
            }
            binding.tvContent.text = convertedText
        }
    }
}
