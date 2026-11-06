package com.batterypulse.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** In-progress charge session, kept in memory and persisted periodically. */
class SessionState(
    var start: Long = 0L,
    var startLevel: Int = 0,
    var startCounter: Float = -1f,
    var integratedMah: Float = 0f,
    var maxTemp: Float = 0f,
    var peakW: Float = 0f,
    var energyWh: Float = 0f,
    var chargeMs: Long = 0L,
) {
    val active: Boolean get() = start > 0L
}

/** Typed wrapper over SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("battery_pulse", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            sp.edit { putBoolean(key, value) }
    }

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            sp.edit { putInt(key, value) }
    }

    private fun long(key: String, def: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getLong(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) =
            sp.edit { putLong(key, value) }
    }

    private fun float(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getFloat(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) =
            sp.edit { putFloat(key, value) }
    }

    private fun string(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            sp.edit { putString(key, value) }
    }

    // ---- Monitoring & calibration ----
    var monitoringEnabled by bool("monitoring", false)
    /** Live mode: foreground service + notification. Off: quiet 15-minute checks. */
    var liveNotification by bool("live_notification", false)
    /** "auto" | "ua" | "ma" */
    var currentUnit by string("current_unit", "auto")
    /** "auto" | "normal" | "inverted" */
    var currentSign by string("current_sign", "auto")
    var designCapacityOverride by int("design_override", 0)

    var maxAbsRawCurrent by int("cal_max_abs", 0)
    var rawCurrentSamples by int("cal_samples", 0)
    var seenNegativeCurrent by bool("cal_seen_neg", false)
    var signVotesNormal by int("cal_votes_normal", 0)
    var signVotesInverted by int("cal_votes_inverted", 0)

    fun resetCalibration() = sp.edit {
        remove("cal_max_abs"); remove("cal_samples"); remove("cal_seen_neg")
        remove("cal_votes_normal"); remove("cal_votes_inverted")
    }

    // ---- Appearance ----
    /** "system" | "light" | "dark" */
    var themeMode by string("theme_mode", "system")
    var dynamicColor by bool("dynamic_color", true)
    var pureBlack by bool("pure_black", false)

    // ---- Alerts ----
    var chargeAlarmOn by bool("alarm_charge_on", false)
    var chargeAlarmLevel by int("alarm_charge_level", 80)
    var chargeAlarmFired by bool("alarm_charge_fired", false)
    var lowAlarmOn by bool("alarm_low_on", false)
    var lowAlarmLevel by int("alarm_low_level", 20)
    var lowAlarmFired by bool("alarm_low_fired", false)
    var tempAlarmOn by bool("alarm_temp_on", false)
    var tempAlarmC by int("alarm_temp_c", 42)
    var tempAlarmFired by bool("alarm_temp_fired", false)

    // ---- Wear tracking ----
    var cumulativeChargePct by float("cum_charge_pct", 0f)
    var trackingSince by long("tracking_since", 0L)

    // ---- Charge session ----
    fun loadSession() = SessionState(
        start = sp.getLong("s_start", 0L),
        startLevel = sp.getInt("s_level", 0),
        startCounter = sp.getFloat("s_counter", -1f),
        integratedMah = sp.getFloat("s_integrated", 0f),
        maxTemp = sp.getFloat("s_max_temp", 0f),
        peakW = sp.getFloat("s_peak_w", 0f),
        energyWh = sp.getFloat("s_energy_wh", 0f),
        chargeMs = sp.getLong("s_charge_ms", 0L),
    )

    fun saveSession(s: SessionState) = sp.edit {
        putLong("s_start", s.start); putInt("s_level", s.startLevel)
        putFloat("s_counter", s.startCounter); putFloat("s_integrated", s.integratedMah)
        putFloat("s_max_temp", s.maxTemp); putFloat("s_peak_w", s.peakW)
        putFloat("s_energy_wh", s.energyWh); putLong("s_charge_ms", s.chargeMs)
    }
}
