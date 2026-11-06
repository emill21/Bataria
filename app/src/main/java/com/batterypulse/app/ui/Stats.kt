package com.batterypulse.app.ui

import com.batterypulse.app.data.ChargeSession
import com.batterypulse.app.data.Sample

data class UsageStats(
    val screenOnDrain: Float?,
    val screenOffDrain: Float?,
    val screenOnMs: Long,
    val drainPerHour: Float?,
    val chargePerHour: Float?,
    val maxTemp: Float?,
    val avgTemp: Float?,
)

private const val MAX_GAP = 35 * 60_000L

/** Discharge/charge rates split by screen state, from minute-by-minute history. */
fun computeUsage(pts: List<Sample>): UsageStats {
    var onDrop = 0f; var onMs = 0L
    var offDrop = 0f; var offMs = 0L
    var gained = 0f; var chargeMs = 0L
    for (k in 1 until pts.size) {
        val a = pts[k - 1]
        val b = pts[k]
        val dt = b.t - a.t
        if (dt <= 0 || dt > MAX_GAP) continue
        if (!a.charging && !b.charging) {
            val drop = (a.level - b.level).coerceAtLeast(0).toFloat()
            if (a.screenOn) { onDrop += drop; onMs += dt } else { offDrop += drop; offMs += dt }
        } else if (a.charging && b.charging) {
            gained += (b.level - a.level).coerceAtLeast(0); chargeMs += dt
        }
    }
    fun rate(drop: Float, ms: Long, minMs: Long) = if (ms >= minMs) drop / (ms / 3_600_000f) else null
    val temps = pts.map { it.tempC }.filter { !it.isNaN() }
    return UsageStats(
        screenOnDrain = rate(onDrop, onMs, 15 * 60_000L),
        screenOffDrain = rate(offDrop, offMs, 30 * 60_000L),
        screenOnMs = onMs,
        drainPerHour = rate(onDrop + offDrop, onMs + offMs, 30 * 60_000L),
        chargePerHour = rate(gained, chargeMs, 10 * 60_000L),
        maxTemp = temps.maxOrNull(),
        avgTemp = if (temps.isEmpty()) null else temps.average().toFloat(),
    )
}

fun chargeSpeedLabel(watts: Float): String = when {
    watts >= 25f -> "Super fast charging"
    watts >= 15f -> "Fast charging"
    watts >= 7f -> "Charging"
    else -> "Slow charging"
}

enum class Quality(val label: String) { GENTLE("Gentle"), FULL("Full charge"), HOT("Ran hot"), DEEP("Deep discharge"), NORMAL("Normal") }

/** How kind a charge was to the battery: shallow, cool charges between ~20–85% wear it least. */
fun chargeQuality(s: ChargeSession): Quality = when {
    s.maxTempC >= 40f -> Quality.HOT
    s.startLevel < 10 -> Quality.DEEP
    s.endLevel >= 98 -> Quality.FULL
    s.startLevel >= 20 && s.endLevel <= 85 -> Quality.GENTLE
    else -> Quality.NORMAL
}
