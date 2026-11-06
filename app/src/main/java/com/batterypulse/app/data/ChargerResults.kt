package com.batterypulse.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChargerResult(
    val id: Long,
    val name: String,
    val avgW: Float,
    val peakW: Float,
    val level: Int,
    val tempC: Float,
    val source: Int,
    val time: Long,
)

fun chargerRating(avgW: Float): String = when {
    avgW >= 20f -> "Super fast"
    avgW >= 12f -> "Fast"
    avgW >= 7f -> "Standard"
    avgW >= 3.5f -> "Slow"
    else -> "Very slow"
}

/** Saved charger-test results, stored in chargers.json. */
object ChargerResults {
    private val _results = MutableStateFlow<List<ChargerResult>>(emptyList())
    val results: StateFlow<List<ChargerResult>> = _results.asStateFlow()
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "chargers.json")
        _results.value = try {
            val f = file!!
            if (!f.exists()) emptyList() else {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ChargerResult(
                        id = o.getLong("id"), name = o.getString("name"),
                        avgW = o.getDouble("avg").toFloat(), peakW = o.getDouble("peak").toFloat(),
                        level = o.optInt("level"), tempC = o.optDouble("temp", 0.0).toFloat(),
                        source = o.optInt("source"), time = o.getLong("time"),
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, r: ChargerResult) {
        init(context)
        _results.value = (_results.value + r)
        save()
    }

    fun remove(context: Context, id: Long) {
        init(context)
        _results.value = _results.value.filterNot { it.id == id }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        _results.value.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id); put("name", r.name); put("avg", r.avgW.toDouble()); put("peak", r.peakW.toDouble())
                put("level", r.level); put("temp", r.tempC.toDouble()); put("source", r.source); put("time", r.time)
            })
        }
        try {
            file?.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }
}
