package com.douyin.danmaku.model

data class RoomInfo(
    val roomId: String,
    val webRoomId: String,
    val title: String = "",
    val anchorName: String = "",
    val viewerCount: Long = 0
)
