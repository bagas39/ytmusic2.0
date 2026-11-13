package com.bagas39.ytmusic2.lyrics

import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.bagas39.ytmusic2.util.FileUtils
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Mengelola pengambilan lirik dari cache lokal (`.lrc`) maupun LRCLIB API publik,
 * serta menyimpan file `.lrc` pendamping untuk lagu offline.
 */
object LyricsRepository {

    private const val LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
    private const val USER_AGENT = "ytmusic2.0/1.3.0 (https://github.com/bagas39/ytmusic2.0)"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    private val mainHandler = Handler(Looper.getMainLooper())
    private val memoryCache = object : LruCache<String, LyricsResult>(20) {}

    private val NOISE_PARENS_REGEX = Regex(
        """[({\[]\s*(?:official\s*(?:music\s*)?video|official\s*audio|lyric\s*video|lyrics?|visualizer|music\s*video|mv|hd|hq|4k|remastered(?:\s*\d{4})?|audio|video|explicit|live(?:\s+at[^)\]}]+)?)\s*[)}\]]""",
        RegexOption.IGNORE_CASE
    )
    private val FEAT_REGEX = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val ARTIST_NOISE_REGEX = Regex("""\s*(?:-|•)?\s*(?:Topic|VEVO|Official)\s*$""", RegexOption.IGNORE_CASE)
    private val PUNCT_NORM_REGEX = Regex("""[\\/:*?"<>|\u29f8\uff0f\uff1a\uff0a\uff1f\uff02\uff1c\uff1e\uff5c_\-]+""")
    private val MULTISPACE_REGEX = Regex("""\s+""")
    private val INVALID_FILENAME_REGEX = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

    /**
     * Membersihkan judul dan nama artis dari embel-embel umum YouTube agar pencarian
     * lirik di LRCLIB lebih akurat. Jika artis kosong dan judul memakai pola `Artis - Judul`,
     * keduanya dipisahkan otomatis.
     */
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

        // Pisahkan "Artist - Title" jika artis belum diketahui
        if (artist.isBlank() && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                artist = ARTIST_NOISE_REGEX.replace(parts[0], "").trim()
                title = parts[1].trim()
            }
        }

        return title to artist
    }

    /**
     * Mencari file `.lrc` lokal di `YTMusic2/Lyrics/` (atau lokasi legacy) yang cocok
     * untuk lagu offline tertentu atau berdasarkan judul.
     */
    fun findLocalLrcFile(title: String, offlineFile: File? = null): File? {
        if (offlineFile != null) {
            val companion = FileUtils.getCompanionLrcFile(offlineFile)
            if (companion.isFile && companion.canRead()) {
                return companion
            }
            val legacyCompanion = File(offlineFile.parentFile, "${offlineFile.nameWithoutExtension}.lrc")
            if (legacyCompanion.isFile && legacyCompanion.canRead()) {
                return legacyCompanion
            }
        }

        val cleanTitle = sanitizeTitleAndArtist(title, "").first
        val normInput = normalizeForMatch(title)
        val normClean = normalizeForMatch(cleanTitle)
        if (normInput.isEmpty() && normClean.isEmpty()) return null

        val dirsToScan = listOf(FileUtils.getLyricsDir(), FileUtils.getDownloadDir())
        for (dir in dirsToScan) {
            val lrcFiles = dir.listFiles { f -> f.isFile && f.extension.equals("lrc", ignoreCase = true) }
                ?: continue
            val matched = lrcFiles.firstOrNull { f ->
                val normFile = normalizeForMatch(f.nameWithoutExtension)
                val normCleanFile = normalizeForMatch(sanitizeTitleAndArtist(f.nameWithoutExtension, "").first)
                normFile == normInput ||
                    (normClean.isNotEmpty() && (normFile == normClean || normCleanFile == normClean))
            }
            if (matched != null) return matched
        }
        return null
    }

    /**
     * Mengecek apakah file audio offline sudah memiliki file lirik `.lrc` lokal.
     */
    fun hasLocalLyrics(audioFile: File): Boolean {
        return findLocalLrcFile(audioFile.nameWithoutExtension, audioFile) != null
    }

    /**
     * Menghapus file `.lrc` lokal milik file audio offline dari `YTMusic2/Lyrics/`
     * (beserta lokasi legacy jika ada) dan membersihkan entri memory cache-nya.
     */
    fun deleteLocalLyrics(audioFile: File): Boolean {
        var deletedAny = false
        val companion = FileUtils.getCompanionLrcFile(audioFile)
        if (companion.exists() && companion.delete()) {
            deletedAny = true
        }
        val legacy = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (legacy.exists() && legacy.delete()) {
            deletedAny = true
        }
        val matched = findLocalLrcFile(audioFile.nameWithoutExtension, audioFile)
        if (matched != null && matched.exists() && matched.delete()) {
            deletedAny = true
        }

        val (cleanTitle, cleanArtist) = sanitizeTitleAndArtist(audioFile.nameWithoutExtension, "")
        memoryCache.remove(buildCacheKey(cleanTitle, cleanArtist))
        return deletedAny
    }

    /**
     * Menyimpan konten LRC dalam encoding UTF-8 secara aman ke [targetFile].
     */
    fun saveLrcToFile(lrcContent: String, targetFile: File): Boolean {
        if (lrcContent.isBlank()) return false
        return try {
            targetFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            targetFile.writeText(lrcContent, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengambil lirik secara asynchronous: memprioritaskan file `.lrc` lokal di `YTMusic2/Lyrics/`,
     * lalu memory cache, dan terakhir LRCLIB API. Hasil dikirim kembali ke main thread melalui [onResult].
     */
    fun getLyricsAsync(
        title: String,
        artist: String = "",
        durationMs: Long = 0L,
        offlineFile: File? = null,
        onResult: (LyricsResult?) -> Unit
    ) {
        if (title.isBlank() && offlineFile == null) {
            mainHandler.post { onResult(null) }
            return
        }

        val effectiveTitle = title.ifBlank { offlineFile?.nameWithoutExtension.orEmpty() }
        val (cleanTitle, cleanArtist) = sanitizeTitleAndArtist(effectiveTitle, artist)
        val cacheKey = buildCacheKey(cleanTitle, cleanArtist)

        thread(name = "ytm-lyrics-fetch") {
            val result = try {
                // 1. Cek file .lrc lokal terlebih dahulu
                val localFile = findLocalLrcFile(effectiveTitle, offlineFile)
                if (localFile != null) {
                    val raw = localFile.readText(Charsets.UTF_8)
                    parseLrcToResult(
                        raw = raw,
                        title = cleanTitle.ifBlank { effectiveTitle },
                        artist = cleanArtist
                    )?.also { memoryCache.put(cacheKey, it) }
                } else {
                    // 2. Cek in-memory LRU cache, atau 3. Ambil dari LRCLIB API
                    val resolved = memoryCache.get(cacheKey) ?: fetchFromLrclib(cleanTitle, cleanArtist, durationMs)?.also { fetched ->
                        memoryCache.put(cacheKey, fetched)
                    }
                    // Jika lagu offline ada dan ditemukan lirik sinkron, simpan otomatis ke YTMusic2/Lyrics/
                    if (offlineFile != null && resolved != null && resolved.isSynced && resolved.rawLrc.isNotBlank()) {
                        val companion = FileUtils.getCompanionLrcFile(offlineFile)
                        saveLrcToFile(resolved.rawLrc, companion)
                    }
                    resolved
                }
            } catch (_: Exception) {
                null
            }

            mainHandler.post { onResult(result) }
        }
    }

    /**
     * Mengunduh lirik secara sinkron (dipanggil dari background thread [SongDownloader])
     * dan menyimpannya sebagai file `.lrc` pendamping di `YTMusic2/Lyrics/`.
     */
    fun downloadAndSaveLrcForOfflineSong(
        title: String,
        artist: String = "",
        targetDir: File = FileUtils.getLyricsDir(),
        mp3File: File? = null
    ): File? {
        val effectiveTitle = title.ifBlank { mp3File?.nameWithoutExtension.orEmpty() }
        if (effectiveTitle.isBlank()) return null

        val (cleanTitle, cleanArtist) = sanitizeTitleAndArtist(effectiveTitle, artist)
        val cacheKey = buildCacheKey(cleanTitle, cleanArtist)

        val result = memoryCache.get(cacheKey)
            ?: fetchFromLrclib(cleanTitle, cleanArtist, durationMs = 0L)?.also {
                memoryCache.put(cacheKey, it)
            }
            ?: return null

        val contentToSave = result.rawLrc.trim()
        if (contentToSave.isEmpty()) return null

        val targetFile = if (mp3File != null) {
            FileUtils.getCompanionLrcFile(mp3File)
        } else {
            val safeBaseName = INVALID_FILENAME_REGEX.replace(effectiveTitle, "_").trim().ifBlank { "lyrics" }
            File(targetDir, "$safeBaseName.lrc")
        }

        return if (saveLrcToFile(contentToSave, targetFile)) targetFile else null
    }

    private fun fetchFromLrclib(cleanTitle: String, cleanArtist: String, durationMs: Long): LyricsResult? {
        val query = listOf(cleanTitle, cleanArtist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isEmpty()) return null

        val primaryResults = requestLrclibSearch(query)
        val fallbackResults = if (primaryResults == null || primaryResults.length() == 0) {
            if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                requestLrclibSearch(cleanTitle)
            } else {
                null
            }
        } else {
            null
        }

        val candidates = if (primaryResults != null && primaryResults.length() > 0) primaryResults else fallbackResults
        if (candidates == null || candidates.length() == 0) return null

        return selectBestCandidate(candidates, cleanTitle, cleanArtist, durationMs)
    }

    private fun requestLrclibSearch(query: String): JSONArray? {
        var conn: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$LRCLIB_SEARCH_URL?q=$encodedQuery")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank()) return null
            JSONArray(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun selectBestCandidate(
        array: JSONArray,
        targetTitle: String,
        targetArtist: String,
        durationMs: Long
    ): LyricsResult? {
        val normTargetTitle = normalizeForMatch(targetTitle)
        val normTargetArtist = normalizeForMatch(targetArtist)
        val targetDurationSec = if (durationMs > 0L) durationMs / 1000.0 else -1.0

        var bestResult: LyricsResult? = null
        var bestScore = Int.MIN_VALUE

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val synced = item.optString("syncedLyrics", "").trim()
            val plain = item.optString("plainLyrics", "").trim()
            if (synced.isEmpty() && plain.isEmpty()) continue

            val trackName = item.optString("trackName", targetTitle)
            val artistName = item.optString("artistName", targetArtist)
            val durationSec = item.optDouble("duration", -1.0)

            var score = 0
            val isSynced = synced.isNotEmpty()
            if (isSynced) score += 100

            val normTrack = normalizeForMatch(trackName)
            val normArtist = normalizeForMatch(artistName)

            if (normTargetTitle.isNotEmpty()) {
                when {
                    normTrack == normTargetTitle -> score += 40
                    normTrack.contains(normTargetTitle) || normTargetTitle.contains(normTrack) -> score += 20
                }
            }

            if (normTargetArtist.isNotEmpty()) {
                when {
                    normArtist == normTargetArtist -> score += 35
                    normArtist.contains(normTargetArtist) || normTargetArtist.contains(normArtist) -> score += 20
                }
            }

            if (targetDurationSec > 0 && durationSec > 0) {
                val diff = abs(durationSec - targetDurationSec)
                when {
                    diff <= 2.0 -> score += 30
                    diff <= 5.0 -> score += 15
                    diff > 20.0 -> score -= 20
                }
            }

            if (score > bestScore) {
                val lines = if (isSynced) LrcParser.parse(synced) else LrcParser.parsePlain(plain)
                if (lines.isNotEmpty()) {
                    bestScore = score
                    bestResult = LyricsResult(
                        title = trackName.ifBlank { targetTitle },
                        artist = artistName.ifBlank { targetArtist },
                        isSynced = isSynced,
                        lines = lines,
                        rawLrc = if (isSynced) synced else plain
                    )
                }
            }
        }

        return bestResult
    }

    private fun parseLrcToResult(raw: String, title: String, artist: String): LyricsResult? {
        val syncedLines = LrcParser.parse(raw)
        if (syncedLines.isNotEmpty()) {
            return LyricsResult(
                title = title,
                artist = artist,
                isSynced = true,
                lines = syncedLines,
                rawLrc = raw
            )
        }
        val plainLines = LrcParser.parsePlain(raw)
        if (plainLines.isNotEmpty()) {
            return LyricsResult(
                title = title,
                artist = artist,
                isSynced = false,
                lines = plainLines,
                rawLrc = raw
            )
        }
        return null
    }

    private fun buildCacheKey(cleanTitle: String, cleanArtist: String): String {
        return "${normalizeForMatch(cleanTitle)}|${normalizeForMatch(cleanArtist)}"
    }

    private fun normalizeForMatch(raw: String): String {
        return raw.lowercase()
            .replace(PUNCT_NORM_REGEX, " ")
            .replace(MULTISPACE_REGEX, " ")
            .trim()
    }
}
