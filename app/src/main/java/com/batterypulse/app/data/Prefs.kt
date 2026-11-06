package com.batterypulse.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small typed wrapper over SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("battery_pulse", Context.MODE_PRIVATE)

    // ---- User settings ----
    var monitoringEnabled: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) = sp.edit { putBoolean("monitoring", v) }

    /** "auto" | "ua" | "ma" — unit reported by BATTERY_PROPERTY_CURRENT_NOW. */
    var currentUnit: String
        get() = sp.getString("current_unit", "auto") ?: "auto"
        set(v) = sp.edit { putString("current_unit", v) }

    /** "auto" | "normal" | "inverted" — sign convention of the current reading. */
    var currentSign: String
        get() = sp.getString("current_sign", "auto") ?: "auto"
        set(v) = sp.edit { putString("current_sign", v) }

    /** Design capacity entered by the user in mAh; 0 = not set. */
    var designCapacityOverride: Int
        get() = sp.getInt("design_override", 0)
        set(v) = sp.edit { putInt("design_override", v) }

    // ---- Auto-calibration evidence ----
    var maxAbsRawCurrent: Int
        get() = sp.getInt("cal_max_abs", 0)
        set(v) = sp.edit { putInt("cal_max_abs", v) }
    var rawCurrentSamples: Int
        get() = sp.getInt("cal_samples", 0)
        set(v) = sp.edit { putInt("cal_samples", v) }
    var seenNegativeCurrent: Boolean
        get() = sp.getBoolean("cal_seen_neg", false)
        set(v) = sp.edit { putBoolean("cal_seen_neg", v) }
    var signVotesNormal: Int
        get() = sp.getInt("cal_votes_normal", 0)
        set(v) = sp.edit { putInt("cal_votes_normal", v) }
    var signVotesInverted: Int
        get() = sp.getInt("cal_votes_inverted", 0)
        set(v) = sp.edit { putInt("cal_votes_inverted", v) }

    fun resetCalibration() = sp.edit {
        remove("cal_max_abs"); remove("cal_samples"); remove("cal_seen_neg")
        remove("cal_votes_normal"); remove("cal_votes_inverted")
    }

    // ---- In-progress charge session (survives process death) ----
    val sessionActive: Boolean get() = sessionStart > 0
    var sessionStart: Long
        get() = sp.getLong("s_start", 0L)
        private set(v) = sp.edit { putLong("s_start", v) }
    var sessionStartLevel: Int
        get() = sp.getInt("s_level", 0)
        private set(v) = sp.edit { putInt("s_level", v) }
    var sessionStartCounter: Float
        get() = sp.getFloat("s_counter", -1f)
        private set(v) = sp.edit { putFloat("s_counter", v) }
    var sessionIntegratedMah: Float
        get() = sp.getFloat("s_integrated", 0f)
        set(v) = sp.edit { putFloat("s_integrated", v) }
    var sessionMaxTemp: Float
        get() = sp.getFloat("s_max_temp", 0f)
        set(v) = sp.edit { putFloat("s_max_temp", v) }

    fun startSession(time: Long, level: Int, counterMah: Float?, tempC: Float) {
        sessionStart = time
        sessionStartLevel = level
        sessionStartCounter = counterMah ?: -1f
        sessionIntegratedMah = 0f
        sessionMaxTemp = if (tempC.isNaN()) 0f else tempC
    }

    fun clearSession() = sp.edit {
        remove("s_start"); remove("s_level"); remove("s_counter")
        remove("s_integrated"); remove("s_max_temp")
    }
}
