package com.batterypulse.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.batterypulse.app.MainActivity
import com.batterypulse.app.R
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.ui.formatCurrent
import com.batterypulse.app.ui.formatTemp
import com.batterypulse.app.ui.statusLabel
import com.batterypulse.app.widget.BatteryWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps sampling while the app is closed so charge sessions (and therefore capacity /
 * health) are measured accurately, and shows live stats in a silent notification.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { tick() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BatteryMonitor.init(this)
        ensureChannel()
        val first = BatteryMonitor.sample()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(first),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                tick()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }

    private var widgetLevel = -1
    private var widgetCharging = false
    private var widgetAt = 0L

    private fun tick() {
        val s = BatteryMonitor.sample()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(s))
        val now = System.currentTimeMillis()
        if (s.level != widgetLevel || s.isCharging != widgetCharging || now - widgetAt > WIDGET_INTERVAL_MS) {
            widgetLevel = s.level
            widgetCharging = s.isCharging
            widgetAt = now
            scope.launch { BatteryWidget.refresh(this@MonitorService) }
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Battery monitor", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live battery stats while background monitoring is on"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(s: BatterySnapshot): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val parts = buildList {
            add(formatCurrent(s.currentMa))
            add(formatTemp(s.temperatureC))
            if (s.voltageMv > 0) add("%.2f V".format(s.voltageMv / 1000f))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("${s.level}% · ${statusLabel(s.status)}")
            .setContentText(parts.joinToString(" · "))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "monitor"
        private const val NOTIFICATION_ID = 42
        private const val SAMPLE_INTERVAL_MS = 10_000L
        private const val WIDGET_INTERVAL_MS = 5 * 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
