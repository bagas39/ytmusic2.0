package com.bagas39.ytmusic2.player

enum class AudioQuality(
    val displayName: String,
    val ytDlpQuality: String,
    val bitrateLabel: String
) {
    AUTO("Otomatis", "5", "Adaptif"),
    LOW("Rendah", "96K", "48-96 kbps"),
    NORMAL("Normal", "192K", "128-192 kbps"),
    HIGH("Tinggi", "0", "160-320 kbps");

    companion object {
        fun fromName(name: String?): AudioQuality {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
        }
    }
}
