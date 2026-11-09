package com.batterypulse.app.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.batterypulse.app.data.BatteryReader
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.Prefs
import com.batterypulse.app.data.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val PAGES = 4

@Composable
fun BatariaWearApp(onReplayIntro: () -> Unit, onWhatsNew: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val store = remember { WearStore(context) }
    var snap by remember { mutableStateOf<BatterySnapshot?>(null) }
    var history by remember { mutableStateOf(emptyList<Point>()) }
    var tick by remember { mutableIntStateOf(0) }

    // Live while open: a fresh reading every few seconds.
    LaunchedEffect(tick) {
        while (true) {
            val s = withContext(Dispatchers.IO) {
                BatteryReader.read(context, prefs).also { store.record(it) }
            }
            snap = s
            history = withContext(Dispatchers.IO) { store.history() }
            delay(4_000)
        }
    }

    val pager = rememberPagerState { PAGES }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val s = snap
            Page {
                if (s == null) {
                    item { Caption("Reading battery…") }
                } else when (page) {
                    0 -> nowPage(s, history, store.fahrenheit)
                    1 -> healthPage(Health.compute(context, prefs, store, s), s, store.fahrenheit)
                    2 -> historyPage(history, s)
                    else -> settingsPage(
                        prefs, store,
                        onChanged = { tick++ },
                        onReplayIntro = onReplayIntro,
                        onWhatsNew = onWhatsNew,
                    )
                }
            }
        }
        PageDots(PAGES, pager.currentPage, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}

/** One scrolling page with the time on top and a scroll bar on the side. */
@Composable
private fun Page(content: ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 36.dp),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ Now

private fun ScalingLazyListScope.nowPage(s: BatterySnapshot, history: List<Point>, fahrenheit: Boolean) {
    val charging = s.isCharging || s.isPlugged
    item {
        LevelOrb(s.level, charging, Modifier.size(128.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.level}%", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
                Text(Fmt.status(s), fontSize = 11.sp, color = levelColor(s.level, charging))
            }
        }
    }
    item {
        val (label, ms) = TimeLeft.estimate(s, history)
        Caption(
            when {
                s.isFull -> "Fully charged"
                ms != null -> "About ${Fmt.duration(ms)} $label"
                charging -> "Working out time to full…"
                else -> "Working out time left…"
            },
            color = Palette.onSurface,
        )
    }
    item {
        PillRow {
            StatPill(Icons.Rounded.Bolt, Fmt.power(s), if (charging) "In" else "Out", Modifier.weight(1f))
            StatPill(Icons.Rounded.ElectricMeter, Fmt.current(s), "Current", Modifier.weight(1f))
        }
    }
    item {
        PillRow {
            StatPill(Icons.Rounded.Thermostat, Fmt.temp(s.temperatureC, fahrenheit), "Temp", Modifier.weight(1f),
                tint = if (s.temperatureC >= 40f) Palette.amber else Palette.primary)
            StatPill(Icons.Rounded.Timer, Fmt.voltage(s), "Voltage", Modifier.weight(1f))
        }
    }
    item {
        val drain = TimeLeft.drainPerHour(history, 24 * 3_600_000L, s.timestamp)
        InfoCard(
            "",
            listOfNotNull(
                drain?.let { "Avg drain (24h)" to "${"%.1f".format(it)}%/h" },
                s.chargeCounterMah?.let { "Charge left" to "${it.roundToInt()} mAh" },
                "Technology" to (s.technology ?: "–"),
            ),
        )
    }
}

// ------------------------------------------------------------------ Health

private fun ScalingLazyListScope.healthPage(h: WatchHealth, s: BatterySnapshot, fahrenheit: Boolean) {
    item { PageTitle("Health") }
    item {
        val pct = h.percent
        LevelOrb(pct?.roundToInt()?.coerceAtMost(100) ?: 0, charging = false, Modifier.size(110.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pct?.let { "${it.roundToInt()}%" } ?: "–", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.onSurface)
                Text(
                    when {
                        pct == null -> "Learning"
                        pct >= 90 -> "Excellent"
                        pct >= 80 -> "Good"
                        pct >= 70 -> "Fair"
                        else -> "Worn"
                    },
                    fontSize = 11.sp, color = Palette.primary,
                )
            }
        }
    }
    item { Caption(h.fullSource) }
    item {
        InfoCard(
            "Capacity",
            listOf(
                "Holds now" to (h.fullMah?.let { "${it.roundToInt()} mAh" } ?: "–"),
                "When new" to (h.designMah?.let { "${it.roundToInt()} mAh" } ?: "–"),
            ),
        )
    }
    item { Caption(h.designSource) }
    item {
        InfoCard(
            "Battery",
            listOfNotNull(
                "Condition" to h.condition,
                "Charge cycles" to (h.cycles?.toString() ?: "Not reported"),
                "Temperature" to Fmt.temp(s.temperatureC, fahrenheit),
                "Voltage" to Fmt.voltage(s),
            ),
        )
    }
}

// ------------------------------------------------------------------ History

private fun ScalingLazyListScope.historyPage(history: List<Point>, s: BatterySnapshot) {
    val now = s.timestamp
    val day = 24 * 3_600_000L
    item { PageTitle("Last 24 hours") }
    item {
        if (history.count { now - it.t <= day } < 2) {
            Caption("History fills in as you wear your watch. Bataria checks quietly every 15 minutes.")
        } else {
            LevelChart(history, now - day, now, Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp))
        }
    }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text("24h ago", fontSize = 10.sp, color = Palette.muted, modifier = Modifier.weight(1f))
            Text("now", fontSize = 10.sp, color = Palette.muted)
        }
    }
    item {
        val lastFull = history.lastOrNull { it.level >= 99 }
        val lastPlug = history.zipWithNext().lastOrNull { (a, b) -> !a.charging && b.charging }?.second
        val unplugged = history.zipWithNext().lastOrNull { (a, b) -> a.charging && !b.charging }?.second
        val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        InfoCard(
            "",
            listOfNotNull(
                TimeLeft.drainPerHour(history, day, now)?.let { "Drain, 24h" to "${"%.1f".format(it)}%/h" },
                TimeLeft.drainPerHour(history, 7 * day, now)?.let { "Drain, 7 days" to "${"%.1f".format(it)}%/h" },
                unplugged?.let { "Off charger" to Fmt.duration(now - it.t) },
                lastPlug?.let { "Last charge" to fmt.format(Date(it.t)) },
                lastFull?.let { "Last full" to fmt.format(Date(it.t)) },
                history.filter { now - it.t <= day && it.tempC > 0f }.maxOfOrNull { it.tempC }
                    ?.let { "Hottest" to Fmt.temp(it, false) },
            ).ifEmpty { listOf("So far" to "${history.size} readings") },
        )
    }
    item {
        LevelChart(history, now - 7 * day, now, Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp))
    }
    item { Caption("Last 7 days") }
}

// ------------------------------------------------------------------ Settings

private fun ScalingLazyListScope.settingsPage(
    prefs: Prefs,
    store: WearStore,
    onChanged: () -> Unit,
    onReplayIntro: () -> Unit,
    onWhatsNew: () -> Unit,
) {
    item { PageTitle("Settings") }
    item {
        var f by remember { mutableStateOf(store.fahrenheit) }
        ToggleChip(
            checked = f,
            onCheckedChange = { f = it; store.fahrenheit = it; onChanged() },
            label = { Text("Fahrenheit") },
            toggleControl = { Switch(checked = f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            Text("Capacity when new", fontSize = 12.sp, color = Palette.muted)
            CapacityStepper(prefs, onChanged)
        }
    }
    item {
        Caption("Leave on Auto unless you know your watch's battery size. Health compares against this.")
    }
    item {
        Chip(
            onClick = onWhatsNew,
            label = { Text("What's new") },
            icon = { Icon(Icons.Rounded.NewReleases, null) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Chip(
            onClick = onReplayIntro,
            label = { Text("Replay intro") },
            icon = { Icon(Icons.Rounded.Replay, null) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        var armed by remember { mutableStateOf(false) }
        Chip(
            onClick = {
                if (armed) { store.clearHistory(); armed = false; onChanged() } else armed = true
            },
            label = { Text(if (armed) "Tap again to clear" else "Clear history") },
            icon = { Icon(Icons.Rounded.History, null) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item { Caption("Bataria for Wear OS ${BuildConfig.VERSION_NAME}\nAdd the Bataria tile and complication from your watch face.") }
}

@Composable
private fun CapacityStepper(prefs: Prefs, onChanged: () -> Unit) {
    var v by remember { mutableIntStateOf(prefs.designCapacityOverride) }
    fun set(n: Int) { v = n.coerceIn(0, 2000); prefs.designCapacityOverride = v; onChanged() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { set(if (v <= 150) 0 else v - 10) }, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) {
            Icon(Icons.Rounded.Remove, "Less")
        }
        Text(
            if (v == 0) "Auto" else "$v mAh",
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.onSurface,
        )
        Button(onClick = { set(if (v == 0) 300 else v + 10) }, colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.size(ButtonDefaults.SmallButtonSize)) {
            Icon(Icons.Rounded.Add, "More")
        }
    }
}

// ------------------------------------------------------------------ Intro & What's new

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple("Bataria", "Your battery, on your wrist.", "Live level, power and temperature, with time left worked out from how you actually use your watch."),
        Triple("Health", "How much it still holds", "Bataria learns your battery's real capacity from its charge counter and compares it with when it was new."),
        Triple("Glance", "Tile and complication", "Swipe to your tiles and add Bataria, or put it on your watch face for a level ring at a glance."),
    )
    Page {
        item {
            LevelOrb(level = 72 + step * 9, charging = step == 2, modifier = Modifier.size(84.dp)) {
                Text("${step + 1}/3", fontSize = 13.sp, color = Palette.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
        item { PageTitle(steps[step].first) }
        item { Caption(steps[step].second, color = Palette.primary) }
        item { Caption(steps[step].third) }
        item {
            Chip(
                onClick = { if (step < steps.lastIndex) step++ else onDone() },
                label = { Text(if (step < steps.lastIndex) "Next" else "Start", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun WhatsNewScreen(releases: List<Release>, onDone: () -> Unit) {
    Page {
        item { PageTitle("What's new") }
        releases.forEach { r ->
            item { Caption("${r.version} · ${r.title}", color = Palette.primary) }
            r.items.forEach { line -> item { Caption("• $line", color = Palette.onSurface) } }
        }
        item {
            Chip(
                onClick = onDone,
                label = { Text("Got it", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
