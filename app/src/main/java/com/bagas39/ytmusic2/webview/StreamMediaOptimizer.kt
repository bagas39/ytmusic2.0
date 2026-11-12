package com.bagas39.ytmusic2.webview

import android.webkit.WebView
import com.bagas39.ytmusic2.player.AudioQuality
import org.json.JSONObject

class StreamMediaOptimizer(private val webView: WebView) {

    var isDataSaverEnabled: Boolean = false
        private set

    var audioQuality: AudioQuality = AudioQuality.AUTO
        private set

    private var isWindowFocused: Boolean = true
    private var currentTitle: String = ""

    fun setDataSaverEnabled(enabled: Boolean) {
        isDataSaverEnabled = enabled
        applyOptimizerState()
    }

    fun setAudioQuality(quality: AudioQuality) {
        audioQuality = quality
        applyOptimizerState()
    }

    fun setWindowFocused(focused: Boolean) {
        isWindowFocused = focused
        val focusedLiteral = if (focused) "true" else "false"
        webView.evaluateJavascript(
            """
            (function() {
                window._ytmWindowFocused = $focusedLiteral;
                if (window._ytmSyncVisualizerDom) {
                    window._ytmSyncVisualizerDom();
                }
            })();
            """.trimIndent(),
            null
        )
    }

    fun updateTrackTitle(title: String) {
        currentTitle = title
    }

    fun injectOptimizerScript() {
        applyOptimizerState()
    }

    private fun applyOptimizerState() {
        val quotedQualityName = JSONObject.quote(audioQuality.name)
        val quotedQualityDisplay = JSONObject.quote(audioQuality.displayName.uppercase())
        val enabledLiteral = if (isDataSaverEnabled) "true" else "false"
        val focusedLiteral = if (isWindowFocused) "true" else "false"

        val script = """
            (function() {
                window._ytmDataSaver = $enabledLiteral;
                window._ytmWindowFocused = $focusedLiteral;
                window._ytmAudioQuality = $quotedQualityName;
                window._ytmAudioQualityDisplay = $quotedQualityDisplay;

                var styleId = 'ytm-stream-optimizer-style';
                function ensureStyle() {
                    var s = document.getElementById(styleId);
                    if (!s) {
                        s = document.createElement('style');
                        s.id = styleId;
                        s.textContent =
                            'ytmusic-player-page ytmusic-player,' +
                            'ytmusic-player-page #song-media-window,' +
                            'ytmusic-player-page #song-video,' +
                            'ytmusic-player-page #player,' +
                            'ytmusic-player-page #movie_player,' +
                            'ytmusic-player-page .html5-video-player,' +
                            'ytmusic-player-page .player-wrapper {' +
                            '  background: transparent !important;' +
                            '  background-color: transparent !important;' +
                            '  box-shadow: none !important;' +
                            '}' +
                            '#ytm-visualizer-overlay {' +
                            '  position: absolute;' +
                            '  inset: 0;' +
                            '  width: 100%;' +
                            '  height: 100%;' +
                            '  display: none;' +
                            '  flex-direction: column;' +
                            '  align-items: center;' +
                            '  justify-content: center;' +
                            '  background: transparent;' +
                            '  z-index: 20;' +
                            '  pointer-events: none !important;' +
                            '  user-select: none;' +
                            '  box-sizing: border-box;' +
                            '}' +
                            'ytmusic-app-layout:not([player-page-open]) #ytm-visualizer-overlay,' +
                            'ytmusic-app-layout[player-ui-state="INACTIVE"] #ytm-visualizer-overlay,' +
                            'ytmusic-player-page:not([player-page-open]) #ytm-visualizer-overlay {' +
                            '  display: none !important;' +
                            '  visibility: hidden !important;' +
                            '  pointer-events: none !important;' +
                            '}' +
                            'ytmusic-app-layout[player-page-open] ytmusic-player-page[player-page-open] #ytm-visualizer-overlay,' +
                            'ytmusic-app-layout[player-ui-state="PLAYER_PAGE_OPEN"] ytmusic-player-page[player-page-open] #ytm-visualizer-overlay {' +
                            '  visibility: visible !important;' +
                            '}' +
                            '.ytm-viz-bars {' +
                            '  display: flex;' +
                            '  align-items: center;' +
                            '  justify-content: center;' +
                            '  gap: 5px;' +
                            '  height: 64px;' +
                            '  margin-bottom: 18px;' +
                            '}' +
                            '.ytm-viz-bar {' +
                            '  width: 3px;' +
                            '  height: var(--bar-peak, 32px);' +
                            '  background-color: rgba(255, 255, 255, 0.82);' +
                            '  border-radius: 99px;' +
                            '  transform: scaleY(0.08);' +
                            '  transform-origin: center;' +
                            '  will-change: transform, opacity;' +
                            '  transition: transform 0.25s ease, opacity 0.25s ease;' +
                            '}' +
                            '#ytm-visualizer-overlay.is-playing .ytm-viz-bar {' +
                            '  transition: none !important;' +
                            '  animation: ytmSpectrum 0.85s cubic-bezier(0.4, 0, 0.2, 1) infinite alternate;' +
                            '}' +
                            '#ytm-visualizer-overlay.is-paused .ytm-viz-bar {' +
                            '  transform: scaleY(0.08) !important;' +
                            '  opacity: 0.28;' +
                            '  animation: none !important;' +
                            '}' +
                            '.ytm-viz-meta {' +
                            '  font-family: Roboto, -apple-system, BlinkMacSystemFont, sans-serif;' +
                            '  font-size: 10px;' +
                            '  font-weight: 500;' +
                            '  letter-spacing: 1.5px;' +
                            '  text-transform: uppercase;' +
                            '  color: rgba(255, 255, 255, 0.42);' +
                            '  text-align: center;' +
                            '}' +
                            '@keyframes ytmSpectrum {' +
                            '  0% { transform: scaleY(0.1); opacity: 0.4; }' +
                            '  50% { transform: scaleY(0.55); opacity: 0.72; }' +
                            '  100% { transform: scaleY(1.0); opacity: 0.9; }' +
                            '}';
                        (document.head || document.documentElement).appendChild(s);
                    }
                }

                function clearRestoredBackgrounds() {
                    var cleared = document.querySelectorAll('[data-ytm-bg-cleared="1"]');
                    for (var i = 0; i < cleared.length; i++) {
                        cleared[i].style.removeProperty('background');
                        cleared[i].style.removeProperty('background-color');
                        cleared[i].style.removeProperty('box-shadow');
                        cleared[i].removeAttribute('data-ytm-bg-cleared');
                    }
                }

                function isAncestryVisible(el) {
                    var cur = el;
                    while (cur && cur !== document.body && cur !== document.documentElement) {
                        var st = window.getComputedStyle(cur);
                        if (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) < 0.1) {
                            return false;
                        }
                        cur = cur.parentElement;
                    }
                    return true;
                }

                function buildOverlayElement() {
                    var overlay = document.createElement('div');
                    overlay.id = 'ytm-visualizer-overlay';
                    overlay.className = 'is-paused';

                    var bars = document.createElement('div');
                    bars.className = 'ytm-viz-bars';
                    var peaks = ['12px', '18px', '26px', '16px', '34px', '44px', '28px', '52px', '36px', '58px', '38px', '50px', '30px', '42px', '24px', '32px', '20px', '14px', '10px'];
                    var delays = ['-0.15s', '-0.55s', '-0.3s', '-0.75s', '-0.1s', '-0.45s', '-0.85s', '-0.25s', '-0.65s', '-0.05s', '-0.5s', '-0.2s', '-0.7s', '-0.35s', '-0.8s', '-0.4s', '-0.6s', '-0.22s', '-0.48s'];
                    var durations = ['0.78s', '0.92s', '0.7s', '1.05s', '0.84s', '0.74s', '0.98s', '0.68s', '0.88s', '0.76s', '0.94s', '0.72s', '1.02s', '0.8s', '0.9s', '0.75s', '0.96s', '0.82s', '0.86s'];

                    for (var i = 0; i < peaks.length; i++) {
                        var b = document.createElement('div');
                        b.className = 'ytm-viz-bar';
                        b.style.setProperty('--bar-peak', peaks[i]);
                        b.style.animationDelay = delays[i];
                        b.style.animationDuration = durations[i];
                        bars.appendChild(b);
                    }
                    overlay.appendChild(bars);

                    var meta = document.createElement('div');
                    meta.className = 'ytm-viz-meta';
                    meta.id = 'ytm-viz-meta-text';
                    meta.textContent = 'MODE AUDIO SAJA  ·  AUDIO ' + (window._ytmAudioQualityDisplay || 'OTOMATIS');
                    overlay.appendChild(meta);

                    return overlay;
                }

                function syncVisualizerDom() {
                    var existing = document.getElementById('ytm-visualizer-overlay');
                    if (!window._ytmDataSaver) {
                        if (existing && existing.parentNode) {
                            existing.parentNode.removeChild(existing);
                        }
                        clearRestoredBackgrounds();
                        var s = document.getElementById(styleId);
                        if (s && s.parentNode) {
                            s.parentNode.removeChild(s);
                        }
                        return;
                    }

                    var layout = document.querySelector('ytmusic-app-layout');
                    var playerPage = document.querySelector('ytmusic-player-page');
                    var isPageOpen = !!((layout && (layout.hasAttribute('player-page-open') || layout.getAttribute('player-ui-state') === 'PLAYER_PAGE_OPEN')) ||
                                        (playerPage && playerPage.hasAttribute('player-page-open')));

                    // Jika full player tidak dibuka (misal diminimize ke mini player atau di halaman playlist/home),
                    // sembunyikan visualizer overlay agar tidak nyangkut.
                    if (!isPageOpen) {
                        if (existing) {
                            existing.style.display = 'none';
                        }
                        return;
                    }

                    if (window._ytmWindowFocused === false) {
                        if (existing && existing.className !== 'is-paused') {
                            existing.className = 'is-paused';
                        }
                        return;
                    }

                    var v = (playerPage ? playerPage.querySelector('video') : null) || document.querySelector('video');
                    var hasActiveMedia = !!(v && (v.currentSrc || v.src) && v.readyState >= 1 && !v.error);
                    var songVideo = playerPage ? playerPage.querySelector('#song-video') : null;
                    if (!playerPage || !songVideo || !hasActiveMedia) {
                        if (existing) existing.style.display = 'none';
                        return;
                    }

                    var svStyle = window.getComputedStyle(songVideo);
                    var svRect = songVideo.getBoundingClientRect();
                    if (svStyle.display === 'none' || svStyle.visibility === 'hidden' || svRect.width < 40 || svRect.height < 40) {
                        if (existing) existing.style.display = 'none';
                        return;
                    }

                    // Fast-path: jika visualizer sudah terpasang rapi di DOM yang aktif dan memenuhi kondisi tampilan
                    if (existing && existing.parentElement && existing.parentElement.isConnected && existing.style.display !== 'none') {
                        var isPlaying = !!(v && !v.paused && !v.ended);
                        var targetClass = isPlaying ? 'is-playing' : 'is-paused';
                        if (existing.className !== targetClass) {
                            existing.className = targetClass;
                        }
                        var metaEl = document.getElementById('ytm-viz-meta-text');
                        var expectedMeta = 'MODE AUDIO SAJA  ·  AUDIO ' + (window._ytmAudioQualityDisplay || 'OTOMATIS');
                        if (metaEl && metaEl.textContent !== expectedMeta) {
                            metaEl.textContent = expectedMeta;
                        }
                        return;
                    }

                    var pageRect = playerPage.getBoundingClientRect();
                    var host = songVideo.parentElement;
                    var cur = songVideo;
                    while (cur && cur !== playerPage && cur !== document.body) {
                        var r = cur.getBoundingClientRect();
                        if (r.height > 0 && r.height < pageRect.height * 0.82) {
                            if (!cur.hasAttribute('data-ytm-bg-cleared')) {
                                cur.style.setProperty('background', 'transparent', 'important');
                                cur.style.setProperty('background-color', 'transparent', 'important');
                                cur.style.setProperty('box-shadow', 'none', 'important');
                                cur.setAttribute('data-ytm-bg-cleared', '1');
                            }
                            if (cur !== songVideo) {
                                host = cur;
                            }
                        } else {
                            break;
                        }
                        cur = cur.parentElement;
                    }

                    if (!host || !isAncestryVisible(host)) {
                        if (existing) existing.style.display = 'none';
                        return;
                    }

                    var hostRect = host.getBoundingClientRect();
                    if (hostRect.width < 80 || hostRect.height < 80 || hostRect.top < -20 || hostRect.bottom > window.innerHeight + 20) {
                        if (existing) existing.style.display = 'none';
                        return;
                    }

                    var cx = hostRect.left + hostRect.width / 2;
                    var cy = hostRect.top + hostRect.height / 2;
                    var topEl = document.elementFromPoint(cx, cy);
                    if (!topEl || !playerPage.contains(topEl)) {
                        if (existing) existing.style.display = 'none';
                        return;
                    }

                    ensureStyle();

                    if (window.getComputedStyle(host).position === 'static') {
                        host.style.position = 'relative';
                    }

                    if (!existing) {
                        existing = buildOverlayElement();
                        host.appendChild(existing);
                    } else if (existing.parentElement !== host) {
                        host.appendChild(existing);
                    }

                    existing.style.display = 'flex';

                    var metaEl = document.getElementById('ytm-viz-meta-text');
                    var expectedMeta = 'MODE AUDIO SAJA  ·  AUDIO ' + (window._ytmAudioQualityDisplay || 'OTOMATIS');
                    if (metaEl && metaEl.textContent !== expectedMeta) {
                        metaEl.textContent = expectedMeta;
                    }

                    var isPlaying = !!(v && !v.paused && !v.ended);
                    var targetClass = isPlaying ? 'is-playing' : 'is-paused';
                    if (existing.className !== targetClass) {
                        existing.className = targetClass;
                    }

                    if (v && !v._hasVizListeners) {
                        v._hasVizListeners = true;
                        var triggerSync = function() {
                            if (window._ytmSyncVisualizerDom) window._ytmSyncVisualizerDom();
                        };
                        v.addEventListener('play', triggerSync);
                        v.addEventListener('pause', triggerSync);
                        v.addEventListener('ended', triggerSync);
                        v.addEventListener('emptied', triggerSync);
                    }
                }

                window._ytmSyncVisualizerDom = syncVisualizerDom;
                syncVisualizerDom();

                if (!window._hasVisualizerObserver) {
                    window._hasVisualizerObserver = true;
                    var obs = new MutationObserver(function() {
                        if (window._ytmSyncVisualizerDom) {
                            window._ytmSyncVisualizerDom();
                        }
                    });
                    var targetLayout = document.querySelector('ytmusic-app-layout') || document.body;
                    obs.observe(targetLayout, { attributes: true, attributeFilter: ['player-page-open', 'player-ui-state'] });
                    var pp = document.querySelector('ytmusic-player-page');
                    if (pp) {
                        obs.observe(pp, { attributes: true, attributeFilter: ['player-page-open', 'player-page-ui-state', 'video-mode'] });
                    }
                }

                if (!window._hasVisualizerSyncInterval) {
                    window._hasVisualizerSyncInterval = true;
                    setInterval(function() {
                        try {
                            if (window._ytmSyncVisualizerDom) {
                                window._ytmSyncVisualizerDom();
                            }
                        } catch (e) {}
                    }, 1500);
                }

                if (!window._hasQualityFetchInterceptor) {
                    window._hasQualityFetchInterceptor = true;
                    var origFetch = window.fetch;
                    window.fetch = async function() {
                        var response = await origFetch.apply(this, arguments);
                        try {
                            var url = arguments[0];
                            var urlStr = (typeof url === 'string') ? url : (url && url.url ? url.url : '');
                            if (response.ok && urlStr.indexOf('/youtubei/v1/player') !== -1) {
                                var targetQ = window._ytmAudioQuality || 'AUTO';
                                if (targetQ !== 'AUTO') {
                                    var clone = response.clone();
                                    var data = await clone.json();
                                    if (data && data.streamingData && Array.isArray(data.streamingData.adaptiveFormats)) {
                                        var formats = data.streamingData.adaptiveFormats;
                                        var audioFormats = formats.filter(function(f) {
                                            return f.mimeType && f.mimeType.indexOf('audio/') === 0;
                                        });
                                        var videoFormats = formats.filter(function(f) {
                                            return !f.mimeType || f.mimeType.indexOf('audio/') !== 0;
                                        });

                                        if (targetQ === 'HIGH') {
                                            audioFormats.sort(function(a, b) { return (b.bitrate || 0) - (a.bitrate || 0); });
                                        } else if (targetQ === 'LOW') {
                                            audioFormats.sort(function(a, b) { return (a.bitrate || 0) - (b.bitrate || 0); });
                                        } else if (targetQ === 'NORMAL') {
                                            audioFormats.sort(function(a, b) {
                                                var diffA = Math.abs((a.bitrate || 0) - 128000);
                                                var diffB = Math.abs((b.bitrate || 0) - 128000);
                                                return diffA - diffB;
                                            });
                                        }

                                        data.streamingData.adaptiveFormats = audioFormats.concat(videoFormats);
                                        var newHeaders = new Headers(response.headers);
                                        newHeaders.delete('content-encoding');
                                        newHeaders.delete('content-length');
                                        return new Response(JSON.stringify(data), {
                                            status: response.status,
                                            statusText: response.statusText,
                                            headers: newHeaders
                                        });
                                    }
                                }
                            }
                        } catch (e) {}
                        return response;
                    };
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}
