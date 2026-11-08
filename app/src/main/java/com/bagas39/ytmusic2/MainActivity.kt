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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.setDesiredHdrHeadroom(1.0f)
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
