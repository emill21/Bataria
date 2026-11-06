package com.batterypulse.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.batterypulse.app.MainActivity
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.HealthEstimate
import com.batterypulse.app.ui.formatTemp
import com.batterypulse.app.ui.statusLabel
import java.util.Locale

class BatteryWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, WIDE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        BatteryMonitor.init(context)
        val s = BatteryMonitor.sample()
        val h = BatteryMonitor.health.value
        provideContent {
            GlanceTheme {
                WidgetContent(s, h)
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)

        suspend fun refresh(context: Context) {
            try {
                BatteryWidget().updateAll(context)
            } catch (e: Exception) {
            }
        }
    }
}

@Composable
private fun WidgetContent(s: BatterySnapshot, h: HealthEstimate) {
    val wide = LocalSize.current.width >= 200.dp
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(colors.widgetBackground)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${s.level}",
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = colors.onSurface),
            )
            Text(
                "%",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant),
                modifier = GlanceModifier.padding(bottom = 6.dp, start = 2.dp),
            )
            if (s.isCharging) {
                Text(
                    " ⚡",
                    style = TextStyle(fontSize = 20.sp, color = colors.primary),
                    modifier = GlanceModifier.padding(bottom = 5.dp),
                )
            }
        }
        Text(
            statusLabel(s.status),
            style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceVariant),
        )
        Spacer(GlanceModifier.height(10.dp))
        LinearProgressIndicator(
            progress = s.level / 100f,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
            color = colors.primary,
            backgroundColor = colors.secondaryContainer,
        )
        if (wide) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Stat("Current", s.currentMa?.let { String.format(Locale.getDefault(), "%+,.0f mA", it) } ?: "—")
                Spacer(GlanceModifier.width(16.dp))
                Stat("Temp", formatTemp(s.temperatureC))
                Spacer(GlanceModifier.width(16.dp))
                Stat("Health", h.healthPercent?.let { String.format(Locale.getDefault(), "%.0f%%", it) } ?: "—")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val colors = GlanceTheme.colors
    Column {
        Text(label, style = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant))
        Text(value, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface))
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}
