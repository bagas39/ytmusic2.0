package com.example.ytmusic2.player

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ytmusic2.util.FileUtils
import java.io.File

class OfflinePlayerManager {

    var onTrackPrepared: ((file: File, index: Int, total: Int, durationMs: Int) -> Unit)? = null
    var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null
    var onProgressTick: ((currentMs: Int, totalMs: Int) -> Unit)? = null
    var onPlayerStopped: (() -> Unit)? = null
    var onError: ((message: String) -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<File> = emptyList()
    var currentIndex: Int = -1
        private set
    private var isUserTrackingSeekBar: Boolean = false

    val playlistSize: Int
        get() = playlist.size

    private val seekHandler = Handler(Looper.getMainLooper())
    private var seekRunnable: Runnable? = null

    val isOfflineActive: Boolean
        get() = mediaPlayer != null && currentIndex >= 0

    val isPlaying: Boolean
        get() = try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    val currentSong: File?
        get() = playlist.getOrNull(currentIndex)

    fun setPlaylist(files: List<File>) {
        playlist = files
        if (currentIndex >= playlist.size) {
            currentIndex = -1
        }
    }

    fun playSong(file: File) {
        val files = FileUtils.getOfflineAudioFiles()
        val targetIndex = files.indexOfFirst { it.absolutePath == file.absolutePath }
        if (targetIndex >= 0) {
            playSongAtIndex(targetIndex, files)
        } else {
            val updated = listOf(file) + files
            playSongAtIndex(0, updated)
        }
    }

    fun playSongAtIndex(index: Int, newPlaylist: List<File>? = null) {
        if (newPlaylist != null) {
            playlist = newPlaylist
        }
        if (playlist.isEmpty() || index !in playlist.indices) return

        currentIndex = index
        val file = playlist[index]

        stopSeekUpdater()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    val duration = mp.duration
                    mp.start()
                    startSeekUpdater()
                    onTrackPrepared?.invoke(file, currentIndex, playlist.size, duration)
                    onPlaybackStateChanged?.invoke(true)
                }
                setOnCompletionListener {
                    if (playlist.isNotEmpty()) {
                        val nextIndex = (currentIndex + 1) % playlist.size
                        playSongAtIndex(nextIndex)
                    } else {
                        stop()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    val msg = "Gagal memutar audio offline ($what, $extra)"
                    onError?.invoke(msg)
                    stop()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            val msg = "Gagal memutar file: ${e.message}"
            onError?.invoke(msg)
            stop()
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                onPlaybackStateChanged?.invoke(false)
            } else {
                mp.start()
                onPlaybackStateChanged?.invoke(true)
            }
        } catch (e: Exception) {
            Log.e("OfflinePlayerManager", "togglePlayPause error", e)
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = (currentIndex + 1) % playlist.size
        playSongAtIndex(nextIndex)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        val mp = mediaPlayer
        val pos = try { mp?.currentPosition ?: 0 } catch (_: Exception) { 0 }
        // Jika sudah berjalan > 3 detik, ulang lagu dari awal seperti pemutar musik standar
        if (mp != null && pos > 3000) {
            try {
                mp.seekTo(0)
                onProgressTick?.invoke(0, mp.duration)
            } catch (_: Exception) {}
            return
        }
        val prevIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playSongAtIndex(prevIndex)
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (_: Exception) {}
    }

    fun setUserTrackingSeekBar(isTracking: Boolean) {
        isUserTrackingSeekBar = isTracking
    }

    fun stop() {
        stopSeekUpdater()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentIndex = -1
        onPlayerStopped?.invoke()
    }

    fun release() {
        stop()
    }

    private fun startSeekUpdater() {
        stopSeekUpdater()
        seekRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp != null && isOfflineActive) {
                    try {
                        if (mp.isPlaying && !isUserTrackingSeekBar) {
                            val pos = mp.currentPosition
                            val dur = mp.duration
                            onProgressTick?.invoke(pos, dur)
                        }
                    } catch (_: Exception) {}
                    seekHandler.postDelayed(this, 500)
                }
            }
        }
        seekHandler.post(seekRunnable!!)
    }

    private fun stopSeekUpdater() {
        seekRunnable?.let { seekHandler.removeCallbacks(it) }
        seekRunnable = null
    }
}
