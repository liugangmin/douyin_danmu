package com.douyin.danmaku.network

import com.douyin.danmaku.model.RoomInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RoomInfoFetcher {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    suspend fun fetchRoomInfo(input: String): RoomInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val webRoomId = parseInput(input)
                if (webRoomId.isNullOrEmpty()) {
                    return@withContext null
                }
                
                val html = fetchLivePage(webRoomId)
                if (html.isNullOrEmpty()) {
                    return@withContext null
                }
                
                parseRoomInfo(html, webRoomId)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private fun parseInput(input: String): String? {
        val trimmed = input.trim()
        
        if (trimmed.matches(Regex("\\d+"))) {
            return trimmed
        }
        
        val patterns = listOf(
            Regex("live\\.douyin\\.com/(\\d+)"),
            Regex("live\\.douyin\\.com/([a-zA-Z0-9_-]+)"),
            Regex("v\\.douyin\\.com/([a-zA-Z0-9]+)"),
            Regex("douyin\\.com/live/([a-zA-Z0-9_-]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    private fun fetchLivePage(webRoomId: String): String? {
        return try {
            val url = "https://live.douyin.com/$webRoomId"
            val ttwid = generateTtwid()
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Cookie", "ttwid=$ttwid")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun generateTtwid(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
    
    private fun parseRoomInfo(html: String, webRoomId: String): RoomInfo? {
        try {
            // 提取roomId
            val roomIdPatterns = listOf(
                Regex("\"roomId\":(\\d+)"),
                Regex("\"room_id\":(\\d+)"),
                Regex("\"id_str\":\"(\\d+)\""),
                Regex("ROOM_ID[=\\s]+[\"']?(\\d+)")
            )
            
            var roomId: String? = null
            for (pattern in roomIdPatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    roomId = match.groupValues[1]
                    break
                }
            }
            
            if (roomId == null) {
                roomId = webRoomId
            }
            
            // 提取标题
            var title = ""
            val titleMatch = Regex("\"title\":\"([^\"]+)\"").find(html)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1]
            }
            
            // 提取主播名
            var anchorName = ""
            val nicknameMatch = Regex("\"nickname\":\"([^\"]+)\"").find(html)
            if (nicknameMatch != null) {
                anchorName = nicknameMatch.groupValues[1]
            }
            
            return RoomInfo(
                roomId = roomId,
                webRoomId = webRoomId,
                title = title,
                anchorName = anchorName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun generateUserUniqueId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (0..999999).random()
        return "${timestamp}${random.toString().padStart(6, '0')}"
    }
}
