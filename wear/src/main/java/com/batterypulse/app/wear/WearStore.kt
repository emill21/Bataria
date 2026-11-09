package com.batterypulse.app.wear

import android.content.Context
import android.os.BatteryManager
import com.batterypulse.app.data.BatteryReader
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.Prefs

/** One history point: kept small, since a watch has little room. */
data class Point(val t: Long, val level: Int, val charging: Boolean, val tempC: Float)

/** Watch-only storage: level history, the measured full capacity and a few settings. */
class WearStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("bataria_wear", Context.MODE_PRIVATE)

    var fahrenheit: Boolean
        get() = sp.getBoolean("fahrenheit", false)
        set(v) = sp.edit().putBoolean("fahrenheit", v).apply()

    /** Full capacity worked out from the charge counter, smoothed over many readings. */
    val fullEstimateMah: Float get() = sp.getFloat("full_est", 0f)
    val fullSamples: Int get() = sp.getInt("full_n", 0)

    fun history(): List<Point> {
        val raw = sp.getString("history", "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(';').mapNotNull { p ->
            val f = p.split(',')
            if (f.size < 4) null
            else runCatching { Point(f[0].toLong(), f[1].toInt(), f[2] == "1", f[3].toInt() / 10f) }.getOrNull()
        }
    }

    /** Saves a point every few minutes (or whenever the level or charging changes). */
    fun record(s: BatterySnapshot) {
        val list = history().toMutableList()
        val last = list.lastOrNull()
        val charging = s.isCharging || s.isFull || s.isPlugged
        if (last != null && s.timestamp - last.t < MIN_GAP && last.level == s.level && last.charging == charging) return
        list += Point(s.timestamp, s.level, charging, if (s.temperatureC.isNaN()) 0f else s.temperatureC)
        val cutoff = s.timestamp - KEEP_MS
        val kept = list.filter { it.t >= cutoff }
        sp.edit().putString(
            "history",
            kept.joinToString(";") { "${it.t},${it.level},${if (it.charging) 1 else 0},${(it.tempC * 10).toInt()}" },
        ).apply()
        learnCapacity(s)
    }

    private fun learnCapacity(s: BatterySnapshot) {
        // The charge counter is only trustworthy away from the ends of the scale.
        val est = s.estimatedFullMah ?: return
        if (s.level !in 30..95 || est !in 80f..3000f) return
        val n = fullSamples
        val prev = fullEstimateMah
        val next = if (n == 0 || prev <= 0f) est else prev + (est - prev) * 0.08f
        sp.edit().putFloat("full_est", next).putInt("full_n", n + 1).apply()
    }

    fun clearHistory() = sp.edit().remove("history").remove("full_est").remove("full_n").apply()

    companion object {
        private const val MIN_GAP = 4 * 60_000L
        private const val KEEP_MS = 7 * 24 * 60 * 60_000L
    }
}

/** Battery health on a watch: design size vs what the battery holds now. */
data class WatchHealth(
    val designMah: Float?,
    val designSource: String,
    val fullMah: Float?,
    val fullSource: String,
    val percent: Float?,
    val cycles: Int?,
    val condition: String,
)

object Health {
    fun compute(context: Context, prefs: Prefs, store: WearStore, s: BatterySnapshot): WatchHealth {
        val (design, designSrc) = when {
            prefs.designCapacityOverride > 0 -> prefs.designCapacityOverride.toFloat() to "Set by you"
            else -> BatteryReader.readSysfsMah("charge_full_design")?.let { it to "Reported by the watch" }
                ?: powerProfile(context)?.let { it to "Manufacturer profile" }
                ?: (null to "Unknown: set it in Settings")
        }
        val sysFull = BatteryReader.readSysfsMah("charge_full")
        val (full, fullSrc) = when {
            sysFull != null -> sysFull to "Measured by the watch"
            store.fullSamples >= 3 && store.fullEstimateMah > 0f -> store.fullEstimateMah to "Learned from ${store.fullSamples} readings"
            else -> null to "Still learning: wear it through a few charges"
        }
        val pct = if (design != null && full != null && design > 0f) (full / design * 100f).coerceIn(1f, 120f) else null
        return WatchHealth(design, designSrc, full, fullSrc, pct, s.cycleCount, condition(s.health))
    }

    /** Watches have small batteries, so allow anything over 100 mAh (the phone app's 1000 mAh floor would hide them). */
    private fun powerProfile(context: Context): Float? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val instance = cls.getConstructor(Context::class.java).newInstance(context)
        val cap = (cls.getMethod("getBatteryCapacity").invoke(instance) as Double).toFloat()
        cap.takeIf { it > 100f && it != 1000f }
    } catch (e: Throwable) {
        null
    }

    private fun condition(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }
}

/** How long until empty (or full), from how fast the level has been moving lately. */
object TimeLeft {
    fun estimate(s: BatterySnapshot, history: List<Point>): Pair<String, Long?> {
        val charging = s.isCharging || s.isPlugged
        if (s.isFull) return "Full" to null
        if (charging) {
            s.chargeTimeRemainingMs?.let { return "until full" to it }
        }
        // Use the current run (since the last plug / unplug), up to the last 6 hours.
        val now = s.timestamp
        val run = history.asReversed().takeWhile { it.charging == charging && now - it.t <= 6 * 3_600_000L }
        if (run.size < 2) return (if (charging) "until full" else "left") to null
        val newest = run.first()
        val oldest = run.last()
        val dt = (newest.t - oldest.t).coerceAtLeast(1)
        val dl = if (charging) newest.level - oldest.level else oldest.level - newest.level
        if (dl < 2 || dt < 20 * 60_000L) return (if (charging) "until full" else "left") to null
        val perMs = dl.toFloat() / dt
        val ms = if (charging) ((100 - s.level) / perMs).toLong() else (s.level / perMs).toLong()
        return (if (charging) "until full" else "left") to ms
    }

    /** Average drain in % per hour over the unplugged parts of [history] within [windowMs]. */
    fun drainPerHour(history: List<Point>, windowMs: Long, now: Long): Float? {
        val pts = history.filter { now - it.t <= windowMs }
        var used = 0
        var ms = 0L
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            if (!a.charging && !b.charging && b.level <= a.level && b.t - a.t < 3 * 3_600_000L) {
                used += a.level - b.level
                ms += b.t - a.t
            }
        }
        if (ms < 30 * 60_000L) return null
        return used / (ms / 3_600_000f)
    }
}
