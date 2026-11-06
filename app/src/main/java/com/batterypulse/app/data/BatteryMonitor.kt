package com.batterypulse.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Process-wide battery state. Whoever calls [sample] (the UI every 2 s, the background
 * service every 10 s and on every battery broadcast) feeds the same pipeline:
 * smoothing, history recording, charge-session tracking and health estimation.
 */
object BatteryMonitor {
    private const val HISTORY_INTERVAL_MS = 60_000L
    private const val HISTORY_KEEP_MS = 3L * 24 * 3_600_000
    private const val MIN_SESSION_DELTA = 15        // % gained before a charge counts
    private const val MAX_INTEGRATION_GAP_MS = 5 * 60_000L
    private const val SYSFS_REFRESH_MS = 10 * 60_000L

    lateinit var prefs: Prefs
        private set
    private lateinit var app: Context

    private val _snapshot = MutableStateFlow<BatterySnapshot?>(null)
    val snapshot: StateFlow<BatterySnapshot?> = _snapshot.asStateFlow()

    private val _history = MutableStateFlow<List<Sample>>(emptyList())
    val history: StateFlow<List<Sample>> = _history.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChargeSession>>(emptyList())
    val sessions: StateFlow<List<ChargeSession>> = _sessions.asStateFlow()

    private val _health = MutableStateFlow(HealthEstimate.EMPTY)
    val health: StateFlow<HealthEstimate> = _health.asStateFlow()

    private val _time = MutableStateFlow<TimeEstimate?>(null)
    val timeEstimate: StateFlow<TimeEstimate?> = _time.asStateFlow()

    private var lastSampleAt = 0L
    private var lastHistoryAt = 0L
    private var emaDrainMa: Float? = null
    private var emaChargeMa: Float? = null

    private var designCache: Pair<Float?, String>? = null
    private var sysFullMah: Float? = null
    private var sysReadAt = 0L

    private val historyFile get() = File(app.filesDir, "history.csv")
    private val sessionsFile get() = File(app.filesDir, "sessions.json")

    @Synchronized
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = Prefs(app)
        _history.value = loadHistory()
        lastHistoryAt = _history.value.lastOrNull()?.t ?: 0L
        _sessions.value = loadSessions()
        recomputeHealth(null)
    }

    @Synchronized
    fun sample(): BatterySnapshot {
        val s = BatteryReader.read(app, prefs)
        val now = s.timestamp
        val gap = if (lastSampleAt > 0) now - lastSampleAt else 0L
        val dtHours = if (gap in 1..MAX_INTEGRATION_GAP_MS) gap / 3_600_000f else 0f
        lastSampleAt = now

        updateSmoothing(s)
        trackSession(s, dtHours)
        if (now - lastHistoryAt >= HISTORY_INTERVAL_MS) appendHistory(s)
        recomputeHealth(s)
        _time.value = computeTime(s, _health.value)
        _snapshot.value = s
        return s
    }

    // ---------- Smoothing ----------

    private fun updateSmoothing(s: BatterySnapshot) {
        val i = s.currentMa ?: return
        if (s.isCharging && i > 0) {
            emaChargeMa = emaChargeMa?.let { it + 0.15f * (i - it) } ?: i
            emaDrainMa = null
        } else if (!s.isPlugged && i < 0) {
            val d = -i
            emaDrainMa = emaDrainMa?.let { it + 0.1f * (d - it) } ?: d
            emaChargeMa = null
        }
    }

    // ---------- Charge sessions (capacity measurement) ----------

    private fun trackSession(s: BatterySnapshot, dtHours: Float) {
        val active = prefs.sessionActive
        if (s.isCharging && !active) {
            prefs.startSession(s.timestamp, s.level, s.chargeCounterMah, s.temperatureC)
            return
        }
        if (!active) return
        if (s.isCharging) {
            val i = s.currentMa
            if (i != null && i > 0 && dtHours > 0f) {
                prefs.sessionIntegratedMah = prefs.sessionIntegratedMah + i * dtHours
            }
            if (!s.temperatureC.isNaN() && s.temperatureC > prefs.sessionMaxTemp) {
                prefs.sessionMaxTemp = s.temperatureC
            }
            return
        }
        finishSession(s)
    }

    private fun finishSession(s: BatterySnapshot) {
        val start = prefs.sessionStart
        val startLevel = prefs.sessionStartLevel
        val startCounter = prefs.sessionStartCounter
        val integrated = prefs.sessionIntegratedMah
        val maxTemp = prefs.sessionMaxTemp
        prefs.clearSession()

        val delta = s.level - startLevel
        if (delta < MIN_SESSION_DELTA) return

        val endCounter = s.chargeCounterMah
        val counterDelta =
            if (startCounter > 0f && endCounter != null) endCounter - startCounter else null
        val (mah, method) = if (counterDelta != null && counterDelta > 0f) {
            counterDelta to "Charge counter"
        } else {
            integrated to "Current integration"
        }
        if (mah <= 0f) return

        val capacity = mah / (delta / 100f)
        val design = design().first
        val plausible = if (design != null) capacity in (design * 0.4f)..(design * 1.3f)
        else capacity in 500f..25_000f
        if (!plausible) return

        val session = ChargeSession(
            start = start, end = s.timestamp, startLevel = startLevel, endLevel = s.level,
            mahAdded = mah, capacityMah = capacity, method = method, maxTempC = maxTemp,
        )
        _sessions.value = (_sessions.value + session).takeLast(100)
        saveSessions()
    }

    // ---------- Health ----------

    fun design(): Pair<Float?, String> {
        designCache?.let { return it }
        val override = prefs.designCapacityOverride
        val result: Pair<Float?, String> = when {
            override > 0 -> override.toFloat() to "Set by you"
            else -> BatteryReader.readSysfsMah("charge_full_design")?.let { it to "Battery fuel gauge" }
                ?: BatteryReader.powerProfileCapacity(app)?.let { it to "Manufacturer power profile" }
                ?: (null to "Unknown — set it in Settings")
        }
        designCache = result
        return result
    }

    fun invalidateDesign() {
        synchronized(this) {
            designCache = null
            recomputeHealth(_snapshot.value)
        }
    }

    private fun recomputeHealth(s: BatterySnapshot?) {
        val (designMah, designSource) = design()
        val now = System.currentTimeMillis()
        if (now - sysReadAt > SYSFS_REFRESH_MS) {
            sysFullMah = BatteryReader.readSysfsMah("charge_full")
            sysReadAt = now
        }
        val recent = _sessions.value.takeLast(5).map { it.capacityMah }.sorted()
        val median = when {
            recent.isEmpty() -> null
            recent.size % 2 == 1 -> recent[recent.size / 2]
            else -> (recent[recent.size / 2 - 1] + recent[recent.size / 2]) / 2f
        }
        val instant = s?.estimatedFullMah

        val sys = sysFullMah
        val est: Float?
        val source: String
        val confidence: Confidence
        when {
            sys != null -> {
                est = sys; source = "Reported by the battery fuel gauge"; confidence = Confidence.HIGH
            }
            median != null -> {
                est = median
                source = "Measured across ${recent.size} charge" + if (recent.size == 1) "" else "s"
                confidence = if (recent.size >= 3) Confidence.HIGH else Confidence.MEDIUM
            }
            instant != null -> {
                est = instant; source = "Quick estimate from the charge counter"; confidence = Confidence.LOW
            }
            else -> {
                est = null; source = "Not enough data yet"; confidence = Confidence.NONE
            }
        }
        val pct = if (est != null && designMah != null && designMah > 0f) {
            (est / designMah * 100f).coerceIn(0f, 120f)
        } else null

        _health.value = HealthEstimate(
            designMah = designMah,
            designSource = designSource,
            estimatedMah = est,
            estimateSource = source,
            confidence = confidence,
            healthPercent = pct,
            cycleCount = s?.cycleCount ?: _health.value.cycleCount,
            sessionsUsed = recent.size,
        )
    }

    // ---------- Time estimates ----------

    private fun computeTime(s: BatterySnapshot, h: HealthEstimate): TimeEstimate? {
        if (s.isFull) return TimeEstimate("Fully charged", null)
        val full = h.estimatedMah ?: s.estimatedFullMah
        if (s.isCharging) {
            s.chargeTimeRemainingMs?.let { return TimeEstimate("until full", it) }
            val rate = emaChargeMa ?: return null
            val counter = s.chargeCounterMah ?: full?.let { it * s.level / 100f } ?: return null
            if (full == null || rate < 50f) return null
            val hours = (full - counter).coerceAtLeast(0f) / rate
            return TimeEstimate("until full (est.)", (hours * 3_600_000).toLong())
        }
        if (!s.isPlugged) {
            val drain = emaDrainMa ?: return null
            if (drain < 5f) return null
            val remaining = s.chargeCounterMah ?: full?.let { it * s.level / 100f } ?: return null
            val hours = remaining / drain
            return TimeEstimate("left at current usage", (hours * 3_600_000).toLong())
        }
        return null
    }

    // ---------- Persistence ----------

    private fun appendHistory(s: BatterySnapshot) {
        val sample = Sample(
            t = s.timestamp,
            level = s.level,
            currentMa = s.currentMa ?: 0f,
            tempC = s.temperatureC,
            voltageMv = s.voltageMv,
            charging = s.isCharging,
        )
        lastHistoryAt = s.timestamp
        val cutoff = s.timestamp - HISTORY_KEEP_MS
        val current = _history.value
        val list = if (current.isNotEmpty() && current.first().t < cutoff) {
            current.filter { it.t >= cutoff } + sample
        } else current + sample
        _history.value = list
        try {
            if (list.size < current.size + 1) {
                historyFile.writeText(list.joinToString("") { it.toLine() })
            } else {
                historyFile.appendText(sample.toLine())
            }
        } catch (e: Exception) {
        }
    }

    private fun Sample.toLine() =
        "$t,$level,$currentMa,${if (tempC.isNaN()) "" else tempC},$voltageMv,${if (charging) 1 else 0}\n"

    private fun loadHistory(): List<Sample> {
        val cutoff = System.currentTimeMillis() - HISTORY_KEEP_MS
        return try {
            if (!historyFile.exists()) return emptyList()
            historyFile.readLines().mapNotNull { line ->
                val p = line.split(',')
                if (p.size < 6) return@mapNotNull null
                val t = p[0].toLongOrNull() ?: return@mapNotNull null
                if (t < cutoff) return@mapNotNull null
                Sample(
                    t = t,
                    level = p[1].toIntOrNull() ?: return@mapNotNull null,
                    currentMa = p[2].toFloatOrNull() ?: 0f,
                    tempC = p[3].toFloatOrNull() ?: Float.NaN,
                    voltageMv = p[4].toIntOrNull() ?: 0,
                    charging = p[5].trim() == "1",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSessions() {
        val arr = JSONArray()
        _sessions.value.forEach { s ->
            arr.put(JSONObject().apply {
                put("start", s.start); put("end", s.end)
                put("startLevel", s.startLevel); put("endLevel", s.endLevel)
                put("mah", s.mahAdded.toDouble()); put("cap", s.capacityMah.toDouble())
                put("method", s.method); put("maxTemp", s.maxTempC.toDouble())
            })
        }
        try {
            sessionsFile.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    private fun loadSessions(): List<ChargeSession> = try {
        if (!sessionsFile.exists()) emptyList() else {
            val arr = JSONArray(sessionsFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChargeSession(
                    start = o.getLong("start"), end = o.getLong("end"),
                    startLevel = o.getInt("startLevel"), endLevel = o.getInt("endLevel"),
                    mahAdded = o.getDouble("mah").toFloat(), capacityMah = o.getDouble("cap").toFloat(),
                    method = o.optString("method", "Charge counter"),
                    maxTempC = o.optDouble("maxTemp", 0.0).toFloat(),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun clearData() {
        try { historyFile.delete(); sessionsFile.delete() } catch (e: Exception) { }
        _history.value = emptyList()
        _sessions.value = emptyList()
        lastHistoryAt = 0L
        prefs.clearSession()
        recomputeHealth(_snapshot.value)
    }
}
