package com.batterypulse.app

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batterypulse.app.data.Appearance
import com.batterypulse.app.ui.AppScaffold
import com.batterypulse.app.ui.HealthScreen
import com.batterypulse.app.ui.HistoryScreen
import com.batterypulse.app.ui.NowScreen
import com.batterypulse.app.ui.SettingsScreen
import com.batterypulse.app.ui.Tab
import com.batterypulse.app.ui.theme.BatteryPulseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders every screen to PNGs in build/screenshots (CI pushes them to the screenshots branch). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1500dp-xhdpi")
class Screenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, dark: Boolean, settleMs: Long = 3_000, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BatteryPulseTheme(Appearance(if (dark) "dark" else "light", false, false), dark) {
                Surface(color = MaterialTheme.colorScheme.surface) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(settleMs)
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun nowDischargingLight() = shot("1_now_discharging_light", dark = false) {
        AppScaffold(Tab.Now, {}) { p ->
            NowScreen(
                FakeData.snapshot(false), FakeData.recent(false), FakeData.health, FakeData.time, null,
                FakeData.history, monitoring = true, padding = p, onOpenHealth = {},
            )
        }
    }

    @Test
    fun nowChargingDark() = shot("2_now_charging_dark", dark = true) {
        AppScaffold(Tab.Now, {}) { p ->
            NowScreen(
                FakeData.snapshot(true), FakeData.recent(true), FakeData.health, FakeData.chargeTime, FakeData.live,
                FakeData.history, monitoring = false, padding = p, onOpenHealth = {},
            )
        }
    }

    @Test
    fun healthLight() = shot("3_health_light", dark = false) {
        AppScaffold(Tab.Health, {}) { p ->
            HealthScreen(FakeData.snapshot(false), FakeData.health, FakeData.sessions, p)
        }
    }

    @Test
    fun healthDark() = shot("4_health_dark", dark = true) {
        AppScaffold(Tab.Health, {}) { p ->
            HealthScreen(FakeData.snapshot(false), FakeData.health, FakeData.sessions, p)
        }
    }

    @Test
    fun historyLight() = shot("5_history_light", dark = false) {
        AppScaffold(Tab.History, {}) { p -> HistoryScreen(FakeData.history, p) }
    }

    @Test
    fun historyDark() = shot("6_history_dark", dark = true) {
        AppScaffold(Tab.History, {}) { p -> HistoryScreen(FakeData.history, p) }
    }

    @Test
    fun settingsLight() = shot("7_settings_light", dark = false) {
        AppScaffold(Tab.Settings, {}) { p -> SettingsScreen(FakeData.snapshot(false), true, p) }
    }
}
