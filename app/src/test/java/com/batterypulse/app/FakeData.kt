package com.batterypulse.app

import android.os.BatteryManager
import com.batterypulse.app.data.BatterySnapshot
import com.batterypulse.app.data.ChargeSession
import com.batterypulse.app.data.Confidence
import com.batterypulse.app.data.HealthEstimate
import com.batterypulse.app.data.LiveCharge
import com.batterypulse.app.data.Sample
import com.batterypulse.app.data.TimeEstimate
import kotlin.math.sin
import kotlin.random.Random

/** Realistic synthetic data so screenshots show every screen fully populated. */
object FakeData {
    val now = 1_762_400_000_000L // fixed so renders are stable

    fun snapshot(charging: Boolean, level: Int = if (charging) 64 else 72, t: Long = now, jitter: Float = 0f) = BatterySnapshot(
        timestamp = t,
        level = level,
        status = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged = if (charging) BatteryManager.BATTERY_PLUGGED_AC else 0,
        health = BatteryManager.BATTERY_HEALTH_GOOD,
        present = true,
        voltageMv = (if (charging) 4210 else 3962) + (jitter * 12).toInt(),
        temperatureC = (if (charging) 34.6f else 29.8f) + jitter * 0.3f,
        technology = "Li-ion",
        currentMa = if (charging) 3480f + jitter * 220f else -612f + jitter * 140f,
        avgCurrentMa = if (charging) 3390f else -540f,
        chargeCounterMah = 4380f * level / 100f,
        energyMwh = null,
        cycleCount = 214,
        chargeTimeRemainingMs = if (charging) 38 * 60_000L else null,
        screenOn = true,
        rawCurrent = ((if (charging) 3480f else -612f) * 1000).toInt(),
        currentUnitUsed = "µA",
    )

    fun recent(charging: Boolean): List<BatterySnapshot> = (0 until 90).map { i ->
        val j = (sin(i / 6.0) * 0.8 + sin(i / 2.3) * 0.4).toFloat()
        snapshot(charging, t = now - (89 - i) * 2_000L, jitter = j)
    }

    val time = TimeEstimate("left at this usage", (9.2 * 3_600_000).toLong())
    val chargeTime = TimeEstimate("until full", 38 * 60_000L)

    val live = LiveCharge(
        start = now - 41 * 60_000L,
        startLevel = 22,
        mahAdded = 1840f,
        peakPowerW = 24.6f,
        avgPowerW = 17.9f,
    )

    val health = HealthEstimate(
        designMah = 5000f,
        designSource = "Manufacturer power profile",
        estimatedMah = 4380f,
        estimateSource = "Measured across 5 charges",
        confidence = Confidence.HIGH,
        healthPercent = 87.6f,
        cycleCount = 214,
        estimatedCycles = 211.4f,
        trackingSince = now - 60L * 24 * 3_600_000,
        sessionsUsed = 5,
    )

    val sessions: List<ChargeSession> = run {
        val r = Random(7)
        (0 until 9).map { i ->
            val end = now - (8 - i) * 4L * 24 * 3_600_000 - 7 * 3_600_000
            val start0 = 15 + r.nextInt(20)
            val end0 = listOf(80, 85, 100, 90, 80)[i % 5]
            val cap = 4560f - i * 22f + r.nextFloat() * 50f
            ChargeSession(
                start = end - (70 + r.nextInt(60)) * 60_000L,
                end = end,
                startLevel = start0,
                endLevel = end0,
                mahAdded = cap * (end0 - start0) / 100f,
                capacityMah = cap,
                method = "Charge counter",
                maxTempC = if (i == 3) 41.2f else 33f + r.nextFloat() * 4f,
                peakPowerW = 18f + r.nextFloat() * 9f,
                avgPowerW = 12f + r.nextFloat() * 5f,
            )
        }
    }

    /** A week of minute samples: daytime use, overnight charge to 80–100%. */
    val history: List<Sample> = run {
        val out = ArrayList<Sample>()
        val r = Random(3)
        var level = 100f
        var charging = false
        var t = now - 7L * 24 * 3_600_000
        while (t <= now) {
            val hour = ((t / 3_600_000) % 24).toInt()
            val night = hour in 0..6
            val screenOn = !night && r.nextFloat() < 0.45f
            if (!charging && (level < 18 || (hour == 23 && level < 60))) charging = true
            if (charging && level >= if (hour < 12) 100f else 82f) charging = false
            val current: Float
            if (charging) {
                val rate = if (level < 80) 38f else 9f // %/h
                level += rate / 60f
                current = rate / 100f * 4380f
            } else {
                val rate = if (screenOn) 9f + r.nextFloat() * 6f else 0.8f
                level -= rate / 60f
                current = -rate / 100f * 4380f
            }
            level = level.coerceIn(1f, 100f)
            val temp = 28f + (if (charging) 6f else 0f) + (if (screenOn) 3f else 0f) + r.nextFloat()
            out.add(
                Sample(
                    t = t,
                    level = level.toInt(),
                    currentMa = current + (r.nextFloat() - 0.5f) * 120f,
                    tempC = temp,
                    voltageMv = (3500 + level * 7.2f + if (charging) 80 else 0).toInt(),
                    charging = charging,
                    screenOn = screenOn,
                )
            )
            t += 60_000L
        }
        out
    }
}
