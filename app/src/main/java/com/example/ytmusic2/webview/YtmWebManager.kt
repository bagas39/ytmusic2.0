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
        webView.setOnLongClickListener { true }
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
            fun onSongState(title: String, artist: String, isPlaying: Boolean) {
                if (title.isNotEmpty()) {
                    onSongStateChanged?.invoke(SongMetadata(title, artist, isPlaying))
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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

    fun captureCurrentSong() {
        val url = webView.url ?: return
        val videoId = extractVideoId(url)
        executeJs(
            """
            (function() {
                var sTitle = '';
                var sArtist = '';
                if (navigator.mediaSession && navigator.mediaSession.metadata) {
                    sTitle = navigator.mediaSession.metadata.title || '';
                    sArtist = navigator.mediaSession.metadata.artist || '';
                }
                if (!sTitle) {
                    var t = document.querySelector('ytmusic-player-bar .title, .ytmusic-player-bar .title, yt-formatted-string.title');
                    if (t) sTitle = t.textContent.trim();
                }
                if (!sArtist) {
                    var a = document.querySelector('ytmusic-player-bar .byline, ytmusic-player-bar .subtitle, .ytmusic-player-bar .byline, .ytmusic-player-bar .subtitle');
                    if (a) sArtist = a.textContent.trim();
                }
                var v = document.querySelector('video');
                var isPlay = (v && !v.paused);
                if (sTitle && window.AndroidBridge && window.AndroidBridge.onSongState) {
                    window.AndroidBridge.onSongState(sTitle, sArtist, isPlay);
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
                    s.textContent = '* { -webkit-user-select: none !important; user-select: none !important; -webkit-touch-callout: none !important; }';
                    (document.head || document.documentElement).appendChild(s);
                }
            } catch (e) {}

            try {
                Object.defineProperty(document, 'hidden', { value: false, writable: true, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: true, configurable: true });
                document.dispatchEvent(new Event('visibilitychange'));

                // Fake focus: YTM free-tier cek focus buat deteksi background playback
                Object.defineProperty(document, 'hasFocus', { value: function() { return true; }, writable: true, configurable: true });
                document.dispatchEvent(new Event('focus'));
                window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
                window.addEventListener('focusout', function(e) { e.stopImmediatePropagation(); }, true);
                window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
            } catch (e) {}

            // Listener otomatis untuk langsung pindah saat lagu selesai diputar
            var videoElem = document.querySelector('video');
            if (videoElem && !videoElem._hasEndedListener) {
                videoElem._hasEndedListener = true;
                videoElem.addEventListener('ended', function() {
                    var nextBtn = document.querySelector('.next-button') || document.querySelector('#next-button');
                    if (nextBtn) { nextBtn.click(); }
                });
            }

            if (window._ytmBypassInterval) {
                clearInterval(window._ytmBypassInterval);
            }
            window._ytmBypassInterval = setInterval(function() {
                var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, .ad-skip-button');
                if (skipBtn) { skipBtn.click(); }

                // "Are you still there?" -> auto-continue
                var youThere = document.querySelector('ytmusic-you-there-renderer');
                if (youThere) {
                    try {
                        console.log('[YTM-BYPASS] you-there FOUND: ' + youThere.outerHTML.slice(0, 500));
                    } catch (e) {}
                    var ytBtn = youThere.querySelector('tp-yt-paper-button, button, [role="button"]');
                    if (ytBtn) { ytBtn.click(); console.log('[YTM-BYPASS] you-there CLICKED'); }
                }

                // Premium upsell / dialog -> tutup
                var dismiss = document.querySelector(
                    'tp-yt-paper-dialog button[aria-label="Close"], ' +
                    'ytmusic-dialog button[aria-label="Close"], ' +
                    'button[aria-label="Dismiss"]'
                );
                if (!dismiss) {
                    var dialogs = document.querySelectorAll('tp-yt-paper-dialog, ytmusic-dialog');
                    dialogs.forEach(function(dlg) {
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

                var isAdShowing = document.querySelector('.ad-showing, .ytp-ad-player-overlay');
                var v = document.querySelector('video');
                if (isAdShowing && v) {
                    v.muted = true;
                    v.playbackRate = 16.0;
                    if (isFinite(v.duration)) { v.currentTime = v.duration; }
                } else if (v && v.playbackRate > 1.0) {
                    v.playbackRate = 1.0;
                    v.muted = false;
                }

                // Laporkan status lagu untuk notifikasi Spotify-style
                try {
                    var sTitle = '';
                    var sArtist = '';
                    if (navigator.mediaSession && navigator.mediaSession.metadata) {
                        sTitle = navigator.mediaSession.metadata.title || '';
                        sArtist = navigator.mediaSession.metadata.artist || '';
                    }
                    if (!sTitle) {
                        var tElem = document.querySelector('ytmusic-player-bar .title, .ytmusic-player-bar .title, yt-formatted-string.title');
                        if (tElem) sTitle = tElem.textContent.trim();
                    }
                    if (!sArtist) {
                        var aElem = document.querySelector('ytmusic-player-bar .byline, ytmusic-player-bar .subtitle, .ytmusic-player-bar .byline, .ytmusic-player-bar .subtitle');
                        if (aElem) sArtist = aElem.textContent.trim();
                    }
                    var isPlay = (v && !v.paused);
                    if (sTitle && window.AndroidBridge && window.AndroidBridge.onSongState) {
                        window.AndroidBridge.onSongState(sTitle, sArtist, isPlay);
                    }
                } catch (e) {}
            }, 500);
        """.trimIndent()
        executeJs(script)
    }
}
