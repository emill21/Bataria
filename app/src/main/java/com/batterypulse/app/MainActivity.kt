package com.batterypulse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.batterypulse.app.ui.OnboardingScreen
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
        // Bring the background mode in line with settings (e.g. after updating from 2.0).
        try {
            BatteryMonitor.applyMonitoring(this)
        } catch (e: Exception) {
        }
        setContent {
            val appearance by BatteryMonitor.appearance.collectAsStateWithLifecycle()
            val dark = appearance.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            var onboarded by remember { mutableStateOf(BatteryMonitor.prefs.onboardingDone) }
            BatteryPulseTheme(appearance, dark) {
                AnimatedContent(
                    targetState = onboarded,
                    transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(300))) },
                    label = "onboarding",
                ) { done ->
                    if (done) {
                        BatteryPulseApp(onReplayTour = { onboarded = false })
                    } else {
                        OnboardingScreen(appearance, onFinish = {
                            BatteryMonitor.prefs.onboardingDone = true
                            BatteryMonitor.prefs.lastSeenVersion = BuildConfig.VERSION_CODE
                            onboarded = true
                        })
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Keep the home-screen widget fresh even when background monitoring is off.
        lifecycleScope.launch { BatteryWidget.refresh(applicationContext) }
    }
}
