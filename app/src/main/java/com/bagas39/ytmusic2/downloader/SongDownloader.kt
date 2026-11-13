package com.bagas39.ytmusic2.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bagas39.ytmusic2.MainActivity
import com.bagas39.ytmusic2.lyrics.LyricsRepository
import com.bagas39.ytmusic2.player.AudioQuality
import com.bagas39.ytmusic2.util.FileUtils
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

class SongDownloader(private val context: Context) {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ytm_download_channel"
        const val DOWNLOAD_NOTIFICATION_ID = 202
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = ConcurrentLinkedQueue<DownloadTask>()

    @Volatile
    var isBusy: Boolean = false
        private set

    @Volatile
    private var activeVideoId: String? = null

    @Volatile
    var audioQuality: AudioQuality = AudioQuality.AUTO

    val queueSize: Int
        get() = queue.size

    init {
        createDownloadNotificationChannel()
    }

    fun init(onInitFailed: ((String) -> Unit)? = null) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            // Update yt-dlp binary di thread terpisah (binary bundle lama >60 hari sering ditolak YouTube)
            thread {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            mainHandler.post {
                onInitFailed?.invoke(e.message ?: "Gagal inisialisasi yt-dlp")
            }
        }
    }

    /**
     * Memeriksa apakah file audio dengan judul serupa sudah pernah diunduh.
     */
    fun findDuplicateFile(title: String): File? {
        if (title.isBlank()) return null
        val existingFiles = FileUtils.getOfflineAudioFiles()

        fun normalize(raw: String): String {
            return raw.lowercase()
                .replace(Regex("[\\\\/:*?\"<>|\u29f8\uff0f\uff1a\uff0a\uff1f\uff02\uff1c\uff1e\uff5c_\\-]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun stripSuffix(raw: String): String {
            val stripped = raw.replace(
                Regex("[(\\[](?:official|lyric|lyrics|audio|video|visualizer|music video|mv|hd|hq|remastered)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE),
                ""
            )
            return normalize(stripped)
        }

        val normTitle = normalize(title)
        val coreTitle = stripSuffix(title)
        if (normTitle.isEmpty()) return null

        return existingFiles.firstOrNull { f ->
            val rawName = f.nameWithoutExtension
            val normFile = normalize(rawName)
            val coreFile = stripSuffix(rawName)
            normFile == normTitle || (coreTitle.isNotEmpty() && coreFile == coreTitle)
        }
    }

    /**
     * Menambahkan permintaan unduhan lagu ke dalam antrean (FIFO).
     */
    fun downloadSong(
        videoId: String,
        title: String,
        artist: String = "",
        forceDownload: Boolean = false,
        onStart: ((displayTitle: String) -> Unit)? = null,
        onDuplicateFound: ((existingFile: File) -> Unit)? = null,
        onSuccess: ((displayTitle: String) -> Unit)? = null,
        onPartialSuccess: ((displayTitle: String, savedFileName: String, warning: String) -> Unit)? = null,
        onError: ((displayTitle: String, error: String) -> Unit)? = null,
        onQueued: ((displayTitle: String, queuePosition: Int) -> Unit)? = null
    ) {
        val displayTitle = if (title.isNotBlank()) title else "Lagu ($videoId)"

        // Cek duplikat jika tidak dipaksa unduh ulang
        if (!forceDownload && title.isNotBlank()) {
            val duplicate = findDuplicateFile(title)
            if (duplicate != null) {
                onDuplicateFound?.invoke(duplicate)
                return
            }
        }

        val task = DownloadTask(
            videoId = videoId,
            title = title,
            artist = artist,
            forceDownload = forceDownload,
            onStart = onStart,
            onDuplicateFound = onDuplicateFound,
            onSuccess = onSuccess,
            onPartialSuccess = onPartialSuccess,
            onError = onError
        )

        synchronized(this) {
            // Cek apakah item yang sama sedang aktif diunduh atau sudah ada di antrean
            if (activeVideoId == videoId) {
                onError?.invoke(displayTitle, "Lagu ini sedang dalam proses pengunduhan")
                return
            }
            if (queue.any { it.videoId == videoId }) {
                onError?.invoke(displayTitle, "Lagu sudah ada di antrean unduhan")
                return
            }

            if (isBusy) {
                queue.add(task)
                val position = queue.size
                onQueued?.invoke(displayTitle, position)
                return
            } else {
                isBusy = true
                activeVideoId = task.videoId
                processTask(task)
            }
        }
    }

    private fun processNextInQueue() {
        synchronized(this) {
            val nextTask = queue.poll()
            if (nextTask != null) {
                isBusy = true
                activeVideoId = nextTask.videoId
                processTask(nextTask)
            } else {
                isBusy = false
                activeVideoId = null
            }
        }
    }

    private fun processTask(task: DownloadTask) {
        val dir = FileUtils.getDownloadDir()
        val displayTitle = if (task.title.isNotBlank()) task.title else "Lagu (${task.videoId})"

        mainHandler.post {
            task.onStart?.invoke(displayTitle)
        }

        val remainingText = if (queue.isNotEmpty()) " [Antrean: ${queue.size}]" else ""
        updateDownloadNotification(
            title = "Mengunduh Lagu",
            text = "$displayTitle (Menyiapkan...)$remainingText",
            progress = -1,
            ongoing = true
        )

        val taskStartMs = System.currentTimeMillis()
        val request = YoutubeDLRequest("https://music.youtube.com/watch?v=${task.videoId}").apply {
            val formatSpec = if (audioQuality == AudioQuality.LOW) "ba[abr<=96]/ba" else "ba"
            addOption("-f", formatSpec)
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", audioQuality.ytDlpQuality)
            addOption("--write-thumbnail")
            addOption("--embed-thumbnail")
            addOption("--add-metadata")
            addOption("--no-playlist")
            addOption("-o", dir.absolutePath + "/%(title)s.%(ext)s")
        }

        thread {
            var exitCode = -1
            var output: String? = null
            var errorMessage: String? = null

            try {
                var lastProgress = -1
                val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                    val p = progress.toInt()
                    if (p != lastProgress) {
                        lastProgress = p
                        val etaText = if (etaInSeconds > 0) " (sisa ${etaInSeconds}s)" else ""
                        val queueStatus = if (queue.isNotEmpty()) " [Antrean: ${queue.size}]" else ""
                        updateDownloadNotification(
                            title = "Mengunduh: $displayTitle",
                            text = if (p > 0) "$p%$etaText$queueStatus" else "Memproses audio...$queueStatus",
                            progress = p,
                            ongoing = true
                        )
                    }
                }
                output = response.out
                exitCode = response.exitCode
            } catch (e: Exception) {
                errorMessage = e.message ?: "Kesalahan tidak diketahui"
            }

            // Pindahkan file gambar thumbnail yang tertinggal di Downloads/ ke Covers/
            try {
                FileUtils.moveStrayThumbnailsToCoversDir()
            } catch (_: Exception) {}

            val recentAudio = FileUtils.getOfflineAudioFiles().firstOrNull { f ->
                f.lastModified() >= taskStartMs - 5_000L
            }
            val downloadedAudio = recentAudio ?: if (exitCode == 0) findDuplicateFile(task.title) else null
            val resolvedTitle = task.title.ifBlank { downloadedAudio?.nameWithoutExtension.orEmpty() }
            val finalDisplayTitle = resolvedTitle.ifBlank { displayTitle }
            val fullErrMsg = errorMessage
                ?: output?.lines()?.lastOrNull { it.isNotBlank() }
                ?: "Gagal memproses konversi audio (FFmpeg)"
            val compactErrMsg = fullErrMsg.lines()
                .lastOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(160)
                ?: fullErrMsg

            if (exitCode == 0 || recentAudio != null) {
                try {
                    LyricsRepository.downloadAndSaveLrcForOfflineSong(
                        title = resolvedTitle,
                        artist = task.artist,
                        targetDir = FileUtils.getLyricsDir(),
                        mp3File = downloadedAudio
                    )
                } catch (_: Exception) {}
            }

            mainHandler.post {
                when {
                    exitCode == 0 -> {
                        updateDownloadNotification(
                            title = "Unduhan Selesai",
                            text = "$finalDisplayTitle tersimpan di offline",
                            isDone = true
                        )
                        task.onSuccess?.invoke(finalDisplayTitle)
                    }
                    recentAudio != null -> {
                        Log.e("SongDownloader", "Post-processing/FFmpeg gagal untuk $finalDisplayTitle (exitCode=$exitCode): $fullErrMsg")
                        updateDownloadNotification(
                            title = "Tersimpan (${recentAudio.extension.uppercase()}) - Konversi MP3 Gagal",
                            text = "$finalDisplayTitle tersimpan tanpa konversi MP3: $compactErrMsg",
                            isDone = true
                        )
                        val partialCallback = task.onPartialSuccess
                        if (partialCallback != null) {
                            partialCallback.invoke(finalDisplayTitle, recentAudio.name, compactErrMsg)
                        } else {
                            task.onSuccess?.invoke(finalDisplayTitle)
                        }
                    }
                    else -> {
                        Log.e("SongDownloader", "Unduh gagal untuk $displayTitle (exitCode=$exitCode): $fullErrMsg")
                        val failTitle = if (exitCode != -1) "Unduh Gagal (Kode $exitCode)" else "Unduh Gagal"
                        updateDownloadNotification(
                            title = failTitle,
                            text = compactErrMsg,
                            ongoing = false
                        )
                        val callbackMsg = if (exitCode != -1) "Unduh gagal (kode $exitCode): $compactErrMsg" else compactErrMsg
                        task.onError?.invoke(displayTitle, callbackMsg)
                    }
                }
                processNextInQueue()
            }
        }
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Status Unduhan Lagu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progres dan status unduhan file lagu"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateDownloadNotification(
        title: String,
        text: String,
        progress: Int = -1,
        ongoing: Boolean = false,
        isDone: Boolean = false
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)

        if (isDone) {
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
            val contentIntent = PendingIntent.getActivity(
                context,
                4,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(contentIntent)
        } else {
            builder.setSmallIcon(android.R.drawable.stat_sys_download)
        }

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else if (ongoing && progress < 0) {
            builder.setProgress(100, 0, true)
        } else {
            builder.setProgress(0, 0, false)
        }

        manager.notify(DOWNLOAD_NOTIFICATION_ID, builder.build())
    }
}
