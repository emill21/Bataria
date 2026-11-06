package com.batterypulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.batterypulse.app.data.AppDrain
import com.batterypulse.app.data.AppUsage
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.DrainReport
import com.batterypulse.app.data.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private val RANGES = listOf("Today", "24 hours", "7 days")

@Composable
fun AppsScreen(history: List<Sample>, capacityMah: Float?, padding: PaddingValues) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(AppUsage.hasAccess(context)) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<DrainReport?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Re-check permission when returning from Settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { access = AppUsage.hasAccess(context) }
    }
    LaunchedEffect(access, range, history.size / 30) {
        if (!access) return@LaunchedEffect
        loading = true
        val end = System.currentTimeMillis()
        val start = when (range) {
            0 -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            1 -> end - 24 * 3_600_000L
            else -> end - 7 * 24 * 3_600_000L
        }
        report = withContext(Dispatchers.Default) {
            try {
                AppUsage.report(context, history, start, end, capacityMah ?: 4000f)
            } catch (e: Exception) {
                null
            }
        }
        loading = false
    }
    AppsContent(
        hasAccess = access,
        report = report,
        loading = loading,
        range = range,
        onRange = { range = it },
        onGrant = { AppUsage.openAccessSettings(context) },
        padding = padding,
    )
}

/** Stateless content, also used by screenshot tests. */
@Composable
fun AppsContent(
    hasAccess: Boolean,
    report: DrainReport?,
    loading: Boolean,
    range: Int,
    onRange: (Int) -> Unit,
    onGrant: () -> Unit,
    padding: PaddingValues,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasAccess) {
            animatedItem(0) {
                GradientCard(top = colors.primaryContainer.copy(alpha = 0.8f), bottom = colors.surfaceContainerLow) {
                    Column(Modifier.padding(24.dp)) {
                        IconBadge(Icons.Rounded.Apps, colors.primary, size = 56.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("See which apps drain your battery", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Battery Pulse matches your battery history with which app was on screen, so you can see " +
                                "where your charge goes. Android needs you to allow “Usage access” for this.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.Lock, null, tint = colors.primary, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Stays on your phone. Nothing is uploaded.",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = onGrant) { Text("Allow usage access") }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Find Battery Pulse in the list and switch it on, then come back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        animatedItem(0) {
            Choice(options = RANGES.mapIndexed { i, s -> i.toString() to s }, selected = range.toString()) {
                onRange(it.toInt())
            }
        }

        if (report == null) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator() else Text("No data yet", color = colors.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        animatedItem(1) { DrainSummary(report) }

        if (report.apps.isEmpty()) {
            animatedItem(2) {
                SectionCard {
                    Text(
                        "No app usage recorded in this period yet. Data builds up as Battery Pulse records your " +
                            "battery history; background monitoring makes it more complete.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            animatedItem(2) { SectionHeader("Apps") }
            val max = report.apps.first().mah
            items(report.apps.size) { i ->
                Entrance(minOf(i + 3, 8)) { AppRow(report.apps[i], max, slotColor(i)) }
            }
        }

        animatedItem(4) {
            SectionCard {
                OtherRow(Icons.Rounded.Bedtime, "Screen off", "Idle and background work over ${formatDuration(report.screenOffMs)}", report.screenOffMah, report.totalMah)
                OtherRow(Icons.Rounded.PhoneAndroid, "System & other", "Lock screen, system UI and unidentified use", report.otherMah, report.totalMah)
            }
        }
        animatedItem(5) {
            Text(
                "Estimates: each stretch of battery drain is shared between the apps that were on screen at the time. " +
                    "Background activity counts under Screen off or System.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun slotColor(i: Int): Color {
    val c = MaterialTheme.colorScheme
    return listOf(c.primary, c.tertiary, c.secondary, c.error, c.inversePrimary)[i % 5]
}

@Composable
private fun DrainSummary(r: DrainReport) {
    val colors = MaterialTheme.colorScheme
    val top = r.apps.take(4)
    val slices = top.mapIndexed { i, a -> a.mah to slotColor(i) } +
        listOf((r.totalMah - top.sumOf { it.mah.toDouble() }.toFloat()).coerceAtLeast(0f) to colors.outlineVariant)
    GradientCard(top = colors.primaryContainer.copy(alpha = 0.55f), bottom = colors.surfaceContainerLow) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val total = slices.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.001f)
                    var start = -90f
                    val gap = if (slices.count { it.first > 0f } > 1) 4f else 0f
                    for ((v, c) in slices) {
                        if (v <= 0f) continue
                        val sweep = 360f * v / total
                        drawArc(
                            c, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Butt),
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%,.0f", r.totalMah), style = MaterialTheme.typography.titleLarge)
                    Text("mAh used", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Biggest drain", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(
                    top.firstOrNull()?.label ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                top.firstOrNull()?.let {
                    Text(
                        String.format(Locale.getDefault(), "%.0f%% of battery used", it.share * 100f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                top.drop(1).forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(slotColor(i + 1)))
                        Spacer(Modifier.width(6.dp))
                        Text(a.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(a: AppDrain, max: Float, color: Color) {
    val colors = MaterialTheme.colorScheme
    SectionCard {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (a.icon != null) {
                Image(a.icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text(a.label.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = color) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(String.format(Locale.getDefault(), "%.0f%%", a.share * 100f), style = MaterialTheme.typography.titleMedium, color = color)
                }
                Text(
                    "${formatDuration(a.foregroundMs)} on screen · " + String.format(Locale.getDefault(), "%,.0f mAh", a.mah),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (a.mah / max).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun OtherRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, mah: Float, total: Float) {
    InfoRow(
        title,
        if (total > 0f) String.format(Locale.getDefault(), "%.0f%%", mah / total * 100f) else "—",
        sub + " · " + String.format(Locale.getDefault(), "%,.0f mAh", mah),
        icon = icon,
    )
}
