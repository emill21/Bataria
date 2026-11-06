package com.batterypulse.app

import android.app.Application
import com.batterypulse.app.data.BatteryMonitor

class BatteryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BatteryMonitor.init(this)
    }
}
