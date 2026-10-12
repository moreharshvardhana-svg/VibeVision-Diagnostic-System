package com.vibevision.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vibevision.app.ui.VibeVisionScreen
import com.vibevision.app.ui.theme.VibeVisionTheme

/**
 * Main Activity hosting the VibeVision Optical Vibration & Diagnostic System.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout for immersive optical viewfinder
        enableEdgeToEdge()

        // Keep screen awake during equipment vibration monitoring
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            VibeVisionTheme {
                VibeVisionScreen()
            }
        }
    }
}
