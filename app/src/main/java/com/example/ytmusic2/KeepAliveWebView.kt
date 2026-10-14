package com.example.ytmusic2

import android.content.Context
import android.util.AttributeSet
import android.view.ActionMode
import android.view.View
import android.webkit.WebView

class KeepAliveWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Paksa WebView selalu menerima status VISIBLE agar pemutaran tidak ditangguhkan
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }

    // Blokir ActionMode (TextSelection / FloatingToolbar) agar sentuhan tidak diserap oleh menu teks
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return null
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        return null
    }
}