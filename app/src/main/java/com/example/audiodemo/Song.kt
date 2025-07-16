package com.example.audiodemo

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val resourceId: Int  // 必须提供资源ID
) 