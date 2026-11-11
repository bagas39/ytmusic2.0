package com.bagas39.ytmusic2.webview

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bagas39.ytmusic2.KeepAliveWebView
import java.io.ByteArrayInputStream

class YtmWebManager(private val webView: KeepAliveWebView) {

    var onSongStateChanged: ((SongMetadata) -> Unit)? = null
    var onTitleDetected: ((String) -> Unit)? = null
    var onPageLifecycleInject: (() -> Unit)? = null

    @Volatile
    private var currentDomVideoId: String? = null

    var isDataSaverEnabled: Boolean = false
        private set

    fun setDataSaverEnabled(enabled: Boolean) {
        isDataSaverEnabled = enabled
        applyDataSaver(enabled)
    }

    private fun applyDataSaver(enabled: Boolean) {
        val script = """
            (function() {
                window._ytmDataSaver = $enabled;
                var styleId = 'ytm-data-saver-style';
                var s = document.getElementById(styleId);
                if ($enabled) {
                    if (!s) {
                        s = document.createElement('style');
                        s.id = styleId;
                        s.textContent = 'ytmusic-player-page .canvas-container { opacity: 0.001 !important; visibility: hidden !important; } ' +
                                        'ytmusic-player-page video { opacity: 0 !important; visibility: hidden !important; }';
                        (document.head || document.documentElement).appendChild(s);
                    }
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.setPlaybackQualityRange === 'function') {
                        try {
                            if (typeof mp.getPlaybackQuality !== 'function' || mp.getPlaybackQuality() !== 'tiny') {
                                mp.setPlaybackQualityRange('tiny', 'tiny');
                                mp.setPlaybackQuality('tiny');
                            }
                        } catch(e) {}
                    }
                } else {
                    if (s && s.parentNode) s.parentNode.removeChild(s);
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.setPlaybackQualityRange === 'function') {
                        try {
                            if (typeof mp.getPlaybackQuality !== 'function' || mp.getPlaybackQuality() === 'tiny') {
                                mp.setPlaybackQualityRange('auto', 'auto');
                                mp.setPlaybackQuality('auto');
                            }
                        } catch(e) {}
                    }
                }
            })();
        """.trimIndent()
        executeJs(script)
    }

    private val adHosts = arrayOf(
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead/",
        "googlesyndication.com"
    )

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setOnLongClickListener { v ->
            val hit = (v as? WebView)?.hitTestResult
            val isInput = hit?.type == WebView.HitTestResult.EDIT_TEXT_TYPE
            !isInput
        }
        webView.isHapticFeedbackEnabled = false
        val isDebuggable = (webView.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebuggable)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        val defaultUa = settings.userAgentString
        settings.userAgentString = defaultUa.replace("; wv", "")

        setupJsBridge()
        setupClients()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupJsBridge() {
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onTitle(title: String) {
                if (title.isNotEmpty()) {
                    onTitleDetected?.invoke(title)
                }
            }

            @JavascriptInterface
            fun onVideoId(videoId: String) {
                if (videoId.isNotBlank()) {
                    currentDomVideoId = videoId.trim()
                }
            }

            @JavascriptInterface
            fun onSongState(
                title: String,
                artist: String,
                isPlaying: Boolean,
                durationMs: Long,
                positionMs: Long,
                artworkUrl: String
            ) {
                if (title.isNotEmpty()) {
                    onSongStateChanged?.invoke(
                        SongMetadata(
                            title = title,
                            artist = artist,
                            isPlaying = isPlaying,
                            durationMs = durationMs.coerceAtLeast(0L),
                            positionMs = positionMs.coerceAtLeast(0L),
                            artworkUrl = artworkUrl
                        )
                    )
                }
            }
        }, "AndroidBridge")
    }

    private fun setupClients() {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: return false
                Log.d("YTM-CONSOLE", msg)
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.startsWith("intent://") || url.startsWith("vnd.youtube") || url.startsWith("market://")) {
                    Log.d("YTM-BYPASS", "Blocked external app redirect: $url")
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()

                for (adHost in adHosts) {
                    if (url.contains(adHost)) {
                        val origin = request?.requestHeaders?.get("Origin") ?: "https://music.youtube.com"
                        val reqHeaders = request?.requestHeaders?.get("Access-Control-Request-Headers") ?: "*"

                        val headers = HashMap<String, String>().apply {
                            put("Access-Control-Allow-Origin", origin)
                            put("Access-Control-Allow-Credentials", "true")
                            put("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
                            put("Access-Control-Allow-Headers", reqHeaders)
                        }

                        return WebResourceResponse(
                            "application/json",
                            "UTF-8",
                            200,
                            "OK",
                            headers,
                            ByteArrayInputStream("{}".toByteArray())
                        )
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onPageLifecycleInject?.invoke()
                injectBypassScript()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageLifecycleInject?.invoke()
                injectBypassScript()
                captureCurrentSong()
            }
        }
    }

    fun loadHome() {
        webView.loadUrl("https://music.youtube.com")
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() = webView.goBack()

    fun extractVideoId(url: String?): String? {
        if (url == null) return null
        val match = Regex("v=([\\w-]{11})").find(url)
        return match?.groupValues?.get(1)
    }

    fun getCurrentVideoId(): String? = currentDomVideoId?.takeIf { it.isNotBlank() } ?: extractVideoId(webView.url)

    fun executeJs(script: String) {
        webView.evaluateJavascript(script, null)
    }

    fun clickPrevious() {
        executeJs(
            """
            (function() {
                if (typeof window._ytmSkipToPreviousTrack === 'function') {
                    window._ytmSkipToPreviousTrack();
                    return;
                }
                window._ytmUserIntentPlaying = true;
                window._ytmUnexpectedPauseStart = 0;
                var queue = document.querySelector('ytmusic-player-queue');
                if (queue) {
                    var items = Array.from(queue.querySelectorAll('ytmusic-player-queue-item'));
                    var selectedIdx = items.findIndex(function(it) { return it.hasAttribute('selected'); });
                    if (selectedIdx > 0) {
                        var prevItem = items[selectedIdx - 1];
                        var clickTarget = prevItem.querySelector('.song-info, .song-title, a') || prevItem;
                        clickTarget.click();
                        return;
                    }
                }
                var fsPrev = document.querySelector('ytmusic-player-controls .previous-button button, ytmusic-player-controls .previous-button');
                if (fsPrev) {
                    var v = document.querySelector('video');
                    if (v && v.currentTime > 0.5) v.currentTime = 0;
                    fsPrev.click();
                    return;
                }
                var mp = document.getElementById('movie_player');
                if (mp && typeof mp.previousVideo === 'function') {
                    try {
                        var v2 = document.querySelector('video');
                        if (v2 && v2.currentTime > 0.5) v2.currentTime = 0;
                        mp.previousVideo();
                        return;
                    } catch(e) {}
                }
                var vFallback = document.querySelector('video');
                if (vFallback) vFallback.currentTime = 0;
            })();
            """.trimIndent()
        )
    }

    fun clickPlayPause() {
        executeJs(
            """
            var v = document.querySelector('video');
            var isCurrentlyPlaying = !!(v && !v.paused);
            window._ytmUserIntentPlaying = !isCurrentlyPlaying;
            if (!isCurrentlyPlaying) {
                window._ytmUnexpectedPauseStart = 0;
            }
            var btn = document.querySelector('#play-pause-button') || document.querySelector('.play-pause-button');
            if (btn) {
                btn.click();
            } else if (v) {
                isCurrentlyPlaying ? v.pause() : v.play();
            }
            """.trimIndent()
        )
    }

    fun clickNext() {
        executeJs(
            """
            window._ytmUserIntentPlaying = true;
            window._ytmUnexpectedPauseStart = 0;
            var b = document.querySelector('.next-button') || document.querySelector('#next-button');
            if (b) b.click();
            """.trimIndent()
        )
    }

    fun pausePlayback() {
        executeJs(
            """
            window._ytmUserIntentPlaying = false;
            var v = document.querySelector('video');
            if (v) v.pause();
            """.trimIndent()
        )
    }

    fun resumePlayback() {
        executeJs(
            """
            window._ytmUserIntentPlaying = true;
            window._ytmUnexpectedPauseStart = 0;
            var v = document.querySelector('video');
            if (v && v.paused) {
                try { v.play(); } catch(e) {}
            }
            var mp = document.getElementById('movie_player');
            if (mp && typeof mp.playVideo === 'function') {
                try { mp.playVideo(); } catch(e) {}
            }
            """.trimIndent()
        )
    }

    fun seekTo(positionMs: Long) {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        executeJs("var v = document.querySelector('video'); if (v && isFinite(v.duration)) { v.currentTime = $seconds; }")
    }

    fun captureCurrentSong() {
        val url = webView.url ?: return
        val videoId = extractVideoId(url)
        executeJs(
            """
            (function() {
                var sTitle = '';
                var sArtist = '';
                var sArt = '';
                if (navigator.mediaSession && navigator.mediaSession.metadata) {
                    sTitle = (navigator.mediaSession.metadata.title || '').trim();
                    sArtist = (navigator.mediaSession.metadata.artist || '').trim();
                    var arts = navigator.mediaSession.metadata.artwork;
                    if (arts && arts.length > 0) {
                        sArt = arts[arts.length - 1].src || '';
                    }
                }
                var playerBar = document.querySelector('ytmusic-player-bar');
                if (playerBar) {
                    if (!sTitle) {
                        var t = playerBar.querySelector('.content-info-wrapper .title, yt-formatted-string.title, .title');
                        if (t) sTitle = (t.getAttribute('title') || t.textContent || '').trim();
                    }
                    if (!sArtist) {
                        var a = playerBar.querySelector('.content-info-wrapper .byline, .content-info-wrapper .subtitle, yt-formatted-string.byline, .byline, .subtitle');
                        if (a) sArtist = (a.getAttribute('title') || a.textContent || '').trim();
                    }
                    if (!sArt) {
                        var img = playerBar.querySelector('img.image, #song-image img, .thumbnail-image-wrapper img');
                        if (img && img.src) sArt = img.src;
                    }
                }
                var sVid = '';
                var mp = document.getElementById('movie_player');
                if (mp && typeof mp.getVideoData === 'function') {
                    var vd = mp.getVideoData();
                    if (vd) {
                        if (!sTitle && vd.title) sTitle = vd.title.trim();
                        if (!sArtist && vd.author) sArtist = vd.author.trim();
                        if (vd.video_id) sVid = vd.video_id.trim();
                    }
                }
                if (!sVid && playerBar) {
                    var linkEl = playerBar.querySelector('a[href*="watch?v="]');
                    if (linkEl && linkEl.href) {
                        var mLink = linkEl.href.match(/[?&]v=([\w-]{11})/);
                        if (mLink) sVid = mLink[1];
                    }
                }
                if (!sVid) {
                    var mUrl = window.location.href.match(/[?&]v=([\w-]{11})/);
                    if (mUrl) sVid = mUrl[1];
                }
                if (sVid && window.AndroidBridge && window.AndroidBridge.onVideoId) {
                    window._lastReportedVideoId = sVid;
                    window.AndroidBridge.onVideoId(sVid);
                }
                var v = document.querySelector('video');
                var actualPlaying = !!(v && !v.paused);
                if (actualPlaying) {
                    window._ytmUserIntentPlaying = true;
                }
                var isPlay = actualPlaying || (window._ytmUserIntentPlaying && (Date.now() - (window._ytmUnexpectedPauseStart || 0) < 3000));
                var durMs = (v && v.duration && isFinite(v.duration)) ? Math.round(v.duration * 1000) : 0;
                var posMs = (v && v.currentTime && isFinite(v.currentTime)) ? Math.round(v.currentTime * 1000) : 0;
                if (sTitle && window.AndroidBridge && window.AndroidBridge.onSongState) {
                    window.AndroidBridge.onSongState(sTitle, sArtist, isPlay, durMs, posMs, sArt);
                } else if (sTitle && window.AndroidBridge && window.AndroidBridge.onTitle) {
                    window.AndroidBridge.onTitle(sTitle);
                }
            })();
            """.trimIndent()
        )
    }

    fun injectBypassScript() {
        val script = """
            try {
                if (!document.getElementById('ytm-no-select-style')) {
                    var s = document.createElement('style');
                    s.id = 'ytm-no-select-style';
                    s.textContent = '* { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; } ' +
                                    'input, textarea, [contenteditable="true"] { -webkit-user-select: text !important; user-select: text !important; -webkit-touch-callout: default !important; } ' +
                                    '.ad-showing video { opacity: 0 !important; } ' +
                                    '.ad-showing .video-ads, .ad-showing .ytp-ad-module, .ytp-ad-player-overlay, .ytp-ad-overlay-container, .ytp-ad-message-container, .ytp-ad-text { display: none !important; }';
                    (document.head || document.documentElement).appendChild(s);
                }
            } catch (e) {}

            // Injeksi status Mode Hemat Kuota (Data Saver)
            window._ytmDataSaver = $isDataSaverEnabled;
            if ($isDataSaverEnabled) {
                try {
                    if (!document.getElementById('ytm-data-saver-style')) {
                        var ds = document.createElement('style');
                        ds.id = 'ytm-data-saver-style';
                        ds.textContent = 'ytmusic-player-page .canvas-container { opacity: 0.001 !important; visibility: hidden !important; } ' +
                                         'ytmusic-player-page video { opacity: 0 !important; visibility: hidden !important; }';
                        (document.head || document.documentElement).appendChild(ds);
                    }
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.setPlaybackQualityRange === 'function') {
                        if (typeof mp.getPlaybackQuality !== 'function' || mp.getPlaybackQuality() !== 'tiny') {
                            mp.setPlaybackQualityRange('tiny', 'tiny');
                            mp.setPlaybackQuality('tiny');
                        }
                    }
                } catch (e) {}
            }

            // Intercept API /youtubei/v1/player to strip adPlacements at network layer
            if (!window._hasPlayerFetchInterceptor) {
                window._hasPlayerFetchInterceptor = true;
                var origFetch = window.fetch;
                window.fetch = async function() {
                    var response = await origFetch.apply(this, arguments);
                    try {
                        var url = arguments[0];
                        var urlStr = (typeof url === 'string') ? url : (url && url.url ? url.url : '');
                        if (response.ok && urlStr.indexOf('/youtubei/v1/player') !== -1) {
                            var clone = response.clone();
                            var data = await clone.json();
                            if (data && (data.adPlacements || data.playerAds || data.adSlots)) {
                                (window._ytmStrippedPayloads = window._ytmStrippedPayloads || []).push({
                                    p: data.playerAds, a: data.adPlacements, s: data.adSlots
                                });
                                delete data.adPlacements;
                                delete data.playerAds;
                                delete data.adSlots;
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
                    } catch (e) {}
                    return response;
                };
            }

            // Strip adPlacements from initial player response if present
            try {
                if (window.ytInitialPlayerResponse) {
                    if (window.ytInitialPlayerResponse.adPlacements || window.ytInitialPlayerResponse.playerAds || window.ytInitialPlayerResponse.adSlots) {
                        (window._ytmStrippedPayloads = window._ytmStrippedPayloads || []).push({
                            p: window.ytInitialPlayerResponse.playerAds,
                            a: window.ytInitialPlayerResponse.adPlacements,
                            s: window.ytInitialPlayerResponse.adSlots
                        });
                    }
                    delete window.ytInitialPlayerResponse.adPlacements;
                    delete window.ytInitialPlayerResponse.playerAds;
                    delete window.ytInitialPlayerResponse.adSlots;
                }
            } catch (e) {}

            try {
                Object.defineProperty(document, 'hidden', { value: false, writable: true, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: true, configurable: true });
                document.dispatchEvent(new Event('visibilitychange'));

                // Fake focus: YTM free-tier cek focus buat deteksi background playback
                Object.defineProperty(document, 'hasFocus', { value: function() { return true; }, writable: true, configurable: true });
                document.dispatchEvent(new Event('focus'));
                window.addEventListener('blur', function(e) {
                    if (e.target === window || e.target === document) {
                        e.stopImmediatePropagation();
                    }
                }, true);
                window.addEventListener('focusout', function(e) {
                    if (e.target === window || e.target === document) {
                        e.stopImmediatePropagation();
                    }
                }, true);
                window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
            } catch (e) {}

            // User Intent Playback Watchdog: status niat putar pengguna
            window._ytmUserIntentPlaying = (typeof window._ytmUserIntentPlaying === 'boolean') ? window._ytmUserIntentPlaying : false;
            window._ytmUnexpectedPauseStart = window._ytmUnexpectedPauseStart || 0;
            window._ytmLastReviveTime = window._ytmLastReviveTime || 0;

            // Tangkap klik langsung user pada tombol play/pause di web UI
            if (!window._ytmUserClickCaptured) {
                window._ytmUserClickCaptured = true;
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    if (!target) return;
                    var playPauseBtn = target.closest('#play-pause-button, .play-pause-button');
                    if (playPauseBtn && e.isTrusted) {
                        var v = document.querySelector('video');
                        var currentlyPlaying = !!(v && !v.paused);
                        if (currentlyPlaying) {
                            window._ytmUserIntentPlaying = false;
                            console.log('[YTM-BYPASS] User explicitly paused via web UI click');
                        } else {
                            window._ytmUserIntentPlaying = true;
                            window._ytmUnexpectedPauseStart = 0;
                            console.log('[YTM-BYPASS] User explicitly played via web UI click');
                        }
                    }
                }, true);
            }

            // Fast skip ad function & RAF loop
            var _fastSkipRaf = null;
            function skipAdsFast() {
                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                var isAd = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, ytd-ad-slot-renderer, ytmusic-ad-slot-renderer') ||
                           (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
                var v = document.querySelector('video');

                if (isAd && v) {
                    var adId = (player && typeof player.getAdVideoId === 'function') ? (player.getAdVideoId() || '') : '';
                    var adSig = adId || (v.currentSrc || v.src || 'dom_ad');
                    var nowSkipTs = Date.now();
                    var isNewAdPod = (adSig !== window._ytmLastAdSig) ||
                                     (window._ytmAdJumpedToEnd && v.currentTime < 1.5 && (nowSkipTs - (window._ytmLastAdCountTs || 0) > 300));
                    if (isNewAdPod) {
                        window._ytmLastAdSig = adSig;
                        window._ytmLastAdCountTs = nowSkipTs;
                        window._ytmAdJumpedToEnd = false;
                        var domDur = (isFinite(v.duration) && v.duration > 0) ? Math.round(v.duration) : 15;
                        (window._ytmStrippedPayloads = window._ytmStrippedPayloads || []).push({
                            domAd: true, dur: domDur
                        });
                    }
                    v.muted = true;
                    v.playbackRate = 16.0;
                    if (isFinite(v.duration) && v.duration > 0) {
                        try {
                            v.currentTime = v.duration;
                            window._ytmAdJumpedToEnd = true;
                        } catch (e) {}
                    }
                    if (player && typeof player.skipAd === 'function') {
                        try { player.skipAd(); } catch (e) {}
                    }
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, .ad-skip-button, .ytp-ad-skip-button-slot button, [id^="skip-button"], .ytp-ad-overlay-close-button');
                    if (skipBtn) {
                        try { skipBtn.click(); } catch (e) {}
                    }

                    if (!_fastSkipRaf) {
                        _fastSkipRaf = requestAnimationFrame(function() {
                            _fastSkipRaf = null;
                            skipAdsFast();
                        });
                    }
                } else {
                    window._ytmLastAdSig = '';
                    window._ytmAdJumpedToEnd = false;
                    if (_fastSkipRaf) {
                        cancelAnimationFrame(_fastSkipRaf);
                        _fastSkipRaf = null;
                    }
                    if (v && v.playbackRate > 1.0) {
                        v.playbackRate = 1.0;
                        v.muted = false;
                    }
                }
            }

            function isElementActive(el) {
                if (!el) return false;
                // Chromium Android WebView meniadakan layout (offsetParent=null, width=0, height=0) saat background/layar mati.
                // Jangan gunakan offsetParent / offsetWidth / offsetHeight sebagai penentu visibilitas!
                if (el.hidden) return false;
                if (el.getAttribute('aria-hidden') === 'true') return false;
                if (typeof el.opened === 'boolean' && !el.opened) return false;
                if (el.hasAttribute('opened') && el.getAttribute('opened') === 'false') return false;
                if (el.disabled || el.hasAttribute('disabled')) return false;
                try {
                    var style = window.getComputedStyle ? window.getComputedStyle(el) : el.style;
                    if (style && (style.display === 'none' || style.visibility === 'hidden')) {
                        return false;
                    }
                } catch (e) {}
                return true;
            }

            function dismissPromos() {
                var dismissRegex = /\b(close|dismiss|not now|no,? thanks|maybe later|later|skip|skip trial|stay on web|continue in browser|tutup|lain kali|tidak,? terima kasih|jangan sekarang|nanti|nanti saja|lewati|lewati uji coba|abaikan|tetap di web|lanjutkan di browser)\b/i;
                var avoidRegex = /\b(open app|open in app|get the app|install|try it free|subscribe|upgrade|buka aplikasi|buka di aplikasi|dapatkan aplikasi|instal|coba gratis|langganan|beli)\b/i;

                var dismiss = document.querySelector(
                    'tp-yt-paper-dialog button[aria-label="Close"], ' +
                    'ytmusic-dialog button[aria-label="Close"], ' +
                    'tp-yt-paper-dialog button[aria-label="Tutup"], ' +
                    'ytmusic-dialog button[aria-label="Tutup"], ' +
                    'button[aria-label="Dismiss"], ' +
                    'button[aria-label="Tutup"]'
                );
                if (dismiss && isElementActive(dismiss)) {
                    try {
                        dismiss.click();
                        console.log('[YTM-BYPASS] Dialog close button clicked');
                    } catch (e) {}
                } else {
                    var dialogs = document.querySelectorAll('tp-yt-paper-dialog, ytmusic-dialog, ytmusic-mealbar-promo-renderer');
                    dialogs.forEach(function(dlg) {
                        if (dlg.querySelector('ytmusic-you-there-renderer')) {
                            return;
                        }
                        var isOpen = dlg.tagName.toLowerCase() === 'ytmusic-mealbar-promo-renderer'
                            ? isElementActive(dlg)
                            : (dlg.open || dlg.hasAttribute('open') || isElementActive(dlg));
                        if (isOpen) {
                            var btns = dlg.querySelectorAll('button, tp-yt-paper-button, yt-button-renderer');
                            for (var i = 0; i < btns.length; i++) {
                                var btn = btns[i];
                                if (!isElementActive(btn)) continue;
                                var label = ((btn.getAttribute('aria-label') || '') + ' ' + (btn.textContent || '')).trim();
                                if (avoidRegex.test(label)) continue;
                                if (dismissRegex.test(label)) {
                                    try {
                                        btn.click();
                                        console.log('[YTM-BYPASS] dialog DISMISSED via regex: ' + label);
                                    } catch (e) {}
                                    break;
                                }
                            }
                        }
                    });
                }
            }

            window._ytmSkipToPreviousTrack = function() {
                window._ytmUserIntentPlaying = true;
                window._ytmUnexpectedPauseStart = 0;

                // 1. Antrean lagu di ytmusic-player-queue (paling presisi dan instan di mobile web)
                var queue = document.querySelector('ytmusic-player-queue');
                if (queue) {
                    var items = Array.from(queue.querySelectorAll('ytmusic-player-queue-item'));
                    var selectedIdx = items.findIndex(function(it) { return it.hasAttribute('selected'); });
                    if (selectedIdx > 0) {
                        var prevItem = items[selectedIdx - 1];
                        var clickTarget = prevItem.querySelector('.song-info, .song-title, a') || prevItem;
                        clickTarget.click();
                        console.log('[YTM-BYPASS] Skipped to previous track via queue index: ' + (selectedIdx - 1));
                        return true;
                    }
                }

                // 2. Tombol native previous di ytmusic-player-controls (full-screen player controls)
                var fsPrev = document.querySelector('ytmusic-player-controls .previous-button button, ytmusic-player-controls .previous-button');
                if (fsPrev) {
                    var v = document.querySelector('video');
                    if (v && v.currentTime > 0.5) v.currentTime = 0;
                    fsPrev.click();
                    console.log('[YTM-BYPASS] Skipped to previous track via fsPrev');
                    return true;
                }

                // 3. API player movie_player.previousVideo()
                var mp = document.getElementById('movie_player');
                if (mp && typeof mp.previousVideo === 'function') {
                    try {
                        var v2 = document.querySelector('video');
                        if (v2 && v2.currentTime > 0.5) v2.currentTime = 0;
                        mp.previousVideo();
                        console.log('[YTM-BYPASS] Skipped to previous track via mp.previousVideo()');
                        return true;
                    } catch(e) {}
                }

                // Fallback jika tidak ada lagu sebelumnya (index 0): rewind lagu ke awal
                var vFallback = document.querySelector('video');
                if (vFallback) {
                    vFallback.currentTime = 0;
                    console.log('[YTM-BYPASS] No previous track in queue, rewound to start');
                }
                return false;
            };

            function injectPrevButton() {
                // Pastikan tidak ada tombol injeksi di full-screen player page (karena di sana tombol Prev native sudah ada)
                var strays = document.querySelectorAll('ytmusic-player-page .ytm-custom-prev-btn, ytmusic-player-controls .ytm-custom-prev-btn, #top-player-bar .ytm-custom-prev-btn');
                for (var s = 0; s < strays.length; s++) {
                    strays[s].remove();
                }

                // Injeksi HANYA pada mini-player bar mobile web yang belum memiliki tombol Previous
                var playPauseBtns = Array.from(document.querySelectorAll('#right-controls-mweb #play-pause-button-mweb'));
                for (var i = 0; i < playPauseBtns.length; i++) {
                    var pp = playPauseBtns[i];
                    if (pp.closest('ytmusic-player-page, ytmusic-player-controls, #top-player-bar')) continue;

                    var parent = pp.parentNode;
                    if (!parent) continue;
                    if (parent.querySelector('.ytm-custom-prev-btn')) continue;

                    var prevBtn = document.createElement('button');
                    prevBtn.className = 'ytm-custom-prev-btn';
                    prevBtn.setAttribute('aria-label', 'Previous track');
                    prevBtn.setAttribute('title', 'Lagu Sebelumnya');
                    prevBtn.style.cssText = 'background: transparent; border: none; outline: none; padding: 6px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; border-radius: 50%; color: #ffffff; vertical-align: middle; margin-right: 2px; -webkit-tap-highlight-color: transparent; flex-shrink: 0;';

                    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                    svg.setAttribute('viewBox', '0 0 24 24');
                    svg.style.cssText = 'width: 24px; height: 24px; fill: currentColor; pointer-events: none;';

                    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
                    path.setAttribute('d', 'M6 6h2v12H6zm3.5 6l8.5 6V6z');
                    svg.appendChild(path);
                    prevBtn.appendChild(svg);

                    prevBtn.addEventListener('click', function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        console.log('[YTM-BYPASS] Injected Prev button clicked');
                        window._ytmSkipToPreviousTrack();
                    });

                    parent.insertBefore(prevBtn, pp);
                    console.log('[YTM-BYPASS] Injected Prev button placed in mini-player bar before ' + (pp.id || pp.className));
                }
            }

            var _lastYouThereTime = 0;
            function checkYouThere() {
                var youThere = document.querySelector('ytmusic-you-there-renderer');
                if (!youThere) return false;

                var parentDlg = youThere.closest('tp-yt-paper-dialog, ytmusic-dialog, [role="dialog"]');
                if (parentDlg) {
                    if (parentDlg.getAttribute('aria-hidden') === 'true') return false;
                    if (parentDlg.style.display === 'none') return false;
                    if (typeof parentDlg.opened === 'boolean' && !parentDlg.opened) return false;
                }

                var now = Date.now();
                if (now - _lastYouThereTime > 1200) {
                    _lastYouThereTime = now;
                    console.log('[YTM-BYPASS] you-there PROMPT FOUND. Dismissing...');

                    var confirmBtn = youThere.querySelector(
                        'yt-button-renderer[dialog-confirm] button, ' +
                        '[dialog-confirm], ' +
                        '#confirm-button button, ' +
                        '#confirm-button, ' +
                        'tp-yt-paper-button, ' +
                        'button[role="button"], ' +
                        'button'
                    );

                    if (confirmBtn) {
                        try {
                            confirmBtn.click();
                            console.log('[YTM-BYPASS] you-there CONFIRMED via button click');
                        } catch (e) {
                            console.log('[YTM-BYPASS] you-there button click err: ' + e);
                        }
                    } else if (parentDlg && typeof parentDlg.close === 'function') {
                        try {
                            parentDlg.close();
                            console.log('[YTM-BYPASS] you-there parent dialog closed directly');
                        } catch (e) {}
                    }

                    revivePlayback('you-there confirmed');
                    return true;
                }
                return false;
            }

            function revivePlayback(reason) {
                if (!window._ytmUserIntentPlaying) return;
                console.log('[YTM-BYPASS] revivePlayback called: ' + reason);

                var v = document.querySelector('video');
                var mp = document.getElementById('movie_player') || document.querySelector('.html5-video-player');

                // 1. Bersihkan popup dialog yang memblokir pemutaran
                checkYouThere();
                dismissPromos();

                // 2. Bangunkan Web AudioContext jika tersuspensi
                try {
                    if (window._ytmAudioCtx && window._ytmAudioCtx.state === 'suspended') {
                        window._ytmAudioCtx.resume();
                    }
                } catch (e) {}

                // 3. Fallback 1: HTML5 video play()
                if (v && v.paused) {
                    try {
                        var p = v.play();
                        if (p && typeof p.catch === 'function') {
                            p.catch(function(err) {
                                console.log('[YTM-BYPASS] v.play() catch: ' + err + ', fallback to movie_player');
                                if (mp && typeof mp.playVideo === 'function') {
                                    try { mp.playVideo(); } catch (e) {}
                                }
                            });
                        }
                    } catch (e) {}
                }

                // 4. Fallback 2: YouTube player API
                if (mp && typeof mp.playVideo === 'function') {
                    try {
                        if (typeof mp.getPlayerState === 'function' && mp.getPlayerState() !== 1) {
                            mp.playVideo();
                        }
                    } catch (e) {}
                }

                // 5. Fallback 3: Retri bertahap di 200ms jika masih jeda
                setTimeout(function() {
                    if (!window._ytmUserIntentPlaying) return;
                    var v2 = document.querySelector('video');
                    if (v2 && v2.paused) {
                        console.log('[YTM-BYPASS] Still paused after 200ms, trying UI click & retry...');
                        var playBtn = document.querySelector('#play-pause-button, .play-pause-button');
                        if (playBtn) {
                            try { playBtn.click(); } catch (e) {}
                        }
                        if (mp && typeof mp.playVideo === 'function') {
                            try { mp.playVideo(); } catch (e) {}
                        }
                        if (v2 && v2.paused) {
                            try { v2.play(); } catch (e) {}
                        }
                    }
                }, 200);

                // 6. Fallback 4: Retri bertahap di 600ms
                setTimeout(function() {
                    if (!window._ytmUserIntentPlaying) return;
                    var v3 = document.querySelector('video');
                    if (v3 && v3.paused) {
                        console.log('[YTM-BYPASS] Still paused after 600ms, final retry...');
                        checkYouThere();
                        dismissPromos();
                        if (v3 && v3.paused) {
                            try { v3.play(); } catch (e) {}
                        }
                        if (mp && typeof mp.playVideo === 'function') {
                            try { mp.playVideo(); } catch (e) {}
                        }
                    }
                }, 600);
            }

            function setupVideoHooks(v) {
                if (!v || v._hasFastSkipHooks) return;
                v._hasFastSkipHooks = true;
                v.addEventListener('ended', function() {
                    window._ytmUserIntentPlaying = true;
                    window._ytmUnexpectedPauseStart = 0;
                    var nextBtn = document.querySelector('.next-button') || document.querySelector('#next-button');
                    if (nextBtn) { nextBtn.click(); }
                });

                v.addEventListener('play', function() {
                    window._ytmUserIntentPlaying = true;
                    window._ytmUnexpectedPauseStart = 0;
                });
                v.addEventListener('playing', function() {
                    window._ytmUserIntentPlaying = true;
                    window._ytmUnexpectedPauseStart = 0;
                });

                v.addEventListener('pause', function() {
                    if (window._ytmUserIntentPlaying) {
                        console.log('[YTM-BYPASS] Video pause event detected while userIntentPlaying=true! Triggering watchdog...');
                        revivePlayback('video.onpause');
                    }
                });

                var events = ['loadedmetadata', 'durationchange', 'canplay', 'play', 'playing', 'timeupdate'];
                events.forEach(function(evt) {
                    v.addEventListener(evt, function() {
                        skipAdsFast();
                        if (window._ytmDataSaver && evt === 'loadedmetadata') {
                            var mp = document.getElementById('movie_player');
                            if (mp && typeof mp.setPlaybackQualityRange === 'function') {
                                try {
                                    if (typeof mp.getPlaybackQuality !== 'function' || mp.getPlaybackQuality() !== 'tiny') {
                                        mp.setPlaybackQualityRange('tiny', 'tiny');
                                        mp.setPlaybackQuality('tiny');
                                    }
                                } catch (e) {}
                            }
                        }
                    });
                });
            }

            setupVideoHooks(document.querySelector('video'));
            injectPrevButton();

            // MutationObserver untuk deteksi kelas ad-showing, tombol Prev, dan elemen dialog secara instant (0ms)
            if (!window._ytmAdObserver) {
                try {
                    window._ytmAdObserver = new MutationObserver(function() {
                        skipAdsFast();
                        injectPrevButton();
                        checkYouThere();
                        dismissPromos();
                    });
                    window._ytmAdObserver.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['class', 'dialog', 'open']
                    });
                } catch (e) {}
            }

            if (window._ytmBypassInterval) {
                clearInterval(window._ytmBypassInterval);
            }
            window._ytmBypassInterval = setInterval(function() {
                var v = document.querySelector('video');
                setupVideoHooks(v);
                injectPrevButton();
                skipAdsFast();
                checkYouThere();
                dismissPromos();

                // Autonomous Watchdog: Jika user berniat memutar lagu tapi video paused
                if (window._ytmUserIntentPlaying && v && v.paused) {
                    var nowPause = Date.now();
                    if (!window._ytmUnexpectedPauseStart) {
                        window._ytmUnexpectedPauseStart = nowPause;
                    }
                    var pausedMs = nowPause - window._ytmUnexpectedPauseStart;
                    if (pausedMs > 200 && (!window._ytmLastReviveTime || (nowPause - window._ytmLastReviveTime > 800))) {
                        window._ytmLastReviveTime = nowPause;
                        revivePlayback('watchdog (' + pausedMs + 'ms)');
                    }
                } else if (v && !v.paused) {
                    window._ytmUnexpectedPauseStart = 0;
                }

                // Laporkan metadata dan status pemutaran lagu ke MediaSession Android
                try {
                    var sTitle = '';
                    var sArtist = '';
                    var sArt = '';
                    if (navigator.mediaSession && navigator.mediaSession.metadata) {
                        sTitle = (navigator.mediaSession.metadata.title || '').trim();
                        sArtist = (navigator.mediaSession.metadata.artist || '').trim();
                        var arts = navigator.mediaSession.metadata.artwork;
                        if (arts && arts.length > 0) {
                            sArt = arts[arts.length - 1].src || '';
                        }
                    }
                    var playerBar = document.querySelector('ytmusic-player-bar');
                    if (playerBar) {
                        if (!sTitle) {
                            var tElem = playerBar.querySelector('.content-info-wrapper .title, yt-formatted-string.title, .title');
                            if (tElem) sTitle = (tElem.getAttribute('title') || tElem.textContent || '').trim();
                        }
                        if (!sArtist) {
                            var aElem = playerBar.querySelector('.content-info-wrapper .byline, .content-info-wrapper .subtitle, yt-formatted-string.byline, .byline, .subtitle');
                            if (aElem) sArtist = (aElem.getAttribute('title') || aElem.textContent || '').trim();
                        }
                        if (!sArt) {
                            var imgElem = playerBar.querySelector('img.image, #song-image img, .thumbnail-image-wrapper img');
                            if (imgElem && imgElem.src) sArt = imgElem.src;
                        }
                    }
                    var sVid = '';
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.getVideoData === 'function') {
                        var vd = mp.getVideoData();
                        if (vd) {
                            if (!sTitle && vd.title) sTitle = vd.title.trim();
                            if (!sArtist && vd.author) sArtist = vd.author.trim();
                            if (vd.video_id) sVid = vd.video_id.trim();
                        }
                    }
                    if (!sVid && playerBar) {
                        var linkElem = playerBar.querySelector('a[href*="watch?v="]');
                        if (linkElem && linkElem.href) {
                            var mLink = linkElem.href.match(/[?&]v=([\w-]{11})/);
                            if (mLink) sVid = mLink[1];
                        }
                    }
                    if (!sVid) {
                        var mUrl = window.location.href.match(/[?&]v=([\w-]{11})/);
                        if (mUrl) sVid = mUrl[1];
                    }
                    if (sVid && sVid !== window._lastReportedVideoId) {
                        window._lastReportedVideoId = sVid;
                        if (window.AndroidBridge && window.AndroidBridge.onVideoId) {
                            window.AndroidBridge.onVideoId(sVid);
                        }
                    }

                    var actualPlaying = !!(v && !v.paused);
                    if (actualPlaying) {
                        window._ytmUserIntentPlaying = true;
                    }
                    // Invariant protection: Jangan biarkan MediaSession turun ke STATE_PAUSED
                    // saat watchdog sedang memulihkan jeda tak terduga (< 3000ms)
                    var isPlay = actualPlaying || (window._ytmUserIntentPlaying && (Date.now() - (window._ytmUnexpectedPauseStart || 0) < 3000));

                    var durMs = (v && v.duration && isFinite(v.duration)) ? Math.round(v.duration * 1000) : 0;
                    var posMs = (v && v.currentTime && isFinite(v.currentTime)) ? Math.round(v.currentTime * 1000) : 0;
                    var nowMs = Date.now();
                    var expectedPosMs = (window._lastReportedPos || 0) + (window._lastReportedPlay ? (nowMs - (window._lastReportTime || nowMs)) : 0);
                    var isSeekJump = Math.abs(posMs - expectedPosMs) > 3500;
                    var isDurChanged = Math.abs(durMs - (window._lastReportedDur || 0)) > 1500;

                    if (sTitle && (
                        sTitle !== window._lastReportedTitle ||
                        sArtist !== window._lastReportedArtist ||
                        isPlay !== window._lastReportedPlay ||
                        sArt !== window._lastReportedArt ||
                        isDurChanged ||
                        isSeekJump
                    )) {
                        window._lastReportedTitle = sTitle;
                        window._lastReportedArtist = sArtist;
                        window._lastReportedPlay = isPlay;
                        window._lastReportedArt = sArt;
                        window._lastReportedDur = durMs;
                        window._lastReportedPos = posMs;
                        window._lastReportTime = nowMs;
                        if (window.AndroidBridge && window.AndroidBridge.onSongState) {
                            window.AndroidBridge.onSongState(sTitle, sArtist, isPlay, durMs, posMs, sArt);
                        }
                    }
                } catch (e) {}
            }, 100);
        """.trimIndent()
        executeJs(script)
    }
}
