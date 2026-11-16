package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.VideoStarRepository
import com.example.navigation.VideoStarNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoStarRepository.initialize(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        setContent {
            var isDarkEditor by mutableStateOf(false)

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
}
