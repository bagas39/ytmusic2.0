package com.example.ytmusic2.webview

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * AdSkipObserver bertugas mengaudit penampung objek iklan mentah (window._ytmStrippedPayloads)
 * dari luar mesin bypass utama.
 * Membedakan placeholder kosong (clientForecastingAdRenderer) dari iklan video nyata (1 atau 2 bertumpuk)
 * serta mengekstrak durasi detik asli dari metadata iklan tersebut.
 */
class AdSkipObserver(private val webView: WebView) {

    var onAdsSkipped: ((count: Int, durationSeconds: Int) -> Unit)? = null

    init {
        setupBridge()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBridge() {
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onAdAudited(count: Int, durationSeconds: Int) {
                if (count > 0) {
                    val safeSeconds = if (durationSeconds > 0) durationSeconds else (count * 15)
                    onAdsSkipped?.invoke(count, safeSeconds)
                }
            }
        }, "AdShieldBridge")
    }

    fun injectObserverScript() {
        val script = """
            (function() {
                if (window._adShieldAuditorInterval) {
                    clearInterval(window._adShieldAuditorInterval);
                }

                function extractDuration(node) {
                    if (!node || typeof node !== 'object') return 0;
                    if (node.lengthSeconds) {
                        var ls = parseInt(node.lengthSeconds, 10);
                        if (ls > 0 && ls < 600) return ls;
                    }
                    if (node.videoDetails && node.videoDetails.lengthSeconds) {
                        var vls = parseInt(node.videoDetails.lengthSeconds, 10);
                        if (vls > 0 && vls < 600) return vls;
                    }
                    if (node.approxDurationMs) {
                        var ms = parseInt(node.approxDurationMs, 10);
                        if (ms > 0) return Math.max(1, Math.round(ms / 1000));
                    }
                    if (node.adDurationMs) {
                        var ams = parseInt(node.adDurationMs, 10);
                        if (ams > 0) return Math.max(1, Math.round(ams / 1000));
                    }
                    if (typeof node.playerVars === 'string') {
                        var m = node.playerVars.match(/length_seconds=(\d+)/);
                        if (m && m[1]) {
                            var pls = parseInt(m[1], 10);
                            if (pls > 0 && pls < 600) return pls;
                        }
                    }
                    if (node.playerResponse) {
                        try {
                            var pr = (typeof node.playerResponse === 'string')
                                ? JSON.parse(node.playerResponse)
                                : node.playerResponse;
                            if (pr && pr.videoDetails && pr.videoDetails.lengthSeconds) {
                                var prls = parseInt(pr.videoDetails.lengthSeconds, 10);
                                if (prls > 0 && prls < 600) return prls;
                            }
                        } catch (e) {}
                    }
                    return 0;
                }

                function auditPayloadItem(item) {
                    if (!item) return { count: 0, seconds: 0 };
                    if (item.domAd) {
                        var dSec = (item.dur && item.dur > 0 && item.dur < 600) ? item.dur : 15;
                        return { count: 1, seconds: dSec };
                    }

                    var adDurations = [];
                    var seenIds = {};
                    var hasExplicitAdBreak = false;

                    function walk(obj, depth) {
                        if (!obj || typeof obj !== 'object' || depth > 14) return;
                        if (Array.isArray(obj)) {
                            for (var i = 0; i < obj.length; i++) walk(obj[i], depth + 1);
                            return;
                        }

                        if (obj.adBreakServiceRenderer) {
                            hasExplicitAdBreak = true;
                        }

                        var adNode = obj.instreamVideoAdRenderer || obj.playerLegacyDesktopWatchAdsRenderer;
                        if (adNode && typeof adNode === 'object') {
                            var adKey = adNode.externalVideoId || adNode.adVideoId || ('ad_' + adDurations.length);
                            if (!seenIds[adKey]) {
                                seenIds[adKey] = true;
                                var dur = extractDuration(adNode) || extractDuration(obj) || 15;
                                adDurations.push(dur);
                            }
                            return;
                        }

                        for (var k in obj) {
                            if (k === 'clientForecastingAdRenderer') continue;
                            if (Object.prototype.hasOwnProperty.call(obj, k)) {
                                walk(obj[k], depth + 1);
                            }
                        }
                    }

                    walk({ p: item.p, s: item.s, a: item.a }, 0);

                    if (adDurations.length > 0) {
                        var sumSec = 0;
                        for (var j = 0; j < adDurations.length; j++) sumSec += adDurations[j];
                        return { count: adDurations.length, seconds: sumSec };
                    }

                    if ((Array.isArray(item.p) && item.p.length > 0) || hasExplicitAdBreak) {
                        var fbCount = (Array.isArray(item.p) && item.p.length > 0) ? item.p.length : 1;
                        return { count: fbCount, seconds: fbCount * 15 };
                    }

                    // Jika hanya berisi clientForecastingAdRenderer kosong, abaikan (0 iklan)
                    return { count: 0, seconds: 0 };
                }

                window._adShieldAuditorInterval = setInterval(function() {
                    try {
                        var queue = window._ytmStrippedPayloads;
                        if (Array.isArray(queue) && queue.length > 0) {
                            var items = queue.splice(0, queue.length);
                            var batchCount = 0;
                            var batchSeconds = 0;
                            for (var i = 0; i < items.length; i++) {
                                var res = auditPayloadItem(items[i]);
                                batchCount += res.count;
                                batchSeconds += res.seconds;
                            }
                            if (batchCount > 0 && window.AdShieldBridge && window.AdShieldBridge.onAdAudited) {
                                window.AdShieldBridge.onAdAudited(batchCount, batchSeconds);
                            }
                        }
                    } catch (e) {}
                }, 400);
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
}
