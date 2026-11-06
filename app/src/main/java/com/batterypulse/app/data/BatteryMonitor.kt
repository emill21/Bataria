package com.batterypulse.app.data

import android.content.Context
import com.batterypulse.app.monitor.Alerts
import com.batterypulse.app.monitor.MonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

/**
 * Process-wide battery state. Whoever calls [sample] (the UI every 2 s, the background
 * service every 10 s and on every battery broadcast) feeds the same pipeline:
 * smoothing, history, charge-session tracking, health estimation and alerts.
 */
object BatteryMonitor {
    private const val HISTORY_INTERVAL_MS = 60_000L
    private const val HISTORY_KEEP_MS = 7L * 24 * 3_600_000
    private const val MIN_SESSION_DELTA = 15        // % gained before a charge counts
    private const val MAX_INTEGRATION_GAP_MS = 5 * 60_000L
    private const val SYSFS_REFRESH_MS = 10 * 60_000L
    private const val SESSION_PERSIST_MS = 30_000L
    private const val RECENT_SIZE = 90

    lateinit var prefs: Prefs
        private set
    private lateinit var app: Context

    private val _snapshot = MutableStateFlow<BatterySnapshot?>(null)
    val snapshot: StateFlow<BatterySnapshot?> = _snapshot.asStateFlow()

    /** Last few minutes of raw samples, for sparklines. */
    private val _recent = MutableStateFlow<List<BatterySnapshot>>(emptyList())
    val recent: StateFlow<List<BatterySnapshot>> = _recent.asStateFlow()

    private val _history = MutableStateFlow<List<Sample>>(emptyList())
    val history: StateFlow<List<Sample>> = _history.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChargeSession>>(emptyList())
    val sessions: StateFlow<List<ChargeSession>> = _sessions.asStateFlow()

    private val _health = MutableStateFlow(HealthEstimate.EMPTY)
    val health: StateFlow<HealthEstimate> = _health.asStateFlow()

    private val _time = MutableStateFlow<TimeEstimate?>(null)
    val timeEstimate: StateFlow<TimeEstimate?> = _time.asStateFlow()

    private val _live = MutableStateFlow<LiveCharge?>(null)
    val liveCharge: StateFlow<LiveCharge?> = _live.asStateFlow()

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _appearance = MutableStateFlow(Appearance("system", true, false))
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    private var session = SessionState()
    private var sessionSavedAt = 0L
    private var lastSampleAt = 0L
    private var lastHistoryAt = 0L
    private var lastLevel = -1
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
        if (prefs.trackingSince == 0L) prefs.trackingSince = System.currentTimeMillis()
        session = prefs.loadSession()
        _monitoring.value = prefs.monitoringEnabled
        _appearance.value = Appearance(prefs.themeMode, prefs.dynamicColor, prefs.pureBlack)
        _history.value = loadHistory()
        lastHistoryAt = _history.value.lastOrNull()?.t ?: 0L
        _sessions.value = loadSessions()
        Alerts.ensureChannel(app)
        recomputeHealth(null)
    }

    // ---------- Settings that the UI observes ----------

    fun setMonitoring(context: Context, on: Boolean) {
        prefs.monitoringEnabled = on
        _monitoring.value = on
        if (on) MonitorService.start(context) else MonitorService.stop(context)
    }

    fun setAppearance(a: Appearance) {
        prefs.themeMode = a.themeMode
        prefs.dynamicColor = a.dynamicColor
        prefs.pureBlack = a.pureBlack
        _appearance.value = a
    }

    // ---------- Sampling ----------

    @Synchronized
    fun sample(): BatterySnapshot {
        val s = BatteryReader.read(app, prefs)
        val now = s.timestamp
        val gap = if (lastSampleAt > 0) now - lastSampleAt else 0L
        val validGap = gap in 1..MAX_INTEGRATION_GAP_MS
        lastSampleAt = now

        updateSmoothing(s)
        trackWear(s)
        trackSession(s, if (validGap) gap else 0L)
        if (now - lastHistoryAt >= HISTORY_INTERVAL_MS) appendHistory(s)
        recomputeHealth(s)
        _time.value = computeTime(s, _health.value)
        _recent.value = (_recent.value + s).takeLast(RECENT_SIZE)
        _snapshot.value = s
        try {
            Alerts.check(app, s, prefs)
        } catch (e: Exception) {
        }
        return s
    }

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

    /** Sums every percent charged; 100 % charged ≈ one full cycle. */
    private fun trackWear(s: BatterySnapshot) {
        if (lastLevel >= 0 && s.isPlugged && s.level > lastLevel) {
            prefs.cumulativeChargePct = prefs.cumulativeChargePct + (s.level - lastLevel)
        }
        lastLevel = s.level
    }

    // ---------- Charge sessions (capacity measurement) ----------

    private fun trackSession(s: BatterySnapshot, gapMs: Long) {
        if (s.isCharging && !session.active) {
            session = SessionState(
                start = s.timestamp,
                startLevel = s.level,
                startCounter = s.chargeCounterMah ?: -1f,
                maxTemp = if (s.temperatureC.isNaN()) 0f else s.temperatureC,
            )
            prefs.saveSession(session)
            sessionSavedAt = s.timestamp
            _live.value = liveFrom(s)
            return
        }
        if (!session.active) {
            _live.value = null
            return
        }
        if (s.isCharging) {
            val hours = gapMs / 3_600_000f
            val i = s.currentMa
            if (i != null && i > 0 && gapMs > 0) session.integratedMah += i * hours
            val p = s.powerW
            if (p != null && p > 0f) {
                session.peakW = max(session.peakW, p)
                if (gapMs > 0) {
                    session.energyWh += p * hours
                    session.chargeMs += gapMs
                }
            }
            if (!s.temperatureC.isNaN() && s.temperatureC > session.maxTemp) session.maxTemp = s.temperatureC
            if (s.timestamp - sessionSavedAt > SESSION_PERSIST_MS) {
                prefs.saveSession(session)
                sessionSavedAt = s.timestamp
            }
            _live.value = liveFrom(s)
            return
        }
        finishSession(s)
        _live.value = null
    }

    private fun liveFrom(s: BatterySnapshot): LiveCharge {
        val counterDelta = s.chargeCounterMah?.let { c ->
            if (session.startCounter > 0f) (c - session.startCounter).takeIf { it >= 0f } else null
        }
        return LiveCharge(
            start = session.start,
            startLevel = session.startLevel,
            mahAdded = counterDelta ?: session.integratedMah.takeIf { it > 0f },
            peakPowerW = session.peakW,
            avgPowerW = if (session.chargeMs > 60_000) session.energyWh / (session.chargeMs / 3_600_000f) else null,
        )
    }

    private fun finishSession(s: BatterySnapshot) {
        val st = session
        session = SessionState()
        prefs.saveSession(session)

        val delta = s.level - st.startLevel
        if (delta < MIN_SESSION_DELTA) return

        val endCounter = s.chargeCounterMah
        val counterDelta =
            if (st.startCounter > 0f && endCounter != null) endCounter - st.startCounter else null
        val (mah, method) = if (counterDelta != null && counterDelta > 0f) {
            counterDelta to "Charge counter"
        } else {
            st.integratedMah to "Current integration"
        }
        if (mah <= 0f) return

        val capacity = mah / (delta / 100f)
        val design = design().first
        val plausible = if (design != null) capacity in (design * 0.4f)..(design * 1.3f)
        else capacity in 500f..25_000f
        if (!plausible) return

        val avgW = if (st.chargeMs > 60_000) st.energyWh / (st.chargeMs / 3_600_000f) else 0f
        val result = ChargeSession(
            start = st.start, end = s.timestamp, startLevel = st.startLevel, endLevel = s.level,
            mahAdded = mah, capacityMah = capacity, method = method, maxTempC = st.maxTemp,
            peakPowerW = st.peakW, avgPowerW = avgW,
        )
        _sessions.value = (_sessions.value + result).takeLast(200)
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
            estimatedCycles = prefs.cumulativeChargePct / 100f,
            trackingSince = prefs.trackingSince,
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
            return TimeEstimate("until full", (hours * 3_600_000).toLong())
        }
        if (!s.isPlugged) {
            val drain = emaDrainMa ?: return null
            if (drain < 5f) return null
            val remaining = s.chargeCounterMah ?: full?.let { it * s.level / 100f } ?: return null
            val hours = remaining / drain
            return TimeEstimate("left at this usage", (hours * 3_600_000).toLong())
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
            screenOn = s.screenOn,
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
        "$t,$level,$currentMa,${if (tempC.isNaN()) "" else tempC},$voltageMv,${if (charging) 1 else 0},${if (screenOn) 1 else 0}\n"

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
                    screenOn = p.getOrNull(6)?.trim() != "0",
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
                put("peakW", s.peakPowerW.toDouble()); put("avgW", s.avgPowerW.toDouble())
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
                    peakPowerW = o.optDouble("peakW", 0.0).toFloat(),
                    avgPowerW = o.optDouble("avgW", 0.0).toFloat(),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun clearData() {
        try {
            historyFile.delete(); sessionsFile.delete()
        } catch (e: Exception) {
        }
        _history.value = emptyList()
        _sessions.value = emptyList()
        lastHistoryAt = 0L
        session = SessionState()
        prefs.saveSession(session)
        prefs.cumulativeChargePct = 0f
        prefs.trackingSince = System.currentTimeMillis()
        recomputeHealth(_snapshot.value)
    }
}
