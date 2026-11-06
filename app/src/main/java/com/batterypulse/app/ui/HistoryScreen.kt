package com.batterypulse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batterypulse.app.data.Sample
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val RANGES_H = listOf(6, 24, 72)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<Sample>, padding: PaddingValues) {
    var selected by rememberSaveable { mutableIntStateOf(1) }
    val hours = RANGES_H[selected]
    val now = history.lastOrNull()?.t?.coerceAtLeast(System.currentTimeMillis()) ?: System.currentTimeMillis()
    val start = now - hours * 3_600_000L
    val pts = remember(history, hours) { history.filter { it.t >= start } }
    val stats = remember(pts) { computeStats(pts) }
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RANGES_H.forEachIndexed { i, h ->
                    SegmentedButton(
                        selected = i == selected,
                        onClick = { selected = i },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = RANGES_H.size),
                    ) { Text(if (h < 24) "${h}h" else "${h / 24}d") }
                }
            }
        }

        if (pts.size < 2) {
            item {
                SectionCard {
                    Text(
                        "History is recorded once a minute while the app is open, or all the time with " +
                            "background monitoring turned on in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Avg drain", stats.drainPerHour?.let { fmt("%.1f %%/h", it) } ?: "—", Modifier.weight(1f))
                MiniStat("Avg charge", stats.chargePerHour?.let { fmt("%.0f %%/h", it) } ?: "—", Modifier.weight(1f))
                MiniStat("Peak temp", stats.maxTemp?.let { fmt("%.1f °C", it) } ?: "—", Modifier.weight(1f))
            }
        }

        item {
            ChartCard(
                title = "Battery level",
                current = pts.lastOrNull()?.let { "${it.level}%" },
                points = pts.map { it.t to it.level.toFloat() },
                startT = start, endT = now, minY = 0f, maxY = 100f,
                yLabels = "100%" to "0%",
                color = colors.primary, hours = hours,
            )
        }
        item {
            val values = pts.map { it.currentMa }
            val lo = floor(minOf(values.minOrNull() ?: -500f, 0f) / 500f) * 500f
            val hi = ceil(max(values.maxOrNull() ?: 500f, 0f) / 500f) * 500f
            ChartCard(
                title = "Current",
                current = pts.lastOrNull()?.let { formatCurrent(it.currentMa) },
                points = pts.map { it.t to it.currentMa },
                startT = start, endT = now,
                minY = lo, maxY = if (hi > lo) hi else lo + 500f,
                yLabels = fmt("%+,.0f", hi) to fmt("%+,.0f", lo),
                color = colors.tertiary, hours = hours, baseline = 0f,
            )
        }
        item {
            val temps = pts.map { it.tempC }.filter { !it.isNaN() }
            val lo = floor((temps.minOrNull() ?: 20f) - 2f)
            val hi = ceil((temps.maxOrNull() ?: 40f) + 2f)
            ChartCard(
                title = "Temperature",
                current = pts.lastOrNull()?.let { formatTemp(it.tempC) },
                points = pts.map { it.t to it.tempC },
                startT = start, endT = now, minY = lo, maxY = hi,
                yLabels = fmt("%.0f°", hi) to fmt("%.0f°", lo),
                color = colors.error, hours = hours,
            )
        }
    }
}

private fun fmt(pattern: String, v: Float) = String.format(Locale.getDefault(), pattern, v)

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    current: String?,
    points: List<Pair<Long, Float>>,
    startT: Long,
    endT: Long,
    minY: Float,
    maxY: Float,
    yLabels: Pair<String, String>,
    color: Color,
    hours: Int,
    baseline: Float? = null,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (current != null) {
                    Text(current, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.height(160.dp)) {
                Column(
                    Modifier.fillMaxHeight().width(48.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(yLabels.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(yLabels.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LineChart(
                    points = points, startT = startT, endT = endT, minY = minY, maxY = maxY,
                    color = color, baseline = baseline,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 6.dp)) {
                Text(
                    if (hours < 24) "−${hours}h" else "−${hours / 24}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class HistoryStats(val drainPerHour: Float?, val chargePerHour: Float?, val maxTemp: Float?)

private fun computeStats(pts: List<Sample>): HistoryStats {
    var dropped = 0f; var drainMs = 0L
    var gained = 0f; var chargeMs = 0L
    for (k in 1 until pts.size) {
        val a = pts[k - 1]; val b = pts[k]
        val dt = b.t - a.t
        if (dt <= 0 || dt > 10 * 60_000L) continue
        if (!a.charging && !b.charging) {
            dropped += (a.level - b.level).coerceAtLeast(0); drainMs += dt
        } else if (a.charging && b.charging) {
            gained += (b.level - a.level).coerceAtLeast(0); chargeMs += dt
        }
    }
    val drain = if (drainMs > 30 * 60_000L) dropped / (drainMs / 3_600_000f) else null
    val charge = if (chargeMs > 10 * 60_000L) gained / (chargeMs / 3_600_000f) else null
    val maxT = pts.map { it.tempC }.filter { !it.isNaN() }.maxOrNull()
    return HistoryStats(drain, charge, maxT)
}
