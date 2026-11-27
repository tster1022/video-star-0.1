package com.example

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.WindowManager
import com.example.data.VideoStarRepository
import com.example.engine.packs.CustomPackRepository
import com.example.navigation.VideoStarNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoStarRepository.initialize(applicationContext)
        CustomPackRepository.initialize(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        enableHighRefreshRateMode()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            var isDarkEditor by remember { mutableStateOf(false) }

            VideoStarNavHost(
                onDarkModeChanged = { dark ->
                    if (isDarkEditor != dark) {
                        isDarkEditor = dark
                        if (dark) {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                            )
                        } else {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                            )
                        }
                    }
                }
            )
        }
    }

    private fun enableHighRefreshRateMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val bestMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (bestMode != null && bestMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        }
    }
}
