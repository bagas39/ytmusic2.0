package com.bagas39.ytmusic2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YtmDarkColorScheme = darkColorScheme(
    primary = YtmPalette.CrimsonPrimary,
    onPrimary = Color.White,
    surface = YtmPalette.ObsidianBg,
    onSurface = YtmPalette.TextPrimary,
    secondaryContainer = YtmPalette.ControlSurface,
    onSecondaryContainer = YtmPalette.TextPrimary
)

@Composable
fun YtmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YtmDarkColorScheme,
        typography = YtmTypography,
        content = content
    )
}
