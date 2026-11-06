package com.batterypulse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.ui.BatteryPulseApp
import com.batterypulse.app.ui.theme.BatteryPulseTheme
import com.batterypulse.app.widget.BatteryWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by BatteryMonitor.appearance.collectAsStateWithLifecycle()
            val dark = appearance.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            BatteryPulseTheme(appearance, dark) {
                BatteryPulseApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep the home-screen widget fresh even when background monitoring is off.
        lifecycleScope.launch { BatteryWidget.refresh(applicationContext) }
    }
}
