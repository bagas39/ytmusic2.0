package com.bagas39.ytmusic2.audiofx

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Mengelola efek audio real-time (Bass Boost, 5-Band Equalizer, Volume Normalizer,
 * serta preset Speed/Pitch seperti Nightcore dan Slowed) untuk pemutar Offline MediaPlayer
 * maupun WebView YouTube Music via WebAudio API.
 */
class AudioFxManager(
    private val context: Context,
    private val webView: WebView
) {

    var onStateChanged: ((AudioFxState) -> Unit)? = null

    var state: AudioFxState = loadPersistedState()
        private set

    private var attachedSessionId: Int = -1
    private var attachedMediaPlayerRef: WeakReference<MediaPlayer>? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun applySpeedPreset(preset: SpeedPreset) {
        val usingSuggestedBass = state.clampedBassBoost == state.speedPreset.suggestedBassBoost
        val nextBass = when {
            usingSuggestedBass -> preset.suggestedBassBoost
            preset == SpeedPreset.SLOWED || preset == SpeedPreset.NIGHTCORE -> {
                maxOf(state.clampedBassBoost, preset.suggestedBassBoost)
            }
            else -> state.clampedBassBoost
        }
        updateState(
            state.copy(
                speedPreset = preset,
                customSpeed = preset.speed,
                customPitch = preset.pitch,
                bassBoostStrength = nextBass
            )
        )
    }

    fun setCustomSpeedAndPitch(speed: Float, pitch: Float) {
        val clampedSpeed = speed.coerceIn(AudioFxState.MIN_SPEED, AudioFxState.MAX_SPEED)
        val clampedPitch = pitch.coerceIn(AudioFxState.MIN_PITCH, AudioFxState.MAX_PITCH)
        val matchedPreset = SpeedPreset.entries.firstOrNull {
            kotlin.math.abs(it.speed - clampedSpeed) < 0.01f &&
                kotlin.math.abs(it.pitch - clampedPitch) < 0.01f
        } ?: SpeedPreset.NORMAL
        updateState(
            state.copy(
                speedPreset = matchedPreset,
                customSpeed = clampedSpeed,
                customPitch = clampedPitch
            )
        )
    }

    fun setBassBoostStrength(strength: Int) {
        updateState(state.copy(bassBoostStrength = strength.coerceIn(0, AudioFxState.MAX_BASS_BOOST)))
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        val gains = if (preset == EqualizerPreset.CUSTOM) {
            state.effectiveBandGainsDb
        } else {
            preset.bandGainsDb
        }
        updateState(
            state.copy(
                equalizerPreset = preset,
                customBandGainsDb = gains
            )
        )
    }

    fun setEqualizerBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until AudioFxState.BAND_COUNT) return
        val updated = state.effectiveBandGainsDb.toMutableList()
        updated[bandIndex] = gainDb.coerceIn(AudioFxState.MIN_EQ_DB, AudioFxState.MAX_EQ_DB)
        updateState(
            state.copy(
                equalizerPreset = EqualizerPreset.CUSTOM,
                customBandGainsDb = updated
            )
        )
    }

    fun setVolumeNormalizerEnabled(enabled: Boolean) {
        updateState(state.copy(volumeNormalizerEnabled = enabled))
    }

    fun resetAll() {
        updateState(AudioFxState())
    }

    fun attachToMediaPlayer(mediaPlayer: MediaPlayer) {
        attachedMediaPlayerRef = WeakReference(mediaPlayer)
        val sessionId = try {
            mediaPlayer.audioSessionId
        } catch (_: Exception) {
            -1
        }
        if (sessionId > 0 && sessionId != attachedSessionId) {
            releaseNativeEffects()
            attachedSessionId = sessionId
        }
        applyToAttachedMediaPlayer()
    }

    fun detachOfflineEffects() {
        attachedMediaPlayerRef = null
        releaseNativeEffects()
        attachedSessionId = -1
    }

    fun injectWebAudioFx() {
        applyToWebView()
    }

    fun release() {
        detachOfflineEffects()
    }

    private fun updateState(newState: AudioFxState) {
        state = newState
        persistState(newState)
        applyToAttachedMediaPlayer()
        applyToWebView()
        onStateChanged?.invoke(newState)
    }

    private fun applyToAttachedMediaPlayer() {
        val mp = attachedMediaPlayerRef?.get() ?: return
        applyPlaybackParams(mp)

        val sessionId = attachedSessionId
        if (sessionId <= 0) return

        // Tunda inisialisasi native AudioEffect sampai ada efek DSP aktif agar HAL perangkat tetap ringan
        if (!state.isDspActive && bassBoost == null && equalizer == null && loudnessEnhancer == null) {
            return
        }

        ensureNativeEffects(sessionId)
        applyNativeBassBoost()
        applyNativeEqualizer()
        applyNativeNormalizer()
    }

    private fun applyPlaybackParams(mp: MediaPlayer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val wasPlaying = mp.isPlaying
            val params = mp.playbackParams
            val targetSpeed = state.effectiveSpeed
            val targetPitch = state.effectivePitch
            if (kotlin.math.abs(params.speed - targetSpeed) < 0.005f &&
                kotlin.math.abs(params.pitch - targetPitch) < 0.005f
            ) {
                return
            }
            params.speed = targetSpeed
            params.pitch = targetPitch
            mp.playbackParams = params
            // Pada beberapa ROM Android, setPlaybackParams otomatis memicu start() meski lagu sedang dijeda
            if (!wasPlaying && mp.isPlaying) {
                mp.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PlaybackParams tidak didukung pada stream ini: ${e.message}")
        }
    }

    private fun ensureNativeEffects(sessionId: Int) {
        if (bassBoost == null && state.clampedBassBoost > 0) {
            try {
                bassBoost = BassBoost(0, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal init BassBoost: ${e.message}")
            }
        }
        if (equalizer == null && state.effectiveBandGainsDb.any { kotlin.math.abs(it) > 0.05f }) {
            try {
                equalizer = Equalizer(0, sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal init Equalizer: ${e.message}")
            }
        }
        if (loudnessEnhancer == null && state.volumeNormalizerEnabled) {
            try {
                loudnessEnhancer = LoudnessEnhancer(sessionId)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal init LoudnessEnhancer: ${e.message}")
            }
        }
    }

    private fun applyNativeBassBoost() {
        val bb = bassBoost ?: return
        try {
            val strength = state.clampedBassBoost
            val shouldEnable = strength > 0
            if (shouldEnable && bb.strengthSupported) {
                bb.setStrength(strength.toShort())
            }
            bb.enabled = shouldEnable
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menerapkan BassBoost: ${e.message}")
        }
    }

    private fun applyNativeEqualizer() {
        val eq = equalizer ?: return
        try {
            val gainsDb = state.effectiveDspBandGainsDb
            val hasNonZero = gainsDb.any { kotlin.math.abs(it) > 0.05f }
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range.getOrNull(1)?.toInt() ?: 1500
            val bandCount = eq.numberOfBands.toInt()
            for (band in 0 until bandCount) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000
                val targetDb = AudioFxState.gainDbForCenterFreqHz(centerHz, gainsDb)
                val targetMb = AudioFxState.gainDbToMillibels(targetDb, minMb, maxMb)
                eq.setBandLevel(band.toShort(), targetMb)
            }
            eq.enabled = hasNonZero
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menerapkan Equalizer: ${e.message}")
        }
    }

    private fun applyNativeNormalizer() {
        val le = loudnessEnhancer ?: return
        try {
            val enabled = state.volumeNormalizerEnabled
            if (enabled) {
                le.setTargetGain(AudioFxState.NORMALIZER_TARGET_GAIN_MB)
            }
            le.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Gagal menerapkan LoudnessEnhancer: ${e.message}")
        }
    }

    private fun releaseNativeEffects() {
        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null
    }

    private fun applyToWebView() {
        val current = state
        val gains = current.effectiveDspBandGainsDb
        val gainsJsArray = gains.joinToString(",") { String.format(Locale.US, "%.2f", it) }
        val speedStr = String.format(Locale.US, "%.3f", current.effectiveSpeed)
        val bassDbStr = String.format(Locale.US, "%.2f", current.bassBoostDbForWeb())
        val preservesPitchLit = if (current.preservesPitchInWeb) "true" else "false"
        val normalizerLit = if (current.volumeNormalizerEnabled) "true" else "false"
        val dspActiveLit = if (current.isDspActive) "true" else "false"
        val nightcoreSmoothLit = if (current.needsNightcoreSmoothing) "true" else "false"

        val script = """
            (function() {
                window._ytmAudioFx = {
                    speed: $speedStr,
                    preservesPitch: $preservesPitchLit,
                    bassDb: $bassDbStr,
                    eqGains: [$gainsJsArray],
                    normalizer: $normalizerLit,
                    dspActive: $dspActiveLit,
                    nightcoreSmooth: $nightcoreSmoothLit
                };

                function applyVideoRate(v) {
                    var adActive = !!(window._ytmAdSkipping && (Date.now() - (window._ytmAdSkipTs || 0) < 4000));
                    if (!v || adActive) return;
                    var fx = window._ytmAudioFx;
                    if (!fx) return;
                    try {
                        if ('preservesPitch' in v && v.preservesPitch !== fx.preservesPitch) {
                            v.preservesPitch = fx.preservesPitch;
                        }
                        if ('webkitPreservesPitch' in v && v.webkitPreservesPitch !== fx.preservesPitch) {
                            v.webkitPreservesPitch = fx.preservesPitch;
                        }
                        if ('mozPreservesPitch' in v && v.mozPreservesPitch !== fx.preservesPitch) {
                            v.mozPreservesPitch = fx.preservesPitch;
                        }
                        if (Math.abs((v.playbackRate || 1) - fx.speed) > 0.005) {
                            v.playbackRate = fx.speed;
                        }
                    } catch (e) {}
                }

                function ensureWebAudioGraph(v) {
                    if (!v) return null;
                    var fx = window._ytmAudioFx;
                    if (!fx) return null;
                    if (v._ytmFxNodes) return v._ytmFxNodes;
                    if (!fx.dspActive) return null;

                    // Hanya kaitkan MediaElementAudioSourceNode pada stream MSE same-origin (blob:)
                    // atau CORS anonymous agar Chromium tidak membisukan audio lintas-origin.
                    var src = v.currentSrc || v.src || '';
                    if (src.indexOf('blob:') !== 0 && v.crossOrigin !== 'anonymous') {
                        return null;
                    }

                    try {
                        var AudioCtx = window.AudioContext || window.webkitAudioContext;
                        if (!AudioCtx) return null;
                        var ctx = window._ytmAudioCtx || new AudioCtx();
                        window._ytmAudioCtx = ctx;

                        var source = ctx.createMediaElementSource(v);
                        var bass = ctx.createBiquadFilter();
                        bass.type = 'lowshelf';
                        bass.frequency.value = 80;
                        bass.gain.value = 0;

                        var freqs = [60, 230, 910, 3600, 14000];
                        var eqNodes = freqs.map(function(freq, idx) {
                            var f = ctx.createBiquadFilter();
                            f.type = idx === 0 ? 'lowshelf' : (idx === freqs.length - 1 ? 'highshelf' : 'peaking');
                            f.frequency.value = freq;
                            f.Q.value = 1.0;
                            f.gain.value = 0;
                            return f;
                        });

                        // Filter anti-harshness dan de-essing halus untuk mode Nightcore
                        var deHarsh = ctx.createBiquadFilter();
                        deHarsh.type = 'peaking';
                        deHarsh.frequency.value = 3200;
                        deHarsh.Q.value = 1.1;
                        deHarsh.gain.value = 0;

                        var deEssLp = ctx.createBiquadFilter();
                        deEssLp.type = 'lowpass';
                        deEssLp.frequency.value = 22000;
                        deEssLp.Q.value = 0.5;

                        var comp = ctx.createDynamicsCompressor();
                        var makeupGain = ctx.createGain();
                        makeupGain.gain.value = 1.0;

                        var tail = source;
                        tail.connect(bass);
                        tail = bass;
                        for (var i = 0; i < eqNodes.length; i++) {
                            tail.connect(eqNodes[i]);
                            tail = eqNodes[i];
                        }
                        tail.connect(deHarsh);
                        deHarsh.connect(deEssLp);
                        deEssLp.connect(comp);
                        comp.connect(makeupGain);
                        makeupGain.connect(ctx.destination);

                        v._ytmFxNodes = {
                            ctx: ctx,
                            bass: bass,
                            eqNodes: eqNodes,
                            deHarsh: deHarsh,
                            deEssLp: deEssLp,
                            comp: comp,
                            makeupGain: makeupGain
                        };
                        return v._ytmFxNodes;
                    } catch (e) {
                        return null;
                    }
                }

                function syncWebAudioParams(v) {
                    var fx = window._ytmAudioFx;
                    if (!fx || !v) return;
                    applyVideoRate(v);

                    var nodes = ensureWebAudioGraph(v);
                    if (!nodes) return;
                    try {
                        var ctx = nodes.ctx;
                        if (ctx.state !== 'running' && !v.paused) {
                            var res = ctx.resume();
                            if (res && typeof res.catch === 'function') res.catch(function() {});
                        }
                        var now = ctx.currentTime;
                        nodes.bass.gain.setTargetAtTime(fx.bassDb || 0, now, 0.03);
                        for (var i = 0; i < nodes.eqNodes.length; i++) {
                            var g = (fx.eqGains && typeof fx.eqGains[i] === 'number') ? fx.eqGains[i] : 0;
                            nodes.eqNodes[i].gain.setTargetAtTime(g, now, 0.03);
                        }
                        if (nodes.deHarsh) {
                            nodes.deHarsh.gain.setTargetAtTime(fx.nightcoreSmooth ? -1.2 : 0, now, 0.03);
                        }
                        if (nodes.deEssLp) {
                            nodes.deEssLp.frequency.setTargetAtTime(fx.nightcoreSmooth ? 16000 : 22000, now, 0.03);
                        }
                        if (fx.normalizer) {
                            nodes.comp.threshold.setTargetAtTime(-24, now, 0.03);
                            nodes.comp.knee.setTargetAtTime(18, now, 0.03);
                            nodes.comp.ratio.setTargetAtTime(6, now, 0.03);
                            nodes.comp.attack.setTargetAtTime(0.005, now, 0.03);
                            nodes.comp.release.setTargetAtTime(0.2, now, 0.03);
                            nodes.makeupGain.gain.setTargetAtTime(fx.nightcoreSmooth ? 1.08 : 1.18, now, 0.03);
                        } else if (fx.dspActive) {
                            nodes.comp.threshold.setTargetAtTime(-2, now, 0.03);
                            nodes.comp.knee.setTargetAtTime(6, now, 0.03);
                            nodes.comp.ratio.setTargetAtTime(12, now, 0.03);
                            nodes.comp.attack.setTargetAtTime(0.003, now, 0.03);
                            nodes.comp.release.setTargetAtTime(0.15, now, 0.03);
                            nodes.makeupGain.gain.setTargetAtTime(fx.nightcoreSmooth ? 0.94 : 1.0, now, 0.03);
                        } else {
                            nodes.comp.threshold.setTargetAtTime(0, now, 0.03);
                            nodes.comp.knee.setTargetAtTime(0, now, 0.03);
                            nodes.comp.ratio.setTargetAtTime(1, now, 0.03);
                            nodes.makeupGain.gain.setTargetAtTime(1.0, now, 0.03);
                        }
                    } catch (e) {}
                }

                function hookVideoFx(v) {
                    if (!v) return;
                    if (!v._hasYtmAudioFxHooks) {
                        v._hasYtmAudioFxHooks = true;
                        ['loadedmetadata', 'play', 'playing', 'ratechange'].forEach(function(evt) {
                            v.addEventListener(evt, function() {
                                syncWebAudioParams(v);
                            });
                        });
                    }
                    syncWebAudioParams(v);
                }

                window._ytmSyncAudioFx = function() {
                    var v = document.querySelector('video');
                    hookVideoFx(v);
                };

                window._ytmSyncAudioFx();
                if (!window._ytmAudioFxInterval) {
                    window._ytmAudioFxInterval = setInterval(function() {
                        try {
                            if (window._ytmSyncAudioFx) window._ytmSyncAudioFx();
                        } catch (e) {}
                    }, 1500);
                }
            })();
        """.trimIndent()

        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(script, null)
        } else {
            webView.post { webView.evaluateJavascript(script, null) }
        }
    }

    private fun loadPersistedState(): AudioFxState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val speedPreset = SpeedPreset.fromName(prefs.getString(KEY_SPEED_PRESET, SpeedPreset.NORMAL.name))
        val customSpeed = prefs.getFloat(KEY_CUSTOM_SPEED, speedPreset.speed)
        val customPitch = prefs.getFloat(KEY_CUSTOM_PITCH, speedPreset.pitch)
        val bassBoost = prefs.getInt(KEY_BASS_BOOST, 0)
        val eqPreset = EqualizerPreset.fromName(prefs.getString(KEY_EQ_PRESET, EqualizerPreset.FLAT.name))
        val customBands = AudioFxState.deserializeBandGains(prefs.getString(KEY_CUSTOM_EQ_BANDS, null))
        val normalizer = prefs.getBoolean(KEY_VOLUME_NORMALIZER, false)
        return AudioFxState(
            speedPreset = speedPreset,
            customSpeed = customSpeed.coerceIn(AudioFxState.MIN_SPEED, AudioFxState.MAX_SPEED),
            customPitch = customPitch.coerceIn(AudioFxState.MIN_PITCH, AudioFxState.MAX_PITCH),
            bassBoostStrength = bassBoost.coerceIn(0, AudioFxState.MAX_BASS_BOOST),
            equalizerPreset = eqPreset,
            customBandGainsDb = customBands,
            volumeNormalizerEnabled = normalizer
        )
    }

    private fun persistState(fx: AudioFxState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SPEED_PRESET, fx.speedPreset.name)
            .putFloat(KEY_CUSTOM_SPEED, fx.effectiveSpeed)
            .putFloat(KEY_CUSTOM_PITCH, fx.effectivePitch)
            .putInt(KEY_BASS_BOOST, fx.clampedBassBoost)
            .putString(KEY_EQ_PRESET, fx.equalizerPreset.name)
            .putString(KEY_CUSTOM_EQ_BANDS, AudioFxState.serializeBandGains(fx.customBandGainsDb))
            .putBoolean(KEY_VOLUME_NORMALIZER, fx.volumeNormalizerEnabled)
            .apply()
    }

    companion object {
        private const val TAG = "AudioFxManager"
        private const val PREFS_NAME = "ytm_prefs"
        private const val KEY_SPEED_PRESET = "fx_speed_preset"
        private const val KEY_CUSTOM_SPEED = "fx_custom_speed"
        private const val KEY_CUSTOM_PITCH = "fx_custom_pitch"
        private const val KEY_BASS_BOOST = "fx_bass_boost"
        private const val KEY_EQ_PRESET = "fx_eq_preset"
        private const val KEY_CUSTOM_EQ_BANDS = "fx_custom_eq_bands"
        private const val KEY_VOLUME_NORMALIZER = "fx_volume_normalizer"
    }
}
