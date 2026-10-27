package com.example.ytmusic2.webview

data class SongMetadata(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val artworkUrl: String = ""
)
