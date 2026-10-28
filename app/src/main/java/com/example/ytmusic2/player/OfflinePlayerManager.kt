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
    var onModesChanged: ((isShuffle: Boolean, repeatMode: RepeatMode) -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var originalPlaylist: List<File> = emptyList()
    private var playlist: List<File> = emptyList()
    var currentIndex: Int = -1
        private set
    private var isUserTrackingSeekBar: Boolean = false

    var isShuffle: Boolean = false
        private set

    var repeatMode: RepeatMode = RepeatMode.OFF
        private set

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

    val currentDurationMs: Long
        get() = try {
            mediaPlayer?.duration?.toLong()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        }

    val currentPositionMs: Long
        get() = try {
            mediaPlayer?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        }

    fun setPlaylist(files: List<File>) {
        originalPlaylist = files
        syncPlaylistOrder(preserveCurrentSong = true)
    }

    fun toggleShuffle(): Boolean {
        isShuffle = !isShuffle
        syncPlaylistOrder(preserveCurrentSong = true)
        onModesChanged?.invoke(isShuffle, repeatMode)
        return isShuffle
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        onModesChanged?.invoke(isShuffle, repeatMode)
        return repeatMode
    }

    private fun syncPlaylistOrder(preserveCurrentSong: Boolean) {
        val currentFile = currentSong
        if (isShuffle) {
            val shuffled = originalPlaylist.shuffled()
            if (preserveCurrentSong && currentFile != null) {
                playlist = listOf(currentFile) + (shuffled.filterNot { it.absolutePath == currentFile.absolutePath })
                currentIndex = 0
            } else {
                playlist = shuffled
                currentIndex = if (currentFile != null) {
                    playlist.indexOfFirst { it.absolutePath == currentFile.absolutePath }
                } else -1
            }
        } else {
            playlist = originalPlaylist
            currentIndex = if (currentFile != null) {
                playlist.indexOfFirst { it.absolutePath == currentFile.absolutePath }
            } else -1
        }

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
            originalPlaylist = newPlaylist
            if (isShuffle) {
                val targetFile = newPlaylist.getOrNull(index)
                val shuffled = newPlaylist.shuffled()
                if (targetFile != null) {
                    playlist = listOf(targetFile) + (shuffled.filterNot { it.absolutePath == targetFile.absolutePath })
                    currentIndex = 0
                } else {
                    playlist = shuffled
                    currentIndex = 0
                }
            } else {
                playlist = originalPlaylist
                currentIndex = index
            }
        } else {
            currentIndex = index
        }

        if (playlist.isEmpty() || currentIndex !in playlist.indices) return

        val file = playlist[currentIndex]

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
                    handleTrackCompletion()
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

    private fun handleTrackCompletion() {
        if (playlist.isEmpty()) {
            stop()
            return
        }

        when (repeatMode) {
            RepeatMode.ONE -> {
                // Ulang lagu yang sama
                seekTo(0)
                try {
                    mediaPlayer?.start()
                    onPlaybackStateChanged?.invoke(true)
                } catch (_: Exception) {
                    playSongAtIndex(currentIndex)
                }
            }
            RepeatMode.ALL -> {
                // Putar lagu berikutnya, jika sudah di akhir ulangi dari indeks 0
                val nextIndex = (currentIndex + 1) % playlist.size
                playSongAtIndex(nextIndex)
            }
            RepeatMode.OFF -> {
                // Putar lagu berikutnya, jika sudah di akhir daftar maka stop
                val nextIndex = currentIndex + 1
                if (nextIndex < playlist.size) {
                    playSongAtIndex(nextIndex)
                } else {
                    stop()
                }
            }
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

    fun pause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                onPlaybackStateChanged?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e("OfflinePlayerManager", "pause error", e)
        }
    }

    fun resume() {
        val mp = mediaPlayer ?: return
        try {
            if (!mp.isPlaying) {
                mp.start()
                onPlaybackStateChanged?.invoke(true)
            }
        } catch (e: Exception) {
            Log.e("OfflinePlayerManager", "resume error", e)
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val nextIndex = if (repeatMode == RepeatMode.OFF && currentIndex + 1 >= playlist.size) {
            0
        } else {
            (currentIndex + 1) % playlist.size
        }
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
