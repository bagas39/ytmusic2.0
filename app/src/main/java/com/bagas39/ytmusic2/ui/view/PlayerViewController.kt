package com.bagas39.ytmusic2.ui.view

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagas39.ytmusic2.R
import com.bagas39.ytmusic2.player.AudioQuality
import com.bagas39.ytmusic2.player.RepeatMode
import com.bagas39.ytmusic2.ui.theme.YtmPalette
import com.bagas39.ytmusic2.ui.theme.YtmTheme
import com.bagas39.ytmusic2.util.FormatUtils
import kotlinx.coroutines.delay

/**
 * Mengontrol dock kontrol dan panel pemutar offline (Jetpack Compose Material 3)
 * yang duduk di bawah KeepAliveWebView pada layout hybrid XML.
 */
class PlayerViewController(private val rootView: View) {

    constructor(activity: Activity) : this(activity.findViewById<View>(android.R.id.content))

    private val composeControlDock: ComposeView = rootView.findViewById(R.id.composeControlDock)

    private var offlineVisibleState by mutableStateOf(false)
    private var offlineTitleState by mutableStateOf("Memutar File Offline")
    private var offlineCurrentMsState by mutableIntStateOf(0)
    private var offlineTotalMsState by mutableIntStateOf(1000)
    private var sliderDragValueState by mutableFloatStateOf(0f)
    private var isDraggingSliderState by mutableStateOf(false)
    private var playPauseLabelState by mutableStateOf("Putar / Jeda")
    private var sleepTimerRemainingMsState by mutableLongStateOf(0L)
    private var isShuffleState by mutableStateOf(false)
    private var repeatModeState by mutableStateOf(RepeatMode.OFF)

    // Ad Counter & Data Saver States
    private var adSkipPopupVisibleState by mutableStateOf(false)
    private var lastSkippedCountState by mutableIntStateOf(1)
    private var lastSkippedSecondsState by mutableIntStateOf(15)
    private var adSkipTotalCountState by mutableIntStateOf(0)
    private var adSkipTotalSecondsState by mutableIntStateOf(0)
    private var adSkipTriggerIdState by mutableIntStateOf(0)
    private var showAdStatsDialogState by mutableStateOf(false)
    private var dataSaverEnabledState by mutableStateOf(false)
    private var audioQualityState by mutableStateOf(AudioQuality.AUTO)

    // Karaoke Lyrics Sheet States
    private var lyricsSheetVisibleState by mutableStateOf(false)
    private var lyricsContentState by mutableStateOf<LyricsUiState>(LyricsUiState.Idle)
    private var lyricsTrackTitleState by mutableStateOf("")
    private var lyricsTrackArtistState by mutableStateOf("")
    private var lyricsPositionMsState by mutableLongStateOf(0L)
    private var lyricsDurationMsState by mutableLongStateOf(0L)
    private var lyricsIsPlayingState by mutableStateOf(false)
    private var offlineHasLocalLyricsState by mutableStateOf(false)

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
    var onDataSaverToggled: ((Boolean) -> Unit)? = null
    var onAudioQualityChanged: ((AudioQuality) -> Unit)? = null
    var onLyricsClicked: (() -> Unit)? = null
    var onLyricsRetryClicked: (() -> Unit)? = null
    var onLyricsManualSearch: ((title: String, artist: String) -> Unit)? = null
    var onLyricsSeekTo: ((positionMs: Long) -> Unit)? = null

    val isOfflinePlayerVisible: Boolean
        get() = offlineVisibleState

    val isLyricsSheetVisible: Boolean
        get() = lyricsSheetVisibleState

    init {
        setupComposeContent()
    }

    private fun setupComposeContent() {
        composeControlDock.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeControlDock.setContent {
            YtmTheme {
                YtmModernControlDock()
                AdStatsDialog()
                KaraokeLyricsBottomSheet(
                    visible = lyricsSheetVisibleState,
                    uiState = lyricsContentState,
                    trackTitle = lyricsTrackTitleState,
                    trackArtist = lyricsTrackArtistState,
                    currentPositionMs = lyricsPositionMsState,
                    durationMs = lyricsDurationMsState,
                    isPlaying = lyricsIsPlayingState,
                    onDismiss = { lyricsSheetVisibleState = false },
                    onRetry = { onLyricsRetryClicked?.invoke() },
                    onManualSearch = { queryTitle, queryArtist ->
                        onLyricsManualSearch?.invoke(queryTitle, queryArtist)
                    },
                    onSeekTo = { positionMs -> onLyricsSeekTo?.invoke(positionMs) },
                    onPrevious = { onPreviousClicked?.invoke() },
                    onPlayPause = { onPlayPauseClicked?.invoke() },
                    onNext = { onNextClicked?.invoke() }
                )
            }
        }
    }

    private fun formatSavedTime(totalSeconds: Int): String {
        if (totalSeconds < 60) return "${totalSeconds}d"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "${hours}j ${minutes}m"
        } else if (seconds > 0) {
            "${minutes}m ${seconds}d"
        } else {
            "${minutes}m"
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun YtmModernControlDock() {
        val context = LocalContext.current
        val isTvMode = remember {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            @Suppress("DEPRECATION")
            val hasTvFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
            val noTouchscreen = !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            isTvUiMode || hasTvFeature || noTouchscreen
        }

        LaunchedEffect(adSkipTriggerIdState) {
            if (adSkipTriggerIdState > 0) {
                adSkipPopupVisibleState = true
                delay(3000L)
                adSkipPopupVisibleState = false
            }
        }

        var isDockExpanded by remember { mutableStateOf(true) }
        var isHandleFocused by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = YtmPalette.ObsidianBg
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = YtmPalette.SubtleDivider
                )

                // Handle geser/ketuk untuk melipat atau membuka dock kontrol
                Surface(
                    color = Color.Transparent,
                    border = if (isHandleFocused) BorderStroke(1.5.dp, YtmPalette.FocusRing) else null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 12) {
                                    isDockExpanded = false
                                } else if (dragAmount < -12) {
                                    isDockExpanded = true
                                }
                            }
                        }
                        .onFocusChanged { isHandleFocused = it.hasFocus }
                        .then(
                            if (isTvMode && isDockExpanded) {
                                Modifier.focusProperties { canFocus = false }
                            } else {
                                Modifier
                                    .clickable { isDockExpanded = !isDockExpanded }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = if (isDockExpanded) {
                                            "Sembunyikan menu kontrol"
                                        } else {
                                            "Buka menu kontrol"
                                        }
                                    }
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isDockExpanded) 36.dp else 28.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isDockExpanded) YtmPalette.ControlBorder else YtmPalette.ActiveBorder
                                )
                        )
                        if (!isDockExpanded) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Buka Menu Kontrol",
                                color = YtmPalette.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isDockExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 20) {
                                        isDockExpanded = false
                                    }
                                }
                            }
                    ) {
                        AnimatedVisibility(
                            visible = adSkipPopupVisibleState,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            val skipLabel = if (lastSkippedCountState > 1) {
                                "$lastSkippedCountState iklan bertumpuk dilewati (${lastSkippedSecondsState}d)"
                            } else {
                                "1 iklan dilewati (${lastSkippedSecondsState}d)"
                            }
                            var isSkipBannerFocused by remember { mutableStateOf(false) }

                            Surface(
                                onClick = { showAdStatsDialogState = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
                                    .heightIn(min = 44.dp)
                                    .onFocusChanged { isSkipBannerFocused = it.hasFocus }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "$skipLabel, ketuk untuk statistik iklan"
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = YtmPalette.SurfaceElevated,
                                border = BorderStroke(
                                    width = if (isSkipBannerFocused) 1.5.dp else 1.dp,
                                    color = if (isSkipBannerFocused) YtmPalette.FocusRing else YtmPalette.ControlBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Titik penanda aktif saat skip iklan baru terjadi
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(YtmPalette.ActiveBorder)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = skipLabel,
                                            color = YtmPalette.TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Total: $adSkipTotalCountState · ${formatSavedTime(adSkipTotalSecondsState)}",
                                        color = YtmPalette.TextMuted,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = offlineVisibleState,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = YtmPalette.SurfaceElevated,
                                border = BorderStroke(1.dp, YtmPalette.ControlBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Pemutar Offline",
                                                color = YtmPalette.AccentText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = offlineTitleState,
                                                color = YtmPalette.TextPrimary,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.basicMarquee(iterations = 2)
                                            )
                                        }

                                        OfflineModeChip(
                                            label = "Tutup",
                                            isActive = false,
                                            contentDescription = "Tutup pemutar offline",
                                            onClick = { onOfflineCloseClicked?.invoke() }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OfflineModeChip(
                                            label = if (isShuffleState) "Acak: Aktif" else "Acak",
                                            isActive = isShuffleState,
                                            contentDescription = if (isShuffleState) {
                                                "Mode acak aktif, ketuk untuk mematikan"
                                            } else {
                                                "Aktifkan mode acak"
                                            },
                                            modifier = Modifier.weight(1f),
                                            onClick = { onShuffleClicked?.invoke() }
                                        )

                                        val isRepeatActive = repeatModeState != RepeatMode.OFF
                                        val repeatLabel = when (repeatModeState) {
                                            RepeatMode.OFF -> "Ulang"
                                            RepeatMode.ALL -> "Ulang Semua"
                                            RepeatMode.ONE -> "Ulang 1"
                                        }
                                        OfflineModeChip(
                                            label = repeatLabel,
                                            isActive = isRepeatActive,
                                            contentDescription = "Mode ulang: $repeatLabel",
                                            modifier = Modifier.weight(1f),
                                            onClick = { onRepeatClicked?.invoke() }
                                        )

                                        val offlineLyricsHighlight = lyricsSheetVisibleState || offlineHasLocalLyricsState
                                        OfflineModeChip(
                                            label = "Lirik",
                                            isActive = offlineLyricsHighlight,
                                            contentDescription = "Buka lirik lagu offline",
                                            modifier = Modifier.weight(1f),
                                            onClick = { onLyricsClicked?.invoke() }
                                        )
                                    }

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
                                            color = YtmPalette.TextMuted,
                                            fontSize = 11.5.sp,
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
                                                .height(32.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = YtmPalette.AccentTextOnDark,
                                                activeTrackColor = YtmPalette.CrimsonPrimary,
                                                inactiveTrackColor = YtmPalette.ControlSurface
                                            )
                                        )

                                        Text(
                                            text = FormatUtils.formatDuration(offlineTotalMsState),
                                            color = YtmPalette.TextMuted,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (offlineVisibleState || isTvMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DockMediaButton(
                                        label = "Prev",
                                        contentDescription = "Lagu sebelumnya",
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        onClick = { onPreviousClicked?.invoke() }
                                    )

                                    var playFocused by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = { onPlayPauseClicked?.invoke() },
                                        modifier = Modifier
                                            .weight(1.35f)
                                            .height(46.dp)
                                            .onFocusChanged { playFocused = it.hasFocus },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = YtmPalette.CrimsonPrimary,
                                            contentColor = Color.White
                                        ),
                                        border = if (playFocused) {
                                            BorderStroke(2.dp, YtmPalette.FocusRing)
                                        } else {
                                            null
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = playPauseLabelState,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    DockMediaButton(
                                        label = "Next",
                                        contentDescription = "Lagu berikutnya",
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        onClick = { onNextClicked?.invoke() }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DockQuickButton(
                                    label = "Unduh",
                                    isActive = false,
                                    contentDescription = "Unduh lagu yang sedang diputar",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onDownloadClicked?.invoke() }
                                )

                                DockQuickButton(
                                    label = "Lirik",
                                    isActive = lyricsSheetVisibleState,
                                    contentDescription = "Buka panel lirik",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onLyricsClicked?.invoke() }
                                )

                                DockQuickButton(
                                    label = "Offline",
                                    isActive = offlineVisibleState,
                                    contentDescription = "Buka daftar lagu offline",
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOfflineClicked?.invoke() }
                                )

                                val isTimerActive = sleepTimerRemainingMsState > 0L
                                val timerLabel = if (isTimerActive) {
                                    FormatUtils.formatDuration(sleepTimerRemainingMsState.toInt())
                                } else {
                                    "Timer"
                                }
                                DockQuickButton(
                                    label = timerLabel,
                                    isActive = isTimerActive,
                                    contentDescription = if (isTimerActive) {
                                        "Sleep timer aktif, sisa $timerLabel"
                                    } else {
                                        "Atur pengatur waktu tidur"
                                    },
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSleepTimerClicked?.invoke() }
                                )
                            }

                            if (!adSkipPopupVisibleState) {
                                val statusText = if (adSkipTotalCountState > 0) {
                                    val saverTag = if (dataSaverEnabledState) " · Audio Saja aktif" else ""
                                    "$adSkipTotalCountState iklan dilewati · ${formatSavedTime(adSkipTotalSecondsState)} dihemat$saverTag"
                                } else {
                                    if (dataSaverEnabledState) {
                                        "YTM Shield · Mode Audio Saja aktif"
                                    } else {
                                        "YTM Shield · Statistik & hemat data"
                                    }
                                }
                                var isStatusFocused by remember { mutableStateOf(false) }

                                Surface(
                                    onClick = { showAdStatsDialogState = true },
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (isStatusFocused) {
                                        BorderStroke(1.5.dp, YtmPalette.FocusRing)
                                    } else {
                                        null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 44.dp)
                                        .onFocusChanged { isStatusFocused = it.hasFocus }
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = "$statusText, ketuk untuk pengaturan dan statistik"
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = if (dataSaverEnabledState) {
                                                YtmPalette.AccentText
                                            } else {
                                                YtmPalette.TextMuted
                                            },
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OfflineModeChip(
        label: String,
        isActive: Boolean,
        contentDescription: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val bgColor = if (isActive) YtmPalette.ActiveTintBg else YtmPalette.ControlSurface
        val borderColor = when {
            isFocused -> YtmPalette.FocusRing
            isActive -> YtmPalette.ActiveBorder
            else -> YtmPalette.ControlBorder
        }
        val textColor = if (isActive) YtmPalette.AccentText else YtmPalette.TextPrimary

        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .onFocusChanged { isFocused = it.hasFocus }
                .semantics(mergeDescendants = true) {
                    selected = isActive
                    this.contentDescription = contentDescription
                },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = bgColor,
                contentColor = textColor
            ),
            border = BorderStroke(width = if (isFocused) 1.5.dp else 1.dp, color = borderColor),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun DockMediaButton(
        label: String,
        contentDescription: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .onFocusChanged { isFocused = it.hasFocus }
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = YtmPalette.ControlSurface,
                contentColor = YtmPalette.TextPrimary
            ),
            border = BorderStroke(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) YtmPalette.FocusRing else YtmPalette.ControlBorder
            ),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun DockQuickButton(
        label: String,
        isActive: Boolean,
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val bgColor = if (isActive) YtmPalette.ActiveTintBg else YtmPalette.ControlSurface
        val borderColor = when {
            isFocused -> YtmPalette.FocusRing
            isActive -> YtmPalette.ActiveBorder
            else -> YtmPalette.ControlBorder
        }
        val textColor = if (isActive) YtmPalette.AccentText else YtmPalette.TextPrimary

        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .height(44.dp)
                .onFocusChanged { isFocused = it.hasFocus }
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics(mergeDescendants = true) {
                            selected = isActive
                            this.contentDescription = contentDescription
                        }
                    } else {
                        Modifier.semantics { selected = isActive }
                    }
                ),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = bgColor,
                contentColor = textColor
            ),
            border = BorderStroke(width = if (isFocused) 1.5.dp else 1.dp, color = borderColor),
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    private fun AdStatsDialog() {
        if (!showAdStatsDialogState) return

        val totalAds = adSkipTotalCountState
        val totalSeconds = adSkipTotalSecondsState
        // Estimasi rata-rata bitrate stream video iklan 480p (~165 KB/detik)
        val dataSavedMb = String.format("%.1f MB", (totalSeconds * 0.165f))
        var isCloseFocused by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdStatsDialogState = false },
            confirmButton = {
                TextButton(
                    onClick = { showAdStatsDialogState = false },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .onFocusChanged { isCloseFocused = it.hasFocus },
                    border = if (isCloseFocused) BorderStroke(1.5.dp, YtmPalette.FocusRing) else null,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Tutup", color = YtmPalette.AccentTextOnDark, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = YtmPalette.DialogSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Statistik & Penghemat Data",
                    color = YtmPalette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YtmPalette.DialogCard,
                        border = BorderStroke(1.dp, YtmPalette.ControlBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatRow("Iklan dilewati", "$totalAds")
                            HorizontalDivider(thickness = 0.5.dp, color = YtmPalette.SubtleDivider)
                            StatRow("Waktu dihemat", formatSavedTime(totalSeconds))
                            HorizontalDivider(thickness = 0.5.dp, color = YtmPalette.SubtleDivider)
                            StatRow("Estimasi kuota iklan (480p)", dataSavedMb)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YtmPalette.DialogCard,
                        border = BorderStroke(1.dp, YtmPalette.ControlBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Mode Audio Saja",
                                    color = YtmPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Kunci video ke 144p untuk menghemat kuota tanpa menurunkan kualitas audio",
                                    color = YtmPalette.TextMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Switch(
                                checked = dataSaverEnabledState,
                                onCheckedChange = { isChecked ->
                                    dataSaverEnabledState = isChecked
                                    onDataSaverToggled?.invoke(isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = YtmPalette.CrimsonPrimary,
                                    uncheckedThumbColor = YtmPalette.TextSecondary,
                                    uncheckedTrackColor = YtmPalette.ControlSurface,
                                    uncheckedBorderColor = YtmPalette.ControlBorder
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YtmPalette.DialogCard,
                        border = BorderStroke(1.dp, YtmPalette.ControlBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Kualitas Audio",
                                    color = YtmPalette.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Berlaku untuk streaming online dan unduhan offline",
                                    color = YtmPalette.TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AudioQuality.entries.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { q ->
                                            val isSelected = audioQualityState == q
                                            var isChipFocused by remember { mutableStateOf(false) }
                                            val chipBorder = when {
                                                isChipFocused -> YtmPalette.FocusRing
                                                isSelected -> YtmPalette.ActiveBorder
                                                else -> YtmPalette.ControlBorder
                                            }
                                            Surface(
                                                onClick = {
                                                    audioQualityState = q
                                                    onAudioQualityChanged?.invoke(q)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) YtmPalette.CrimsonPrimary else YtmPalette.ControlSurface,
                                                border = BorderStroke(
                                                    width = if (isChipFocused) 1.5.dp else 1.dp,
                                                    color = chipBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .onFocusChanged { isChipFocused = it.hasFocus }
                                                    .semantics(mergeDescendants = true) {
                                                        selected = isSelected
                                                        contentDescription = "${q.displayName}, ${q.bitrateLabel}"
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = q.displayName,
                                                        color = if (isSelected) Color.White else YtmPalette.TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = q.bitrateLabel,
                                                        color = if (isSelected) {
                                                            YtmPalette.CrimsonSubtext
                                                        } else {
                                                            YtmPalette.TextMutedOnControl
                                                        },
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    private fun StatRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = YtmPalette.TextMuted, fontSize = 12.5.sp)
            Text(
                text = value,
                color = YtmPalette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    fun showOfflinePlayer(title: String, durationMs: Int) {
        offlineTitleState = title
        val safeDuration = if (durationMs > 0) durationMs else 1000
        offlineTotalMsState = safeDuration
        offlineCurrentMsState = 0
        sliderDragValueState = 0f
        isDraggingSliderState = false
        playPauseLabelState = "Jeda"
        offlineVisibleState = true
    }

    fun hideOfflinePlayer() {
        offlineVisibleState = false
        isDraggingSliderState = false
        playPauseLabelState = "Putar / Jeda"
    }

    fun updatePlayPauseButton(isPlaying: Boolean) {
        playPauseLabelState = if (isPlaying) "Jeda" else "Putar"
    }

    fun updateProgress(currentMs: Int) {
        offlineCurrentMsState = currentMs
        if (!isDraggingSliderState) {
            sliderDragValueState = currentMs.toFloat()
        }
    }

    fun updatePlaybackModes(isShuffle: Boolean, repeatMode: RepeatMode) {
        isShuffleState = isShuffle
        repeatModeState = repeatMode
    }

    fun updateSleepTimerButton(remainingMs: Long) {
        sleepTimerRemainingMsState = remainingMs
    }

    fun setInitialAdStats(totalSkipped: Int, totalSeconds: Int) {
        adSkipTotalCountState = totalSkipped
        adSkipTotalSecondsState = totalSeconds
    }

    fun showAdSkippedPopup(
        skippedCount: Int,
        skippedSeconds: Int,
        totalSkipped: Int,
        totalSeconds: Int
    ) {
        lastSkippedCountState = skippedCount
        lastSkippedSecondsState = skippedSeconds
        adSkipTotalCountState = totalSkipped
        adSkipTotalSecondsState = totalSeconds
        adSkipTriggerIdState += 1
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        dataSaverEnabledState = enabled
    }

    fun setAudioQuality(quality: AudioQuality) {
        audioQualityState = quality
    }

    fun showLyricsSheet() {
        lyricsSheetVisibleState = true
    }

    fun hideLyricsSheet() {
        lyricsSheetVisibleState = false
    }

    fun setLyricsUiState(state: LyricsUiState) {
        lyricsContentState = state
    }

    fun setOfflineHasLocalLyrics(hasLyrics: Boolean) {
        offlineHasLocalLyricsState = hasLyrics
    }

    fun updateLyricsTrackMeta(
        title: String,
        artist: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        lyricsTrackTitleState = title
        lyricsTrackArtistState = artist
        lyricsIsPlayingState = isPlaying
        lyricsPositionMsState = positionMs.coerceAtLeast(0L)
        lyricsDurationMsState = durationMs.coerceAtLeast(0L)
    }

    fun updateLyricsPlaybackProgress(positionMs: Long, durationMs: Long) {
        lyricsPositionMsState = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L) {
            lyricsDurationMsState = durationMs
        }
    }
}
