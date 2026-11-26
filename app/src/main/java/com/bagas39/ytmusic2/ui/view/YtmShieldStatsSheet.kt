package com.bagas39.ytmusic2.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagas39.ytmusic2.ui.theme.YtmPalette
import kotlinx.coroutines.delay

/**
 * Sheet khusus YTM Shield yang menampilkan statistik blokir iklan lokal
 * serta toggle cepat Mode Audio Saja (hemat kuota 144p).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtmShieldStatsSheet(
    visible: Boolean,
    totalAdsSkipped: Int,
    totalSecondsSaved: Int,
    formattedSavedTime: String,
    dataSaverEnabled: Boolean,
    onDismiss: () -> Unit,
    onDataSaverToggled: (Boolean) -> Unit
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

    // Estimasi rata-rata bitrate stream video iklan 480p (~165 KB/detik)
    val dataSavedMb = String.format("%.1f MB", totalSecondsSaved * 0.165f)

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
                            text = "YTM Shield",
                            color = YtmPalette.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Statistik blokir iklan dan mode hemat kuota",
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
                            .semantics { contentDescription = "Tutup panel YTM Shield" },
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
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = YtmPalette.HeaderSurface,
                        border = BorderStroke(1.dp, YtmPalette.DockBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(YtmPalette.CrimsonPrimary)
                                )
                                Text(
                                    text = "Proteksi aktif di latar belakang",
                                    color = YtmPalette.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = totalAdsSkipped.toString(),
                                    color = YtmPalette.TextPrimary,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.alignByBaseline()
                                )
                                Text(
                                    text = "iklan dilewati",
                                    color = YtmPalette.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }

                            HorizontalDivider(
                                thickness = 0.75.dp,
                                color = YtmPalette.SubtleDivider
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Waktu dihemat",
                                        color = YtmPalette.TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = formattedSavedTime,
                                        color = YtmPalette.TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .width(1.dp)
                                        .height(30.dp)
                                        .background(YtmPalette.SubtleDivider)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Estimasi kuota (480p)",
                                        color = YtmPalette.TextMuted,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = dataSavedMb,
                                        color = YtmPalette.TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            if (totalAdsSkipped == 0) {
                                Text(
                                    text = "Statistik bertambah otomatis setiap iklan berhasil dilewati.",
                                    color = YtmPalette.TextMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    var isSaverFocused by remember { mutableStateOf(false) }
                    val saverBorder = when {
                        isSaverFocused -> YtmPalette.FocusRing
                        dataSaverEnabled -> YtmPalette.ActiveBorder
                        else -> YtmPalette.DockBorder
                    }

                    Surface(
                        onClick = { onDataSaverToggled(!dataSaverEnabled) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (dataSaverEnabled) YtmPalette.ActiveTintBg else YtmPalette.HeaderSurface,
                        border = BorderStroke(if (isSaverFocused) 1.5.dp else 1.dp, saverBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .onFocusChanged { isSaverFocused = it.hasFocus }
                            .semantics(mergeDescendants = true) {
                                selected = dataSaverEnabled
                                contentDescription = if (dataSaverEnabled) {
                                    "Mode Audio Saja aktif, ketuk untuk mematikan"
                                } else {
                                    "Mode Audio Saja nonaktif, ketuk untuk mengaktifkan"
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Mode Audio Saja",
                                    color = YtmPalette.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Kunci video ke 144p untuk menghemat kuota tanpa menurunkan kualitas audio",
                                    color = YtmPalette.TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Switch(
                                checked = dataSaverEnabled,
                                onCheckedChange = onDataSaverToggled,
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
    }
}
