package com.batterypulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.ChargeSession
import com.batterypulse.app.data.Confidence
import com.batterypulse.app.data.HealthEstimate
import java.util.Locale

@Composable
fun HealthScreen(
    s: BatterySnapshot?,
    health: HealthEstimate,
    sessions: List<ChargeSession>,
    padding: PaddingValues,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = padding.withPage(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        animatedItem(0) { HealthHero(health) }

        animatedItem(1) { CapacityCard(health, s) }

        if (sessions.size >= 2) {
            item { TrendCard(sessions, health.designMah) }
        }

        if (health.confidence != Confidence.HIGH) {
            item {
                SectionCard(title = "How to improve accuracy", container = colors.secondaryContainer) {
                    Tip("1", "Turn on background monitoring so charges are recorded while the app is closed.")
                    Tip("2", "Charge by 15% or more in one go — 20% → 80% is ideal.")
                    Tip("3", "After three charges, confidence becomes High.")
                }
            }
        }

        animatedItem(2) { SectionHeader(if (sessions.isEmpty()) "Charge history" else "Charge history · ${sessions.size}") }
        if (sessions.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "No measured charges yet. Plug in and charge by at least 15%.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            val list = sessions.asReversed().take(30)
            items(list.size) { i -> Entrance(minOf(i + 4, 8)) { SessionCard(list[i], health.designMah) } }
        }

        animatedItem(3) {
            SectionCard(title = "About these numbers") {
                InfoRow("Estimate source", "", health.estimateSource, icon = Icons.Rounded.Science)
                InfoRow("Design capacity", "", health.designSource, icon = Icons.Rounded.BatteryStd)
                s?.let { InfoRow("Android status", healthLabel(it.health), icon = Icons.Rounded.Smartphone) }
            }
        }
    }
}

private fun verdict(pct: Float?): String = when {
    pct == null -> "Measuring"
    pct >= 90f -> "Excellent"
    pct >= 80f -> "Good"
    pct >= 70f -> "Fair"
    else -> "Worn"
}

@Composable
private fun HealthHero(health: HealthEstimate) {
    val colors = MaterialTheme.colorScheme
    val pct = health.healthPercent
    val (accent, container) = when {
        pct == null -> colors.outline to colors.surfaceContainerLow
        pct >= 80f -> colors.primary to colors.primaryContainer.copy(alpha = 0.7f)
        pct >= 70f -> colors.tertiary to colors.tertiaryContainer.copy(alpha = 0.7f)
        else -> colors.error to colors.errorContainer.copy(alpha = 0.7f)
    }
    GradientCard(top = container, bottom = colors.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WavyArcGauge(
                progress = (pct ?: 0f) / 100f,
                color = accent,
                track = accent.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
                stroke = 8.dp,
                amplitude = 2.6.dp,
                wavelength = 38.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Estimated health", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            pct?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                            fontSize = 88.sp, lineHeight = 88.sp,
                            fontWeight = FontWeight.Black, letterSpacing = (-3).sp,
                        )
                        if (pct != null) {
                            Text(
                                "%", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 15.dp, start = 2.dp),
                            )
                        }
                    }
                    Text(verdict(pct), style = MaterialTheme.typography.headlineSmall, color = accent)
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ConfidenceDots(health.confidence, accent)
                    Text(
                        health.confidence.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HeroStatStrip(
                stats = listOf(
                    HeroStat(Icons.Rounded.BatteryStd, "Now", formatMah(health.estimatedMah)),
                    HeroStat(Icons.Rounded.Science, "Design", formatMah(health.designMah)),
                    HeroStat(
                        Icons.Rounded.Autorenew, "Cycles",
                        health.cycleCount?.toString()
                            ?: String.format(Locale.getDefault(), "~%.0f", health.estimatedCycles),
                    ),
                ),
                container = colors.surfaceContainerLowest.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ConfidenceDots(c: Confidence, accent: Color) {
    val level = when (c) {
        Confidence.NONE -> 0
        Confidence.LOW -> 1
        Confidence.MEDIUM -> 2
        Confidence.HIGH -> 3
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .width(28.dp)
                    .height(6.dp)
                    .background(if (i < level) accent else accent.copy(alpha = 0.2f), CircleShape)
            )
        }
    }
}

@Composable
private fun CapacityCard(health: HealthEstimate, s: BatterySnapshot?) {
    val colors = MaterialTheme.colorScheme
    val design = health.designMah
    val est = health.estimatedMah
    SectionCard(title = "Capacity") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            CapacityBar("Design", design, design, colors.outline)
            Spacer(Modifier.height(12.dp))
            CapacityBar("Now", est, design ?: est, colors.primary)
            if (design != null && est != null) {
                val lost = design - est
                Spacer(Modifier.height(12.dp))
                Text(
                    if (lost > 0f) String.format(Locale.getDefault(), "%,.0f mAh lost to wear", lost)
                    else "No measurable wear",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        s?.estimatedFullMah?.let {
            InfoRow("Battery estimate", formatMah(it), "Charge remaining ÷ current level")
        }
    }
}

@Composable
private fun CapacityBar(label: String, value: Float?, max: Float?, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(formatMah(value), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (value != null && max != null && max > 0f) (value / max).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(14.dp),
            color = color,
            trackColor = color.copy(alpha = 0.14f),
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MiniCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            IconBadge(Icons.Rounded.Autorenew, MaterialTheme.colorScheme.primary, size = 36.dp)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendCard(sessions: List<ChargeSession>, design: Float?) {
    val colors = MaterialTheme.colorScheme
    val pts = sessions.map { it.end to if (design != null && design > 0f) it.capacityMah / design * 100f else it.capacityMah }
    val values = pts.map { it.second }
    val lo = (values.min() - 5f).let { kotlin.math.floor(it / 5f) * 5f }
    val hi = (values.max() + 5f).let { kotlin.math.ceil(it / 5f) * 5f }
    var selected by remember { mutableStateOf<Long?>(null) }
    val shownIdx = selected?.let { nearestIndex(pts, it) } ?: pts.lastIndex
    val shown = pts[shownIdx]
    val unit = if (design != null) "%" else " mAh"
    SectionCard(title = "Health over time") {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                String.format(Locale.getDefault(), "%.0f", shown.second) + unit + " · " + formatDate(shown.first),
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.height(140.dp)) {
                Column(Modifier.fillMaxHeight().width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(String.format(Locale.getDefault(), "%.0f", hi) + if (design != null) "%" else "", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(String.format(Locale.getDefault(), "%.0f", lo) + if (design != null) "%" else "", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                InteractiveChart(
                    points = pts,
                    startT = pts.first().first,
                    endT = pts.last().first,
                    minY = lo, maxY = hi,
                    color = colors.primary,
                    gapMs = Long.MAX_VALUE,
                    selectedT = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Tip(n: String, text: String) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(n, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(session: ChargeSession, design: Float?) {
    val colors = MaterialTheme.colorScheme
    val q = chargeQuality(session)
    val (qc, qon) = when (q) {
        Quality.GENTLE -> colors.primaryContainer to colors.onPrimaryContainer
        Quality.HOT, Quality.DEEP -> colors.errorContainer to colors.onErrorContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDateTime(session.start), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${session.startLevel}% → ${session.endLevel}% in ${formatDuration(session.end - session.start)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMah(session.capacityMah), style = MaterialTheme.typography.titleLarge)
                    if (design != null && design > 0f) {
                        Text(
                            String.format(Locale.getDefault(), "%.0f%% health", session.capacityMah / design * 100f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(q.label, null, qc, qon)
                Pill(String.format(Locale.getDefault(), "+%,.0f mAh", session.mahAdded), null, colors.surfaceContainerHigh, colors.onSurface)
                if (session.peakPowerW > 0f) {
                    Pill(String.format(Locale.getDefault(), "Peak %.1f W", session.peakPowerW), null, colors.surfaceContainerHigh, colors.onSurface)
                }
                if (session.maxTempC > 0f) {
                    Pill(String.format(Locale.getDefault(), "Max %.1f °C", session.maxTempC), null, colors.surfaceContainerHigh, colors.onSurface)
                }
            }
        }
    }
}
