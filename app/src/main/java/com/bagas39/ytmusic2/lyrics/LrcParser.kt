package com.bagas39.ytmusic2.lyrics

/**
 * Parser untuk file lirik berformat LRC standar (`[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]`, `[mm:ss]`)
 * serta penyedia fallback lirik teks polos dan pencarian indeks baris aktif berdasarkan posisi lagu.
 */
object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG_REGEX = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")

    /**
     * Mem-parse konten LRC mentah menjadi daftar [LyricLine] yang terurut menaik berdasarkan waktu.
     * Mendukung beberapa tag waktu dalam satu baris dan menerapkan tag `[offset:...]`.
     */
    fun parse(rawLrc: String): List<LyricLine> {
        if (rawLrc.isBlank()) return emptyList()

        var offsetMs = 0L
        val parsed = mutableListOf<LyricLine>()

        rawLrc.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val offsetMatch = OFFSET_TAG_REGEX.matchEntire(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }

            val timeMatches = TIME_TAG_REGEX.findAll(line).toList()
            if (timeMatches.isEmpty()) {
                // Abaikan tag metadata LRC seperti [ar:...], [ti:...], [al:...], [by:...]
                return@forEach
            }

            val lyricText = TIME_TAG_REGEX.replace(line, "").trim()
            // Lewati baris kosong yang tidak memiliki teks maupun metadata relevan
            if (lyricText.isEmpty()) return@forEach

            for (match in timeMatches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = ((minutes * 60L + seconds) * 1000L + millis + offsetMs).coerceAtLeast(0L)
                parsed.add(LyricLine(timeMs = totalMs, text = lyricText))
            }
        }

        return parsed.sortedBy { it.timeMs }
    }

    /**
     * Mem-parse lirik teks biasa (tanpa timestamp) dengan `timeMs = -1L`.
     */
    fun parsePlain(rawText: String): List<LyricLine> {
        if (rawText.isBlank()) return emptyList()
        return rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !METADATA_TAG_REGEX.matches(it) }
            .map { LyricLine(timeMs = -1L, text = it) }
            .toList()
    }

    /**
     * Mengembalikan indeks baris lirik yang sedang aktif pada [positionMs],
     * atau `-1` jika belum mencapai baris pertama atau lirik tidak memiliki timestamp.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < 0L) return -1
        return lines.indexOfLast { it.timeMs in 0L..positionMs }
    }
}
