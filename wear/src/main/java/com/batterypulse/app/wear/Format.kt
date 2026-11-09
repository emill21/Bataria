package com.batterypulse.app.wear

import com.batterypulse.app.data.BatterySnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

object Fmt {
    fun duration(ms: Long): String {
        val mins = (ms / 60_000L).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when {
            h >= 48 -> "${h / 24}d ${h % 24}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun shortDuration(ms: Long): String {
        val mins = (ms / 60_000L).coerceAtLeast(0)
        return if (mins >= 60) "${mins / 60}h" else "${mins}m"
    }

    fun temp(c: Float, fahrenheit: Boolean): String = when {
        c.isNaN() || c == 0f -> "–"
        fahrenheit -> "${(c * 9 / 5 + 32).roundToInt()}°F"
        else -> "${"%.1f".format(c)}°C"
    }

    fun power(s: BatterySnapshot): String = s.powerW?.let { "%.2f W".format(abs(it)) } ?: "–"
    fun current(s: BatterySnapshot): String = s.currentMa?.let { "${abs(it).roundToInt()} mA" } ?: "–"
    fun voltage(s: BatterySnapshot): String = if (s.voltageMv > 0) "%.2f V".format(s.voltageMv / 1000f) else "–"

    fun status(s: BatterySnapshot): String = when {
        s.isFull -> "Full"
        s.isCharging -> when (s.plugged) {
            android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging wirelessly"
            else -> "Charging"
        }
        s.isPlugged -> "Plugged in"
        else -> "On battery"
    }
}
