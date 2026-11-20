package com.bagas39.ytmusic2.lyrics

import kotlin.math.abs

/**
 * Helper murni Kotlin (tanpa ketergantungan Android framework) untuk pembersihan judul,
 * pemecahan judul ganda/medley, pencocokan durasi kandidat lirik, dan tag metadata durasi LRC.
 */
internal object LyricsMatcher {

    private val NOISE_PARENS_REGEX = Regex(
        """[({\[]\s*(?:official\s*(?:music\s*)?video|vertical\s*(?:music\s*)?video|official\s*visualizer|official\s*audio|lyric\s*video|lyrics?|visualizer|music\s*video|mv|hd|hq|4k|remastered(?:\s*\d{4})?|audio|video|explicit|live(?:\s+at[^)\]}]+)?)\s*[)}\]]""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_REGEX = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val ARTIST_NOISE_REGEX = Regex("""\s*(?:-|•)?\s*(?:Topic|VEVO|Official)\s*$""", RegexOption.IGNORE_CASE)
    private val ARTIST_TITLE_SPLIT_REGEX = Regex("""\s+[-–—]\s+""")
    private val DUAL_TITLE_SPLIT_REGEX = Regex("""\s+[/\u29f8\uff0f|]\s+|[\u29f8\uff0f]""")
    private val DURATION_TAG_REGEX = Regex("""(?im)^\[duration:(\d+)]$""")
    private val PUNCT_NORM_REGEX = Regex("""[\\/:*?"<>|\u29f8\uff0f\uff1a\uff0a\uff1f\uff02\uff1c\uff1e\uff5c_\-–—]+""")
    private val MULTISPACE_REGEX = Regex("""\s+""")

    fun sanitizeTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.trim()
        var artist = rawArtist
            .replace(ARTIST_NOISE_REGEX, "")
            .trim()

        if (artist.equals("YouTube Music", ignoreCase = true) ||
            artist.equals("Offline", ignoreCase = true) ||
            artist.startsWith("Offline (", ignoreCase = true) ||
            artist.equals("Lagu Offline", ignoreCase = true)
        ) {
            artist = ""
        }

        // Hapus noise dalam tanda kurung dan kata feat./ft. pada judul
        title = NOISE_PARENS_REGEX.replace(title, " ")
        title = FEAT_REGEX.replace(title, "")
        title = MULTISPACE_REGEX.replace(title, " ").trim()

        // Pisahkan "Artist - Title" (termasuk en-dash / em-dash) jika artis belum diketahui
        if (artist.isBlank()) {
            val parts = ARTIST_TITLE_SPLIT_REGEX.split(title, limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                artist = ARTIST_NOISE_REGEX.replace(parts[0], "").trim()
                title = parts[1].trim()
            }
        }

        return title to artist
    }

    fun extractTitleSearchVariants(cleanTitle: String): List<String> {
        val trimmed = cleanTitle.trim()
        if (trimmed.isEmpty()) return emptyList()
        val variants = linkedSetOf(trimmed)
        val segments = DUAL_TITLE_SPLIT_REGEX.split(trimmed)
            .map { it.trim() }
            .filter { it.length >= 2 }
        if (segments.size >= 2) {
            // Utamakan segmen lengkap terlebih dahulu; sub-segmen dicoba hanya sebagai cadangan
            segments.take(3).forEach { variants.add(it) }
        }
        return variants.toList()
    }

    fun extractDurationTagMs(rawLrc: String): Long {
        val match = DURATION_TAG_REGEX.find(rawLrc) ?: return 0L
        return match.groupValues[1].toLongOrNull()?.takeIf { it > 0L } ?: 0L
    }

    fun formatLrcWithDurationTag(rawLrc: String, durationMs: Long): String {
        val stripped = stripDurationTag(rawLrc)
        if (stripped.isEmpty()) return ""
        return if (durationMs > 0L) {
            "[duration:$durationMs]\n$stripped"
        } else {
            stripped
        }
    }

    fun stripDurationTag(rawLrc: String): String {
        return rawLrc.lineSequence()
            .filterNot { DURATION_TAG_REGEX.matches(it.trim()) }
            .joinToString("\n")
            .trim()
    }

    fun titlesMatch(normTrack: String, normTargetTitle: String): Boolean {
        if (normTrack.isEmpty() || normTargetTitle.isEmpty()) return false
        return normTrack == normTargetTitle ||
            normTrack.contains(normTargetTitle) ||
            normTargetTitle.contains(normTrack)
    }

    fun scoreCandidateMetadata(
        normTrack: String,
        normArtist: String,
        candidateDurationSec: Double,
        normTargetTitle: String,
        normTargetArtist: String,
        targetDurationSec: Double
    ): Int {
        var score = 0
        if (normTargetTitle.isNotEmpty() && normTrack.isNotEmpty()) {
            when {
                normTrack == normTargetTitle -> score += 40
                normTrack.contains(normTargetTitle) || normTargetTitle.contains(normTrack) -> score += 20
            }
        }
        if (normTargetArtist.isNotEmpty() && normArtist.isNotEmpty()) {
            when {
                normArtist == normTargetArtist -> score += 35
                normArtist.contains(normTargetArtist) || normTargetArtist.contains(normArtist) -> score += 20
            }
        }
        if (candidateDurationSec > 0.0 && candidateDurationSec < 15.0 && (targetDurationSec <= 0.0 || targetDurationSec > 30.0)) {
            score -= 80
        } else if (targetDurationSec > 0 && candidateDurationSec > 0) {
            val diff = abs(candidateDurationSec - targetDurationSec)
            when {
                diff <= 1.0 -> score += 45
                diff <= 2.5 -> score += 35
                diff <= 5.0 -> score += 15
                diff <= 10.0 -> score += 0
                diff <= 20.0 -> score -= 20
                else -> score -= 45
            }
        }
        return score
    }

    /**
     * Soft tie-breaker that penalizes synced LRCs whose first line jumps awkwardly early
     * (>8s before line 2 despite a fast early verse cadence), breaking exact metadata ties
     * in favor of cleanly timed syncs without disqualifying solo candidates.
     */
    fun scoreSyncedLineCadence(lines: List<LyricLine>): Int {
        if (lines.size < 4) return 0
        val firstGapMs = lines[1].timeMs - lines[0].timeMs
        if (lines[0].text.length > 48 || firstGapMs <= 8000L) return 0

        val followGaps = (1 until minOf(lines.size - 1, 5))
            .map { lines[it + 1].timeMs - lines[it].timeMs }
            .sorted()
        if (followGaps.isEmpty()) return 0

        val medianFollowGapMs = followGaps[followGaps.size / 2]
        return if (medianFollowGapMs in 500L..4500L) -3 else 0
    }

    fun buildCacheKey(cleanTitle: String, cleanArtist: String, durationMs: Long = 0L): String {
        val durationBucketSec = if (durationMs > 0L) durationMs / 1000L else 0L
        return "${normalizeForMatch(cleanTitle)}|${normalizeForMatch(cleanArtist)}|$durationBucketSec"
    }

    fun normalizeForMatch(raw: String): String {
        return raw.lowercase()
            .replace(PUNCT_NORM_REGEX, " ")
            .replace(MULTISPACE_REGEX, " ")
            .trim()
    }
}
