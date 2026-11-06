package com.batterypulse.app.data

import android.os.BatteryManager

/** One reading of every battery value Android exposes to regular apps. */
data class BatterySnapshot(
    val timestamp: Long,
    val level: Int,
    val status: Int,
    val plugged: Int,
    val health: Int,
    val present: Boolean,
    val voltageMv: Int,
    val temperatureC: Float,
    val technology: String?,
    /** Positive = flowing into the battery, negative = out. Null if unsupported. */
    val currentMa: Float?,
    val avgCurrentMa: Float?,
    val chargeCounterMah: Float?,
    val energyMwh: Float?,
    val cycleCount: Int?,
    val chargeTimeRemainingMs: Long?,
    val screenOn: Boolean,
    /** Diagnostics: the untouched CURRENT_NOW value and the unit it was read as. */
    val rawCurrent: Int?,
    val currentUnitUsed: String,
) {
    val isCharging: Boolean get() = status == BatteryManager.BATTERY_STATUS_CHARGING
    val isFull: Boolean get() = status == BatteryManager.BATTERY_STATUS_FULL
    val isPlugged: Boolean get() = plugged != 0

    /** Watts, positive while charging. */
    val powerW: Float?
        get() = currentMa?.let { if (voltageMv > 0) it * voltageMv / 1_000_000f else null }

    /** Full capacity implied by the battery's charge counter at the current level. */
    val estimatedFullMah: Float?
        get() = chargeCounterMah?.takeIf { level >= 10 }?.let { it * 100f / level }
}

/** Compact sample persisted to history (one per minute). */
data class Sample(
    val t: Long,
    val level: Int,
    val currentMa: Float,
    val tempC: Float,
    val voltageMv: Int,
    val charging: Boolean,
    val screenOn: Boolean,
)

/** A completed charge from which a capacity measurement was derived. */
data class ChargeSession(
    val start: Long,
    val end: Long,
    val startLevel: Int,
    val endLevel: Int,
    val mahAdded: Float,
    val capacityMah: Float,
    val method: String,
    val maxTempC: Float,
    val peakPowerW: Float,
    val avgPowerW: Float,
)

/** The charge happening right now. */
data class LiveCharge(
    val start: Long,
    val startLevel: Int,
    val mahAdded: Float?,
    val peakPowerW: Float,
    val avgPowerW: Float?,
)

enum class Confidence(val label: String) {
    NONE("No data yet"),
    LOW("Low confidence"),
    MEDIUM("Medium confidence"),
    HIGH("High confidence"),
}

data class HealthEstimate(
    val designMah: Float?,
    val designSource: String,
    val estimatedMah: Float?,
    val estimateSource: String,
    val confidence: Confidence,
    val healthPercent: Float?,
    val cycleCount: Int?,
    val estimatedCycles: Float,
    val trackingSince: Long,
    val sessionsUsed: Int,
) {
    companion object {
        val EMPTY = HealthEstimate(
            null, "Unknown", null, "Not enough data yet", Confidence.NONE, null, null, 0f, 0L, 0
        )
    }
}

data class TimeEstimate(val label: String, val millis: Long?)

data class Appearance(val themeMode: String, val dynamicColor: Boolean, val pureBlack: Boolean) {
    fun isDark(systemDark: Boolean) = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
}
