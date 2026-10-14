package com.example.ytmusic2.util

object FormatUtils {

    /**
     * Mengonversi durasi milidetik ke format "mm:ss" atau "h:mm:ss".
     */
    fun formatDuration(millis: Int): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            String.format("%d:%02d:%02d", hours, remMinutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Mengonversi ukuran file byte ke representasi "X.X MB".
     */
    fun formatFileSizeMb(bytes: Long): String {
        return "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
