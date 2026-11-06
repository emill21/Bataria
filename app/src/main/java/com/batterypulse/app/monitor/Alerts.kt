package com.batterypulse.app.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.batterypulse.app.MainActivity
import com.batterypulse.app.R
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.Prefs

/** Charge-limit, low-battery and temperature alerts. Each fires once until conditions reset. */
object Alerts {
    private const val CHANNEL_ID = "alerts"
    private const val ID_CHARGE = 1001
    private const val ID_LOW = 1002
    private const val ID_TEMP = 1003

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Battery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Charge limit, low battery and temperature alerts"
                }
            )
        }
    }

    fun check(context: Context, s: BatterySnapshot, prefs: Prefs) {
        // Charge limit
        if (prefs.chargeAlarmOn && s.isPlugged && s.level >= prefs.chargeAlarmLevel) {
            if (!prefs.chargeAlarmFired) {
                prefs.chargeAlarmFired = true
                notify(
                    context, ID_CHARGE, "Charged to ${s.level}%",
                    "Unplug now to reduce battery wear."
                )
            }
        } else if (!s.isPlugged && prefs.chargeAlarmFired) {
            prefs.chargeAlarmFired = false
            cancel(context, ID_CHARGE)
        }

        // Low battery
        if (prefs.lowAlarmOn && !s.isPlugged && s.level <= prefs.lowAlarmLevel) {
            if (!prefs.lowAlarmFired) {
                prefs.lowAlarmFired = true
                notify(context, ID_LOW, "Battery at ${s.level}%", "Time to plug in.")
            }
        } else if ((s.isPlugged || s.level > prefs.lowAlarmLevel + 2) && prefs.lowAlarmFired) {
            prefs.lowAlarmFired = false
            cancel(context, ID_LOW)
        }

        // Temperature
        val t = s.temperatureC
        if (!t.isNaN()) {
            if (prefs.tempAlarmOn && t >= prefs.tempAlarmC) {
                if (!prefs.tempAlarmFired) {
                    prefs.tempAlarmFired = true
                    notify(
                        context, ID_TEMP, String.format("Battery is hot: %.1f °C", t),
                        "Heat wears batteries fastest. Stop heavy use or unplug for a while."
                    )
                }
            } else if (t < prefs.tempAlarmC - 3 && prefs.tempAlarmFired) {
                prefs.tempAlarmFired = false
            }
        }
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    private fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
