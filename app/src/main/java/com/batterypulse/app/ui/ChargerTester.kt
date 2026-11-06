package com.batterypulse.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatteryReader
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.ChargerResult
import com.batterypulse.app.data.ChargerResults
import com.batterypulse.app.data.chargerRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TEST_MS = 30_000L
private const val WARMUP_MS = 5_000L

sealed interface TestState {
    data object Idle : TestState
    data class Measuring(val elapsedMs: Long, val watts: Float, val peak: Float) : TestState
    data class Done(val avgW: Float, val peakW: Float, val level: Int, val tempC: Float, val source: Int) : TestState
    data class Failed(val reason: String) : TestState
}

/** Full-screen charger benchmark: measures power into the battery for 30 s and saves the result. */
@Composable
fun ChargerTesterScreen(
    live: BatterySnapshot?,
    onClose: () -> Unit,
    initialState: TestState = TestState.Idle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(initialState) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) { ChargerResults.init(context) }
    val saved by ChargerResults.results.collectAsState()
    val colors = MaterialTheme.colorScheme
    val plugged = live?.isPlugged == true

    fun start() {
        job?.cancel()
        job = scope.launch {
            val samples = ArrayList<Float>()
            var peak = 0f
            val t0 = System.currentTimeMillis()
            var last: BatterySnapshot? = null
            while (isActive) {
                val s = withContext(Dispatchers.Default) { BatteryReader.read(context, BatteryMonitor.prefs) }
                last = s
                val elapsed = System.currentTimeMillis() - t0
                if (!s.isPlugged) {
                    state = TestState.Failed("The charger was unplugged. Plug it back in and try again.")
                    return@launch
                }
                val w = (s.powerW ?: 0f).coerceAtLeast(0f)
                if (elapsed >= WARMUP_MS) samples.add(w)
                peak = maxOf(peak, w)
                state = TestState.Measuring(elapsed, w, peak)
                if (elapsed >= TEST_MS) break
                delay(500)
            }
            val l = last
            state = if (samples.isEmpty() || l == null) {
                TestState.Failed("Your phone didn't report charging current, so it can't be measured.")
            } else {
                TestState.Done(samples.average().toFloat(), peak, l.level, l.temperatureC, l.plugged)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.surface)) {
        val appearance by BatteryMonitor.appearance.collectAsState()
        AnimatedBackground(appearance.background, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Charger tester", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Find out how fast your chargers and cables really are.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { job?.cancel(); onClose() }) { Icon(Icons.Rounded.Close, "Close") }
                }
            }
            item {
                GradientCard(top = colors.tertiaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
                        label = "tester",
                    ) { st ->
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            when (st) {
                                TestState.Idle -> IdleContent(plugged, live) { start() }
                                is TestState.Measuring -> MeasuringContent(st)
                                is TestState.Done -> DoneContent(st, saved.size,
                                    onSave = { name ->
                                        ChargerResults.add(
                                            context,
                                            ChargerResult(
                                                System.currentTimeMillis(), name, st.avgW, st.peakW, st.level,
                                                st.tempC, st.source, System.currentTimeMillis(),
                                            ),
                                        )
                                        state = TestState.Idle
                                    },
                                    onRetry = { start() },
                                )
                                is TestState.Failed -> {
                                    Text(st.reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { state = TestState.Idle }) { Text("OK") }
                                }
                            }
                        }
                    }
                }
            }
            if (saved.isNotEmpty()) {
                item { SectionHeader("Your chargers") }
                val ranked = saved.sortedByDescending { it.avgW }
                val best = ranked.first().avgW.coerceAtLeast(1f)
                items(ranked.size) { i ->
                    Entrance(i) {
                        SavedCharger(ranked[i], best, i == 0) { ChargerResults.remove(context, ranked[i].id) }
                    }
                }
            }
            item {
                Text(
                    "Measures the power flowing into your battery, which is what makes it charge. Phones slow " +
                        "charging down when nearly full or warm, so test below about 80% for a fair comparison.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun IdleContent(plugged: Boolean, live: BatterySnapshot?, onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bob by rememberInfiniteTransition(label = "plug").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "bob",
    )
    Box(
        Modifier.size(110.dp).clip(CircleShape).background(colors.tertiary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (plugged) Icons.Rounded.Power else Icons.Rounded.Usb, contentDescription = null,
            tint = colors.tertiary,
            modifier = Modifier.size(56.dp).padding(top = if (plugged) 0.dp else (bob * 8).dp),
        )
    }
    Spacer(Modifier.height(18.dp))
    if (!plugged) {
        Text("Plug in a charger", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Connect the charger and cable you want to test. The test starts when you're ready.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
        )
    } else {
        Text("Ready to test", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "${pluggedLabel(live?.plugged ?: 0)} · ${live?.level ?: 0}% · " +
                (live?.powerW?.let { String.format(Locale.getDefault(), "%.1f W now", it.coerceAtLeast(0f)) } ?: "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        if ((live?.level ?: 0) >= 80) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Battery is above 80%, so the phone may slow charging. Results will look lower than usual.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start 30-second test") }
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep the screen on and don't use the phone during the test.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeasuringContent(st: TestState.Measuring) {
    val colors = MaterialTheme.colorScheme
    val scale = maxOf(30f, st.peak * 1.2f)
    WavyArcGauge(
        progress = st.watts / scale,
        color = colors.tertiary,
        track = colors.tertiary.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(1f),
        stroke = 10.dp, amplitude = 2.6.dp, wavelength = 34.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(String.format(Locale.getDefault(), "%.1f", st.watts), style = MaterialTheme.typography.displayMedium)
            Text("watts", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            Text(chargerRating(st.watts), style = MaterialTheme.typography.titleMedium, color = colors.tertiary)
        }
    }
    Spacer(Modifier.height(16.dp))
    LinearProgressIndicator(
        progress = { (st.elapsedMs.toFloat() / TEST_MS).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = colors.tertiary,
        trackColor = colors.tertiary.copy(alpha = 0.15f),
        strokeCap = StrokeCap.Round,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Measuring… ${((TEST_MS - st.elapsedMs).coerceAtLeast(0) / 1000)} s left · peak " +
            String.format(Locale.getDefault(), "%.1f W", st.peak),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun DoneContent(st: TestState.Done, count: Int, onSave: (String) -> Unit, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("Charger ${count + 1}") }
    Text("Result", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(String.format(Locale.getDefault(), "%.1f", st.avgW), fontSize = 64.sp, lineHeight = 64.sp, style = MaterialTheme.typography.displayLarge)
        Text(" W", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
    }
    Text(
        chargerRating(st.avgW),
        style = MaterialTheme.typography.titleMedium,
        color = colors.onTertiary,
        modifier = Modifier.clip(CircleShape).background(colors.tertiary).padding(horizontal = 14.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(14.dp))
    HeroStatStrip(
        stats = listOf(
            HeroStat(Icons.Rounded.Power, "Peak", String.format(Locale.getDefault(), "%.1f W", st.peakW)),
            HeroStat(Icons.Rounded.Usb, "Type", pluggedLabel(st.source)),
            HeroStat(Icons.Rounded.Power, "At level", "${st.level}%"),
        ),
        container = colors.surfaceContainerLowest.copy(alpha = 0.7f),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        label = { Text("Name this charger") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Test again") }
        Button(onClick = { onSave(name.ifBlank { "Charger ${count + 1}" }) }, modifier = Modifier.weight(1f)) { Text("Save") }
    }
}

@Composable
private fun SavedCharger(r: ChargerResult, best: Float, top: Boolean, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (top) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Fastest",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onPrimary,
                            modifier = Modifier.clip(CircleShape).background(colors.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "${chargerRating(r.avgW)} · peak " + String.format(Locale.getDefault(), "%.1f W", r.peakW) +
                        " · at ${r.level}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (r.avgW / best).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (top) colors.primary else colors.tertiary,
                    trackColor = colors.tertiary.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(String.format(Locale.getDefault(), "%.1f W", r.avgW), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete", tint = colors.onSurfaceVariant) }
        }
    }
}
