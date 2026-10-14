package com.example.ytmusic2.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.ytmusic2.MainActivity
import com.example.ytmusic2.util.FileUtils
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class SongDownloader(private val context: Context) {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ytm_download_channel"
        const val DOWNLOAD_NOTIFICATION_ID = 202
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    var isBusy: Boolean = false
        private set

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
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "").lowercase().trim()

        return existingFiles.firstOrNull { f ->
            val fName = f.nameWithoutExtension.lowercase().trim()
            fName == cleanTitle ||
                (cleanTitle.length >= 4 && fName.contains(cleanTitle)) ||
                (fName.length >= 4 && cleanTitle.contains(fName))
        }
    }

    /**
     * Mengunduh lagu dari YouTube Music menggunakan yt-dlp dan FFmpeg.
     */
    fun downloadSong(
        videoId: String,
        title: String,
        forceDownload: Boolean = false,
        onStart: ((displayTitle: String) -> Unit)? = null,
        onDuplicateFound: ((existingFile: File) -> Unit)? = null,
        onSuccess: ((displayTitle: String) -> Unit)? = null,
        onError: ((displayTitle: String, error: String) -> Unit)? = null
    ) {
        if (isBusy) {
            onError?.invoke(title, "Unduhan sedang berjalan, harap tunggu")
            return
        }

        // Cek duplikat jika tidak dipaksa unduh ulang
        if (!forceDownload && title.isNotBlank()) {
            val duplicate = findDuplicateFile(title)
            if (duplicate != null) {
                onDuplicateFound?.invoke(duplicate)
                return
            }
        }

        val dir = FileUtils.getDownloadDir()
        val displayTitle = if (title.isNotBlank()) title else "Lagu ($videoId)"

        isBusy = true
        onStart?.invoke(displayTitle)

        updateDownloadNotification(
            title = "Mengunduh Lagu",
            text = "$displayTitle (Menyiapkan...)",
            progress = -1,
            ongoing = true
        )

        val request = YoutubeDLRequest("https://music.youtube.com/watch?v=$videoId").apply {
            addOption("-f", "ba")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--embed-thumbnail")
            addOption("--add-metadata")
            addOption("--no-playlist")
            addOption("-o", dir.absolutePath + "/%(title)s.%(ext)s")
        }

        thread {
            try {
                var lastProgress = -1
                val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                    val p = progress.toInt()
                    if (p != lastProgress) {
                        lastProgress = p
                        val etaText = if (etaInSeconds > 0) " (sisa ${etaInSeconds}s)" else ""
                        updateDownloadNotification(
                            title = "Mengunduh: $displayTitle",
                            text = if (p > 0) "$p%$etaText" else "Memproses audio...",
                            progress = p,
                            ongoing = true
                        )
                    }
                }

                val output = response.out
                val exitCode = response.exitCode

                mainHandler.post {
                    isBusy = false
                    if (exitCode == 0) {
                        updateDownloadNotification(
                            title = "Unduhan Selesai",
                            text = "$displayTitle tersimpan di offline",
                            isDone = true
                        )
                        onSuccess?.invoke(displayTitle)
                    } else {
                        val errMsg = output?.lines()?.lastOrNull { it.isNotBlank() } ?: "Gagal memproses audio"
                        updateDownloadNotification(
                            title = "Unduh Gagal (Kode $exitCode)",
                            text = errMsg,
                            ongoing = false
                        )
                        onError?.invoke(displayTitle, "Unduh gagal (kode $exitCode): $errMsg")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isBusy = false
                    val errorMsg = e.message ?: "Kesalahan tidak diketahui"
                    updateDownloadNotification(
                        title = "Unduh Gagal",
                        text = errorMsg,
                        ongoing = false
                    )
                    onError?.invoke(displayTitle, errorMsg)
                }
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
