package com.batterypulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.batterypulse.app.ui.BatteryPulseApp
import com.batterypulse.app.ui.theme.BatteryPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BatteryPulseTheme {
                BatteryPulseApp()
            }
        }
    }
}
