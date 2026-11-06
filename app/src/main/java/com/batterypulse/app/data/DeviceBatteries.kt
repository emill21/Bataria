package com.batterypulse.app.data

import android.os.Build
import java.util.Locale

/** A recognised phone and its manufacturer-published (typical) battery capacity. */
data class KnownDevice(val name: String, val capacityMah: Int)

/**
 * Built-in table of popular phones' battery sizes, so health works out of the box even
 * when Android doesn't expose the design capacity. Matched on Build.MODEL.
 */
object DeviceBatteries {
    // Pixel: Build.MODEL is the marketing name.
    private val pixels = mapOf(
        "PIXEL 4A" to 3140, "PIXEL 4A (5G)" to 3885, "PIXEL 5" to 4080, "PIXEL 5A" to 4680,
        "PIXEL 6" to 4614, "PIXEL 6 PRO" to 5003, "PIXEL 6A" to 4410,
        "PIXEL 7" to 4355, "PIXEL 7 PRO" to 5000, "PIXEL 7A" to 4385, "PIXEL FOLD" to 4821,
        "PIXEL 8" to 4575, "PIXEL 8 PRO" to 5050, "PIXEL 8A" to 4492,
        "PIXEL 9" to 4700, "PIXEL 9 PRO" to 4700, "PIXEL 9 PRO XL" to 5060, "PIXEL 9 PRO FOLD" to 4650,
        "PIXEL 9A" to 5100,
        "PIXEL 10" to 4970, "PIXEL 10 PRO" to 4870, "PIXEL 10 PRO XL" to 5200, "PIXEL 10 PRO FOLD" to 5015,
    )

    // Samsung: Build.MODEL is a code like SM-S928B; match on the prefix.
    private val samsung = listOf(
        "SM-G990" to KnownDevice("Galaxy S21 FE", 4500),
        "SM-G991" to KnownDevice("Galaxy S21", 4000),
        "SM-G996" to KnownDevice("Galaxy S21+", 4800),
        "SM-G998" to KnownDevice("Galaxy S21 Ultra", 5000),
        "SM-S901" to KnownDevice("Galaxy S22", 3700),
        "SM-S906" to KnownDevice("Galaxy S22+", 4500),
        "SM-S908" to KnownDevice("Galaxy S22 Ultra", 5000),
        "SM-S711" to KnownDevice("Galaxy S23 FE", 4500),
        "SM-S911" to KnownDevice("Galaxy S23", 3900),
        "SM-S916" to KnownDevice("Galaxy S23+", 4700),
        "SM-S918" to KnownDevice("Galaxy S23 Ultra", 5000),
        "SM-S721" to KnownDevice("Galaxy S24 FE", 4700),
        "SM-S921" to KnownDevice("Galaxy S24", 4000),
        "SM-S926" to KnownDevice("Galaxy S24+", 4900),
        "SM-S928" to KnownDevice("Galaxy S24 Ultra", 5000),
        "SM-S931" to KnownDevice("Galaxy S25", 4000),
        "SM-S936" to KnownDevice("Galaxy S25+", 4900),
        "SM-S937" to KnownDevice("Galaxy S25 Edge", 3900),
        "SM-S938" to KnownDevice("Galaxy S25 Ultra", 5000),
        "SM-A155" to KnownDevice("Galaxy A15", 5000),
        "SM-A156" to KnownDevice("Galaxy A15 5G", 5000),
        "SM-A165" to KnownDevice("Galaxy A16", 5000),
        "SM-A166" to KnownDevice("Galaxy A16 5G", 5000),
        "SM-A256" to KnownDevice("Galaxy A25", 5000),
        "SM-A266" to KnownDevice("Galaxy A26", 5000),
        "SM-A346" to KnownDevice("Galaxy A34", 5000),
        "SM-A356" to KnownDevice("Galaxy A35", 5000),
        "SM-A366" to KnownDevice("Galaxy A36", 5000),
        "SM-A546" to KnownDevice("Galaxy A54", 5000),
        "SM-A556" to KnownDevice("Galaxy A55", 5000),
        "SM-A566" to KnownDevice("Galaxy A56", 5000),
        "SM-F721" to KnownDevice("Galaxy Z Flip4", 3700),
        "SM-F731" to KnownDevice("Galaxy Z Flip5", 3700),
        "SM-F741" to KnownDevice("Galaxy Z Flip6", 4000),
        "SM-F761" to KnownDevice("Galaxy Z Flip7 FE", 4000),
        "SM-F766" to KnownDevice("Galaxy Z Flip7", 4300),
        "SM-F936" to KnownDevice("Galaxy Z Fold4", 4400),
        "SM-F946" to KnownDevice("Galaxy Z Fold5", 4400),
        "SM-F956" to KnownDevice("Galaxy Z Fold6", 4400),
        "SM-F966" to KnownDevice("Galaxy Z Fold7", 4400),
    )

    // OnePlus: Build.MODEL is a CPH code.
    private val onePlus = mapOf(
        "CPH2447" to KnownDevice("OnePlus 11", 5000), "CPH2449" to KnownDevice("OnePlus 11", 5000),
        "CPH2451" to KnownDevice("OnePlus 11", 5000),
        "CPH2573" to KnownDevice("OnePlus 12", 5400), "CPH2581" to KnownDevice("OnePlus 12", 5400),
        "CPH2583" to KnownDevice("OnePlus 12", 5400),
        "CPH2585" to KnownDevice("OnePlus 12R", 5500), "CPH2609" to KnownDevice("OnePlus 12R", 5500),
        "CPH2611" to KnownDevice("OnePlus 12R", 5500),
        "CPH2649" to KnownDevice("OnePlus 13", 6000), "CPH2653" to KnownDevice("OnePlus 13", 6000),
        "CPH2655" to KnownDevice("OnePlus 13", 6000),
    )

    /** Friendly name for this phone, recognised or not. */
    fun phoneName(): String = detect()?.name ?: run {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }

    fun detect(model: String = Build.MODEL ?: ""): KnownDevice? {
        val m = model.trim().uppercase(Locale.ROOT)
        pixels[m]?.let { return KnownDevice(model.trim(), it) }
        samsung.firstOrNull { m.startsWith(it.first) }?.let { return it.second }
        onePlus[m]?.let { return it }
        return null
    }
}
