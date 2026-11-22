package com.bagas39.ytmusic2.audiofx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFxModelsTest {

    @Test
    fun defaultStateIsInactiveWithFlatNormalParameters() {
        val state = AudioFxState()

        assertEquals(1.0f, state.effectiveSpeed, 0.001f)
        assertEquals(1.0f, state.effectivePitch, 0.001f)
        assertEquals(0, state.clampedBassBoost)
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), state.effectiveBandGainsDb)
        assertFalse(state.isDspActive)
        assertFalse(state.isPlaybackModified)
        assertFalse(state.isAnyEffectActive)
        assertTrue(state.preservesPitchInWeb)
    }

    @Test
    fun nightcoreAndSlowedPresetsExposeExpectedSpeedPitchAndWebPitchFlags() {
        val nightcore = AudioFxState(
            speedPreset = SpeedPreset.NIGHTCORE,
            customSpeed = SpeedPreset.NIGHTCORE.speed,
            customPitch = SpeedPreset.NIGHTCORE.pitch
        )
        assertEquals(1.15f, nightcore.effectiveSpeed, 0.001f)
        assertEquals(1.15f, nightcore.effectivePitch, 0.001f)
        assertTrue(nightcore.isPlaybackModified)
        assertFalse(nightcore.preservesPitchInWeb)

        val slowed = AudioFxState(
            speedPreset = SpeedPreset.SLOWED,
            customSpeed = SpeedPreset.SLOWED.speed,
            customPitch = SpeedPreset.SLOWED.pitch,
            bassBoostStrength = SpeedPreset.SLOWED.suggestedBassBoost
        )
        assertEquals(0.85f, slowed.effectiveSpeed, 0.001f)
        assertEquals(0.85f, slowed.effectivePitch, 0.001f)
        assertEquals(350, slowed.clampedBassBoost)
        assertTrue(slowed.isDspActive)
        assertFalse(slowed.preservesPitchInWeb)
    }

    @Test
    fun clampingAndWebBassDbConversionStayWithinSafeBounds() {
        val extreme = AudioFxState(
            customSpeed = 5.0f,
            customPitch = 0.1f,
            bassBoostStrength = 2500,
            equalizerPreset = EqualizerPreset.CUSTOM,
            customBandGainsDb = listOf(-30f, 25f)
        )

        assertEquals(AudioFxState.MAX_SPEED, extreme.effectiveSpeed, 0.001f)
        assertEquals(AudioFxState.MIN_PITCH, extreme.effectivePitch, 0.001f)
        assertEquals(AudioFxState.MAX_BASS_BOOST, extreme.clampedBassBoost)
        assertEquals(12f, extreme.bassBoostDbForWeb(), 0.001f)
        assertEquals(listOf(-12f, 12f, 0f, 0f, 0f), extreme.effectiveBandGainsDb)
    }

    @Test
    fun gainDbToMillibelsClampsToHardwareBandRange() {
        assertEquals(1500.toShort(), AudioFxState.gainDbToMillibels(20f, -1500, 1500))
        assertEquals((-1000).toShort(), AudioFxState.gainDbToMillibels(-15f, -1000, 1000))
        assertEquals(450.toShort(), AudioFxState.gainDbToMillibels(4.5f, -1500, 1500))
    }

    @Test
    fun gainDbForCenterFreqHzMapsToNearestCanonicalBand() {
        val gains = listOf(6f, 4f, 0f, 1f, 2f)

        assertEquals(6f, AudioFxState.gainDbForCenterFreqHz(60, gains), 0.001f)
        assertEquals(4f, AudioFxState.gainDbForCenterFreqHz(250, gains), 0.001f)
        assertEquals(0f, AudioFxState.gainDbForCenterFreqHz(1000, gains), 0.001f)
        assertEquals(1f, AudioFxState.gainDbForCenterFreqHz(4000, gains), 0.001f)
        assertEquals(2f, AudioFxState.gainDbForCenterFreqHz(12000, gains), 0.001f)
    }

    @Test
    fun bandGainSerializationRoundTripsAndHandlesMalformedInput() {
        val encoded = AudioFxState.serializeBandGains(listOf(6f, -2.5f, 0f, 3.5f, 18f))
        val decoded = AudioFxState.deserializeBandGains(encoded)
        assertEquals(listOf(6f, -2.5f, 0f, 3.5f, 12f), decoded)

        assertEquals(listOf(0f, 0f, 0f, 0f, 0f), AudioFxState.deserializeBandGains(null))
        assertEquals(listOf(2f, 0f, 0f, 0f, 0f), AudioFxState.deserializeBandGains("2.0,invalid"))
    }
}
