package com.bagas39.ytmusic2.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagas39.ytmusic2.audiofx.AudioFxState
import com.bagas39.ytmusic2.audiofx.EqualizerPreset
import com.bagas39.ytmusic2.audiofx.SpeedPreset
import com.bagas39.ytmusic2.ui.theme.YtmPalette
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Panel kontrol Audio FX Studio (Normal / Nightcore / Slowed, Bass Boost,
 * Equalizer 5-band, dan Volume Normalizer) untuk pemutar Offline & Online.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioFxStudioSheet(
    visible: Boolean,
    state: AudioFxState,
    onDismiss: () -> Unit,
    onSelectSpeedPreset: (SpeedPreset) -> Unit,
    onBassBoostChanged: (Int) -> Unit,
    onSelectEqualizerPreset: (EqualizerPreset) -> Unit,
    onEqualizerBandChanged: (bandIndex: Int, gainDb: Float) -> Unit,
    onVolumeNormalizerToggled: (Boolean) -> Unit,
    onResetAll: () -> Unit
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
                    color = YtmPalette.ActiveBorder,
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
                AudioFxHeader(
                    state = state,
                    closeFocusRequester = closeFocusRequester,
                    onResetAll = onResetAll,
                    onDismiss = onDismiss
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = YtmPalette.CrimsonHairline.copy(alpha = 0.55f)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SpeedPresetSection(
                        state = state,
                        onSelectSpeedPreset = onSelectSpeedPreset
                    )

                    BassAndNormalizerCard(
                        state = state,
                        onBassBoostChanged = onBassBoostChanged,
                        onVolumeNormalizerToggled = onVolumeNormalizerToggled
                    )

                    EqualizerCard(
                        state = state,
                        onSelectEqualizerPreset = onSelectEqualizerPreset,
                        onEqualizerBandChanged = onEqualizerBandChanged
                    )

                    Text(
                        text = "Efek berlaku real-time untuk pemutar Offline dan streaming Online (MSE).",
                        color = YtmPalette.TextMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioFxHeader(
    state: AudioFxState,
    closeFocusRequester: FocusRequester,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val summaryParts = buildList {
        add(state.speedPreset.displayName)
        if (state.clampedBassBoost > 0) add("Bass ${state.clampedBassBoost / 10}%")
        if (state.equalizerPreset != EqualizerPreset.FLAT) add("EQ ${state.equalizerPreset.displayName}")
        if (state.volumeNormalizerEnabled) add("Normalizer aktif")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Audio FX Studio",
                color = YtmPalette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summaryParts.joinToString(" · "),
                color = if (state.isAnyEffectActive) YtmPalette.AccentText else YtmPalette.TextMuted,
                fontSize = 12.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioFxHeaderButton(
                text = "Reset",
                enabled = state.isAnyEffectActive,
                contentDescriptionText = "Reset semua efek audio ke normal",
                onClick = onResetAll
            )

            AudioFxHeaderButton(
                text = "Tutup",
                enabled = true,
                contentDescriptionText = "Tutup Audio FX Studio",
                modifier = Modifier.focusRequester(closeFocusRequester),
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun AudioFxHeaderButton(
    text: String,
    enabled: Boolean,
    contentDescriptionText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        !enabled -> YtmPalette.SubtleDivider
        isFocused -> YtmPalette.FocusRing
        else -> YtmPalette.ControlBorder
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .semantics { contentDescription = contentDescriptionText },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = YtmPalette.ControlSurface,
            contentColor = YtmPalette.TextPrimary,
            disabledContainerColor = YtmPalette.HeaderSurface,
            disabledContentColor = YtmPalette.TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedPresetSection(
    state: AudioFxState,
    onSelectSpeedPreset: (SpeedPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mode Kecepatan & Nada",
                color = YtmPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = String.format(Locale.US, "%.2fx", state.effectiveSpeed),
                color = if (state.isPlaybackModified) YtmPalette.AccentText else YtmPalette.TextMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpeedPreset.entries.forEach { preset ->
                val isSelected = state.speedPreset == preset
                var isFocused by remember { mutableStateOf(false) }
                val title = when (preset) {
                    SpeedPreset.NORMAL -> "Normal"
                    SpeedPreset.NIGHTCORE -> "Nightcore"
                    SpeedPreset.SLOWED -> "Slowed"
                }
                val subtitle = when (preset) {
                    SpeedPreset.NORMAL -> "1.0x"
                    SpeedPreset.NIGHTCORE -> "1.12x · Pitch +"
                    SpeedPreset.SLOWED -> "0.85x · Bass"
                }
                val borderColor = when {
                    isFocused -> YtmPalette.FocusRing
                    isSelected -> YtmPalette.ActiveBorder
                    else -> YtmPalette.ControlBorder
                }

                Surface(
                    onClick = { onSelectSpeedPreset(preset) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) YtmPalette.CrimsonPrimary else YtmPalette.ControlSurface,
                    border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .onFocusChanged { isFocused = it.hasFocus }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            contentDescription = "Mode ${preset.displayName}"
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else YtmPalette.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            color = if (isSelected) YtmPalette.CrimsonSubtext else YtmPalette.TextMutedOnControl,
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

@Composable
private fun BassAndNormalizerCard(
    state: AudioFxState,
    onBassBoostChanged: (Int) -> Unit,
    onVolumeNormalizerToggled: (Boolean) -> Unit
) {
    val bassPercent = (state.clampedBassBoost / 10).coerceIn(0, 100)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = YtmPalette.HeaderSurface,
        border = BorderStroke(1.dp, YtmPalette.ControlBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Boost",
                        color = YtmPalette.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$bassPercent%",
                        color = if (bassPercent > 0) YtmPalette.AccentText else YtmPalette.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Slider(
                    value = state.clampedBassBoost.toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / 50f).roundToInt() * 50).coerceIn(0, AudioFxState.MAX_BASS_BOOST)
                        onBassBoostChanged(snapped)
                    },
                    valueRange = 0f..AudioFxState.MAX_BASS_BOOST.toFloat(),
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = YtmPalette.ActiveBorder,
                        activeTrackColor = YtmPalette.CrimsonPrimary,
                        inactiveTrackColor = YtmPalette.ControlSurface,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Pengatur Bass Boost, saat ini $bassPercent persen" }
                )
            }

            HorizontalDivider(thickness = 1.dp, color = YtmPalette.SubtleDivider)

            var isSwitchRowFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onVolumeNormalizerToggled(!state.volumeNormalizerEnabled) },
                color = Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                border = if (isSwitchRowFocused) BorderStroke(1.5.dp, YtmPalette.FocusRing) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .onFocusChanged { isSwitchRowFocused = it.hasFocus }
                    .semantics(mergeDescendants = true) {
                        selected = state.volumeNormalizerEnabled
                        contentDescription = if (state.volumeNormalizerEnabled) {
                            "Volume Normalizer aktif, ketuk untuk mematikan"
                        } else {
                            "Volume Normalizer nonaktif, ketuk untuk mengaktifkan"
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Volume Normalizer",
                            color = YtmPalette.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Menjaga tingkat kenyaringan antar-lagu tetap seimbang",
                            color = YtmPalette.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = state.volumeNormalizerEnabled,
                        onCheckedChange = onVolumeNormalizerToggled,
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerCard(
    state: AudioFxState,
    onSelectEqualizerPreset: (EqualizerPreset) -> Unit,
    onEqualizerBandChanged: (bandIndex: Int, gainDb: Float) -> Unit
) {
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val gains = state.effectiveBandGainsDb

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = YtmPalette.HeaderSurface,
        border = BorderStroke(1.dp, YtmPalette.ControlBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Equalizer 5-Band",
                    color = YtmPalette.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = state.equalizerPreset.displayName,
                    color = if (state.equalizerPreset != EqualizerPreset.FLAT) {
                        YtmPalette.AccentText
                    } else {
                        YtmPalette.TextMuted
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqualizerPreset.entries
                    .filter { it != EqualizerPreset.CUSTOM }
                    .forEach { preset ->
                        val isSelected = state.equalizerPreset == preset
                        var isFocused by remember { mutableStateOf(false) }
                        val borderColor = when {
                            isFocused -> YtmPalette.FocusRing
                            isSelected -> YtmPalette.ActiveBorder
                            else -> YtmPalette.ControlBorder
                        }

                        Surface(
                            onClick = { onSelectEqualizerPreset(preset) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) YtmPalette.ActiveTintBg else YtmPalette.ControlSurface,
                            border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .onFocusChanged { isFocused = it.hasFocus }
                                .semantics(mergeDescendants = true) {
                                    selected = isSelected
                                    contentDescription = "Preset equalizer ${preset.displayName}"
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = preset.displayName,
                                    color = if (isSelected) YtmPalette.AccentText else YtmPalette.TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
            }

            HorizontalDivider(thickness = 1.dp, color = YtmPalette.SubtleDivider)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bandLabels.forEachIndexed { index, freqLabel ->
                    val gainDb = gains.getOrElse(index) { 0f }
                    val roundedDb = gainDb.roundToInt()
                    val gainText = if (roundedDb > 0) "+$roundedDb dB" else "$roundedDb dB"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = freqLabel,
                            color = YtmPalette.TextSecondary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(54.dp)
                        )

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(10.dp)
                                    .background(YtmPalette.ControlBorder)
                            )
                            Slider(
                                value = gainDb,
                                onValueChange = { raw ->
                                    onEqualizerBandChanged(index, raw.roundToInt().toFloat())
                                },
                                valueRange = AudioFxState.MIN_EQ_DB..AudioFxState.MAX_EQ_DB,
                                steps = 23,
                                colors = SliderDefaults.colors(
                                    thumbColor = YtmPalette.ActiveBorder,
                                    activeTrackColor = YtmPalette.CrimsonPrimary,
                                    inactiveTrackColor = YtmPalette.ControlSurface,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        contentDescription = "Equalizer $freqLabel, saat ini $gainText"
                                    }
                            )
                        }

                        Text(
                            text = gainText,
                            color = if (roundedDb != 0) YtmPalette.AccentText else YtmPalette.TextMuted,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }
        }
    }
}
