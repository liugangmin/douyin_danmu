package com.douyin.danmaku.network

import com.douyin.danmaku.model.RoomInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 直播间信息获取
 */
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
        
        // 如果是纯数字，直接返回
        if (trimmed.matches(Regex("\\d+"))) {
            return trimmed
        }
        
        // 各种链接格式
        val patterns = listOf(
            Regex("live\\.douyin\\.com/(\\d+)"),
            Regex("live\\.douyin\\.com/([a-zA-Z0-9_-]+)"),
            Regex("v\\.douyin\\.com/([a-zA-Z0-9]+)"),
            Regex("douyin\\.com/live/([a-zA-Z0-9_-]+)"),
            Regex("www\\.douyin\\.com/.*?roomId=(\\d+)"),
            Regex("www\\.douyin\\.com/.*?room_id=(\\d+)")
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
            
            // 生成随机ttwid
            val ttwid = generateTtwid()
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "none")
                .addHeader("Sec-Fetch-User", "?1")
                .addHeader("Cookie", "ttwid=$ttwid; __ac_nonce=0684c9b8e00d8e8f4b7d")
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
            // 提取roomId - 多种方式尝试
            val roomIdPatterns = listOf(
                Regex("\"roomId\"\s*:\s*(\\d+)"),
                Regex("\"room_id\"\s*:\s*(\\d+)"),
                Regex("\"id_str\"\s*:\s*\"(\\d+)\""),
                Regex("ROOM_ID\s*=\s*[\'\"]?(\\d+)[\'\"]?"),
                Regex("web_rid\"\s*:\s*\"(\\d+)\""),
                Regex("\"rid\"\s*:\s*\"(\\d+)\"")
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
                // 如果找不到roomId，尝试使用webRoomId
                roomId = webRoomId
            }
            
            // 提取标题
            val titlePatterns = listOf(
                Regex("\"title\"\s*:\s*\"([^\"]+)\""),
                Regex("<title>([^<]+)</title>")
            )
            var title = ""
            for (pattern in titlePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    title = match.groupValues[1]
                    break
                }
            }
            
            // 提取主播名
            val nicknamePatterns = listOf(
                Regex("\"nickname\"\s*:\s*\"([^\"]+)\""),
                Regex("\"anchorName\"\s*:\s*\"([^\"]+)\"")
            )
            var anchorName = ""
            for (pattern in nicknamePatterns) {
                val match = pattern.find(html)
                if (match != null) {
                    anchorName = match.groupValues[1]
                    break
                }
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
