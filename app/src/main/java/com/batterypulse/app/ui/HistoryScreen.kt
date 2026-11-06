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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.batterypulse.app.data.Sample
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val RANGES_H = listOf(6, 24, 72, 168)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<Sample>, padding: PaddingValues) {
    var selectedRange by rememberSaveable { mutableIntStateOf(1) }
    var cursor by remember { mutableStateOf<Long?>(null) }
    val hours = RANGES_H[selectedRange]
    val now = max(history.lastOrNull()?.t ?: 0L, System.currentTimeMillis())
    val start = now - hours * 3_600_000L
    val pts = remember(history, hours) { history.filter { it.t >= start } }
    val stats = remember(pts) { computeUsage(pts) }
    val chargingRanges = remember(pts) { ranges(pts, { it.t }) { it.charging } }
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RANGES_H.forEachIndexed { i, h ->
                    SegmentedButton(
                        selected = i == selectedRange,
                        onClick = { selectedRange = i; cursor = null },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = RANGES_H.size),
                    ) { Text(if (h < 24) "${h}h" else "${h / 24}d") }
                }
            }
        }

        if (pts.size < 2) {
            item {
                SectionCard {
                    Text(
                        "History is recorded every minute while the app is open, or all the time with " +
                            "background monitoring on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("Drain", formatRate(stats.drainPerHour), Modifier.weight(1f))
                MiniStat("Charge", stats.chargePerHour?.let { fmt("%.0f%%/h", it) } ?: "—", Modifier.weight(1f))
                MiniStat("Peak", stats.maxTemp?.let { fmt("%.1f°", it) } ?: "—", Modifier.weight(1f))
            }
        }

        item {
            Text(
                "Press and drag on a chart to inspect it. Shaded areas are charging.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        item {
            ChartCard(
                title = "Battery level",
                samples = pts,
                value = { it.level.toFloat() },
                format = { "${it.toInt()}%" },
                startT = start, endT = now, minY = 0f, maxY = 100f,
                yLabels = "100%" to "0%",
                color = colors.primary, hours = hours,
                shaded = chargingRanges, shadeColor = colors.tertiary,
                cursor = cursor, onCursor = { cursor = it },
            )
        }
        item {
            val values = downsample(pts.map { it.t to it.currentMa }, start, now).map { it.second }
            val lo = floor(minOf(values.minOrNull() ?: -500f, 0f) / 500f) * 500f
            val hi = ceil(max(values.maxOrNull() ?: 500f, 0f) / 500f) * 500f
            ChartCard(
                title = "Current",
                samples = pts,
                value = { it.currentMa },
                format = { formatCurrent(it) },
                startT = start, endT = now,
                minY = lo, maxY = if (hi > lo) hi else lo + 500f,
                yLabels = fmt("%+,.0f", hi) to fmt("%+,.0f", lo),
                color = colors.tertiary, hours = hours, baseline = 0f,
                shaded = chargingRanges, shadeColor = colors.tertiary,
                cursor = cursor, onCursor = { cursor = it },
            )
        }
        item {
            val temps = downsample(pts.map { it.t to it.tempC }, start, now).map { it.second }.filter { !it.isNaN() }
            val lo = floor((temps.minOrNull() ?: 20f) - 2f)
            val hi = ceil((temps.maxOrNull() ?: 40f) + 2f)
            ChartCard(
                title = "Temperature",
                samples = pts,
                value = { it.tempC },
                format = { formatTemp(it) },
                startT = start, endT = now, minY = lo, maxY = hi,
                yLabels = fmt("%.0f°", hi) to fmt("%.0f°", lo),
                color = colors.error, hours = hours,
                shaded = chargingRanges, shadeColor = colors.tertiary,
                cursor = cursor, onCursor = { cursor = it },
            )
        }
        item {
            val volts = pts.map { it.voltageMv.toFloat() }.filter { it > 0f }
            val lo = floor(((volts.minOrNull() ?: 3500f) - 50f) / 100f) * 100f
            val hi = ceil(((volts.maxOrNull() ?: 4400f) + 50f) / 100f) * 100f
            ChartCard(
                title = "Voltage",
                samples = pts,
                value = { if (it.voltageMv > 0) it.voltageMv.toFloat() else Float.NaN },
                format = { fmt("%.2f V", it / 1000f) },
                startT = start, endT = now, minY = lo, maxY = hi,
                yLabels = fmt("%.1f V", hi / 1000f) to fmt("%.1f V", lo / 1000f),
                color = colors.secondary, hours = hours,
                shaded = chargingRanges, shadeColor = colors.tertiary,
                cursor = cursor, onCursor = { cursor = it },
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
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    samples: List<Sample>,
    value: (Sample) -> Float,
    format: (Float) -> String,
    startT: Long,
    endT: Long,
    minY: Float,
    maxY: Float,
    yLabels: Pair<String, String>,
    color: Color,
    hours: Int,
    shaded: List<Pair<Long, Long>>,
    shadeColor: Color,
    cursor: Long?,
    onCursor: (Long?) -> Unit,
    baseline: Float? = null,
) {
    val points = remember(samples, startT, endT) {
        downsample(samples.map { it.t to value(it) }, startT, endT)
    }
    val gap = maxOf(10 * 60_000L, (endT - startT) / 140 * 3)
    val idx = cursor?.let { nearestIndex(points, it) } ?: points.lastIndex
    val shown = points.getOrNull(idx)
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        shown?.let { if (cursor != null) formatDayClock(it.first) else "Latest" } ?: "No data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (shown != null && !shown.second.isNaN()) {
                    Text(format(shown.second), style = MaterialTheme.typography.headlineSmall, color = color)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.height(170.dp)) {
                Column(
                    Modifier.fillMaxHeight().width(44.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(yLabels.first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(yLabels.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                InteractiveChart(
                    points = points, startT = startT, endT = endT, minY = minY, maxY = maxY,
                    color = color, baseline = baseline, shaded = shaded, shadeColor = shadeColor, gapMs = gap,
                    selectedT = cursor, onSelect = onCursor,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp)) {
                Text(
                    formatDayClock(startT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
