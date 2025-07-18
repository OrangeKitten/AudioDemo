package com.example.audiodemo

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val resourceId: Int,  // 必须提供资源ID
    var channelCount: Int = 0,  // 声道数
    var sampleRate: Int = 0,    // 采样率
    var bitDepth: Int = 0       // 位深度
) 