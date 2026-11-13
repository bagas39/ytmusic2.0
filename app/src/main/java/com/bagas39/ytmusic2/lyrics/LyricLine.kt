package com.bagas39.ytmusic2.lyrics

/**
 * Merepresentasikan satu baris lirik beserta penanda waktunya (dalam milidetik).
 * Untuk lirik polos tanpa timestamp, [timeMs] bernilai `-1L`.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * Hasil pencarian/pemuatan lirik untuk suatu lagu, baik dalam format
 * time-synced (.lrc) maupun lirik teks polos.
 */
data class LyricsResult(
    val title: String,
    val artist: String,
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val rawLrc: String
)
