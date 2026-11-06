package com.batterypulse.app.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batterypulse.app.data.BatteryMonitor

/** Restarts background monitoring after a reboot or app update, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        BatteryMonitor.init(context)
        if (BatteryMonitor.prefs.monitoringEnabled) {
            try {
                MonitorService.start(context)
            } catch (e: Exception) {
                // Background start can be refused on some OEM builds; the user can reopen the app.
            }
        }
    }
}
