package com.bagas39.ytmusic2

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var onWindowFocusChange: ((Boolean) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
            val root = rootView
            val composeDock = root?.findViewById<View>(R.id.composeControlDock)
            if (composeDock != null && !composeDock.hasFocus()) {
                composeDock.requestFocus()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
