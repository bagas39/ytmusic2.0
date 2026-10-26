package com.example.ytmusic2.ui.view

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.example.ytmusic2.R
import com.example.ytmusic2.util.FormatUtils

/**
 * Mengontrol interaksi tampilan (view binding & event listeners) untuk player offline dan baris kontrol.
 * Menerapkan prinsip UI event hoisting: Controller mengekspos event lambda,
 * dan menyediakan fungsi mutasi tampilan untuk sinkronisasi state.
 */
class PlayerViewController(private val rootView: View) {

    constructor(activity: Activity) : this(activity.findViewById<View>(android.R.id.content))

    // Offline player view references
    private val layoutOfflinePlayer: View = rootView.findViewById(R.id.layoutOfflinePlayer)
    private val tvOfflineTitle: TextView = rootView.findViewById(R.id.tvOfflineTitle)
    private val btnOfflineClose: Button = rootView.findViewById(R.id.btnOfflineClose)
    private val sbOfflineProgress: SeekBar = rootView.findViewById(R.id.sbOfflineProgress)
    private val tvOfflineCurrentTime: TextView = rootView.findViewById(R.id.tvOfflineCurrentTime)
    private val tvOfflineTotalTime: TextView = rootView.findViewById(R.id.tvOfflineTotalTime)

    // Control bar view references
    private val btnPrevious: Button = rootView.findViewById(R.id.btnPrevious)
    private val btnPlayPause: Button = rootView.findViewById(R.id.btnPlayPause)
    private val btnNext: Button = rootView.findViewById(R.id.btnNext)
    private val btnDownload: Button = rootView.findViewById(R.id.btnDownload)
    private val btnOffline: Button = rootView.findViewById(R.id.btnOffline)
    private val btnSleepTimer: Button = rootView.findViewById(R.id.btnSleepTimer)

    // Hoisted Events / Callbacks
    var onPreviousClicked: (() -> Unit)? = null
    var onPlayPauseClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onDownloadClicked: (() -> Unit)? = null
    var onOfflineClicked: (() -> Unit)? = null
    var onSleepTimerClicked: (() -> Unit)? = null
    var onOfflineCloseClicked: (() -> Unit)? = null
    var onSeekTrackingChanged: ((isTracking: Boolean) -> Unit)? = null
    var onSeekTo: ((progressMs: Int) -> Unit)? = null

    init {
        setupListeners()
    }

    private fun setupListeners() {
        btnPrevious.setOnClickListener { onPreviousClicked?.invoke() }
        btnPlayPause.setOnClickListener { onPlayPauseClicked?.invoke() }
        btnNext.setOnClickListener { onNextClicked?.invoke() }
        btnDownload.setOnClickListener { onDownloadClicked?.invoke() }
        btnOffline.setOnClickListener { onOfflineClicked?.invoke() }
        btnSleepTimer.setOnClickListener { onSleepTimerClicked?.invoke() }
        btnOfflineClose.setOnClickListener { onOfflineCloseClicked?.invoke() }

        sbOfflineProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvOfflineCurrentTime.text = FormatUtils.formatDuration(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                onSeekTrackingChanged?.invoke(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onSeekTrackingChanged?.invoke(false)
                onSeekTo?.invoke(seekBar?.progress ?: 0)
            }
        })
    }

    val isOfflinePlayerVisible: Boolean
        get() = layoutOfflinePlayer.visibility == View.VISIBLE

    /**
     * Menampilkan panel pemutar offline dengan informasi judul dan durasi lagu.
     */
    fun showOfflinePlayer(title: String, durationMs: Int) {
        tvOfflineTitle.text = title
        tvOfflineTitle.isSelected = true
        layoutOfflinePlayer.visibility = View.VISIBLE
        val safeDuration = if (durationMs > 0) durationMs else 1000
        sbOfflineProgress.max = safeDuration
        tvOfflineTotalTime.text = FormatUtils.formatDuration(safeDuration)
        tvOfflineCurrentTime.text = "00:00"
        btnPlayPause.text = "⏸ Pause"
    }

    /**
     * Menyembunyikan panel pemutar offline dan me-reset teks tombol play/pause.
     */
    fun hideOfflinePlayer() {
        layoutOfflinePlayer.visibility = View.GONE
        btnPlayPause.text = "⏯ Play/Pause"
    }

    /**
     * Memperbarui label tombol play/pause sesuai status pemutaran.
     */
    fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.text = if (isPlaying) "⏸ Pause" else "▶ Play"
    }

    /**
     * Memperbarui posisi SeekBar dan label waktu pemutaran saat ini.
     */
    fun updateProgress(currentMs: Int) {
        sbOfflineProgress.progress = currentMs
        tvOfflineCurrentTime.text = FormatUtils.formatDuration(currentMs)
    }

    /**
     * Memperbarui label tombol Sleep Timer dengan sisa waktu hitung mundur.
     */
    fun updateSleepTimerButton(remainingMs: Long) {
        if (remainingMs > 0L) {
            btnSleepTimer.text = "⏲ ${FormatUtils.formatDuration(remainingMs.toInt())}"
        } else {
            btnSleepTimer.text = "⏲ Timer"
        }
    }
}
