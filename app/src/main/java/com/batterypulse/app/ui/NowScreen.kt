package com.batterypulse.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.HealthEstimate
import com.batterypulse.app.data.TimeEstimate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    s: BatterySnapshot?,
    health: HealthEstimate,
    time: TimeEstimate?,
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
    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(s, time) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Rounded.Bolt,
                    label = "Current",
                    value = formatCurrent(s.currentMa),
                    sub = s.avgCurrentMa?.let { "avg ${formatCurrent(it)}" }
                        ?: if (s.currentMa == null) "Not reported by device" else directionText(s),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Rounded.Power,
                    label = "Power",
                    value = formatPower(s.powerW),
                    sub = directionText(s),
                    modifier = Modifier.weight(1f),
                    accent = colors.tertiary,
                )
            }
        }
        item {
            val hot = !s.temperatureC.isNaN() && s.temperatureC >= 40f
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    icon = Icons.Rounded.FlashOn,
                    label = "Voltage",
                    value = formatVoltage(s.voltageMv),
                    modifier = Modifier.weight(1f),
                    accent = colors.secondary,
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
                )
            }
        }
        item {
            Card(
                onClick = onOpenHealth,
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Battery health", style = MaterialTheme.typography.labelLarge)
                        Text(
                            health.healthPercent?.let { String.format(Locale.getDefault(), "%.0f%%", it) } ?: "Measuring…",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(health.confidence.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Est. capacity", style = MaterialTheme.typography.labelMedium)
                        Text(formatMah(health.estimatedMah), style = MaterialTheme.typography.titleMedium)
                        health.cycleCount?.let {
                            Text("$it cycles", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "Details") {
                InfoRow("Power source", pluggedLabel(s.plugged))
                InfoRow("Charge remaining", formatMah(s.chargeCounterMah), "From the fuel gauge's charge counter")
                s.energyMwh?.let { InfoRow("Energy remaining", String.format(Locale.getDefault(), "%,.0f mWh", it)) }
                InfoRow("Android health status", healthLabel(s.health))
                InfoRow("Technology", s.technology?.takeIf { it.isNotBlank() } ?: "—")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(s: BatterySnapshot, time: TimeEstimate?) {
    val colors = MaterialTheme.colorScheme
    val ringColor = when {
        s.level <= 15 && !s.isCharging -> colors.error
        s.isCharging -> colors.tertiary
        else -> colors.primary
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val boltAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "bolt",
    )
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LevelRing(level = s.level, color = ringColor, modifier = Modifier.size(232.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (s.isCharging) {
                        Icon(
                            Icons.Rounded.Bolt, contentDescription = "Charging",
                            tint = ringColor,
                            modifier = Modifier.size(28.dp).alpha(boltAlpha),
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${s.level}", fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp)
                        Text(
                            "%", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 12.dp, start = 2.dp),
                        )
                    }
                    Text(statusLabel(s.status), style = MaterialTheme.typography.titleMedium)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Pill(
                    text = pluggedLabel(s.plugged),
                    icon = Icons.Rounded.Usb,
                    container = colors.surface.copy(alpha = 0.6f),
                    content = colors.onSurface,
                )
                if (time != null) {
                    val text = time.millis?.let { "${formatDuration(it)} ${time.label}" } ?: time.label
                    Pill(
                        text = text,
                        icon = Icons.Rounded.Schedule,
                        container = colors.surface.copy(alpha = 0.6f),
                        content = colors.onSurface,
                    )
                }
                s.powerW?.let { w ->
                    if (s.isCharging && w > 0f) {
                        Pill(
                            text = String.format(Locale.getDefault(), "%.1f W in", w),
                            icon = Icons.Rounded.Speed,
                            container = colors.surface.copy(alpha = 0.6f),
                            content = colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}
