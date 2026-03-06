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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    suspend fun fetchRoomInfo(input: String): RoomInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // 解析输入，获取webRoomId
                val webRoomId = parseInput(input)
                if (webRoomId.isNullOrEmpty()) {
                    return@withContext null
                }
                
                // 请求直播间页面
                val html = fetchLivePage(webRoomId)
                if (html.isNullOrEmpty()) {
                    return@withContext null
                }
                
                // 解析页面获取roomId和其他信息
                parseRoomInfo(html, webRoomId)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseInput(input: String): String? {
        // 如果是纯数字，直接返回
        if (input.matches(Regex("\\d+"))) {
            return input
        }
        
        // 如果是直播间链接，提取ID
        val patterns = listOf(
            Regex("live\\.douyin\\.com/(\\d+)"),
            Regex("live\\.douyin\\.com/([a-zA-Z0-9]+)"),
            Regex("v\\.douyin\\.com/([a-zA-Z0-9]+)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    private fun fetchLivePage(webRoomId: String): String? {
        return try {
            val url = "https://live.douyin.com/$webRoomId"
            
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Cache-Control", "no-cache")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseRoomInfo(html: String, webRoomId: String): RoomInfo? {
        try {
            // 尝试从页面中提取roomId
            // 方法1: 从ROOM_ID变量中提取
            val roomIdPattern = Regex("ROOM_ID\\s*=\\s*['\"](\\d+)['\"]")
            val roomIdMatch = roomIdPattern.find(html)
            val roomId = roomIdMatch?.groupValues?.get(1)
            
            // 方法2: 从id_str中提取
            val idStrPattern = Regex("\"id_str\"\\s*:\\s*\"(\\d+)\"")
            val idStrMatch = idStrPattern.find(html)
            val roomIdFromIdStr = idStrMatch?.groupValues?.get(1)
            
            // 方法3: 从room对象中提取
            val roomPattern = Regex("\"room_id\"\\s*:\\s*(\\d+)")
            val roomMatch = roomPattern.find(html)
            val roomIdFromRoom = roomMatch?.groupValues?.get(1)
            
            val finalRoomId = roomId ?: roomIdFromIdStr ?: roomIdFromRoom ?: return null
            
            // 提取直播间标题
            val titlePattern = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"")
            val titleMatch = titlePattern.find(html)
            val title = titleMatch?.groupValues?.get(1) ?: ""
            
            // 提取主播名
            val nicknamePattern = Regex("\"nickname\"\\s*:\\s*\"([^\"]+)\"")
            val nicknameMatch = nicknamePattern.find(html)
            val anchorName = nicknameMatch?.groupValues?.get(1) ?: ""
            
            return RoomInfo(
                roomId = finalRoomId,
                webRoomId = webRoomId,
                title = title,
                anchorName = anchorName
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    fun generateUserUniqueId(): String {
        // 生成一个随机的用户ID
        val timestamp = System.currentTimeMillis()
        val random = (0..999999).random()
        return "${timestamp}${random.toString().padStart(6, '0')}"
    }
}
