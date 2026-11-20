package com.bagas39.ytmusic2.lyrics

import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.LruCache
import com.bagas39.ytmusic2.util.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Mengelola pengambilan lirik dari cache lokal (`.lrc`) maupun rantai API publik
 * (LRCLIB -> NetEase -> KuGou -> Lyrics.ovh), serta menyimpan file `.lrc` pendamping untuk lagu offline.
 */
object LyricsRepository {

    private const val LRCLIB_SEARCH_URL = "https://lrclib.net/api/search"
    private const val NETEASE_SEARCH_URL = "https://music.163.com/api/search/get"
    private const val NETEASE_LYRIC_URL = "https://music.163.com/api/song/lyric"
    private const val KUGOU_SEARCH_URL = "https://lyrics.kugou.com/search"
    private const val KUGOU_DOWNLOAD_URL = "https://lyrics.kugou.com/download"
    private const val LYRICS_OVH_BASE_URL = "https://api.lyrics.ovh/v1"
    private const val USER_AGENT = "ytmusic2.0/1.3.0 (https://github.com/bagas39/ytmusic2.0)"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000
    private const val DURATION_REVALIDATE_TOLERANCE_MS = 2500L

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val memoryCache = object : LruCache<String, LyricsResult>(20) {}

    private val INVALID_FILENAME_REGEX = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
    private val OVH_HEADER_REGEX = Regex("""^Paroles de la chanson [^\r\n]+[\r\n]+""", RegexOption.IGNORE_CASE)

    internal data class ScoredLyricsCandidate(
        val score: Int,
        val result: LyricsResult
    )

    /**
     * Membersihkan judul dan nama artis dari embel-embel umum YouTube agar pencarian
     * lirik di LRCLIB lebih akurat.
     */
    fun sanitizeTitleAndArtist(rawTitle: String, rawArtist: String): Pair<String, String> =
        LyricsMatcher.sanitizeTitleAndArtist(rawTitle, rawArtist)

    internal fun extractTitleSearchVariants(cleanTitle: String): List<String> =
        LyricsMatcher.extractTitleSearchVariants(cleanTitle)

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
        val prefix = "${normalizeForMatch(cleanTitle)}|${normalizeForMatch(cleanArtist)}|"
        memoryCache.snapshot().keys
            .filter { it.startsWith(prefix) }
            .forEach { memoryCache.remove(it) }
        return deletedAny
    }

    /**
     * Mengukur durasi file audio lokal dalam milidetik via [MediaMetadataRetriever] secara aman.
     */
    fun extractAudioDurationMs(audioFile: File?): Long {
        if (audioFile == null || !audioFile.isFile || !audioFile.canRead()) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }

    internal fun extractDurationTagMs(rawLrc: String): Long =
        LyricsMatcher.extractDurationTagMs(rawLrc)

    internal fun formatLrcWithDurationTag(rawLrc: String, durationMs: Long): String =
        LyricsMatcher.formatLrcWithDurationTag(rawLrc, durationMs)

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
     * lalu memory cache, dan terakhir rantai API (LRCLIB -> NetEase -> KuGou -> Lyrics.ovh).
     * Jika file `.lrc` lokal belum memiliki tag durasi yang cocok dengan audio offline aktual,
     * penyedia daring akan dicek ulang agar versi sinkronisasi yang sesuai durasi diprioritaskan.
     * Hasil dikirim kembali ke main thread melalui [onResult].
     */
    fun getLyricsAsync(
        title: String,
        artist: String = "",
        durationMs: Long = 0L,
        offlineFile: File? = null,
        ignoreLocalCache: Boolean = false,
        onResult: (LyricsResult?) -> Unit
    ) {
        if (title.isBlank() && offlineFile == null) {
            mainHandler.post { onResult(null) }
            return
        }

        val effectiveTitle = title.ifBlank { offlineFile?.nameWithoutExtension.orEmpty() }
        val (cleanTitle, cleanArtist) = sanitizeTitleAndArtist(effectiveTitle, artist)

        thread(name = "ytm-lyrics-fetch") {
            val result = try {
                val resolvedDurationMs = if (durationMs > 0L) durationMs else extractAudioDurationMs(offlineFile)
                val cacheKey = buildCacheKey(cleanTitle, cleanArtist, resolvedDurationMs)

                // 1. Cek file .lrc lokal terlebih dahulu kecuali dipaksa cari ulang
                val localFile = if (!ignoreLocalCache) findLocalLrcFile(effectiveTitle, offlineFile) else null
                if (localFile != null) {
                    val raw = localFile.readText(Charsets.UTF_8)
                    val localParsed = parseLrcToResult(
                        raw = raw,
                        title = cleanTitle.ifBlank { effectiveTitle },
                        artist = cleanArtist
                    )
                    val stampedDurMs = extractDurationTagMs(raw)
                    val needsDurationRevalidation = resolvedDurationMs > 0L &&
                        (stampedDurMs <= 0L || abs(stampedDurMs - resolvedDurationMs) > DURATION_REVALIDATE_TOLERANCE_MS)

                    val finalLocal = if (needsDurationRevalidation) {
                        val refreshed = fetchLyricsWithFallbacks(cleanTitle, cleanArtist, resolvedDurationMs)
                        when {
                            refreshed != null && refreshed.isSynced -> {
                                val targetSave = if (offlineFile != null) FileUtils.getCompanionLrcFile(offlineFile) else localFile
                                saveLrcToFile(formatLrcWithDurationTag(refreshed.rawLrc, resolvedDurationMs), targetSave)
                                refreshed
                            }
                            refreshed != null && localParsed?.isSynced == true -> {
                                // Server tidak memberi lirik sinkron yang lebih cocok; tandai file lokal sebagai sudah tervalidasi
                                val targetSave = if (offlineFile != null) FileUtils.getCompanionLrcFile(offlineFile) else localFile
                                saveLrcToFile(formatLrcWithDurationTag(raw, resolvedDurationMs), targetSave)
                                localParsed
                            }
                            else -> localParsed
                        }
                    } else {
                        localParsed
                    }
                    finalLocal?.also { memoryCache.put(cacheKey, it) }
                } else {
                    // 2. Cek in-memory LRU cache (bila tidak force-refresh), atau 3. Ambil dari penyedia lirik
                    val cached = if (!ignoreLocalCache) memoryCache.get(cacheKey) else null
                    val resolved = cached
                        ?: fetchLyricsWithFallbacks(cleanTitle, cleanArtist, resolvedDurationMs)?.also { fetched ->
                            memoryCache.put(cacheKey, fetched)
                        }
                    // Jika lagu offline ada dan ditemukan lirik sinkron, simpan otomatis ke YTMusic2/Lyrics/
                    if (offlineFile != null && resolved != null && resolved.isSynced && resolved.rawLrc.isNotBlank()) {
                        val companion = FileUtils.getCompanionLrcFile(offlineFile)
                        saveLrcToFile(formatLrcWithDurationTag(resolved.rawLrc, resolvedDurationMs), companion)
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
        mp3File: File? = null,
        durationMs: Long = 0L
    ): File? {
        val effectiveTitle = title.ifBlank { mp3File?.nameWithoutExtension.orEmpty() }
        if (effectiveTitle.isBlank()) return null

        val resolvedDurationMs = if (durationMs > 0L) durationMs else extractAudioDurationMs(mp3File)
        val (cleanTitle, cleanArtist) = sanitizeTitleAndArtist(effectiveTitle, artist)
        val cacheKey = buildCacheKey(cleanTitle, cleanArtist, resolvedDurationMs)

        val result = memoryCache.get(cacheKey)
            ?: fetchLyricsWithFallbacks(cleanTitle, cleanArtist, resolvedDurationMs)?.also {
                memoryCache.put(cacheKey, it)
            }
            ?: return null

        val contentToSave = formatLrcWithDurationTag(result.rawLrc, resolvedDurationMs)
        if (contentToSave.isEmpty()) return null

        val targetFile = if (mp3File != null) {
            FileUtils.getCompanionLrcFile(mp3File)
        } else {
            val safeBaseName = INVALID_FILENAME_REGEX.replace(effectiveTitle, "_").trim().ifBlank { "lyrics" }
            File(targetDir, "$safeBaseName.lrc")
        }

        return if (saveLrcToFile(contentToSave, targetFile)) targetFile else null
    }

    /**
     * Mencoba penyedia lirik secara berurutan (LRCLIB -> NetEase -> KuGou -> Lyrics.ovh).
     * Mengutamakan lirik sinkron (`isSynced == true`) dan memecah judul ganda jika judul utuh
     * tidak menghasilkan lirik sinkron di penyedia terkait.
     */
    private fun fetchLyricsWithFallbacks(
        cleanTitle: String,
        cleanArtist: String,
        durationMs: Long
    ): LyricsResult? {
        val titleVariants = extractTitleSearchVariants(cleanTitle)
        if (titleVariants.isEmpty()) return null

        var plainFallback: LyricsResult? = null
        val syncedCapableFetchers: List<(String, String, Long) -> ScoredLyricsCandidate?> = listOf(
            ::fetchFromLrclib,
            ::fetchFromNetease,
            ::fetchFromKugou
        )
        for (fetcher in syncedCapableFetchers) {
            val primary = fetcher(titleVariants.first(), cleanArtist, durationMs)
            if (primary != null) {
                if (primary.result.isSynced) return primary.result
                if (plainFallback == null) plainFallback = primary.result
            }
            if (titleVariants.size > 1) {
                var bestSegment: ScoredLyricsCandidate? = null
                for (segment in titleVariants.drop(1)) {
                    val candidate = fetcher(segment, cleanArtist, durationMs) ?: continue
                    if (candidate.result.isSynced) {
                        if (bestSegment == null || candidate.score > bestSegment.score) {
                            bestSegment = candidate
                        }
                    } else if (plainFallback == null) {
                        plainFallback = candidate.result
                    }
                }
                if (bestSegment != null) return bestSegment.result
            }
        }
        if (plainFallback != null) return plainFallback
        for (variant in titleVariants) {
            val ovh = fetchFromLyricsOvh(variant, cleanArtist)
            if (ovh != null) return ovh
        }
        return null
    }

    private fun fetchFromLrclib(cleanTitle: String, cleanArtist: String, durationMs: Long): ScoredLyricsCandidate? {
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
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val body = requestUrlText("$LRCLIB_SEARCH_URL?q=$encodedQuery") ?: return null
        return try {
            JSONArray(body)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromNetease(cleanTitle: String, cleanArtist: String, durationMs: Long): ScoredLyricsCandidate? {
        val primaryQuery = listOf(cleanTitle, cleanArtist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (primaryQuery.isEmpty()) return null

        val neteaseHeaders = mapOf(
            "Referer" to "https://music.163.com",
            "User-Agent" to BROWSER_USER_AGENT
        )

        val primarySongs = requestNeteaseSearch(primaryQuery, neteaseHeaders)
        val songs = if (primarySongs != null && primarySongs.length() > 0) {
            primarySongs
        } else if (cleanArtist.isNotBlank() && cleanTitle.isNotBlank()) {
            requestNeteaseSearch(cleanTitle, neteaseHeaders)
        } else {
            null
        }
        if (songs == null || songs.length() == 0) return null

        val bestMatch = pickBestJsonCandidate(songs, cleanTitle, cleanArtist, durationMs) { song ->
            val songId = song.optLong("id", -1L)
            if (songId <= 0L) return@pickBestJsonCandidate null
            val trackName = song.optString("name", "").trim()
            val artistName = song.optJSONArray("artists")
                ?.optJSONObject(0)
                ?.optString("name", "")
                ?.trim()
                .orEmpty()
            Triple(trackName, artistName, song.optDouble("duration", -1.0) / 1000.0)
        } ?: return null

        val (bestSong, metaScore) = bestMatch
        val bestSongId = bestSong.optLong("id", -1L)
        val bestTrackName = bestSong.optString("name", "").trim().ifBlank { cleanTitle }
        val bestArtistName = bestSong.optJSONArray("artists")
            ?.optJSONObject(0)
            ?.optString("name", "")
            ?.trim()
            .orEmpty()
            .ifBlank { cleanArtist }

        val lyricBody = requestUrlText(
            "$NETEASE_LYRIC_URL?id=$bestSongId&lv=1&kv=1&tv=-1",
            neteaseHeaders
        ) ?: return null

        return try {
            val json = JSONObject(lyricBody)
            val standardLrc = json.optJSONObject("lrc")?.optString("lyric", "")?.trim().orEmpty()
            val karaokeLrc = json.optJSONObject("klyric")?.optString("lyric", "")?.trim().orEmpty()
            val rawLrc = standardLrc.ifEmpty { karaokeLrc }
            if (rawLrc.isEmpty()) return null
            val parsed = parseLrcToResult(rawLrc, bestTrackName, bestArtistName) ?: return null
            ScoredLyricsCandidate(score = (if (parsed.isSynced) 100 else 0) + metaScore, result = parsed)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestNeteaseSearch(query: String, headers: Map<String, String>): JSONArray? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val body = requestUrlText("$NETEASE_SEARCH_URL?s=$encodedQuery&type=1&limit=5", headers) ?: return null
        return try {
            JSONObject(body).optJSONObject("result")?.optJSONArray("songs")
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromKugou(cleanTitle: String, cleanArtist: String, durationMs: Long): ScoredLyricsCandidate? {
        val query = listOf(cleanArtist, cleanTitle)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isEmpty()) return null

        val durationCandidates = if (durationMs > 0L) requestKugouCandidates(query, durationMs) else null
        val candidates = if (durationCandidates != null && durationCandidates.length() > 0) {
            durationCandidates
        } else {
            requestKugouCandidates(query, durationMs = null)
        }
        if (candidates == null || candidates.length() == 0) return null

        val bestMatch = pickBestJsonCandidate(candidates, cleanTitle, cleanArtist, durationMs) { item ->
            val id = item.optString("id", "").trim()
            val accessKey = item.optString("accesskey", "").trim()
            if (id.isEmpty() || accessKey.isEmpty()) return@pickBestJsonCandidate null
            val trackName = item.optString("song", "").trim()
            val artistName = item.optString("singer", "").trim()
            Triple(trackName, artistName, item.optDouble("duration", -1.0) / 1000.0)
        } ?: return null

        val (bestCandidate, metaScore) = bestMatch
        val bestId = bestCandidate.optString("id", "").trim()
        val bestAccessKey = bestCandidate.optString("accesskey", "").trim()
        val bestTrackName = bestCandidate.optString("song", "").trim().ifBlank { cleanTitle }
        val bestArtistName = bestCandidate.optString("singer", "").trim().ifBlank { cleanArtist }

        val downloadUrl = "$KUGOU_DOWNLOAD_URL?ver=1&client=pc" +
            "&id=${URLEncoder.encode(bestId, "UTF-8")}" +
            "&accesskey=${URLEncoder.encode(bestAccessKey, "UTF-8")}" +
            "&fmt=lrc&charset=utf8"
        val downloadBody = requestUrlText(downloadUrl) ?: return null

        return try {
            val base64Content = JSONObject(downloadBody).optString("content", "").trim()
            if (base64Content.isEmpty()) return null
            val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
            val rawLrc = String(decodedBytes, Charsets.UTF_8).trim()
            if (rawLrc.isEmpty()) return null
            val parsed = parseLrcToResult(rawLrc, bestTrackName, bestArtistName) ?: return null
            ScoredLyricsCandidate(score = (if (parsed.isSynced) 100 else 0) + metaScore, result = parsed)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestKugouCandidates(query: String, durationMs: Long?): JSONArray? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // Jangan kirim &duration=0 ke KuGou karena KuGou akan mengembalikan 0 kandidat
        val durationParam = if (durationMs != null && durationMs > 0L) "&duration=$durationMs" else ""
        val body = requestUrlText("$KUGOU_SEARCH_URL?ver=1&man=yes&client=pc&keyword=$encodedQuery$durationParam")
            ?: return null
        return try {
            JSONObject(body).optJSONArray("candidates")
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromLyricsOvh(cleanTitle: String, cleanArtist: String): LyricsResult? {
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return null

        val encodedArtist = encodeUrlPathSegment(cleanArtist)
        val encodedTitle = encodeUrlPathSegment(cleanTitle)
        val body = requestUrlText("$LYRICS_OVH_BASE_URL/$encodedArtist/$encodedTitle") ?: return null

        return try {
            val rawLyrics = JSONObject(body).optString("lyrics", "")
            val cleaned = OVH_HEADER_REGEX.replace(rawLyrics, "")
                .replace("\r\n", "\n")
                .trim()
            if (cleaned.isEmpty()) return null
            parseLrcToResult(cleaned, cleanTitle, cleanArtist)
        } catch (_: Exception) {
            null
        }
    }

    private fun requestUrlText(urlString: String, headers: Map<String, String> = emptyMap()): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }

            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            body.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun encodeUrlPathSegment(segment: String): String {
        return URLEncoder.encode(segment.trim(), "UTF-8").replace("+", "%20")
    }

    private fun selectBestCandidate(
        array: JSONArray,
        targetTitle: String,
        targetArtist: String,
        durationMs: Long
    ): ScoredLyricsCandidate? {
        val normTargetTitle = normalizeForMatch(targetTitle)
        val normTargetArtist = normalizeForMatch(targetArtist)
        val targetDurationSec = if (durationMs > 0L) durationMs / 1000.0 else -1.0

        var bestCandidate: ScoredLyricsCandidate? = null

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val synced = item.optString("syncedLyrics", "").trim()
            val plain = item.optString("plainLyrics", "").trim()
            if (synced.isEmpty() && plain.isEmpty()) continue

            val trackName = item.optString("trackName", targetTitle)
            val artistName = item.optString("artistName", targetArtist)
            val durationSec = item.optDouble("duration", -1.0)
            val isSynced = synced.isNotEmpty()

            val baseScore = (if (isSynced) 100 else 0) + scoreCandidateMetadata(
                normTrack = normalizeForMatch(trackName),
                normArtist = normalizeForMatch(artistName),
                candidateDurationSec = durationSec,
                normTargetTitle = normTargetTitle,
                normTargetArtist = normTargetArtist,
                targetDurationSec = targetDurationSec
            )

            if (bestCandidate == null || baseScore >= bestCandidate.score) {
                val lines = if (isSynced) LrcParser.parse(synced) else LrcParser.parsePlain(plain)
                if (lines.isNotEmpty()) {
                    val finalScore = baseScore + if (isSynced) LyricsMatcher.scoreSyncedLineCadence(lines) else 0
                    if (bestCandidate == null || finalScore > bestCandidate.score) {
                        bestCandidate = ScoredLyricsCandidate(
                            score = finalScore,
                            result = LyricsResult(
                                title = trackName.ifBlank { targetTitle },
                                artist = artistName.ifBlank { targetArtist },
                                isSynced = isSynced,
                                lines = lines,
                                rawLrc = if (isSynced) synced else plain
                            )
                        )
                    }
                }
            }
        }

        return bestCandidate
    }

    private fun pickBestJsonCandidate(
        array: JSONArray,
        cleanTitle: String,
        cleanArtist: String,
        durationMs: Long,
        extractMeta: (JSONObject) -> Triple<String, String, Double>?
    ): Pair<JSONObject, Int>? {
        val normTargetTitle = normalizeForMatch(cleanTitle)
        val normTargetArtist = normalizeForMatch(cleanArtist)
        val targetDurationSec = if (durationMs > 0L) durationMs / 1000.0 else -1.0

        var bestItem: JSONObject? = null
        var bestScore = Int.MIN_VALUE

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val (trackName, artistName, durationSec) = extractMeta(item) ?: continue
            val normTrack = normalizeForMatch(trackName)
            if (!titlesMatch(normTrack, normTargetTitle)) continue

            val score = scoreCandidateMetadata(
                normTrack = normTrack,
                normArtist = normalizeForMatch(artistName),
                candidateDurationSec = durationSec,
                normTargetTitle = normTargetTitle,
                normTargetArtist = normTargetArtist,
                targetDurationSec = targetDurationSec
            )
            if (score > bestScore) {
                bestScore = score
                bestItem = item
            }
        }

        return bestItem?.let { it to bestScore }
    }

    private fun titlesMatch(normTrack: String, normTargetTitle: String): Boolean =
        LyricsMatcher.titlesMatch(normTrack, normTargetTitle)

    internal fun scoreCandidateMetadata(
        normTrack: String,
        normArtist: String,
        candidateDurationSec: Double,
        normTargetTitle: String,
        normTargetArtist: String,
        targetDurationSec: Double
    ): Int = LyricsMatcher.scoreCandidateMetadata(
        normTrack = normTrack,
        normArtist = normArtist,
        candidateDurationSec = candidateDurationSec,
        normTargetTitle = normTargetTitle,
        normTargetArtist = normTargetArtist,
        targetDurationSec = targetDurationSec
    )

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

    internal fun buildCacheKey(cleanTitle: String, cleanArtist: String, durationMs: Long = 0L): String =
        LyricsMatcher.buildCacheKey(cleanTitle, cleanArtist, durationMs)

    internal fun normalizeForMatch(raw: String): String =
        LyricsMatcher.normalizeForMatch(raw)
}
