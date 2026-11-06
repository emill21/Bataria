package com.batterypulse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
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
        item {
            val pct = health.healthPercent
            val (verdict, container, onContainer) = when {
                pct == null -> Triple("Measuring", colors.surfaceContainerHigh, colors.onSurface)
                pct >= 90f -> Triple("Excellent", colors.primaryContainer, colors.onPrimaryContainer)
                pct >= 80f -> Triple("Good", colors.primaryContainer, colors.onPrimaryContainer)
                pct >= 70f -> Triple("Fair", colors.tertiaryContainer, colors.onTertiaryContainer)
                else -> Triple("Worn — consider replacing", colors.errorContainer, colors.onErrorContainer)
            }
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("Estimated health", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            pct?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                            fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold,
                        )
                        if (pct != null) {
                            Text("%", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                    Text(verdict, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { ((pct ?: 0f) / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = onContainer,
                        trackColor = onContainer.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${health.confidence.label} · ${health.estimateSource}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SectionCard(title = "Capacity") {
                InfoRow("Design capacity", formatMah(health.designMah), health.designSource)
                InfoRow("Estimated full capacity", formatMah(health.estimatedMah), health.estimateSource)
                s?.estimatedFullMah?.let {
                    InfoRow("Fuel gauge estimate", formatMah(it), "Charge counter ÷ current level")
                }
                InfoRow(
                    "Charge cycles",
                    health.cycleCount?.toString() ?: "—",
                    if (health.cycleCount == null) "Reported on Android 14+ by supported devices" else "Reported by Android",
                )
                s?.let { InfoRow("Android health status", healthLabel(it.health)) }
            }
        }

        if (health.confidence != Confidence.HIGH) {
            item {
                SectionCard(title = "Improve accuracy") {
                    Text(
                        "Health is measured from real charges: Battery Pulse tracks how much charge flows in " +
                            "while the level rises, and works out the full capacity. Charges of 15% or more " +
                            "count — a 20%→80% charge is ideal. Three charges gives a high-confidence result.\n\n" +
                            "Turn on background monitoring in Settings so charges are recorded while the app is closed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item {
            Text(
                "Charge sessions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        if (sessions.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "No measured charges yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            item {
                SectionCard {
                    val list = sessions.asReversed().take(20)
                    list.forEachIndexed { i, session ->
                        SessionRow(session)
                        if (i < list.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ChargeSession) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(session.start), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${session.startLevel}% → ${session.endLevel}% · ${formatDuration(session.end - session.start)} · " +
                    String.format(Locale.getDefault(), "+%,.0f mAh", session.mahAdded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                session.method + if (session.maxTempC > 0f) String.format(Locale.getDefault(), " · peak %.1f °C", session.maxTempC) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatMah(session.capacityMah), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}
