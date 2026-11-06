package com.batterypulse.app.ui

import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun statusLabel(status: Int): String = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
    else -> "Unknown"
}

fun healthLabel(health: Int): String = when (health) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    else -> "Unknown"
}

fun pluggedLabel(plugged: Int): String = when (plugged) {
    0 -> "On battery"
    BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
    8 -> "Dock" // BATTERY_PLUGGED_DOCK (API 33)
    else -> "Plugged in"
}

fun formatCurrent(ma: Float?): String =
    ma?.let { String.format(Locale.getDefault(), "%+,.0f mA", it) } ?: "— mA"

fun formatPower(w: Float?): String =
    w?.let { String.format(Locale.getDefault(), "%+.2f W", it) } ?: "— W"

fun formatTemp(c: Float): String =
    if (c.isNaN()) "— °C" else String.format(Locale.getDefault(), "%.1f °C", c)

fun formatVoltage(mv: Int): String =
    if (mv <= 0) "— V" else String.format(Locale.getDefault(), "%.3f V", mv / 1000f)

fun formatMah(mah: Float?): String =
    mah?.let { String.format(Locale.getDefault(), "%,.0f mAh", it) } ?: "—"

fun formatDuration(ms: Long): String {
    val totalMin = abs(ms) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

fun formatDateTime(t: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(t))

fun formatClock(t: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

fun formatDayClock(t: Long): String =
    SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(t))

fun formatDate(t: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(t))

fun formatRate(pctPerHour: Float?): String =
    pctPerHour?.let { String.format(Locale.getDefault(), "%.1f%%/h", it) } ?: "—"
