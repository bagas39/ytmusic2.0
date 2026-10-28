package com.example.ytmusic2.ui.view

import android.app.Activity
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ytmusic2.R
import com.example.ytmusic2.player.RepeatMode
import com.example.ytmusic2.util.FormatUtils

/**
 * Mengontrol interaksi tampilan menggunakan arsitektur Hybrid:
 * KeepAliveWebView tetap di XML murni untuk stabilitas 100%, sedangkan
 * panel Offline Player dan baris kontrol bawah dirender dengan Jetpack Compose Material 3.
 */
class PlayerViewController(private val rootView: View) {

    constructor(activity: Activity) : this(activity.findViewById<View>(android.R.id.content))

    private val composeControlDock: ComposeView = rootView.findViewById(R.id.composeControlDock)

    // Reactive Compose States
    private var offlineVisibleState by mutableStateOf(false)
    private var offlineTitleState by mutableStateOf("Memutar File Offline")
    private var offlineCurrentMsState by mutableIntStateOf(0)
    private var offlineTotalMsState by mutableIntStateOf(1000)
    private var sliderDragValueState by mutableFloatStateOf(0f)
    private var isDraggingSliderState by mutableStateOf(false)
    private var playPauseLabelState by mutableStateOf("⏯ Play / Pause")
    private var sleepTimerRemainingMsState by mutableLongStateOf(0L)
    private var isShuffleState by mutableStateOf(false)
    private var repeatModeState by mutableStateOf(RepeatMode.OFF)

    // Hoisted Events / Callbacks
    var onPreviousClicked: (() -> Unit)? = null
    var onPlayPauseClicked: (() -> Unit)? = null
    var onNextClicked: (() -> Unit)? = null
    var onDownloadClicked: (() -> Unit)? = null
    var onOfflineClicked: (() -> Unit)? = null
    var onSleepTimerClicked: (() -> Unit)? = null
    var onOfflineCloseClicked: (() -> Unit)? = null
    var onShuffleClicked: (() -> Unit)? = null
    var onRepeatClicked: (() -> Unit)? = null
    var onSeekTrackingChanged: ((isTracking: Boolean) -> Unit)? = null
    var onSeekTo: ((progressMs: Int) -> Unit)? = null

    val isOfflinePlayerVisible: Boolean
        get() = offlineVisibleState

    init {
        setupComposeContent()
    }

    private fun setupComposeContent() {
        composeControlDock.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeControlDock.setContent {
            val ytmDarkColorScheme = darkColorScheme(
                primary = Color(0xFFFF0033),
                onPrimary = Color.White,
                surface = Color(0xFF0F0F0F),
                onSurface = Color(0xFFF1F1F1),
                secondaryContainer = Color(0xFF1E1E24),
                onSecondaryContainer = Color(0xFFEAEAEA)
            )

            MaterialTheme(colorScheme = ytmDarkColorScheme) {
                YtmModernControlDock()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun YtmModernControlDock() {
        val obsidianBg = Color(0xFF0F0F0F)
        val cardSurface = Color(0xFF1A1A20)
        val elevatedPill = Color(0xFF202027)
        val subtleBorder = Color(0xFF2C2C35)
        val ytmCrimson = Color(0xFFFF0033)
        val ytmCrimsonLight = Color(0xFFFF4D6D)
        val mutedText = Color(0xFFAAAAAA)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = obsidianBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF16161C), obsidianBg)
                        )
                    )
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF26262E)
                )

                // Panel Pemutar Offline (Muncul dengan animasi halus saat aktif)
                AnimatedVisibility(
                    visible = offlineVisibleState,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = cardSurface,
                        border = BorderStroke(1.dp, Color(0xFF2E2E38))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Badge ikon musik beraksen merah
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38121B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "♫",
                                        color = ytmCrimsonLight,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PEMUTAR OFFLINE",
                                        color = ytmCrimsonLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = offlineTitleState,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.basicMarquee()
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Tombol Shuffle
                                val shuffleBg = if (isShuffleState) Color(0xFF3D1620) else Color(0xFF24242C)
                                val shuffleBorder = if (isShuffleState) ytmCrimsonLight else Color(0xFF33333F)
                                val shuffleColor = if (isShuffleState) ytmCrimsonLight else Color(0xFFB5B5B5)

                                Surface(
                                    onClick = { onShuffleClicked?.invoke() },
                                    shape = CircleShape,
                                    color = shuffleBg,
                                    border = BorderStroke(1.dp, shuffleBorder),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "🔀",
                                            fontSize = 11.sp,
                                            color = shuffleColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Tombol Repeat (Off -> All -> One)
                                val isRepeatActive = repeatModeState != RepeatMode.OFF
                                val repeatBg = if (isRepeatActive) Color(0xFF3D1620) else Color(0xFF24242C)
                                val repeatBorder = if (isRepeatActive) ytmCrimsonLight else Color(0xFF33333F)
                                val repeatColor = if (isRepeatActive) ytmCrimsonLight else Color(0xFFB5B5B5)
                                val repeatText = when (repeatModeState) {
                                    RepeatMode.OFF -> "🔁"
                                    RepeatMode.ALL -> "🔁"
                                    RepeatMode.ONE -> "🔂"
                                }

                                Surface(
                                    onClick = { onRepeatClicked?.invoke() },
                                    shape = CircleShape,
                                    color = repeatBg,
                                    border = BorderStroke(1.dp, repeatBorder),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = repeatText,
                                            fontSize = 11.sp,
                                            color = repeatColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    onClick = { onOfflineCloseClicked?.invoke() },
                                    shape = CircleShape,
                                    color = Color(0xFF2A2A34),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "✕",
                                            color = Color(0xFFDDDDDD),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            val safeMax = offlineTotalMsState.coerceAtLeast(1000).toFloat()
                            val displayMs = if (isDraggingSliderState) {
                                sliderDragValueState.toInt()
                            } else {
                                offlineCurrentMsState
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = FormatUtils.formatDuration(displayMs),
                                    color = mutedText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Slider(
                                    value = displayMs.toFloat().coerceIn(0f, safeMax),
                                    onValueChange = { newVal ->
                                        if (!isDraggingSliderState) {
                                            isDraggingSliderState = true
                                            onSeekTrackingChanged?.invoke(true)
                                        }
                                        sliderDragValueState = newVal
                                    },
                                    onValueChangeFinished = {
                                        val targetMs = sliderDragValueState.toInt()
                                        offlineCurrentMsState = targetMs
                                        isDraggingSliderState = false
                                        onSeekTrackingChanged?.invoke(false)
                                        onSeekTo?.invoke(targetMs)
                                    },
                                    valueRange = 0f..safeMax,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .height(26.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ytmCrimsonLight,
                                        activeTrackColor = ytmCrimson,
                                        inactiveTrackColor = Color(0xFF343440)
                                    )
                                )

                                Text(
                                    text = FormatUtils.formatDuration(offlineTotalMsState),
                                    color = mutedText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Area Tombol Navigasi & Aksi Cepat
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Baris 1: Kontrol Utama (Prev, Play/Pause, Next)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onPreviousClicked?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = elevatedPill,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, subtleBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "⏮  Prev",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { onPlayPauseClicked?.invoke() },
                            modifier = Modifier
                                .weight(1.35f)
                                .height(46.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ytmCrimson,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 1.dp
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = playPauseLabelState,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { onNextClicked?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = elevatedPill,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, subtleBorder),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Next  ⏭",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Baris 2: Aksi Cepat (Unduh, Offline, Sleep Timer)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Unduh
                        OutlinedButton(
                            onClick = { onDownloadClicked?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF17171D),
                                contentColor = Color(0xFFEAEAEA)
                            ),
                            border = BorderStroke(1.dp, subtleBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "⬇ Unduh",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // Tombol Offline (Menyala saat mode offline aktif)
                        val offlineActiveBg = if (offlineVisibleState) Color(0xFF33121B) else Color(0xFF17171D)
                        val offlineActiveBorder = if (offlineVisibleState) ytmCrimsonLight else subtleBorder
                        val offlineActiveText = if (offlineVisibleState) ytmCrimsonLight else Color(0xFFEAEAEA)

                        OutlinedButton(
                            onClick = { onOfflineClicked?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = offlineActiveBg,
                                contentColor = offlineActiveText
                            ),
                            border = BorderStroke(1.dp, offlineActiveBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = "🎵 Offline",
                                fontSize = 12.sp,
                                fontWeight = if (offlineVisibleState) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }

                        // Tombol Sleep Timer (Menyala saat timer sedang berjalan)
                        val isTimerActive = sleepTimerRemainingMsState > 0L
                        val timerBg = if (isTimerActive) Color(0xFF33161E) else Color(0xFF17171D)
                        val timerBorder = if (isTimerActive) ytmCrimsonLight else subtleBorder
                        val timerTextColor = if (isTimerActive) ytmCrimsonLight else Color(0xFFEAEAEA)
                        val timerLabel = if (isTimerActive) {
                            "⏲ ${FormatUtils.formatDuration(sleepTimerRemainingMsState.toInt())}"
                        } else {
                            "⏲ Timer"
                        }

                        OutlinedButton(
                            onClick = { onSleepTimerClicked?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = timerBg,
                                contentColor = timerTextColor
                            ),
                            border = BorderStroke(1.dp, timerBorder),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = timerLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isTimerActive) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Menampilkan panel pemutar offline dengan informasi judul dan durasi lagu.
     */
    fun showOfflinePlayer(title: String, durationMs: Int) {
        offlineTitleState = title
        val safeDuration = if (durationMs > 0) durationMs else 1000
        offlineTotalMsState = safeDuration
        offlineCurrentMsState = 0
        sliderDragValueState = 0f
        isDraggingSliderState = false
        playPauseLabelState = "⏸  Pause"
        offlineVisibleState = true
    }

    /**
     * Menyembunyikan panel pemutar offline dan me-reset teks tombol play/pause.
     */
    fun hideOfflinePlayer() {
        offlineVisibleState = false
        isDraggingSliderState = false
        playPauseLabelState = "⏯  Play / Pause"
    }

    /**
     * Memperbarui label tombol play/pause sesuai status pemutaran.
     */
    fun updatePlayPauseButton(isPlaying: Boolean) {
        playPauseLabelState = if (isPlaying) "⏸  Pause" else "▶  Play"
    }

    /**
     * Memperbarui posisi Slider dan label waktu pemutaran saat ini.
     */
    fun updateProgress(currentMs: Int) {
        offlineCurrentMsState = currentMs
        if (!isDraggingSliderState) {
            sliderDragValueState = currentMs.toFloat()
        }
    }

    /**
     * Memperbarui status shuffle dan repeat mode pada UI.
     */
    fun updatePlaybackModes(isShuffle: Boolean, repeatMode: RepeatMode) {
        isShuffleState = isShuffle
        repeatModeState = repeatMode
    }

    /**
     * Memperbarui label tombol Sleep Timer dengan sisa waktu hitung mundur.
     */
    fun updateSleepTimerButton(remainingMs: Long) {
        sleepTimerRemainingMsState = remainingMs
    }
}
