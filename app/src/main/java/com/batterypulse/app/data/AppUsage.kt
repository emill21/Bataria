package com.batterypulse.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppDrain(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val foregroundMs: Long,
    val mah: Float,
    val share: Float,
)

data class DrainReport(
    val apps: List<AppDrain>,
    /** Drain while the screen was off (idle, background work). */
    val screenOffMah: Float,
    val screenOffMs: Long,
    /** Screen on but no app in the foreground we could identify (system UI, lock screen…). */
    val otherMah: Float,
    val totalMah: Float,
)

/**
 * Estimates battery used by each app: every discharge interval in the history is split between
 * the apps that were on screen during it (from Android's usage events), by time overlap.
 * Drain with the screen off is reported separately.
 */
object AppUsage {
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val MAX_GAP = 35 * 60_000L

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(context: Context) {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(withPackage)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
            }
        }
    }

    private data class Interval(val pkg: String, val start: Long, val end: Long)

    /** Merged on-screen intervals per app between [start] and [end]. */
    private fun foreground(context: Context, start: Long, end: Long): List<Interval> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = usm.queryEvents(start, end) ?: return emptyList()
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>() // key: pkg/class
        val raw = HashMap<String, MutableList<LongArray>>()
        fun close(key: String, at: Long) {
            val s = open.remove(key) ?: return
            if (at > s) raw.getOrPut(key.substringBefore('/')) { mutableListOf() }.add(longArrayOf(s, at))
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val key = "${e.packageName}/${e.className}"
            when (e.eventType) {
                ACTIVITY_RESUMED -> if (!open.containsKey(key)) open[key] = e.timeStamp
                ACTIVITY_PAUSED -> close(key, e.timeStamp)
                SCREEN_NON_INTERACTIVE -> open.keys.toList().forEach { close(it, e.timeStamp) }
            }
        }
        open.keys.toList().forEach { close(it, end) }

        val out = ArrayList<Interval>()
        for ((pkg, list) in raw) {
            list.sortBy { it[0] }
            var cs = list[0][0]
            var ce = list[0][1]
            for (k in 1 until list.size) {
                val (s, en) = list[k]
                if (s <= ce) ce = maxOf(ce, en) else {
                    out.add(Interval(pkg, cs, ce)); cs = s; ce = en
                }
            }
            out.add(Interval(pkg, cs, ce))
        }
        return out.sortedBy { it.start }
    }

    fun report(context: Context, history: List<Sample>, start: Long, end: Long, capacityMah: Float): DrainReport {
        val intervals = foreground(context, start, end)
        val acc = HashMap<String, Double>()
        val fg = HashMap<String, Long>()
        var off = 0.0
        var offMs = 0L
        var other = 0.0
        val pts = history.filter { it.t in start..end }
        for (k in 1 until pts.size) {
            val a = pts[k - 1]
            val b = pts[k]
            val dt = b.t - a.t
            if (dt <= 0 || dt > MAX_GAP || a.charging || b.charging) continue
            val avgCurrent = (a.currentMa + b.currentMa) / 2f
            val mah = if (dt <= 3 * 60_000L && avgCurrent < 0f) {
                -avgCurrent * dt / 3_600_000.0
            } else {
                (a.level - b.level).coerceAtLeast(0) / 100.0 * capacityMah
            }
            if (mah <= 0.0) {
                if (!a.screenOn) offMs += dt
                continue
            }
            var covered = 0L
            val shares = ArrayList<Pair<String, Long>>()
            for (iv in intervals) {
                if (iv.start >= b.t) break
                if (iv.end <= a.t) continue
                val ov = minOf(iv.end, b.t) - maxOf(iv.start, a.t)
                if (ov > 0) {
                    shares.add(iv.pkg to ov); covered += ov
                }
            }
            val denom = maxOf(dt, covered).toDouble()
            for ((pkg, ov) in shares) {
                acc[pkg] = (acc[pkg] ?: 0.0) + mah * ov / denom
                fg[pkg] = (fg[pkg] ?: 0L) + ov
            }
            val rest = mah * (1.0 - covered / denom).coerceAtLeast(0.0)
            if (a.screenOn && covered > 0) other += rest
            else if (a.screenOn) other += rest
            else {
                off += rest; offMs += dt
            }
        }
        val total = acc.values.sum() + off + other
        val pm = context.packageManager
        val size = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val apps = acc.entries
            .filter { it.value >= 0.5 }
            .sortedByDescending { it.value }
            .take(40)
            .map { (pkg, mah) ->
                val (label, icon) = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val l = pm.getApplicationLabel(info).toString()
                    val ic = try {
                        pm.getApplicationIcon(info).toBitmap(size, size).asImageBitmap()
                    } catch (e: Throwable) {
                        null
                    }
                    l to ic
                } catch (e: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } to null
                }
                AppDrain(pkg, label, icon, fg[pkg] ?: 0L, mah.toFloat(), if (total > 0) (mah / total).toFloat() else 0f)
            }
        return DrainReport(apps, off.toFloat(), offMs, other.toFloat(), total.toFloat())
    }
}
