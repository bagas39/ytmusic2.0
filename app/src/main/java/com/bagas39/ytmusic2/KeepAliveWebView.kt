package com.bagas39.ytmusic2

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onWindowFocusChange: ((Boolean) -> Unit)? = null
    private var pendingDockFocusCheck: Runnable? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU ||
                event.keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU
            ) {
                cancelPendingDockFocusCheck()
                if (focusControlDock()) return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && hasFocus()) {
                scheduleDockFocusFallbackIfAtBottom()
            } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            ) {
                cancelPendingDockFocusCheck()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && focusControlDock()) {
            cancelPendingDockFocusCheck()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        cancelPendingDockFocusCheck()
        super.onDetachedFromWindow()
    }

    private fun cancelPendingDockFocusCheck() {
        pendingDockFocusCheck?.let { removeCallbacks(it) }
        pendingDockFocusCheck = null
    }

    /**
     * Memindahkan fokus D-Pad ke panel kontrol Compose di bawah WebView.
     */
    private fun focusControlDock(): Boolean {
        val composeDock = rootView?.findViewById<View>(R.id.composeControlDock) ?: return false
        if (composeDock.hasFocus()) return false
        return composeDock.requestFocus(View.FOCUS_DOWN)
    }

    /**
     * Blink memproses ArrowDown secara asinkron di renderer dan sering menelan KeyEvent
     * meski fokus sudah mentok di bagian bawah; pindahkan fokus ke Dock bila DOM tidak bergerak.
     */
    private fun scheduleDockFocusFallbackIfAtBottom() {
        cancelPendingDockFocusCheck()
        val captureJs = """
            (function() {
                var ae = document.activeElement;
                while (ae && ae.shadowRoot && ae.shadowRoot.activeElement) {
                    ae = ae.shadowRoot.activeElement;
                }
                var se = document.scrollingElement || document.documentElement;
                var rect = ae && ae.getBoundingClientRect ? ae.getBoundingClientRect() : null;
                var top = rect ? rect.top : 0;
                var bottom = rect ? rect.bottom : 0;
                var height = rect ? rect.height : window.innerHeight;
                var scrollTop = se ? se.scrollTop : window.scrollY;
                var activeDesc = ae && ae.getAttribute ? (ae.getAttribute('aria-activedescendant') || '') : '';
                window.__ytmDpadTrack = {
                    ae: ae,
                    top: top,
                    bottom: bottom,
                    height: height,
                    scrollTop: scrollTop,
                    activeDesc: activeDesc
                };
            })();
        """.trimIndent()
        evaluateJavascript(captureJs, null)

        val check = Runnable {
            pendingDockFocusCheck = null
            if (!hasFocus()) return@Runnable
            val verifyJs = """
                (function() {
                    var prev = window.__ytmDpadTrack;
                    if (!prev) return 'MOVED';
                    var ae = document.activeElement;
                    while (ae && ae.shadowRoot && ae.shadowRoot.activeElement) {
                        ae = ae.shadowRoot.activeElement;
                    }
                    var se = document.scrollingElement || document.documentElement;
                    var rect = ae && ae.getBoundingClientRect ? ae.getBoundingClientRect() : null;
                    var top = rect ? rect.top : 0;
                    var scrollTop = se ? se.scrollTop : window.scrollY;
                    var activeDesc = ae && ae.getAttribute ? (ae.getAttribute('aria-activedescendant') || '') : '';
                    if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA' || ae.isContentEditable)) {
                        return 'INPUT';
                    }
                    var nearBottom = prev.height < window.innerHeight * 0.5 && prev.bottom >= window.innerHeight * 0.72;
                    if (!nearBottom) return 'MOVED';
                    var sameEl = ae === prev.ae;
                    var sameTop = Math.abs(top - prev.top) < 2;
                    var sameScroll = Math.abs(scrollTop - prev.scrollTop) < 2;
                    var sameDesc = activeDesc === prev.activeDesc;
                    return (sameEl && sameTop && sameScroll && sameDesc) ? 'STUCK' : 'MOVED';
                })();
            """.trimIndent()
            evaluateJavascript(verifyJs) { result ->
                if (hasFocus() && result == "\"STUCK\"") {
                    focusControlDock()
                }
            }
        }
        pendingDockFocusCheck = check
        postDelayed(check, 160L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasFocus()) {
            requestFocus()
        }
        return super.onTouchEvent(event)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Paksa WebView selalu menerima status VISIBLE agar pemutaran tidak ditangguhkan
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        onWindowFocusChange?.invoke(hasWindowFocus)
    }

    // Blokir ActionMode (TextSelection / FloatingToolbar) agar sentuhan tidak diserap oleh menu teks
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return null
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        return null
    }
}
