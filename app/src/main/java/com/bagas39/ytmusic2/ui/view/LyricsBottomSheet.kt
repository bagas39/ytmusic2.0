package com.bagas39.ytmusic2.ui.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagas39.ytmusic2.lyrics.LrcParser
import com.bagas39.ytmusic2.lyrics.LyricLine
import com.bagas39.ytmusic2.lyrics.LyricsResult
import com.bagas39.ytmusic2.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State tampilan panel lirik karaoke yang mencerminkan kondisi sebenarnya:
 * belum ada lagu diputar, sedang memuat, lirik siap ditampilkan, atau tidak ditemukan.
 */
sealed interface LyricsUiState {
    data object Idle : LyricsUiState

    data class Loading(
        val title: String,
        val artist: String
    ) : LyricsUiState

    data class Ready(
        val result: LyricsResult,
        val isOfflineMode: Boolean,
        val isSavedLocally: Boolean
    ) : LyricsUiState

    data class NotFound(
        val title: String,
        val artist: String,
        val reason: String
    ) : LyricsUiState
}

/**
 * Palet Obsidian Crimson dengan rasio kontras terverifikasi >= 4.5:1 untuk teks
 * dan >= 3.0:1 untuk batas kontrol interaktif serta ring fokus.
 */
private object LyricsPalette {
    val SheetBg = Color(0xFF111116)
    val HeaderSurface = Color(0xFF1B1B22)
    val ControlSurface = Color(0xFF24242E)
    val ControlBorder = Color(0xFF6E6E84)
    val SubtleDivider = Color(0xFF2B2B36)
    val ActiveLineBg = Color(0xFF291219)
    val ActiveLineBorder = Color(0xFFFF4D6D)
    val CrimsonPrimary = Color(0xFFC80028)
    val AccentText = Color(0xFFFF8FA3)
    val AccentTextOnDark = Color(0xFFFF758F)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextUpcoming = Color(0xFFC8C8D4)
    val TextMuted = Color(0xFF9696A8)
    val FocusRing = Color(0xFFF5F5F7)
}

/**
 * Panel Bottom Sheet Lirik Karaoke real-time untuk mode pemutaran Online maupun Offline.
 * Mendukung sorotan baris aktif, auto-scroll dengan jeda otomatis saat pengguna menggulir manual,
 * tap-to-seek pada setiap baris bertimestamp, pencarian manual kata kunci, dan navigasi keyboard/TV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeLyricsBottomSheet(
    visible: Boolean,
    uiState: LyricsUiState,
    trackTitle: String,
    trackArtist: String,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onManualSearch: (title: String, artist: String) -> Unit,
    onSeekTo: (positionMs: Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    var isRendered by remember { mutableStateOf(visible) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isManualSearchOpen by remember { mutableStateOf(false) }
    val headerFocusRequester = remember { FocusRequester() }

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
                isManualSearchOpen = false
            }
        }
    }

    LaunchedEffect(isRendered) {
        if (isRendered) {
            delay(150L)
            try {
                headerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (!visible && !isRendered) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LyricsPalette.SheetBg,
        contentColor = LyricsPalette.TextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = LyricsPalette.ControlBorder,
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
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                LyricsSheetHeader(
                    uiState = uiState,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    isManualSearchOpen = isManualSearchOpen,
                    initialFocusRequester = headerFocusRequester,
                    onToggleManualSearch = { isManualSearchOpen = !isManualSearchOpen },
                    onDismiss = onDismiss
                )

                AnimatedVisibility(
                    visible = isManualSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ManualLyricsSearchForm(
                        initialTitle = trackTitle,
                        initialArtist = trackArtist,
                        onSubmit = { queryTitle, queryArtist ->
                            isManualSearchOpen = false
                            onManualSearch(queryTitle, queryArtist)
                        },
                        onCancel = { isManualSearchOpen = false }
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = LyricsPalette.SubtleDivider)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (uiState) {
                        is LyricsUiState.Idle -> {
                            LyricsEmptyState(
                                onOpenManualSearch = { isManualSearchOpen = true },
                                onDismiss = onDismiss
                            )
                        }
                        is LyricsUiState.Loading -> {
                            LyricsLoadingState(
                                title = uiState.title,
                                artist = uiState.artist
                            )
                        }
                        is LyricsUiState.NotFound -> {
                            LyricsNotFoundState(
                                title = uiState.title,
                                reason = uiState.reason,
                                onRetry = onRetry,
                                onOpenManualSearch = { isManualSearchOpen = true }
                            )
                        }
                        is LyricsUiState.Ready -> {
                            LyricsSyncedContent(
                                result = uiState.result,
                                currentPositionMs = currentPositionMs,
                                onSeekTo = onSeekTo
                            )
                        }
                    }
                }

                if (!isManualSearchOpen && (trackTitle.isNotBlank() || durationMs > 0L)) {
                    LyricsPlaybackFooter(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeekTo = onSeekTo,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricsSheetHeader(
    uiState: LyricsUiState,
    trackTitle: String,
    trackArtist: String,
    isManualSearchOpen: Boolean,
    initialFocusRequester: FocusRequester,
    onToggleManualSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayTitle = when (uiState) {
        is LyricsUiState.Ready -> uiState.result.title.ifBlank { trackTitle }
        is LyricsUiState.Loading -> uiState.title.ifBlank { trackTitle }
        is LyricsUiState.NotFound -> uiState.title.ifBlank { trackTitle }
        is LyricsUiState.Idle -> "Lirik Lagu"
    }
    val displayArtist = when (uiState) {
        is LyricsUiState.Ready -> uiState.result.artist.ifBlank { trackArtist }
        is LyricsUiState.Loading -> uiState.artist.ifBlank { trackArtist }
        is LyricsUiState.NotFound -> uiState.artist.ifBlank { trackArtist }
        is LyricsUiState.Idle -> ""
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
                text = displayTitle.ifBlank { "Lirik Lagu" },
                color = LyricsPalette.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )

            if (displayArtist.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayArtist,
                    color = LyricsPalette.TextMuted,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uiState is LyricsUiState.Ready) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sourceLabel = when {
                        uiState.isOfflineMode && uiState.isSavedLocally -> "Offline (.lrc lokal)"
                        uiState.isOfflineMode -> "Pemutar Offline"
                        trackTitle.isBlank() -> "Pencarian Manual"
                        else -> "Streaming Online"
                    }
                    StatusTag(text = sourceLabel, isAccent = false)

                    val syncLabel = if (uiState.result.isSynced) {
                        "Karaoke Sinkron"
                    } else {
                        "Teks Polos"
                    }
                    StatusTag(text = syncLabel, isAccent = uiState.result.isSynced)
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LyricsFocusOutlinedButton(
                text = "Cari",
                onClick = onToggleManualSearch,
                isActive = isManualSearchOpen,
                contentDescriptionText = "Cari lirik manual"
            )

            LyricsFocusOutlinedButton(
                text = "Tutup",
                onClick = onDismiss,
                modifier = Modifier.focusRequester(initialFocusRequester),
                contentDescriptionText = "Tutup panel lirik"
            )
        }
    }
}

@Composable
private fun StatusTag(text: String, isAccent: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAccent) LyricsPalette.ActiveLineBg else LyricsPalette.HeaderSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isAccent) LyricsPalette.ActiveLineBorder else LyricsPalette.ControlBorder
        )
    ) {
        Text(
            text = text,
            color = if (isAccent) LyricsPalette.AccentText else LyricsPalette.TextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ManualLyricsSearchForm(
    initialTitle: String,
    initialArtist: String,
    onSubmit: (title: String, artist: String) -> Unit,
    onCancel: () -> Unit
) {
    var titleQuery by remember { mutableStateOf(initialTitle) }
    var artistQuery by remember { mutableStateOf(initialArtist) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = LyricsPalette.TextPrimary,
        unfocusedTextColor = LyricsPalette.TextPrimary,
        focusedBorderColor = LyricsPalette.ActiveLineBorder,
        unfocusedBorderColor = LyricsPalette.ControlBorder,
        focusedLabelColor = LyricsPalette.AccentTextOnDark,
        unfocusedLabelColor = LyricsPalette.TextMuted,
        cursorColor = LyricsPalette.AccentTextOnDark,
        focusedContainerColor = LyricsPalette.HeaderSurface,
        unfocusedContainerColor = LyricsPalette.HeaderSurface
    )

    Surface(
        color = LyricsPalette.HeaderSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = titleQuery,
                onValueChange = { titleQuery = it },
                label = { Text("Judul lagu") },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = artistQuery,
                onValueChange = { artistQuery = it },
                label = { Text("Artis (opsional)") },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (titleQuery.isNotBlank()) {
                            onSubmit(titleQuery.trim(), artistQuery.trim())
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LyricsPalette.ControlBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = LyricsPalette.ControlSurface,
                        contentColor = LyricsPalette.TextPrimary
                    )
                ) {
                    Text("Batal", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (titleQuery.isNotBlank()) {
                            onSubmit(titleQuery.trim(), artistQuery.trim())
                        }
                    },
                    enabled = titleQuery.isNotBlank(),
                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LyricsPalette.CrimsonPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Cari Lirik", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LyricsSyncedContent(
    result: LyricsResult,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val activeIndex by remember(result.lines, currentPositionMs) {
        derivedStateOf {
            if (result.isSynced) {
                LrcParser.findActiveLineIndex(result.lines, currentPositionMs)
            } else {
                -1
            }
        }
    }

    var isAutoScrollPaused by remember(result) { mutableStateOf(false) }
    var focusedLineIndex by remember(result) { mutableStateOf(-1) }
    var isProgrammaticScroll by remember(result) { mutableStateOf(false) }

    LaunchedEffect(result) {
        if (activeIndex < 0) {
            listState.scrollToItem(0)
        }
    }

    // Deteksi guliran manual pengguna agar tidak berebut posisi dengan auto-scroll
    LaunchedEffect(listState.isScrollInProgress, isProgrammaticScroll) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            isAutoScrollPaused = true
        }
    }

    LaunchedEffect(isAutoScrollPaused, focusedLineIndex) {
        if (isAutoScrollPaused && focusedLineIndex < 0) {
            delay(4000L)
            isAutoScrollPaused = false
        }
    }

    LaunchedEffect(activeIndex, isAutoScrollPaused, focusedLineIndex, result) {
        if (result.isSynced && activeIndex >= 0 && !isAutoScrollPaused && focusedLineIndex < 0) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.takeIf { it > 0 }
                ?: snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val upperThirdOffset = -(viewportHeight / 4)
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = upperThirdOffset
                )
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!result.isSynced) {
            Surface(
                color = LyricsPalette.HeaderSurface,
                border = BorderStroke(1.dp, LyricsPalette.ControlBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Lirik tanpa penanda waktu (mode baca). Ketuk baris tidak menggeser detik lagu.",
                    color = LyricsPalette.TextUpcoming,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 12.dp,
                    bottom = 76.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = result.lines,
                    key = { index, line -> "$index:${line.timeMs}" }
                ) { index, line ->
                    val isActive = result.isSynced && index == activeIndex
                    val isPast = result.isSynced && activeIndex >= 0 && index < activeIndex
                    LyricLineItem(
                        line = line,
                        isSynced = result.isSynced,
                        isActive = isActive,
                        isPast = isPast,
                        onFocusChanged = { hasFocus ->
                            // Jeda auto-scroll saat D-Pad menjelajahi baris agar baris terfokus tidak terbuang keluar layar
                            if (hasFocus) {
                                focusedLineIndex = index
                                if (result.isSynced) {
                                    isAutoScrollPaused = true
                                }
                            } else if (focusedLineIndex == index) {
                                focusedLineIndex = -1
                            }
                        },
                        onClick = {
                            if (result.isSynced && line.timeMs >= 0L) {
                                focusedLineIndex = -1
                                isAutoScrollPaused = false
                                onSeekTo(line.timeMs)
                            }
                        }
                    )
                }
            }

            if (result.isSynced && isAutoScrollPaused && activeIndex >= 0) {
                var isFollowFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        focusedLineIndex = -1
                        isAutoScrollPaused = false
                        coroutineScope.launch {
                            val viewportHeight = listState.layoutInfo.viewportSize.height
                            val upperThirdOffset = if (viewportHeight > 0) -(viewportHeight / 4) else 0
                            isProgrammaticScroll = true
                            try {
                                listState.animateScrollToItem(activeIndex, upperThirdOffset)
                            } finally {
                                isProgrammaticScroll = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(22.dp),
                    border = if (isFollowFocused) BorderStroke(1.5.dp, LyricsPalette.FocusRing) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LyricsPalette.CrimsonPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .onFocusChanged { isFollowFocused = it.hasFocus }
                ) {
                    Text(
                        text = "Ikuti Lirik Aktif",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isSynced: Boolean,
    isActive: Boolean,
    isPast: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val animatedBg by animateColorAsState(
        targetValue = if (isActive) LyricsPalette.ActiveLineBg else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "lyricRowBg"
    )

    val borderStroke = when {
        isFocused -> BorderStroke(1.5.dp, LyricsPalette.FocusRing)
        isActive -> BorderStroke(1.dp, LyricsPalette.ActiveLineBorder)
        else -> null
    }

    val textColor = when {
        isActive -> LyricsPalette.TextPrimary
        isPast -> LyricsPalette.TextMuted
        else -> LyricsPalette.TextUpcoming
    }

    val textSize = if (isActive) 20.sp else 16.sp
    val textLineHeight = if (isActive) 28.sp else 23.sp
    val textWeight = when {
        isActive -> FontWeight.Bold
        !isPast -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val isInteractiveLine = isSynced && line.timeMs >= 0L
    val timeLabel = if (isInteractiveLine) {
        FormatUtils.formatDuration(line.timeMs.toInt())
    } else {
        ""
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .onFocusChanged { focusState ->
            isFocused = focusState.hasFocus
            onFocusChanged(focusState.hasFocus)
        }
        .then(
            if (isInteractiveLine) {
                Modifier.semantics {
                    selected = isActive
                    contentDescription = "Detik $timeLabel, ${line.text}, ketuk untuk lompat ke bagian ini"
                }
            } else {
                Modifier
                    .focusable()
                    .semantics {
                        contentDescription = line.text
                    }
            }
        )

    if (isInteractiveLine) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = animatedBg,
            border = borderStroke,
            modifier = rowModifier
        ) {
            LyricLineContent(
                timeLabel = timeLabel,
                text = line.text,
                isActive = isActive,
                textColor = textColor,
                textSize = textSize,
                textLineHeight = textLineHeight,
                textWeight = textWeight
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = if (isFocused) BorderStroke(1.5.dp, LyricsPalette.FocusRing) else null,
            modifier = rowModifier
        ) {
            LyricLineContent(
                timeLabel = "",
                text = line.text,
                isActive = false,
                textColor = textColor,
                textSize = textSize,
                textLineHeight = textLineHeight,
                textWeight = textWeight
            )
        }
    }
}

@Composable
private fun LyricLineContent(
    timeLabel: String,
    text: String,
    isActive: Boolean,
    textColor: Color,
    textSize: androidx.compose.ui.unit.TextUnit,
    textLineHeight: androidx.compose.ui.unit.TextUnit,
    textWeight: FontWeight
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (timeLabel.isNotEmpty()) {
            Text(
                text = timeLabel,
                color = if (isActive) LyricsPalette.AccentText else LyricsPalette.TextMuted,
                fontSize = 11.5.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(46.dp)
            )
        }

        Text(
            text = text,
            color = textColor,
            fontSize = textSize,
            lineHeight = textLineHeight,
            fontWeight = textWeight,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LyricsLoadingState(
    title: String,
    artist: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = LyricsPalette.AccentTextOnDark,
            trackColor = LyricsPalette.ControlSurface,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (title.isNotBlank()) "Mencari lirik untuk \"$title\"..." else "Mencari lirik lagu...",
            color = LyricsPalette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        if (artist.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                color = LyricsPalette.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LyricsEmptyState(
    onOpenManualSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Belum ada lagu yang diputar",
            color = LyricsPalette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Putar lagu di YouTube Music atau pilih lagu dari daftar Offline untuk memuat liriknya secara otomatis.",
            color = LyricsPalette.TextMuted,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onOpenManualSearch,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LyricsPalette.ControlBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LyricsPalette.ControlSurface,
                    contentColor = LyricsPalette.TextPrimary
                )
            ) {
                Text("Cari Manual", fontSize = 13.sp)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LyricsPalette.CrimsonPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("Tutup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LyricsNotFoundState(
    title: String,
    reason: String,
    onRetry: () -> Unit,
    onOpenManualSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lirik tidak ditemukan",
            color = LyricsPalette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        val detailMessage = if (reason.isNotBlank()) {
            reason
        } else {
            "Tidak ada kecocokan untuk \"$title\". Coba periksa koneksi internet atau sesuaikan judul lagu."
        }
        Text(
            text = detailMessage,
            color = LyricsPalette.TextMuted,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, LyricsPalette.ControlBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LyricsPalette.ControlSurface,
                    contentColor = LyricsPalette.TextPrimary
                )
            ) {
                Text("Coba Lagi", fontSize = 13.sp)
            }
            Button(
                onClick = onOpenManualSearch,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LyricsPalette.CrimsonPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("Ubah Kata Kunci", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LyricsPlaybackFooter(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValueMs by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1000L).toFloat()
    val shownPosMs = if (isDragging) dragValueMs.toLong() else currentPositionMs.coerceIn(0L, safeDuration.toLong())

    Surface(
        color = LyricsPalette.HeaderSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            HorizontalDivider(thickness = 1.dp, color = LyricsPalette.SubtleDivider)
            Spacer(modifier = Modifier.height(8.dp))

            if (durationMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FormatUtils.formatDuration(shownPosMs.toInt()),
                        color = LyricsPalette.TextUpcoming,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Slider(
                        value = shownPosMs.toFloat().coerceIn(0f, safeDuration),
                        onValueChange = { newVal ->
                            isDragging = true
                            dragValueMs = newVal
                        },
                        onValueChangeFinished = {
                            val targetMs = dragValueMs.toLong()
                            isDragging = false
                            onSeekTo(targetMs)
                        },
                        valueRange = 0f..safeDuration,
                        colors = SliderDefaults.colors(
                            thumbColor = LyricsPalette.AccentTextOnDark,
                            activeTrackColor = LyricsPalette.CrimsonPrimary,
                            inactiveTrackColor = LyricsPalette.ControlSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        text = FormatUtils.formatDuration(durationMs.toInt()),
                        color = LyricsPalette.TextMuted,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            var isPlayFocused by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricsFocusOutlinedButton(
                    text = "Prev",
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                )

                Button(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .weight(1.3f)
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .onFocusChanged { isPlayFocused = it.hasFocus },
                    shape = RoundedCornerShape(12.dp),
                    border = if (isPlayFocused) BorderStroke(1.5.dp, LyricsPalette.FocusRing) else null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LyricsPalette.CrimsonPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = if (isPlaying) "Jeda" else "Putar",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                LyricsFocusOutlinedButton(
                    text = "Next",
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Tombol aksi sekunder di dalam panel lirik dengan cincin fokus D-Pad yang jelas untuk Android TV.
 */
@Composable
private fun LyricsFocusOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    contentDescriptionText: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isFocused -> LyricsPalette.FocusRing
        isActive -> LyricsPalette.ActiveLineBorder
        else -> LyricsPalette.ControlBorder
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .then(
                if (contentDescriptionText != null) {
                    Modifier.semantics { contentDescription = contentDescriptionText }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        border = BorderStroke(if (isFocused) 1.5.dp else 1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isActive) LyricsPalette.ActiveLineBg else LyricsPalette.ControlSurface,
            contentColor = if (isActive) LyricsPalette.AccentText else LyricsPalette.TextPrimary
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
