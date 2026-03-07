package com.douyin.danmaku.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object EmojiUtils {
    
    private const val EMOJI_BASE_URL = "https://p3-webcast.douyinpic.com/img/"
    private const val CACHE_DIR = "emoji_cache"
    
    private val client = OkHttpClient.Builder().build()
    private val emojiCache = ConcurrentHashMap<String, String>()
    private val bitmapCache = LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt())
    
    private val emojiPatterns = mapOf(
        Regex("\\[([^\\[\\]]+)\\]") to "webcast/emoji_normal/"
    )
    
    fun parseEmojiText(text: String): SpannableString {
        val spannable = SpannableString(text)
        return spannable
    }
    
    fun containsEmoji(text: String): Boolean {
        return text.contains("[") && text.contains("]")
    }
    
    fun getEmojiCode(text: String): List<String> {
        val result = mutableListOf<String>()
        val pattern = Regex("\\[([^\\[\\]]+)\\]")
        pattern.findAll(text).forEach { match ->
            result.add(match.groupValues[1])
        }
        return result
    }
    
    suspend fun preloadEmojis(context: Context, text: String) {
        if (!containsEmoji(text)) return
        
        val emojiCodes = getEmojiCode(text)
        for (code in emojiCodes) {
            getEmojiUrl(code)?.let { url ->
                loadEmojiBitmap(context, url)
            }
        }
    }
    
    private fun getEmojiUrl(code: String): String? {
        return emojiCache[code] ?: run {
            val url = "${EMOJI_BASE_URL}webcast/emoji_normal/${code}.png"
            emojiCache[code] = url
            url
        }
    }
    
    private suspend fun loadEmojiBitmap(context: Context, url: String): Bitmap? {
        val cached = bitmapCache.get(url)
        if (cached != null) return cached
        
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = getCacheFile(context, url)
                if (cacheFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bitmap != null) {
                        bitmapCache.put(url, bitmap)
                    }
                    return@withContext bitmap
                }
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        bitmapCache.put(url, bitmap)
                        saveToCache(cacheFile, bytes)
                    }
                    bitmap
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun getCacheFile(context: Context, url: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val fileName = url.hashCode().toString()
        return File(cacheDir, fileName)
    }
    
    private fun saveToCache(file: File, data: ByteArray) {
        try {
            FileOutputStream(file).use { it.write(data) }
        } catch (e: Exception) {
        }
    }
    
    fun getBitmapFromCache(url: String): Bitmap? {
        return bitmapCache.get(url)
    }
    
    fun createEmojiSpan(context: Context, bitmap: Bitmap, size: Int): ImageSpan {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
        return ImageSpan(context, scaledBitmap, ImageSpan.ALIGN_BASELINE)
    }
    
    fun parseTextWithEmoji(context: Context, text: String, textSize: Float): SpannableString {
        val spannable = SpannableString(text)
        
        if (!containsEmoji(text)) return spannable
        
        val pattern = Regex("\\[([^\\[\\]]+)\\]")
        val size = (textSize * 1.2).toInt()
        
        pattern.findAll(text).forEach { match ->
            val code = match.groupValues[1]
            val url = getEmojiUrl(code) ?: return@forEach
            val bitmap = getBitmapFromCache(url) ?: return@forEach
            
            val span = createEmojiSpan(context, bitmap, size)
            spannable.setSpan(span, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        return spannable
    }
}
