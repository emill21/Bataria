package com.batterypulse.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.batterypulse.app.ui.formatDuration
import com.batterypulse.app.ui.formatTemp
import com.batterypulse.app.ui.statusLabel

/** Compact ring widget: level in a circular gauge. */
class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        BatteryMonitor.init(context)
        val s = BatteryMonitor.sample()
        val wc = widgetColors(context)
        val color = when {
            s.isCharging -> wc.charging
            s.level <= 15 -> wc.low
            else -> wc.accent
        }
        val ring = ringBitmap(s.level, color, wc.track)
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(GlanceTheme.colors.widgetBackground)
                        .padding(8.dp).clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(ImageProvider(ring), contentDescription = "Battery ${s.level}%", modifier = GlanceModifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${s.level}%",
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                        )
                        Text(
                            if (s.isCharging) "⚡ charging" else formatTemp(s.temperatureC),
                            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

class RingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RingWidget()
}

/** Wide widget: level, time estimate and a 24-hour chart. */
class ChartWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        BatteryMonitor.init(context)
        val s = BatteryMonitor.sample()
        val time = BatteryMonitor.timeEstimate.value
        val wc = widgetColors(context)
        val chart = chartBitmap(BatteryMonitor.history.value + listOf(), s.timestamp, if (s.isCharging) wc.charging else wc.accent)
        provideContent {
            GlanceTheme { ChartContent(s.level, statusLabel(s.status), time?.let { t -> t.millis?.let { "${formatDuration(it)} ${t.label}" } ?: t.label }, formatTemp(s.temperatureC), chart) }
        }
    }
}

@Composable
private fun ChartContent(level: Int, status: String, time: String?, temp: String, chart: android.graphics.Bitmap) {
    val colors = GlanceTheme.colors
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(colors.widgetBackground)
            .padding(16.dp).clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("$level%", style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.onSurface))
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight().padding(bottom = 4.dp)) {
                Text(status, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.primary))
                Text(time ?: "Last 24 hours", style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant), maxLines = 1)
            }
            Text(temp, style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceVariant), modifier = GlanceModifier.padding(bottom = 4.dp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Image(ImageProvider(chart), contentDescription = "Battery level, last 24 hours", modifier = GlanceModifier.fillMaxWidth().defaultWeight())
    }
}

class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChartWidget()
}
