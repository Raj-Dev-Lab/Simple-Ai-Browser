package com.rajdevlab.sab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rajdevlab.sab.ui.SabBrowserApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = (application as SabApplication).browserEngine
        setContent {
            SabBrowserApp(
                engine = engine,
                initialUri = intent?.dataString
            )
        }
    }
}
