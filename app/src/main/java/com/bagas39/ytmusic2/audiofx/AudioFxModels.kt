package com.bagas39.ytmusic2.audiofx

import kotlin.math.abs
import kotlin.math.roundToInt

enum class SpeedPreset(
    val displayName: String,
    val speed: Float,
    val pitch: Float,
    val suggestedBassBoost: Int,
    val preservesPitchInWeb: Boolean,
    val toneCompensationDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)
) {
    NORMAL("Normal (1.0x)", 1.0f, 1.0f, 0, true),
    NIGHTCORE(
        displayName = "Nightcore (1.12x)",
        speed = 1.12f,
        pitch = 1.12f,
        suggestedBassBoost = 200,
        preservesPitchInWeb = false,
        toneCompensationDb = listOf(2.0f, 1.0f, 0f, -2.0f, -1.5f)
    ),
    SLOWED("Slowed / Chill (0.85x)", 0.85f, 0.85f, 350, false);

    companion object {
        fun fromName(name: String?): SpeedPreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class EqualizerPreset(
    val displayName: String,
    val bandGainsDb: List<Float>
) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("Bass Boost", listOf(6f, 4f, 0f, 1f, 2f)),
    VOCAL("Vokal Jernih", listOf(-1f, 1f, 4f, 3f, 1f)),
    ROCK("Rock", listOf(4f, 2f, -1f, 3f, 4f)),
    POP("Pop", listOf(-1f, 2f, 4f, 2f, -1f)),
    CUSTOM("Kustom", listOf(0f, 0f, 0f, 0f, 0f));

    companion object {
        val CANONICAL_FREQUENCIES_HZ: List<Int> = listOf(60, 230, 910, 3600, 14000)

        fun fromName(name: String?): EqualizerPreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FLAT
        }
    }
}

data class AudioFxState(
    val speedPreset: SpeedPreset = SpeedPreset.NORMAL,
    val customSpeed: Float = 1.0f,
    val customPitch: Float = 1.0f,
    val bassBoostStrength: Int = 0,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val customBandGainsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val volumeNormalizerEnabled: Boolean = false
) {
    val effectiveSpeed: Float
        get() = customSpeed.coerceIn(MIN_SPEED, MAX_SPEED)

    val effectivePitch: Float
        get() = customPitch.coerceIn(MIN_PITCH, MAX_PITCH)

    val clampedBassBoost: Int
        get() = bassBoostStrength.coerceIn(0, MAX_BASS_BOOST)

    val effectiveBandGainsDb: List<Float>
        get() {
            val raw = if (equalizerPreset == EqualizerPreset.CUSTOM) {
                customBandGainsDb
            } else {
                equalizerPreset.bandGainsDb
            }
            return List(BAND_COUNT) { idx ->
                (raw.getOrNull(idx) ?: 0f).coerceIn(MIN_EQ_DB, MAX_EQ_DB)
            }
        }

    val needsNightcoreSmoothing: Boolean
        get() = speedPreset == SpeedPreset.NIGHTCORE || effectivePitch > 1.04f

    val activeToneCompensationDb: List<Float>
        get() = when {
            speedPreset == SpeedPreset.NIGHTCORE -> SpeedPreset.NIGHTCORE.toneCompensationDb
            effectivePitch > 1.04f -> {
                val scale = ((effectivePitch - 1.0f) / 0.12f).coerceIn(0f, 1.25f)
                SpeedPreset.NIGHTCORE.toneCompensationDb.map { it * scale }
            }
            else -> speedPreset.toneCompensationDb
        }

    val effectiveDspBandGainsDb: List<Float>
        get() {
            val base = effectiveBandGainsDb
            val comp = activeToneCompensationDb
            return List(BAND_COUNT) { idx ->
                ((base.getOrNull(idx) ?: 0f) + (comp.getOrNull(idx) ?: 0f)).coerceIn(MIN_EQ_DB, MAX_EQ_DB)
            }
        }

    val isDspActive: Boolean
        get() = clampedBassBoost > 0 ||
            volumeNormalizerEnabled ||
            needsNightcoreSmoothing ||
            effectiveDspBandGainsDb.any { abs(it) > 0.05f }

    val isPlaybackModified: Boolean
        get() = abs(effectiveSpeed - 1.0f) > 0.01f || abs(effectivePitch - 1.0f) > 0.01f

    val isAnyEffectActive: Boolean
        get() = isDspActive || isPlaybackModified

    val preservesPitchInWeb: Boolean
        get() = abs(effectivePitch - effectiveSpeed) > 0.02f ||
            (abs(effectivePitch - 1.0f) <= 0.01f && speedPreset.preservesPitchInWeb)

    fun bassBoostDbForWeb(): Float = (clampedBassBoost / MAX_BASS_BOOST.toFloat()) * MAX_WEB_BASS_DB

    companion object {
        const val BAND_COUNT = 5
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
        const val MAX_BASS_BOOST = 1000
        const val MIN_EQ_DB = -12f
        const val MAX_EQ_DB = 12f
        const val MAX_WEB_BASS_DB = 12f
        const val NORMALIZER_TARGET_GAIN_MB = 350

        fun gainDbToMillibels(gainDb: Float, minMb: Int, maxMb: Int): Short {
            val safeMin = minOf(minMb, maxMb)
            val safeMax = maxOf(minMb, maxMb)
            val mb = (gainDb * 100f).roundToInt().coerceIn(safeMin, safeMax)
            return mb.toShort()
        }

        // Maps an arbitrary hardware EQ band center frequency to the closest canonical 5-band gain.
        fun gainDbForCenterFreqHz(centerFreqHz: Int, canonicalGainsDb: List<Float>): Float {
            val freqs = EqualizerPreset.CANONICAL_FREQUENCIES_HZ
            if (canonicalGainsDb.isEmpty()) return 0f
            var bestIdx = 0
            var bestDist = Int.MAX_VALUE
            for (i in freqs.indices) {
                val dist = abs(centerFreqHz - freqs[i])
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            return (canonicalGainsDb.getOrNull(bestIdx) ?: 0f).coerceIn(MIN_EQ_DB, MAX_EQ_DB)
        }

        fun serializeBandGains(gains: List<Float>): String {
            return List(BAND_COUNT) { idx ->
                (gains.getOrNull(idx) ?: 0f).coerceIn(MIN_EQ_DB, MAX_EQ_DB)
            }.joinToString(",")
        }

        fun deserializeBandGains(raw: String?): List<Float> {
            if (raw.isNullOrBlank()) return List(BAND_COUNT) { 0f }
            val parts = raw.split(",")
            return List(BAND_COUNT) { idx ->
                (parts.getOrNull(idx)?.trim()?.toFloatOrNull() ?: 0f).coerceIn(MIN_EQ_DB, MAX_EQ_DB)
            }
        }
    }
}
