package com.example.ytmusic2.webview

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.ytmusic2.KeepAliveWebView
import java.io.ByteArrayInputStream

class YtmWebManager(private val webView: KeepAliveWebView) {

    var onSongStateChanged: ((SongMetadata) -> Unit)? = null
    var onTitleDetected: ((String) -> Unit)? = null
    var onPageLifecycleInject: (() -> Unit)? = null

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
                if (msg.contains("[YTM-BYPASS]")) {
                    Log.d("YTM-BYPASS", msg)
                }
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
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

    fun getCurrentVideoId(): String? = extractVideoId(webView.url)

    fun executeJs(script: String) {
        webView.evaluateJavascript(script, null)
    }

    fun clickPrevious() {
        executeJs("var b = document.querySelector('.previous-button') || document.querySelector('#previous-button'); if(b) b.click();")
    }

    fun clickPlayPause() {
        executeJs(
            """
            var btn = document.querySelector('#play-pause-button') || document.querySelector('.play-pause-button');
            if (btn) { btn.click(); } else {
                var v = document.querySelector('video');
                if (v) { v.paused ? v.play() : v.pause(); }
            }
            """.trimIndent()
        )
    }

    fun clickNext() {
        executeJs("var b = document.querySelector('.next-button') || document.querySelector('#next-button'); if(b) b.click();")
    }

    fun pausePlayback() {
        executeJs("var v = document.querySelector('video'); if (v) v.pause();")
    }

    fun resumePlayback() {
        executeJs("var v = document.querySelector('video'); if (v && v.paused) v.play();")
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
                var mp = document.getElementById('movie_player');
                if (mp && typeof mp.getVideoData === 'function') {
                    var vd = mp.getVideoData();
                    if (vd) {
                        if (!sTitle && vd.title) sTitle = vd.title.trim();
                        if (!sArtist && vd.author) sArtist = vd.author.trim();
                    }
                }
                var v = document.querySelector('video');
                var isPlay = (v && !v.paused);
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

            function setupVideoHooks(v) {
                if (!v || v._hasFastSkipHooks) return;
                v._hasFastSkipHooks = true;
                v.addEventListener('ended', function() {
                    var nextBtn = document.querySelector('.next-button') || document.querySelector('#next-button');
                    if (nextBtn) { nextBtn.click(); }
                });
                var events = ['loadedmetadata', 'durationchange', 'canplay', 'play', 'playing', 'timeupdate'];
                events.forEach(function(evt) {
                    v.addEventListener(evt, skipAdsFast);
                });
            }

            setupVideoHooks(document.querySelector('video'));

            var _lastYouThereTime = 0;
            function isElementVisible(el) {
                if (!el) return false;
                if (el.offsetParent === null && el.offsetWidth === 0 && el.offsetHeight === 0) return false;
                var style = window.getComputedStyle ? window.getComputedStyle(el) : null;
                if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
                return true;
            }

            function checkYouThere() {
                var youThere = document.querySelector('ytmusic-you-there-renderer');
                if (!youThere) return;

                // Pastikan dialog benar-benar aktif/terlihat, bukan elemen dorman yang di-cache di DOM
                var parentDlg = youThere.closest('tp-yt-paper-dialog, ytmusic-dialog, [role="dialog"]');
                if (parentDlg) {
                    if (parentDlg.getAttribute('aria-hidden') === 'true') return;
                    if (parentDlg.style.display === 'none') return;
                    if (typeof parentDlg.opened === 'boolean' && !parentDlg.opened) return;
                    if (!isElementVisible(parentDlg)) return;
                }

                if (!isElementVisible(youThere)) return;

                var confirmBtn = youThere.querySelector('yt-button-renderer[dialog-confirm] button, [dialog-confirm], tp-yt-paper-button, button');
                if (!confirmBtn || !isElementVisible(confirmBtn)) return;

                var now = Date.now();
                if (now - _lastYouThereTime > 1500) {
                    _lastYouThereTime = now;
                    try {
                        confirmBtn.click();
                        console.log('[YTM-BYPASS] you-there CONFIRMED');
                    } catch (e) {}

                    setTimeout(function() {
                        var v = document.querySelector('video');
                        if (v && v.paused) {
                            try {
                                v.play();
                                console.log('[YTM-BYPASS] you-there AUTO-RESUMED playback');
                            } catch (e) {
                                var pBtn = document.querySelector('#play-pause-button, .play-pause-button');
                                if (pBtn) try { pBtn.click(); } catch (err) {}
                            }
                        }
                    }, 250);
                }
            }

            // MutationObserver untuk deteksi kelas ad-showing dan elemen iklan/dialog secara instant (0ms)
            if (!window._ytmAdObserver) {
                try {
                    window._ytmAdObserver = new MutationObserver(function() {
                        skipAdsFast();
                        checkYouThere();
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
                skipAdsFast();
                checkYouThere();

                // Premium upsell / dialog -> tutup
                var dismiss = document.querySelector(
                    'tp-yt-paper-dialog button[aria-label="Close"], ' +
                    'ytmusic-dialog button[aria-label="Close"], ' +
                    'button[aria-label="Dismiss"]'
                );
                if (!dismiss) {
                    var dialogs = document.querySelectorAll('tp-yt-paper-dialog, ytmusic-dialog');
                    dialogs.forEach(function(dlg) {
                        // Jangan sentuh jika ini dialog you-there (sudah di-handle checkYouThere agar tidak double-click)
                        if (dlg.querySelector('ytmusic-you-there-renderer')) {
                            return;
                        }
                        if (dlg.open || dlg.hasAttribute('open')) {
                            try {
                                console.log('[YTM-BYPASS] dialog FOUND: ' + dlg.outerHTML.slice(0, 800));
                            } catch (e) {}
                            var btns = dlg.querySelectorAll('button');
                            if (btns.length) {
                                btns[btns.length - 1].click();
                                console.log('[YTM-BYPASS] dialog CLICKED last button');
                            }
                        }
                    });
                } else {
                    dismiss.click();
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
                    var mp = document.getElementById('movie_player');
                    if (mp && typeof mp.getVideoData === 'function') {
                        var vd = mp.getVideoData();
                        if (vd) {
                            if (!sTitle && vd.title) sTitle = vd.title.trim();
                            if (!sArtist && vd.author) sArtist = vd.author.trim();
                        }
                    }
                    var isPlay = (v && !v.paused);
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
