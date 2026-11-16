package com.bagas39.ytmusic2

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.bagas39.ytmusic2.coordinator.AppCoordinator

class MainActivity : AppCompatActivity() {

    private lateinit var coordinator: AppCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val refreshRate = display?.mode?.refreshRate ?: 60.0f
            window.attributes = window.attributes.apply {
                preferredRefreshRate = refreshRate
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                window.setDesiredHdrHeadroom(1.0f)
            } catch (_: Throwable) {
                // Diabaikan jika framework OEM tertentu belum mendukung method ini
            }
        }

        coordinator = AppCoordinator(this)
        coordinator.onCreate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!coordinator.handleBackPressed()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        coordinator.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        coordinator.onWindowFocusChanged(hasFocus)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    coordinator.handlePlayPause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    coordinator.handlePause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    coordinator.handlePlay()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    coordinator.handlePause()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    coordinator.handleNext()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    coordinator.handlePrevious()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        coordinator.onPause()
    }

    override fun onStop() {
        super.onStop()
        coordinator.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        coordinator.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        coordinator.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
