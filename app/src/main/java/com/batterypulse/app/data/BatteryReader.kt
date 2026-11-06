package com.batterypulse.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reads raw battery values and normalises the OEM quirks:
 *  - CURRENT_NOW may be reported in µA (spec) or mA (some OEMs)
 *  - its sign may be inverted, or always positive
 *  - CHARGE_COUNTER may be µAh (spec) or mAh
 *  - voltage may be in V, mV or µV
 */
object BatteryReader {

    fun read(context: Context, prefs: Prefs): BatterySnapshot {
        val intent: Intent? = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val rawLevel = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = (if (rawLevel >= 0 && scale > 0) (rawLevel * 100f / scale).roundToInt()
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).coerceIn(0, 100)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
        val voltage = normaliseVoltage(intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
        val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = if (rawTemp == Int.MIN_VALUE) Float.NaN else rawTemp / 10f
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null

        val rawNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        calibrate(rawNow, plugged, prefs)
        val unit = resolveUnit(prefs)
        val currentNow = normaliseCurrent(rawNow, unit, status, plugged, prefs)
        val currentAvg = normaliseCurrent(rawAvg, unit, status, plugged, prefs)

        val rawCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah = if (isValid(rawCounter) && rawCounter > 0) {
            val impliedFull = rawCounter * 100f / max(level, 1)
            if (impliedFull > 200_000f) rawCounter / 1000f else rawCounter.toFloat()
        } else null

        val rawEnergy = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        val energyMwh = if (rawEnergy > 0 && rawEnergy != Long.MAX_VALUE) rawEnergy / 1_000_000f else null

        val remaining = if (Build.VERSION.SDK_INT >= 28) {
            bm.computeChargeTimeRemaining().takeIf { it > 0 }
        } else null

        return BatterySnapshot(
            timestamp = System.currentTimeMillis(),
            level = level,
            status = status,
            plugged = plugged,
            health = health,
            present = present,
            voltageMv = voltage,
            temperatureC = temp,
            technology = tech,
            currentMa = currentNow,
            avgCurrentMa = currentAvg,
            chargeCounterMah = counterMah,
            energyMwh = energyMwh,
            cycleCount = cycles,
            chargeTimeRemainingMs = remaining,
            rawCurrent = rawNow.takeIf { isValid(it) },
            currentUnitUsed = if (unit == CurrentUnit.UA) "µA" else "mA",
        )
    }

    private enum class CurrentUnit { UA, MA }

    private fun isValid(v: Int) = v != Int.MIN_VALUE && v != Int.MAX_VALUE

    private fun calibrate(raw: Int, plugged: Int, prefs: Prefs) {
        if (!isValid(raw) || raw == 0) return
        val a = abs(raw)
        if (a > prefs.maxAbsRawCurrent) prefs.maxAbsRawCurrent = a
        if (prefs.rawCurrentSamples < 100) prefs.rawCurrentSamples = prefs.rawCurrentSamples + 1
        if (raw < 0 && !prefs.seenNegativeCurrent) prefs.seenNegativeCurrent = true
        // Unplugged, the battery can only discharge, so the sign tells us the convention.
        if (plugged == 0 && prefs.signVotesNormal + prefs.signVotesInverted < 500) {
            if (raw < 0) prefs.signVotesNormal = prefs.signVotesNormal + 1
            else prefs.signVotesInverted = prefs.signVotesInverted + 1
        }
    }

    private fun resolveUnit(prefs: Prefs): CurrentUnit = when (prefs.currentUnit) {
        "ua" -> CurrentUnit.UA
        "ma" -> CurrentUnit.MA
        else -> {
            // No phone draws 20 A, so any reading that large must be µA. If many readings
            // never come close, the device is reporting mA.
            if (prefs.maxAbsRawCurrent >= 20_000 || prefs.rawCurrentSamples < 15) CurrentUnit.UA else CurrentUnit.MA
        }
    }

    private fun normaliseCurrent(raw: Int, unit: CurrentUnit, status: Int, plugged: Int, prefs: Prefs): Float? {
        if (!isValid(raw) || raw == 0) return null
        val ma = if (unit == CurrentUnit.UA) raw / 1000f else raw.toFloat()
        return when (prefs.currentSign) {
            "normal" -> ma
            "inverted" -> -ma
            else -> when {
                plugged == 0 -> -abs(ma)
                !prefs.seenNegativeCurrent ->
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING) abs(ma) else -abs(ma)
                prefs.signVotesInverted > prefs.signVotesNormal -> -ma
                else -> ma
            }
        }
    }

    private fun normaliseVoltage(v: Int): Int = when {
        v <= 0 -> 0
        v < 100 -> v * 1000          // volts
        v > 100_000 -> v / 1000      // microvolts
        else -> v                    // millivolts (spec)
    }

    // ---- Capacity sources ----

    /** Kernel fuel-gauge values; readable on some devices, blocked by SELinux on most. */
    fun readSysfsMah(name: String): Float? {
        val paths = listOf(
            "/sys/class/power_supply/battery/$name",
            "/sys/class/power_supply/bms/$name",
        )
        for (p in paths) {
            try {
                val v = File(p).readText().trim().toLongOrNull() ?: continue
                if (v <= 0) continue
                return if (v > 100_000) v / 1000f else v.toFloat()
            } catch (e: Throwable) {
            }
        }
        return null
    }

    /** Design capacity from the OEM's power_profile.xml via the framework PowerProfile class. */
    fun powerProfileCapacity(context: Context): Float? = try {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val instance = cls.getConstructor(Context::class.java).newInstance(context)
        val cap = cls.getMethod("getBatteryCapacity").invoke(instance) as Double
        // Many devices ship a placeholder of 1000 mAh; treat that as unknown.
        cap.toFloat().takeIf { it > 1000f }
    } catch (e: Throwable) {
        null
    }
}
