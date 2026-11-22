package com.bagas39.ytmusic2.coordinator

import android.content.Intent
import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bagas39.ytmusic2.KeepAliveWebView
import com.bagas39.ytmusic2.MusicBackgroundService
import com.bagas39.ytmusic2.R
import com.bagas39.ytmusic2.audiofx.AudioFxManager
import com.bagas39.ytmusic2.audiofx.AudioFxState
import com.bagas39.ytmusic2.audiofx.EqualizerPreset
import com.bagas39.ytmusic2.audiofx.SpeedPreset
import com.bagas39.ytmusic2.downloader.SongDownloader
import com.bagas39.ytmusic2.lyrics.LyricsRepository
import com.bagas39.ytmusic2.player.AudioQuality
import com.bagas39.ytmusic2.player.OfflinePlayerManager
import com.bagas39.ytmusic2.webview.StreamMediaOptimizer
import com.bagas39.ytmusic2.ui.dialog.AppDialogManager
import com.bagas39.ytmusic2.ui.view.LyricsUiState
import com.bagas39.ytmusic2.ui.view.PlayerViewController
import com.bagas39.ytmusic2.util.FileUtils
import com.bagas39.ytmusic2.util.FormatUtils
import com.bagas39.ytmusic2.util.PermissionHelper
import com.bagas39.ytmusic2.webview.YtmWebManager
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
    private lateinit var adSkipObserver: com.bagas39.ytmusic2.webview.AdSkipObserver
    private lateinit var streamOptimizer: StreamMediaOptimizer
    private lateinit var audioFxManager: AudioFxManager
    private lateinit var offlinePlayer: OfflinePlayerManager
    private lateinit var downloader: SongDownloader
    private lateinit var viewController: PlayerViewController
    private lateinit var dialogManager: AppDialogManager

    private var currentOnlineTitle: String = ""
    private var currentOnlineArtist: String = ""
    private var currentOnlineIsPlaying: Boolean = false
    private var currentOnlinePositionMs: Long = 0L
    private var currentOnlineDurationMs: Long = 0L
    private var sleepTimer: CountDownTimer? = null
    private var sleepTimerRemainingMs: Long = 0L
    private var lastLoadedLyricsKey: String = ""
    private var lastLoadedLyricsDurationSec: Long = 0L
    private var lyricsRequestSeq: Long = 0L

    /**
     * Listener posisi detik lagu real-time untuk sinkronisasi lirik berjalan (karaoke mode)
     * dan seek tracking terpadu baik saat Online (WebView) maupun Offline (MediaPlayer).
     */
    var onPlaybackProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null

    val currentPositionMs: Long
        get() = if (::offlinePlayer.isInitialized && offlinePlayer.isOfflineActive) {
            offlinePlayer.currentPositionMs
        } else {
            currentOnlinePositionMs
        }

    val currentDurationMs: Long
        get() = if (::offlinePlayer.isInitialized && offlinePlayer.isOfflineActive) {
            offlinePlayer.currentDurationMs
        } else {
            currentOnlineDurationMs
        }

    fun seekTo(positionMs: Long) {
        handleSeekTo(positionMs)
    }

    fun onCreate() {
        PermissionHelper.checkAndRequestNotificationPermission(activity)
        PermissionHelper.requestIgnoreBatteryOptimization(activity)
        startBackgroundMusicService()

        viewController = PlayerViewController(activity)
        dialogManager = AppDialogManager(activity)

        val initialPrefs = activity.getSharedPreferences("ytm_prefs", android.content.Context.MODE_PRIVATE)
        val savedAdsTotal = initialPrefs.getInt("total_ads_skipped", 0)
        var savedSecondsTotal = initialPrefs.getInt("total_ad_seconds_saved", 0)
        if (savedSecondsTotal == 0 && savedAdsTotal > 0) {
            savedSecondsTotal = savedAdsTotal * 15
            initialPrefs.edit().putInt("total_ad_seconds_saved", savedSecondsTotal).apply()
        }
        viewController.setInitialAdStats(savedAdsTotal, savedSecondsTotal)

        val savedDataSaver = initialPrefs.getBoolean("data_saver_enabled", false)
        viewController.setDataSaverEnabled(savedDataSaver)

        val savedQualityName = initialPrefs.getString("audio_quality", AudioQuality.AUTO.name)
        val savedQuality = AudioQuality.fromName(savedQualityName)
        viewController.setAudioQuality(savedQuality)

        initModules(savedDataSaver, savedQuality)
        webManager.setDataSaverEnabled(savedDataSaver)

        // Rapikan file thumbnail lama dari Downloads/ ke Covers/ di background
        kotlin.concurrent.thread(name = "ytm-cover-cleanup") {
            try {
                if (PermissionHelper.hasStoragePermission(activity)) {
                    FileUtils.moveStrayThumbnailsToCoversDir()
                }
            } catch (_: Exception) {}
        }

        setupViewControllerEvents()
        webManager.loadHome()
    }

    private fun startBackgroundMusicService() {
        val serviceIntent = Intent(activity, MusicBackgroundService::class.java)
        ContextCompat.startForegroundService(activity, serviceIntent)

        // Hoisting kontrol dari MediaSession & status bar notifikasi
        MusicBackgroundService.controlCallback = { action ->
            activity.runOnUiThread {
                when (action) {
                    MusicBackgroundService.ACTION_PREV -> handlePrevious()
                    MusicBackgroundService.ACTION_PLAY -> handlePlay()
                    MusicBackgroundService.ACTION_PLAY_PAUSE -> handlePlayPause()
                    MusicBackgroundService.ACTION_PAUSE -> handlePause()
                    MusicBackgroundService.ACTION_NEXT -> handleNext()
                }
            }
        }
        MusicBackgroundService.seekCallback = { positionMs ->
            activity.runOnUiThread {
                handleSeekTo(positionMs)
            }
        }
    }

    private fun initModules(initialDataSaver: Boolean, initialQuality: AudioQuality) {
        // Downloader
        downloader = SongDownloader(activity).apply {
            audioQuality = initialQuality
            init(onInitFailed = { error ->
                Toast.makeText(activity, "Gagal init yt-dlp: $error", Toast.LENGTH_LONG).show()
            })
        }

        // Web Manager & Ad Skip Observer (Terisolasi dari bypass engine)
        val webView = activity.findViewById<KeepAliveWebView>(R.id.webViewYoutubeMusic)
        audioFxManager = AudioFxManager(activity, webView)
        streamOptimizer = StreamMediaOptimizer(webView).apply {
            setDataSaverEnabled(initialDataSaver)
            setAudioQuality(initialQuality)
        }
        webView.onWindowFocusChange = { hasFocus ->
            streamOptimizer.setWindowFocused(hasFocus)
        }
        adSkipObserver = com.bagas39.ytmusic2.webview.AdSkipObserver(webView).apply {
            onAdsSkipped = { count, durationSeconds ->
                val prefs = activity.getSharedPreferences("ytm_prefs", android.content.Context.MODE_PRIVATE)
                val updatedTotal = prefs.getInt("total_ads_skipped", 0) + count
                val updatedSeconds = prefs.getInt("total_ad_seconds_saved", 0) + durationSeconds
                prefs.edit()
                    .putInt("total_ads_skipped", updatedTotal)
                    .putInt("total_ad_seconds_saved", updatedSeconds)
                    .apply()
                activity.runOnUiThread {
                    viewController.showAdSkippedPopup(count, durationSeconds, updatedTotal, updatedSeconds)
                }
            }
        }

        webManager = YtmWebManager(webView).apply {
            onPageLifecycleInject = {
                adSkipObserver.injectObserverScript()
                streamOptimizer.injectOptimizerScript()
                audioFxManager.injectWebAudioFx()
            }
            onTitleDetected = { title ->
                activity.runOnUiThread {
                    currentOnlineTitle = title
                    if (!offlinePlayer.isOfflineActive) {
                        syncLyricsMetaToView()
                    }
                    streamOptimizer.updateTrackTitle(title)
                }
            }
            onSongStateChanged = { metadata ->
                activity.runOnUiThread {
                    val trackChanged = currentOnlineTitle != metadata.title || currentOnlineArtist != metadata.artist
                    currentOnlineTitle = metadata.title
                    currentOnlineArtist = metadata.artist
                    currentOnlineIsPlaying = metadata.isPlaying
                    currentOnlinePositionMs = metadata.positionMs
                    currentOnlineDurationMs = metadata.durationMs
                    streamOptimizer.updateTrackTitle(metadata.title)

                    if (!offlinePlayer.isOfflineActive) {
                        syncLyricsMetaToView()
                        val shouldRefreshLyrics = viewController.isLyricsSheetVisible &&
                            (trackChanged || (metadata.durationMs > 0L && lastLoadedLyricsDurationSec == 0L))
                        if (shouldRefreshLyrics) {
                            loadLyricsForCurrentTrack(forceRefresh = false)
                        }
                        onPlaybackProgress?.invoke(metadata.positionMs, metadata.durationMs)
                        updatePlaybackNotification(
                            title = metadata.title,
                            artist = metadata.artist,
                            isPlaying = metadata.isPlaying,
                            durationMs = metadata.durationMs,
                            positionMs = metadata.positionMs,
                            artworkSource = metadata.artworkUrl,
                            forceUpdate = true
                        )
                    } else if (metadata.isPlaying) {
                        offlinePlayer.stop()
                        syncLyricsMetaToView()
                        if (viewController.isLyricsSheetVisible) {
                            loadLyricsForCurrentTrack(forceRefresh = false)
                        }
                        updatePlaybackNotification(
                            title = metadata.title,
                            artist = metadata.artist,
                            isPlaying = metadata.isPlaying,
                            durationMs = metadata.durationMs,
                            positionMs = metadata.positionMs,
                            artworkSource = metadata.artworkUrl,
                            forceUpdate = true
                        )
                    }
                }
            }
            onPlaybackProgressChanged = { posMs, durMs ->
                activity.runOnUiThread {
                    currentOnlinePositionMs = posMs
                    currentOnlineDurationMs = durMs
                    if (!offlinePlayer.isOfflineActive) {
                        viewController.updateLyricsPlaybackProgress(posMs, durMs)
                        onPlaybackProgress?.invoke(posMs, durMs)
                        if (durMs > 0L && viewController.isLyricsSheetVisible &&
                            currentOnlineTitle.isNotBlank() && lastLoadedLyricsDurationSec == 0L
                        ) {
                            loadLyricsForCurrentTrack(forceRefresh = false)
                        }
                    }
                }
            }
        }

        // Offline Player Manager
        offlinePlayer = OfflinePlayerManager().apply {
            onMediaPlayerPrepared = { mp ->
                audioFxManager.attachToMediaPlayer(mp)
            }
            onTrackPrepared = { file, index, total, durationMs ->
                viewController.showOfflinePlayer(file.nameWithoutExtension, durationMs)
                viewController.setOfflineHasLocalLyrics(LyricsRepository.hasLocalLyrics(file))
                syncLyricsMetaToView()
                if (viewController.isLyricsSheetVisible) {
                    loadLyricsForCurrentTrack(forceRefresh = false)
                }
                updatePlaybackNotification(
                    title = file.nameWithoutExtension,
                    artist = "Offline (${index + 1}/$total)",
                    isPlaying = true,
                    durationMs = durationMs.toLong(),
                    positionMs = 0L,
                    artworkSource = file.absolutePath
                )
            }
            onPlaybackStateChanged = { isPlaying ->
                viewController.updatePlayPauseButton(isPlaying)
                syncLyricsMetaToView()
                val song = offlinePlayer.currentSong
                if (song != null) {
                    val artistText = if (offlinePlayer.playlistSize > 0 && offlinePlayer.currentIndex >= 0) {
                        "Offline (${offlinePlayer.currentIndex + 1}/${offlinePlayer.playlistSize})"
                    } else {
                        "Lagu Offline"
                    }
                    updatePlaybackNotification(
                        title = song.nameWithoutExtension,
                        artist = artistText,
                        isPlaying = isPlaying,
                        durationMs = offlinePlayer.currentDurationMs,
                        positionMs = offlinePlayer.currentPositionMs,
                        artworkSource = song.absolutePath
                    )
                }
            }
            onProgressTick = { currentMs, totalMs ->
                viewController.updateProgress(currentMs)
                if (offlinePlayer.isOfflineActive) {
                    viewController.updateLyricsPlaybackProgress(currentMs.toLong(), totalMs.toLong())
                    onPlaybackProgress?.invoke(currentMs.toLong(), totalMs.toLong())
                }
            }
            onPlayerStopped = {
                audioFxManager.detachOfflineEffects()
                viewController.hideOfflinePlayer()
                viewController.setOfflineHasLocalLyrics(false)
                syncLyricsMetaToView()
                if (viewController.isLyricsSheetVisible) {
                    lastLoadedLyricsKey = ""
                    lastLoadedLyricsDurationSec = 0L
                    loadLyricsForCurrentTrack(forceRefresh = false)
                }
                val title = currentOnlineTitle.ifBlank { "YouTube Music" }
                val artist = if (currentOnlineTitle.isNotBlank()) "Siap memutar" else "Tidak ada lagu yang diputar"
                updatePlaybackNotification(
                    title = title,
                    artist = artist,
                    isPlaying = false,
                    durationMs = 0L,
                    positionMs = 0L,
                    artworkSource = ""
                )
                webManager.captureCurrentSong()
            }
            onError = { msg ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
            onModesChanged = { isShuffle, repeatMode ->
                viewController.updatePlaybackModes(isShuffle, repeatMode)
            }
        }
    }

    private fun setupViewControllerEvents() {
        viewController.onPreviousClicked = { handlePrevious() }
        viewController.onPlayPauseClicked = { handlePlayPause() }
        viewController.onNextClicked = { handleNext() }
        viewController.onDownloadClicked = { startDownloadProcess() }
        viewController.onLyricsClicked = { openLyricsSheet() }
        viewController.onLyricsRetryClicked = { loadLyricsForCurrentTrack(forceRefresh = true) }
        viewController.onLyricsManualSearch = { queryTitle, queryArtist ->
            loadLyricsCustomQuery(queryTitle, queryArtist)
        }
        viewController.onLyricsSeekTo = { positionMs -> handleSeekTo(positionMs) }
        viewController.onOfflineClicked = { showOfflineFiles() }
        viewController.onSleepTimerClicked = { showSleepTimerOptions() }
        viewController.onOfflineCloseClicked = { offlinePlayer.stop() }
        viewController.onDataSaverToggled = { isEnabled ->
            val prefs = activity.getSharedPreferences("ytm_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("data_saver_enabled", isEnabled).apply()
            streamOptimizer.setDataSaverEnabled(isEnabled)
            webManager.setDataSaverEnabled(isEnabled)
            Toast.makeText(
                activity,
                if (isEnabled) "Mode Audio Saja Aktif (Video 144p)" else "Mode Audio Saja Dinonaktifkan",
                Toast.LENGTH_SHORT
            ).show()
        }
        viewController.onAudioQualityChanged = { quality ->
            val prefs = activity.getSharedPreferences("ytm_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("audio_quality", quality.name).apply()
            streamOptimizer.setAudioQuality(quality)
            downloader.audioQuality = quality
            Toast.makeText(
                activity,
                "Kualitas Audio: ${quality.displayName} (${quality.bitrateLabel})",
                Toast.LENGTH_SHORT
            ).show()
        }
        viewController.onShuffleClicked = {
            val enabled = offlinePlayer.toggleShuffle()
            val text = if (enabled) "Shuffle: Aktif" else "Shuffle: Nonaktif"
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        }
        viewController.onRepeatClicked = {
            val mode = offlinePlayer.cycleRepeatMode()
            val text = when (mode) {
                com.bagas39.ytmusic2.player.RepeatMode.OFF -> "Repeat: Nonaktif"
                com.bagas39.ytmusic2.player.RepeatMode.ALL -> "Repeat: Semua Lagu"
                com.bagas39.ytmusic2.player.RepeatMode.ONE -> "Repeat: Satu Lagu"
            }
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        }
        viewController.onSeekTrackingChanged = { isTracking ->
            offlinePlayer.setUserTrackingSeekBar(isTracking)
        }
        viewController.onSeekTo = { progressMs ->
            offlinePlayer.seekTo(progressMs)
            val song = offlinePlayer.currentSong
            if (song != null) {
                val artistText = if (offlinePlayer.playlistSize > 0 && offlinePlayer.currentIndex >= 0) {
                    "Offline (${offlinePlayer.currentIndex + 1}/${offlinePlayer.playlistSize})"
                } else {
                    "Lagu Offline"
                }
                updatePlaybackNotification(
                    title = song.nameWithoutExtension,
                    artist = artistText,
                    isPlaying = offlinePlayer.isPlaying,
                    durationMs = offlinePlayer.currentDurationMs,
                    positionMs = progressMs.toLong(),
                    artworkSource = song.absolutePath,
                    forceUpdate = true
                )
            }
        }
    }

    // Event handlers: navigasi player dan unduhan

    fun handlePrevious() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.playPrevious()
        } else {
            webManager.clickPrevious()
        }
    }

    fun handlePlay() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.resume()
        } else {
            webManager.resumePlayback()
        }
    }

    fun handlePlayPause() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.togglePlayPause()
        } else {
            webManager.clickPlayPause()
        }
    }

    fun handlePause() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.pause()
        } else {
            webManager.pausePlayback()
        }
    }

    fun handleNext() {
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.playNext()
        } else {
            webManager.clickNext()
        }
    }

    fun handleSeekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        if (offlinePlayer.isOfflineActive) {
            offlinePlayer.seekTo(safePos)
            viewController.updateProgress(safePos.toInt())
            viewController.updateLyricsPlaybackProgress(safePos, offlinePlayer.currentDurationMs)
            onPlaybackProgress?.invoke(safePos, offlinePlayer.currentDurationMs)
        } else {
            webManager.seekTo(safePos)
            currentOnlinePositionMs = safePos
            viewController.updateLyricsPlaybackProgress(safePos, currentOnlineDurationMs)
            onPlaybackProgress?.invoke(safePos, currentOnlineDurationMs)
        }
    }

    private fun syncLyricsMetaToView() {
        val offlineSong = if (offlinePlayer.isOfflineActive) offlinePlayer.currentSong else null
        if (offlineSong != null) {
            val (cleanTitle, cleanArtist) = LyricsRepository.sanitizeTitleAndArtist(
                offlineSong.nameWithoutExtension,
                ""
            )
            viewController.updateLyricsTrackMeta(
                title = cleanTitle.ifBlank { offlineSong.nameWithoutExtension },
                artist = cleanArtist,
                isPlaying = offlinePlayer.isPlaying,
                positionMs = offlinePlayer.currentPositionMs,
                durationMs = offlinePlayer.currentDurationMs
            )
        } else {
            viewController.updateLyricsTrackMeta(
                title = currentOnlineTitle,
                artist = currentOnlineArtist,
                isPlaying = currentOnlineIsPlaying,
                positionMs = currentOnlinePositionMs,
                durationMs = currentOnlineDurationMs
            )
        }
    }

    private fun openLyricsSheet() {
        syncLyricsMetaToView()
        viewController.showLyricsSheet()
        loadLyricsForCurrentTrack(forceRefresh = false)
    }

    private fun loadLyricsForCurrentTrack(forceRefresh: Boolean) {
        val offlineSong = if (offlinePlayer.isOfflineActive) offlinePlayer.currentSong else null
        val isOffline = offlineSong != null
        val rawTitle = if (isOffline) offlineSong!!.nameWithoutExtension else currentOnlineTitle
        val rawArtist = if (isOffline) "" else currentOnlineArtist
        val durationMs = if (isOffline) offlinePlayer.currentDurationMs else currentOnlineDurationMs
        val durationSec = if (durationMs > 0L) durationMs / 1000L else 0L

        if (rawTitle.isBlank()) {
            lastLoadedLyricsKey = ""
            lastLoadedLyricsDurationSec = 0L
            viewController.setLyricsUiState(LyricsUiState.Idle)
            return
        }

        val trackKey = "$isOffline|${offlineSong?.absolutePath.orEmpty()}|$rawTitle|$rawArtist"
        val isSameTrack = trackKey == lastLoadedLyricsKey
        val needsDurationUpgrade = !isOffline && isSameTrack && lastLoadedLyricsDurationSec == 0L && durationSec > 0L
        if (!forceRefresh && isSameTrack && !needsDurationUpgrade) {
            return
        }

        val (cleanTitle, cleanArtist) = LyricsRepository.sanitizeTitleAndArtist(rawTitle, rawArtist)
        val displayTitle = cleanTitle.ifBlank { rawTitle }
        val displayArtist = cleanArtist.ifBlank { rawArtist }
        val requestId = ++lyricsRequestSeq
        lastLoadedLyricsDurationSec = durationSec

        if (!needsDurationUpgrade) {
            viewController.setLyricsUiState(LyricsUiState.Loading(displayTitle, displayArtist))
        }

        LyricsRepository.getLyricsAsync(
            title = rawTitle,
            artist = rawArtist,
            durationMs = durationMs,
            offlineFile = offlineSong,
            ignoreLocalCache = forceRefresh || needsDurationUpgrade
        ) { result ->
            if (requestId != lyricsRequestSeq) return@getLyricsAsync
            if (result != null && result.lines.isNotEmpty()) {
                lastLoadedLyricsKey = trackKey
                lastLoadedLyricsDurationSec = durationSec
                val isSaved = offlineSong != null && LyricsRepository.hasLocalLyrics(offlineSong)
                if (offlineSong != null) {
                    viewController.setOfflineHasLocalLyrics(isSaved)
                }
                viewController.setLyricsUiState(
                    LyricsUiState.Ready(
                        result = result,
                        isOfflineMode = isOffline,
                        isSavedLocally = isSaved
                    )
                )
            } else if (!needsDurationUpgrade) {
                lastLoadedLyricsKey = ""
                lastLoadedLyricsDurationSec = 0L
                viewController.setLyricsUiState(
                    LyricsUiState.NotFound(
                        title = displayTitle,
                        artist = displayArtist,
                        reason = "Lirik tidak ditemukan di penyimpanan lokal maupun penyedia daring."
                    )
                )
            }
        }
    }

    private fun loadLyricsCustomQuery(queryTitle: String, queryArtist: String) {
        val trimmedTitle = queryTitle.trim()
        val trimmedArtist = queryArtist.trim()
        if (trimmedTitle.isBlank()) return

        val offlineSong = if (offlinePlayer.isOfflineActive) offlinePlayer.currentSong else null
        val isOffline = offlineSong != null
        val durationMs = currentDurationMs
        val requestId = ++lyricsRequestSeq

        viewController.setLyricsUiState(LyricsUiState.Loading(trimmedTitle, trimmedArtist))

        LyricsRepository.getLyricsAsync(
            title = trimmedTitle,
            artist = trimmedArtist,
            durationMs = durationMs,
            offlineFile = offlineSong,
            ignoreLocalCache = true
        ) { result ->
            if (requestId != lyricsRequestSeq) return@getLyricsAsync
            if (result != null && result.lines.isNotEmpty()) {
                val trackTitle = if (isOffline) offlineSong!!.nameWithoutExtension else currentOnlineTitle
                val trackArtist = if (isOffline) "" else currentOnlineArtist
                lastLoadedLyricsKey = "$isOffline|${offlineSong?.absolutePath.orEmpty()}|$trackTitle|$trackArtist"
                lastLoadedLyricsDurationSec = if (durationMs > 0L) durationMs / 1000L else 0L
                val isSaved = offlineSong != null && LyricsRepository.hasLocalLyrics(offlineSong)
                if (offlineSong != null) {
                    viewController.setOfflineHasLocalLyrics(isSaved)
                }
                viewController.setLyricsUiState(
                    LyricsUiState.Ready(
                        result = result,
                        isOfflineMode = isOffline,
                        isSavedLocally = isSaved
                    )
                )
            } else {
                lastLoadedLyricsKey = ""
                viewController.setLyricsUiState(
                    LyricsUiState.NotFound(
                        title = trimmedTitle,
                        artist = trimmedArtist,
                        reason = "Tidak ada kecocokan lirik untuk kata kunci tersebut."
                    )
                )
            }
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
            artist = currentOnlineArtist,
            forceDownload = forceDownload,
            onStart = { displayTitle ->
                Toast.makeText(activity, "Mulai mengunduh: $displayTitle", Toast.LENGTH_SHORT).show()
            },
            onQueued = { displayTitle, queuePosition ->
                Toast.makeText(activity, "Ditambahkan ke antrean (#$queuePosition): $displayTitle", Toast.LENGTH_SHORT).show()
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
            onPartialSuccess = { _, savedFileName, warning ->
                Toast.makeText(
                    activity,
                    "Lagu & lirik tersimpan ($savedFileName), namun konversi MP3 gagal: $warning",
                    Toast.LENGTH_LONG
                ).show()
                offlinePlayer.setPlaylist(FileUtils.getOfflineAudioFiles())
            },
            onError = { _, error ->
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    // Dialog dan manajemen file offline

    fun showOfflineFiles() {
        val files = FileUtils.getOfflineAudioFiles()
        offlinePlayer.setPlaylist(files)

        dialogManager.showOfflineFilesDialog(
            files = files,
            storagePath = FileUtils.getDownloadDir().absolutePath,
            hasLyricsPredicate = { file -> LyricsRepository.hasLocalLyrics(file) },
            onSelectFileIndex = { which ->
                showFileActions(which, files)
            },
            onBatchDeleteClicked = {
                dialogManager.showBatchDeleteDialog(
                    files = files,
                    hasLyricsPredicate = { file -> LyricsRepository.hasLocalLyrics(file) },
                    onConfirmBatchDelete = { selectedFiles ->
                        performBatchDelete(selectedFiles)
                    }
                )
            },
            onOpenFolder = {
                FileUtils.openDownloadFolder(activity)
            }
        )
    }

    private fun showFileActions(index: Int, files: List<File>) {
        if (index !in files.indices) return
        val file = files[index]
        val hasLyrics = LyricsRepository.hasLocalLyrics(file)

        dialogManager.showFileActionDialog(
            file = file,
            hasLyrics = hasLyrics,
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
            onDownloadLyrics = {
                Toast.makeText(activity, "Mencari lirik untuk ${file.nameWithoutExtension}...", Toast.LENGTH_SHORT).show()
                LyricsRepository.getLyricsAsync(
                    title = file.nameWithoutExtension,
                    offlineFile = file,
                    ignoreLocalCache = true
                ) { result ->
                    if (result != null && result.rawLrc.isNotBlank()) {
                        val lrcFile = FileUtils.getCompanionLrcFile(file)
                        if (!lrcFile.exists()) {
                            val fileDurationMs = LyricsRepository.extractAudioDurationMs(file)
                            val stampedContent = LyricsRepository.formatLrcWithDurationTag(result.rawLrc, fileDurationMs)
                            LyricsRepository.saveLrcToFile(stampedContent, lrcFile)
                        }
                        if (offlinePlayer.currentSong?.absolutePath == file.absolutePath) {
                            viewController.setOfflineHasLocalLyrics(true)
                            lastLoadedLyricsKey = ""
                            lastLoadedLyricsDurationSec = 0L
                            if (viewController.isLyricsSheetVisible) {
                                loadLyricsForCurrentTrack(forceRefresh = false)
                            }
                        }
                        val statusMsg = if (result.isSynced) {
                            "Lirik karaoke (.lrc) tersimpan di YTMusic2/Lyrics"
                        } else {
                            "Lirik teks tersimpan di YTMusic2/Lyrics"
                        }
                        Toast.makeText(activity, statusMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, "Lirik tidak ditemukan di LRCLIB", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteLyricsOnly = {
                if (LyricsRepository.deleteLocalLyrics(file)) {
                    if (offlinePlayer.currentSong?.absolutePath == file.absolutePath) {
                        viewController.setOfflineHasLocalLyrics(false)
                        lastLoadedLyricsKey = ""
                        lastLoadedLyricsDurationSec = 0L
                        val (cleanTitle, cleanArtist) = LyricsRepository.sanitizeTitleAndArtist(
                            file.nameWithoutExtension,
                            ""
                        )
                        viewController.setLyricsUiState(
                            LyricsUiState.NotFound(
                                title = cleanTitle.ifBlank { file.nameWithoutExtension },
                                artist = cleanArtist,
                                reason = "File lirik lokal telah dihapus. Ketuk Coba Lagi untuk mencari ulang."
                            )
                        )
                    }
                    Toast.makeText(activity, "File lirik (.lrc) dihapus", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(activity, "File lirik tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
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
            if (FileUtils.deleteSongAndCompanions(file)) {
                LyricsRepository.deleteLocalLyrics(file)
                Toast.makeText(activity, "Lagu & file pendamping dihapus", Toast.LENGTH_SHORT).show()
                showOfflineFiles()
            } else {
                Toast.makeText(activity, "Gagal menghapus file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performBatchDelete(selectedFiles: List<File>) {
        if (selectedFiles.isEmpty()) return

        val activePath = offlinePlayer.currentSong?.absolutePath
        if (activePath != null && selectedFiles.any { it.absolutePath == activePath }) {
            offlinePlayer.stop()
        }

        var deletedCount = 0
        for (file in selectedFiles) {
            if (FileUtils.deleteSongAndCompanions(file)) {
                LyricsRepository.deleteLocalLyrics(file)
                deletedCount++
            }
        }

        val remainingFiles = FileUtils.getOfflineAudioFiles()
        offlinePlayer.setPlaylist(remainingFiles)

        if (deletedCount > 0) {
            Toast.makeText(
                activity,
                "Berhasil menghapus $deletedCount lagu beserta atributnya",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(activity, "Gagal menghapus file terpilih", Toast.LENGTH_SHORT).show()
        }

        if (remainingFiles.isNotEmpty()) {
            showOfflineFiles()
        }
    }

    private var lastNotifTitle: String? = null
    private var lastNotifArtist: String? = null
    private var lastNotifPlaying: Boolean? = null
    private var lastNotifDurationMs: Long? = null
    private var lastNotifArtwork: String? = null

    private fun updatePlaybackNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        artworkSource: String = "",
        forceUpdate: Boolean = false
    ) {
        if (!forceUpdate &&
            title == lastNotifTitle &&
            artist == lastNotifArtist &&
            isPlaying == lastNotifPlaying &&
            durationMs == lastNotifDurationMs &&
            artworkSource == lastNotifArtwork
        ) {
            return
        }
        lastNotifTitle = title
        lastNotifArtist = artist
        lastNotifPlaying = isPlaying
        lastNotifDurationMs = durationMs
        lastNotifArtwork = artworkSource

        val serviceIntent = Intent(activity, MusicBackgroundService::class.java).apply {
            action = MusicBackgroundService.ACTION_UPDATE_SONG
            putExtra(MusicBackgroundService.EXTRA_TITLE, title)
            putExtra(MusicBackgroundService.EXTRA_ARTIST, artist)
            putExtra(MusicBackgroundService.EXTRA_IS_PLAYING, isPlaying)
            putExtra(MusicBackgroundService.EXTRA_DURATION_MS, durationMs)
            putExtra(MusicBackgroundService.EXTRA_POSITION_MS, positionMs)
            putExtra(MusicBackgroundService.EXTRA_ARTWORK_SOURCE, artworkSource)
        }
        try {
            ContextCompat.startForegroundService(activity, serviceIntent)
        } catch (_: Exception) {}
    }

    private fun showSleepTimerOptions() {
        val isActive = sleepTimer != null && sleepTimerRemainingMs > 0L
        val remainingText = if (isActive) {
            FormatUtils.formatDuration(sleepTimerRemainingMs.toInt())
        } else {
            null
        }
        dialogManager.showSleepTimerDialog(
            isActive = isActive,
            remainingText = remainingText,
            onSelectMinutes = { minutes ->
                startSleepTimer(minutes)
            },
            onCancelTimer = {
                cancelSleepTimer()
                Toast.makeText(activity, "Sleep Timer dimatikan", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        val durationMs = minutes * 60 * 1000L
        sleepTimerRemainingMs = durationMs
        viewController.updateSleepTimerButton(durationMs)

        sleepTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                sleepTimerRemainingMs = millisUntilFinished
                viewController.updateSleepTimerButton(millisUntilFinished)
            }

            override fun onFinish() {
                sleepTimer = null
                sleepTimerRemainingMs = 0L
                viewController.updateSleepTimerButton(0L)
                handlePause()
                val title = lastNotifTitle ?: currentOnlineTitle.ifBlank { "YouTube Music" }
                updatePlaybackNotification(title, "Timer tidur selesai", false)
                Toast.makeText(activity, "Sleep Timer selesai, musik dihentikan", Toast.LENGTH_LONG).show()
            }
        }.start()

        Toast.makeText(activity, "Sleep Timer diatur: $minutes menit", Toast.LENGTH_SHORT).show()
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerRemainingMs = 0L
        viewController.updateSleepTimerButton(0L)
    }

    // Kontrol Audio FX (disiapkan untuk UI Audio FX Studio)

    fun getAudioFxState(): AudioFxState = audioFxManager.state

    fun applySpeedPreset(preset: SpeedPreset) {
        audioFxManager.applySpeedPreset(preset)
    }

    fun setCustomSpeedAndPitch(speed: Float, pitch: Float) {
        audioFxManager.setCustomSpeedAndPitch(speed, pitch)
    }

    fun setBassBoostStrength(strength: Int) {
        audioFxManager.setBassBoostStrength(strength)
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        audioFxManager.applyEqualizerPreset(preset)
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        audioFxManager.setEqualizerBandGain(bandIndex, gainDb)
    }

    fun setVolumeNormalizerEnabled(enabled: Boolean) {
        audioFxManager.setVolumeNormalizerEnabled(enabled)
    }

    fun resetAudioFx() {
        audioFxManager.resetAll()
    }

    // Lifecycle dan back handlers

    fun onResume() {
        streamOptimizer.setWindowFocused(activity.hasWindowFocus())
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        streamOptimizer.setWindowFocused(hasFocus)
    }

    fun onPause() {
        streamOptimizer.setWindowFocused(false)
        webManager.injectBypassScript()
        adSkipObserver.injectObserverScript()
        streamOptimizer.injectOptimizerScript()
        audioFxManager.injectWebAudioFx()
    }

    fun onStop() {
        streamOptimizer.setWindowFocused(false)
        webManager.injectBypassScript()
        adSkipObserver.injectObserverScript()
        streamOptimizer.injectOptimizerScript()
        audioFxManager.injectWebAudioFx()
    }

    fun onDestroy() {
        cancelSleepTimer()
        MusicBackgroundService.controlCallback = null
        MusicBackgroundService.seekCallback = null
        audioFxManager.release()
        offlinePlayer.release()
        lastNotifTitle = null
        lastNotifArtist = null
        lastNotifPlaying = null
        lastNotifDurationMs = null
        lastNotifArtwork = null
    }

    fun handleBackPressed(): Boolean {
        if (viewController.isLyricsSheetVisible) {
            viewController.hideLyricsSheet()
            return true
        }
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
