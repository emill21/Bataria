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
import com.batterypulse.app.ui.AppsContent
import com.batterypulse.app.ui.ChargerTesterScreen
import com.batterypulse.app.ui.TestState
import com.batterypulse.app.data.AppDrain
import com.batterypulse.app.data.ChargerResult
import com.batterypulse.app.data.ChargerResults
import com.batterypulse.app.data.DrainReport
import com.batterypulse.app.widget.chartBitmap
import com.batterypulse.app.widget.ringBitmap
import androidx.test.core.app.ApplicationProvider
import com.batterypulse.app.ui.OnboardingScreen
import com.batterypulse.app.ui.SettingsScreen
import com.batterypulse.app.ui.Tab
import com.batterypulse.app.ui.theme.BatariaTheme
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
            BatariaTheme(Appearance(if (dark) "dark" else "light", accent, false, background), dark) {
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

    private val fakeReport = run {
        val apps = listOf(
            "YouTube" to 612f, "Instagram" to 431f, "Chrome" to 288f, "Spotify" to 164f,
            "Google Maps" to 139f, "WhatsApp" to 92f, "Gmail" to 41f,
        )
        val total = apps.sumOf { it.second.toDouble() }.toFloat() + 240f + 120f
        DrainReport(
            apps = apps.mapIndexed { i, (n, m) ->
                AppDrain("pkg.$i", n, null, ((m / 4.2f) * 60_000).toLong(), m, m / total)
            },
            screenOffMah = 240f, screenOffMs = 9 * 3_600_000L, otherMah = 120f, totalMah = total,
        )
    }

    @Test
    fun appsDark() = shot("12_apps_dark_ocean", dark = true, accent = ocean, background = "aurora") { bg ->
        AppScaffold(Tab.Apps, {}, bg) { p ->
            AppsContent(hasAccess = true, report = fakeReport, loading = false, range = 0, onRange = {}, onGrant = {}, padding = p)
        }
    }

    @Test
    fun appsPermission() = shot("13_apps_permission_light", dark = false, background = "particles") { bg ->
        AppScaffold(Tab.Apps, {}, bg) { p ->
            AppsContent(hasAccess = false, report = null, loading = false, range = 0, onRange = {}, onGrant = {}, padding = p)
        }
    }

    @Test
    fun chargerTester() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ChargerResults.init(ctx)
        if (ChargerResults.results.value.isEmpty()) {
            val now = FakeData.now
            ChargerResults.add(ctx, ChargerResult(1, "Pixel 30W + USB-C cable", 24.3f, 27.9f, 34, 33f, 1, now))
            ChargerResults.add(ctx, ChargerResult(2, "Car charger", 9.6f, 11.2f, 41, 31f, 1, now - 86_400_000))
            ChargerResults.add(ctx, ChargerResult(3, "Laptop USB port", 4.1f, 4.6f, 52, 30f, 2, now - 2 * 86_400_000))
        }
        shot("14_charger_tester_dark", dark = true, accent = teal, background = "rings") { _ ->
            ChargerTesterScreen(
                live = FakeData.snapshot(true), onClose = {},
                initialState = TestState.Done(18.7f, 23.4f, 46, 33.8f, 1),
            )
        }
    }

    @Test
    fun widgetArt() {
        val dir = File("build/screenshots").apply { mkdirs() }
        val ring = ringBitmap(72, 0xFF2B6A3F.toInt(), 0x3C2B6A3F)
        FileOutputStream(File(dir, "15_widget_ring_art.png")).use { ring.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val chart = chartBitmap(FakeData.history, FakeData.now, 0xFF2B6A3F.toInt())
        FileOutputStream(File(dir, "16_widget_chart_art.png")).use { chart.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun launcherIcon() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File("build/screenshots").apply { mkdirs() }
        val size = 432
        val icon = ctx.getDrawable(R.mipmap.ic_launcher) as android.graphics.drawable.AdaptiveIconDrawable
        // Full adaptive icon (system mask applied) and the raw layers
        fun render(d: android.graphics.drawable.Drawable, name: String) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        render(icon, "17_icon_adaptive.png")
        val layers = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(layers)
        icon.background.setBounds(0, 0, size, size); icon.background.draw(c)
        icon.foreground.setBounds(0, 0, size, size); icon.foreground.draw(c)
        FileOutputStream(File(dir, "18_icon_layers.png")).use { layers.compress(Bitmap.CompressFormat.PNG, 100, it) }
        icon.monochrome?.let { mono ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val mc = Canvas(bmp)
            mc.drawColor(0xFFDCE8F5.toInt())
            mono.setTint(0xFF1F3A5F.toInt())
            mono.setBounds(0, 0, size, size); mono.draw(mc)
            FileOutputStream(File(dir, "19_icon_themed.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
