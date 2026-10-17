package com.example.ytmusic2.coordinator

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ytmusic2.KeepAliveWebView
import com.example.ytmusic2.MusicBackgroundService
import com.example.ytmusic2.R
import com.example.ytmusic2.downloader.SongDownloader
import com.example.ytmusic2.player.OfflinePlayerManager
import com.example.ytmusic2.ui.dialog.AppDialogManager
import com.example.ytmusic2.ui.view.PlayerViewController
import com.example.ytmusic2.util.FileUtils
import com.example.ytmusic2.util.PermissionHelper
import com.example.ytmusic2.webview.YtmWebManager
import java.io.File

/**
 * AppCoordinator bertindak sebagai orchestrator utama aplikasi.
 * Menghubungkan dan mengoordinasikan interaksi antara:
 * - Activity (lifecycle, permission, thread handling)
 * - [PlayerViewController] (UI player & event listeners)
 * - [AppDialogManager] (Dialog konfirmasi & aksi file)
 * - [YtmWebManager] (Webview YouTube Music & ad-bypass)
 * - [OfflinePlayerManager] (Pemutaran audio lokal & seek tracking)
 * - [SongDownloader] (yt-dlp download pipeline)
 * - [MusicBackgroundService] (Foreground service & MediaStyle notification)
 */
class AppCoordinator(private val activity: AppCompatActivity) {

    private lateinit var webManager: YtmWebManager
    private lateinit var offlinePlayer: OfflinePlayerManager
    private lateinit var downloader: SongDownloader
    private lateinit var viewController: PlayerViewController
    private lateinit var dialogManager: AppDialogManager

    private var currentOnlineTitle: String = ""

    fun onCreate() {
        // 1. Cek & minta izin notifikasi serta optimasi baterai, lalu jalankan Foreground Service
        PermissionHelper.checkAndRequestNotificationPermission(activity)
        PermissionHelper.requestIgnoreBatteryOptimization(activity)
        startBackgroundMusicService()

        // 2. Inisialisasi komponen UI & Dialog
        viewController = PlayerViewController(activity)
        dialogManager = AppDialogManager(activity)

        // 3. Inisialisasi modul logika & domain
        initModules()

        // 4. Hubungkan event UI dari ViewController ke orkestrator
        setupViewControllerEvents()

        // 5. Muat halaman beranda YouTube Music
        webManager.loadHome()
    }

    private fun startBackgroundMusicService() {
        val serviceIntent = Intent(activity, MusicBackgroundService::class.java)
        ContextCompat.startForegroundService(activity, serviceIntent)

        // Hoisting kontrol dari status bar notifikasi (Prev / Play-Pause / Next)
        MusicBackgroundService.controlCallback = { action ->
            activity.runOnUiThread {
                when (action) {
                    MusicBackgroundService.ACTION_PREV -> handlePrevious()
                    MusicBackgroundService.ACTION_PLAY_PAUSE -> handlePlayPause()
                    MusicBackgroundService.ACTION_NEXT -> handleNext()
                }
            }
        }
    }

    private fun initModules() {
        // Downloader
        downloader = SongDownloader(activity).apply {
            init(onInitFailed = { error ->
                Toast.makeText(activity, "Gagal init yt-dlp: $error", Toast.LENGTH_LONG).show()
            })
        }

        // Web Manager
        val webView = activity.findViewById<KeepAliveWebView>(R.id.webViewYoutubeMusic)
        webManager = YtmWebManager(webView).apply {
            onTitleDetected = { title ->
                currentOnlineTitle = title
            }
            onSongStateChanged = { metadata ->
                currentOnlineTitle = metadata.title
                // Jika sedang mode offline dan user memutar lagu di webview YTM:
                if (offlinePlayer.isOfflineActive) {
                    if (metadata.isPlaying) {
                        offlinePlayer.stop()
                        updatePlaybackNotification(metadata.title, metadata.artist, metadata.isPlaying)
                    }
                } else {
                    updatePlaybackNotification(metadata.title, metadata.artist, metadata.isPlaying)
                }
            }
        }

        // Offline Player Manager
        offlinePlayer = OfflinePlayerManager().apply {
            onTrackPrepared = { file, index, total, durationMs ->
                viewController.showOfflinePlayer(file.nameWithoutExtension, durationMs)
                updatePlaybackNotification(file.nameWithoutExtension, "Offline (${index + 1}/$total)", true)
            }
            onPlaybackStateChanged = { isPlaying ->
                viewController.updatePlayPauseButton(isPlaying)
                val song = offlinePlayer.currentSong
                if (song != null) {
                    val artistText = if (offlinePlayer.playlistSize > 0 && offlinePlayer.currentIndex >= 0) {
                        "Offline (${offlinePlayer.currentIndex + 1}/${offlinePlayer.playlistSize})"
                    } else {
                        "Lagu Offline"
                    }
                    updatePlaybackNotification(song.nameWithoutExtension, artistText, isPlaying)
                }
            }
            onProgressTick = { currentMs, _ ->
                viewController.updateProgress(currentMs)
            }
            onPlayerStopped = {
                viewController.hideOfflinePlayer()
                val title = currentOnlineTitle.ifBlank { "YouTube Music" }
                val artist = if (currentOnlineTitle.isNotBlank()) "Siap memutar" else "Tidak ada lagu yang diputar"
                updatePlaybackNotification(title, artist, false)
                webManager.captureCurrentSong()
            }
            onError = { msg ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViewControllerEvents() {
        viewController.onPreviousClicked = { handlePrevious() }
        viewController.onPlayPauseClicked = { handlePlayPause() }
        viewController.onNextClicked = { handleNext() }
        viewController.onDownloadClicked = { startDownloadProcess() }
        viewController.onOfflineClicked = { showOfflineFiles() }
        viewController.onOfflineCloseClicked = { offlinePlayer.stop() }
        viewController.onSeekTrackingChanged = { isTracking ->
            offlinePlayer.setUserTrackingSeekBar(isTracking)
        }
        viewController.onSeekTo = { progressMs ->
            offlinePlayer.seekTo(progressMs)
        }
    }

    // ==========================================
    // Event Handlers (Navigasi Player & Download)
    // ==========================================

    fun handlePrevious() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.playPrevious()
        } else {
            webManager.clickPrevious()
        }
    }

    fun handlePlayPause() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.togglePlayPause()
        } else {
            webManager.clickPlayPause()
        }
    }

    fun handleNext() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.playNext()
        } else {
            webManager.clickNext()
        }
    }

    fun startDownloadProcess(forceDownload: Boolean = false) {
        if (!PermissionHelper.hasStoragePermission(activity)) {
            PermissionHelper.requestStoragePermission(activity)
            return
        }

        val videoId = webManager.getCurrentVideoId()
        if (videoId == null) {
            Toast.makeText(activity, "Putar lagu dulu, lalu tekan Unduh", Toast.LENGTH_SHORT).show()
            return
        }

        downloader.downloadSong(
            videoId = videoId,
            title = currentOnlineTitle,
            forceDownload = forceDownload,
            onStart = { displayTitle ->
                Toast.makeText(activity, "Mulai mengunduh: $displayTitle", Toast.LENGTH_SHORT).show()
            },
            onDuplicateFound = { existingFile ->
                dialogManager.showDuplicateConfirmDialog(
                    existingFile = existingFile,
                    onRedownload = { startDownloadProcess(forceDownload = true) },
                    onPlayOffline = {
                        webManager.pausePlayback()
                        offlinePlayer.playSong(existingFile)
                    }
                )
            },
            onSuccess = { displayTitle ->
                Toast.makeText(activity, "Selesai unduh: $displayTitle", Toast.LENGTH_LONG).show()
                offlinePlayer.setPlaylist(FileUtils.getOfflineAudioFiles())
            },
            onError = { _, error ->
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    // ==========================================
    // Dialog & Manajemen File Offline
    // ==========================================

    fun showOfflineFiles() {
        val files = FileUtils.getOfflineAudioFiles()
        offlinePlayer.setPlaylist(files)

        dialogManager.showOfflineFilesDialog(
            files = files,
            storagePath = FileUtils.getDownloadDir().absolutePath,
            onSelectFileIndex = { which ->
                showFileActions(which, files)
            },
            onOpenFolder = {
                FileUtils.openDownloadFolder(activity)
            }
        )
    }

    private fun showFileActions(index: Int, files: List<File>) {
        if (index !in files.indices) return
        val file = files[index]

        dialogManager.showFileActionDialog(
            file = file,
            onPlay = {
                webManager.pausePlayback()
                offlinePlayer.playSongAtIndex(index, files)
                Toast.makeText(activity, "Memutar: ${file.name}", Toast.LENGTH_SHORT).show()
            },
            onOpenWith = {
                FileUtils.openFileWithExternalApp(activity, file)
            },
            onShare = {
                FileUtils.shareFile(activity, file)
            },
            onDelete = {
                confirmDelete(file)
            }
        )
    }

    private fun confirmDelete(file: File) {
        dialogManager.showDeleteConfirmDialog(file) {
            if (offlinePlayer.currentSong?.absolutePath == file.absolutePath) {
                offlinePlayer.stop()
            }
            if (file.delete()) {
                Toast.makeText(activity, "File dihapus", Toast.LENGTH_SHORT).show()
                showOfflineFiles()
            } else {
                Toast.makeText(activity, "Gagal menghapus file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var lastNotifTitle: String? = null
    private var lastNotifArtist: String? = null
    private var lastNotifPlaying: Boolean? = null

    private fun updatePlaybackNotification(title: String, artist: String, isPlaying: Boolean) {
        if (title == lastNotifTitle && artist == lastNotifArtist && isPlaying == lastNotifPlaying) {
            return
        }
        lastNotifTitle = title
        lastNotifArtist = artist
        lastNotifPlaying = isPlaying

        val serviceIntent = Intent(activity, MusicBackgroundService::class.java).apply {
            action = MusicBackgroundService.ACTION_UPDATE_SONG
            putExtra(MusicBackgroundService.EXTRA_TITLE, title)
            putExtra(MusicBackgroundService.EXTRA_ARTIST, artist)
            putExtra(MusicBackgroundService.EXTRA_IS_PLAYING, isPlaying)
        }
        try {
            ContextCompat.startForegroundService(activity, serviceIntent)
        } catch (_: Exception) {}
    }

    // ==========================================
    // Lifecycle & Back Handlers
    // ==========================================

    fun onPause() {
        webManager.injectBypassScript()
    }

    fun onStop() {
        webManager.injectBypassScript()
    }

    fun onDestroy() {
        MusicBackgroundService.controlCallback = null
        offlinePlayer.release()
        lastNotifTitle = null
        lastNotifArtist = null
        lastNotifPlaying = null
    }

    fun handleBackPressed(): Boolean {
        if (offlinePlayer.isOfflineActive || viewController.isOfflinePlayerVisible) {
            offlinePlayer.stop()
            viewController.hideOfflinePlayer()
            return true
        }
        if (webManager.canGoBack()) {
            webManager.goBack()
            return true
        }
        return false
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PermissionHelper.STORAGE_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownloadProcess()
            } else {
                Toast.makeText(activity, "Izin penyimpanan ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
