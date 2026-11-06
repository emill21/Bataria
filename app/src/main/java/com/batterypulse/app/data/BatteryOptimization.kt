package com.batterypulse.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** "Unrestricted" battery usage = exempt from Android's battery optimizations. */
object BatteryOptimization {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Shows Android's "Let Bataria always run in the background?" prompt, or the app's settings page. */
    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        val pkg = Uri.parse("package:${context.packageName}")
        val attempts = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        for (i in attempts) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
            }
        }
    }
}
