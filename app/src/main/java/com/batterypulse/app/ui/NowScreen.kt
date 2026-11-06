package com.batterypulse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.HealthEstimate
import com.batterypulse.app.data.LiveCharge
import com.batterypulse.app.data.Sample
import com.batterypulse.app.data.TimeEstimate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    s: BatterySnapshot?,
    recent: List<BatterySnapshot>,
    health: HealthEstimate,
    time: TimeEstimate?,
    live: LiveCharge?,
    history: List<Sample>,
    monitoring: Boolean,
    padding: PaddingValues,
    onOpenHealth: () -> Unit,
) {
    if (s == null) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val withPermission = rememberWithNotificationPermission()
    val dayStart = (history.lastOrNull()?.t ?: System.currentTimeMillis()) - 24 * 3_600_000L
    val usage = remember(history) { computeUsage(history.filter { it.t >= dayStart }) }

    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(s, time) }

        item {
            AnimatedVisibility(
                visible = s.isCharging && live != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (live != null) LiveChargeCard(s, live)
            }
        }

        if (!monitoring) {
            item {
                Card(
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Unlock accurate health", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Background monitoring records every charge, powers alerts and keeps the " +
                                "widget live. It uses a tiny amount of battery.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { withPermission { BatteryMonitor.setMonitoring(context, true) } }) {
                            Text("Turn on")
                        }
                    }
                }
            }
        }

        item {
            val hot = !s.temperatureC.isNaN() && s.temperatureC >= 40f
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Rounded.Bolt,
                        label = "Current",
                        value = formatCurrent(s.currentMa),
                        sub = if (s.currentMa == null) "Not reported" else directionText(s),
                        modifier = Modifier.weight(1f),
                        accent = if (s.isCharging) colors.tertiary else colors.primary,
                        spark = recent.map { it.currentMa ?: Float.NaN },
                    )
                    StatTile(
                        icon = Icons.Rounded.Power,
                        label = "Power",
                        value = formatPower(s.powerW),
                        sub = s.avgCurrentMa?.let { "avg ${formatCurrent(it)}" } ?: directionText(s),
                        modifier = Modifier.weight(1f),
                        accent = colors.tertiary,
                        spark = recent.map { it.powerW ?: Float.NaN },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        icon = Icons.Rounded.FlashOn,
                        label = "Voltage",
                        value = formatVoltage(s.voltageMv),
                        sub = voltageNote(s.voltageMv),
                        modifier = Modifier.weight(1f),
                        accent = colors.secondary,
                        spark = recent.map { it.voltageMv.toFloat() },
                    )
                    StatTile(
                        icon = Icons.Rounded.Thermostat,
                        label = "Temperature",
                        value = formatTemp(s.temperatureC),
                        sub = when {
                            s.temperatureC.isNaN() -> null
                            s.temperatureC >= 45f -> "Too hot — let it cool"
                            hot -> "Warm"
                            s.temperatureC < 5f -> "Cold"
                            else -> "Normal"
                        },
                        modifier = Modifier.weight(1f),
                        accent = if (hot) colors.error else colors.primary,
                        spark = recent.map { it.temperatureC },
                    )
                }
            }
        }

        item { UsageCard(usage) }

        item {
            Card(
                onClick = onOpenHealth,
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    WavyArcGauge(
                        progress = (health.healthPercent ?: 0f) / 100f,
                        color = colors.primary,
                        track = colors.onSecondaryContainer.copy(alpha = 0.12f),
                        modifier = Modifier.size(84.dp),
                    ) {
                        Text(
                            health.healthPercent?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Battery health", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatMah(health.estimatedMah)} of ${formatMah(health.designMah)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            health.confidence.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Open health")
                }
            }
        }

        item {
            SectionCard(title = "Details") {
                InfoRow("Power source", pluggedLabel(s.plugged), icon = Icons.Rounded.Usb)
                InfoRow(
                    "Charge remaining", formatMah(s.chargeCounterMah),
                    "From the fuel gauge", icon = Icons.Rounded.Speed,
                )
                s.energyMwh?.let {
                    InfoRow("Energy remaining", String.format(Locale.getDefault(), "%,.0f mWh", it), icon = Icons.Rounded.FlashOn)
                }
                InfoRow("Android health status", healthLabel(s.health), icon = Icons.Rounded.Smartphone)
                InfoRow("Technology", s.technology?.takeIf { it.isNotBlank() } ?: "—", icon = Icons.Rounded.Bolt)
                if (!s.present) InfoRow("Battery present", "No")
            }
        }
    }
}

private fun directionText(s: BatterySnapshot): String {
    val i = s.currentMa ?: return "—"
    return when {
        i > 0 -> "Into battery"
        i < 0 -> "Out of battery"
        else -> "Idle"
    }
}

private fun voltageNote(mv: Int): String? = when {
    mv <= 0 -> null
    mv >= 4350 -> "High (near full)"
    mv >= 3700 -> "Nominal"
    mv >= 3500 -> "Low"
    else -> "Very low"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(s: BatterySnapshot, time: TimeEstimate?) {
    val colors = MaterialTheme.colorScheme
    val (liquid, onLiquid) = when {
        s.level <= 15 && !s.isCharging -> colors.error to colors.onError
        s.isCharging -> colors.tertiary to colors.onTertiary
        else -> colors.primary to colors.onPrimary
    }
    val shown by animateIntAsState(s.level, tween(900), label = "levelNumber")

    Card(
        shape = HeroShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiquidGauge(
                level = s.level,
                charging = s.isCharging,
                liquid = liquid,
                onLiquid = onLiquid,
                textColor = colors.onSurface,
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f),
            ) { textColor ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s.isCharging) {
                        Icon(
                            Icons.Rounded.Bolt, contentDescription = "Charging",
                            tint = textColor, modifier = Modifier.size(30.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$shown",
                            fontSize = 84.sp,
                            lineHeight = 84.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-3).sp,
                            color = textColor,
                        )
                        Text(
                            "%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
                        )
                    }
                    Text(statusLabel(s.status), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (time != null) {
                val main = time.millis?.let { formatDuration(it) } ?: time.label
                Text(main, style = MaterialTheme.typography.headlineMedium)
                if (time.millis != null) {
                    Text(time.label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Pill(pluggedLabel(s.plugged), Icons.Rounded.Usb, colors.secondaryContainer, colors.onSecondaryContainer)
                if (!s.temperatureC.isNaN()) {
                    Pill(formatTemp(s.temperatureC), Icons.Rounded.Thermostat, colors.secondaryContainer, colors.onSecondaryContainer)
                }
                s.powerW?.let { w ->
                    Pill(
                        String.format(Locale.getDefault(), "%.1f W", kotlin.math.abs(w)),
                        Icons.Rounded.Power, colors.secondaryContainer, colors.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChargeCard(s: BatterySnapshot, live: LiveCharge) {
    val colors = MaterialTheme.colorScheme
    val watts = (s.powerW ?: 0f).coerceAtLeast(0f)
    val peak = maxOf(live.peakPowerW, watts, 1f)
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(chargeSpeedLabel(watts), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(formatDuration(s.timestamp - live.start), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            BigNumber(String.format(Locale.getDefault(), "%.1f", watts), "W")
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (watts / peak).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colors.tertiary,
                trackColor = colors.onTertiaryContainer.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                LiveStat("Added", live.mahAdded?.let { String.format(Locale.getDefault(), "+%,.0f mAh", it) } ?: "—", Modifier.weight(1f))
                LiveStat("Since", "${live.startLevel}% → ${s.level}%", Modifier.weight(1f))
                LiveStat("Peak", String.format(Locale.getDefault(), "%.1f W", live.peakPowerW), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColorAlpha())
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LocalContentColorAlpha(): Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f)

@Composable
private fun UsageCard(u: UsageStats) {
    val colors = MaterialTheme.colorScheme
    val maxRate = maxOf(u.screenOnDrain ?: 0f, u.screenOffDrain ?: 0f, 1f)
    SectionCard(title = "Drain · last 24 h") {
        UsageBar("Screen on", u.screenOnDrain, maxRate, colors.primary)
        UsageBar("Screen off", u.screenOffDrain, maxRate, colors.secondary)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Screen-on time", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(if (u.screenOnMs > 0) formatDuration(u.screenOnMs) else "—", style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("Average drain", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(formatRate(u.drainPerHour), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (u.screenOnDrain == null && u.screenOffDrain == null) {
            Text(
                "Rates appear after about 30 minutes of recorded use.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun UsageBar(label: String, rate: Float?, max: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(formatRate(rate), style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ((rate ?: 0f) / max).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
        )
    }
}
