package com.bagas39.ytmusic2.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagas39.ytmusic2.audiofx.AudioFxState
import com.bagas39.ytmusic2.audiofx.EqualizerPreset
import com.bagas39.ytmusic2.player.AudioQuality
import com.bagas39.ytmusic2.ui.theme.YtmPalette
import com.bagas39.ytmusic2.util.FormatUtils
import kotlinx.coroutines.delay

/**
 * Hub menu kontrol audio terpusat (Audio FX Studio, Sleep Timer, dan Kualitas Audio)
 * agar pengaturan pemutaran mudah diakses tanpa memenuhi baris dock utama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlMenuSheet(
    visible: Boolean,
    audioFxState: AudioFxState,
    sleepTimerRemainingMs: Long,
    audioQuality: AudioQuality,
    onDismiss: () -> Unit,
    onOpenAudioFx: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onAudioQualitySelected: (AudioQuality) -> Unit
) {
    var isRendered by remember { mutableStateOf(visible) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            isRendered = true
        } else if (isRendered) {
            try {
                if (sheetState.isVisible) {
                    sheetState.hide()
                }
            } finally {
                isRendered = false
            }
        }
    }

    LaunchedEffect(isRendered) {
        if (isRendered) {
            delay(150L)
            try {
                closeFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (!visible && !isRendered) return

    val fxSummary = buildList {
        add(audioFxState.speedPreset.displayName)
        if (audioFxState.clampedBassBoost > 0) add("Bass ${audioFxState.clampedBassBoost / 10}%")
        if (audioFxState.equalizerPreset != EqualizerPreset.FLAT) add("EQ ${audioFxState.equalizerPreset.displayName}")
        if (audioFxState.volumeNormalizerEnabled) add("Normalizer")
    }.joinToString(" · ")
    val isTimerActive = sleepTimerRemainingMs > 0L
    val timerSummary = if (isTimerActive) {
        "Aktif · Sisa ${FormatUtils.formatDuration(sleepTimerRemainingMs.toInt())}"
    } else {
        "Nonaktif · Atur otomatis berhenti"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YtmPalette.SheetBg,
        contentColor = YtmPalette.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = YtmPalette.ControlBorder,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }
        },
        modifier = Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Menu & Pengaturan",
                            color = YtmPalette.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Audio FX, sleep timer, dan kualitas audio",
                            color = YtmPalette.TextMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    var isCloseFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .focusRequester(closeFocusRequester)
                            .onFocusChanged { isCloseFocused = it.hasFocus }
                            .semantics { contentDescription = "Tutup menu pengaturan" },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = if (isCloseFocused) 1.5.dp else 1.dp,
                            color = if (isCloseFocused) YtmPalette.FocusRing else YtmPalette.ControlBorder
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = YtmPalette.ControlSurface,
                            contentColor = YtmPalette.TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Tutup", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = YtmPalette.SubtleDivider
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuActionTile(
                        title = "Audio FX Studio",
                        subtitle = fxSummary,
                        isHighlighted = audioFxState.isAnyEffectActive,
                        contentDescriptionText = "Buka Audio FX Studio, status saat ini $fxSummary",
                        onClick = {
                            onDismiss()
                            onOpenAudioFx()
                        }
                    )

                    MenuActionTile(
                        title = "Sleep Timer",
                        subtitle = timerSummary,
                        isHighlighted = isTimerActive,
                        contentDescriptionText = "Atur Sleep Timer, status saat ini $timerSummary",
                        onClick = {
                            onDismiss()
                            onOpenSleepTimer()
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = YtmPalette.HeaderSurface,
                        border = BorderStroke(1.dp, YtmPalette.DockBorder),
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Berlaku untuk streaming online dan unduhan offline",
                                    color = YtmPalette.TextMuted,
                                    fontSize = 12.sp
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
                                        pair.forEach { quality ->
                                            val isSelected = audioQuality == quality
                                            var isChipFocused by remember { mutableStateOf(false) }
                                            val chipBorder = when {
                                                isChipFocused -> YtmPalette.FocusRing
                                                isSelected -> YtmPalette.ActiveBorder
                                                else -> YtmPalette.DockBorder
                                            }
                                            Surface(
                                                onClick = { onAudioQualitySelected(quality) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) {
                                                    YtmPalette.CrimsonPrimary
                                                } else {
                                                    YtmPalette.ControlSurface
                                                },
                                                border = BorderStroke(
                                                    width = if (isChipFocused) 1.5.dp else 1.dp,
                                                    color = chipBorder
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 52.dp)
                                                    .onFocusChanged { isChipFocused = it.hasFocus }
                                                    .semantics(mergeDescendants = true) {
                                                        selected = isSelected
                                                        contentDescription = "${quality.displayName}, ${quality.bitrateLabel}"
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = quality.displayName,
                                                        color = if (isSelected) Color.White else YtmPalette.TextPrimary,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = quality.bitrateLabel,
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
        }
    }
}

@Composable
private fun MenuActionTile(
    title: String,
    subtitle: String,
    isHighlighted: Boolean,
    contentDescriptionText: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isFocused -> YtmPalette.FocusRing
        isHighlighted -> YtmPalette.ActiveBorder
        else -> YtmPalette.DockBorder
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isHighlighted) YtmPalette.ActiveTintBg else YtmPalette.HeaderSurface,
        border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .semantics(mergeDescendants = true) {
                contentDescription = contentDescriptionText
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = title,
                    color = YtmPalette.TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (isHighlighted) YtmPalette.AccentText else YtmPalette.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ChevronRightIcon(
                color = if (isHighlighted) YtmPalette.ActiveBorder else YtmPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun ChevronRightIcon(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 2.dp.toPx()
        val left = size.width * 0.32f
        val right = size.width * 0.72f
        val midY = size.height * 0.5f
        drawLine(
            color = color,
            start = Offset(left, size.height * 0.2f),
            end = Offset(right, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(right, midY),
            end = Offset(left, size.height * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
