package com.batterypulse.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batterypulse.app.data.Appearance
import com.batterypulse.app.ui.AppScaffold
import com.batterypulse.app.ui.HealthScreen
import com.batterypulse.app.ui.HistoryScreen
import com.batterypulse.app.ui.NowScreen
import com.batterypulse.app.ui.OnboardingScreen
import com.batterypulse.app.ui.SettingsScreen
import com.batterypulse.app.ui.Tab
import com.batterypulse.app.ui.theme.BatteryPulseTheme
import com.batterypulse.app.ui.theme.DEFAULT_SEED
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

    private val violet = 0xFF7E57C2.toInt()
    private val ocean = 0xFF1E88E5.toInt()
    private val rose = 0xFFD81B60.toInt()
    private val amber = 0xFFFFB300.toInt()
    private val teal = 0xFF00897B.toInt()

    private fun shot(
        name: String,
        dark: Boolean,
        accent: Int = DEFAULT_SEED,
        background: String = "none",
        settleMs: Long = 4_000,
        content: @Composable (String) -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BatteryPulseTheme(Appearance(if (dark) "dark" else "light", accent, false, background), dark) {
                content(background)
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
    fun nowDischargingLight() = shot("1_now_light_aurora", dark = false, background = "aurora") { bg ->
        AppScaffold(Tab.Now, {}, bg) { p ->
            NowScreen(
                FakeData.snapshot(false), FakeData.recent(false), FakeData.health, FakeData.time, null,
                FakeData.history, monitoring = true, padding = p, onOpenHealth = {},
            )
        }
    }

    @Test
    fun nowChargingDark() = shot("2_now_dark_violet_bubbles", dark = true, accent = violet, background = "bubbles") { bg ->
        AppScaffold(Tab.Now, {}, bg) { p ->
            NowScreen(
                FakeData.snapshot(true), FakeData.recent(true), FakeData.health, FakeData.chargeTime, FakeData.live,
                FakeData.history, monitoring = true, padding = p, onOpenHealth = {},
            )
        }
    }

    @Test
    fun healthLight() = shot("3_health_light_ocean_waves", dark = false, accent = ocean, background = "waves") { bg ->
        AppScaffold(Tab.Health, {}, bg) { p ->
            HealthScreen(FakeData.snapshot(false), FakeData.health, FakeData.sessions, p)
        }
    }

    @Test
    fun healthDark() = shot("4_health_dark_rose_constellation", dark = true, accent = rose, background = "particles") { bg ->
        AppScaffold(Tab.Health, {}, bg) { p ->
            HealthScreen(FakeData.snapshot(false), FakeData.health, FakeData.sessions, p)
        }
    }

    @Test
    fun historyDark() = shot("5_history_dark_space", dark = true, background = "space") { bg ->
        AppScaffold(Tab.History, {}, bg) { p -> HistoryScreen(FakeData.history, p) }
    }

    @Test
    fun nowDarkPulse() = shot("6_now_dark_teal_pulse", dark = true, accent = teal, background = "rings") { bg ->
        AppScaffold(Tab.Now, {}, bg) { p ->
            NowScreen(
                FakeData.snapshot(false), FakeData.recent(false), FakeData.health, FakeData.time, null,
                FakeData.history, monitoring = true, padding = p, onOpenHealth = {},
            )
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h2300dp-xhdpi")
    fun settingsLight() = shot("7_settings_light_amber_grid", dark = false, accent = amber, background = "grid") { bg ->
        AppScaffold(Tab.Settings, {}, bg) { p -> SettingsScreen(FakeData.snapshot(false), true, p) }
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-xhdpi")
    fun tourWelcome() = shot("8_tour_welcome", dark = true, accent = violet, background = "aurora") { bg ->
        OnboardingScreen(Appearance("dark", violet, false, bg), onFinish = {}, initialPage = 0)
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-xhdpi")
    fun tourHealth() = shot("9_tour_health", dark = false, accent = ocean, background = "waves") { bg ->
        OnboardingScreen(Appearance("light", ocean, false, bg), onFinish = {}, initialPage = 2)
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-xhdpi")
    fun tourMonitoring() = shot("10_tour_monitoring", dark = true, background = "space") { bg ->
        OnboardingScreen(Appearance("dark", DEFAULT_SEED, false, bg), onFinish = {}, initialPage = 4)
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-xhdpi")
    fun tourDone() = shot("11_tour_done", dark = false, accent = rose, background = "bubbles") { bg ->
        OnboardingScreen(Appearance("light", rose, false, bg), onFinish = {}, initialPage = 6)
    }
}
