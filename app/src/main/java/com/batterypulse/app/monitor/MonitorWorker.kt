package com.batterypulse.app.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.batterypulse.app.data.BatteryMonitor
import com.batterypulse.app.widget.BatteryWidget
import java.util.concurrent.TimeUnit

/**
 * Quiet monitoring: samples every ~15 minutes with no notification. Charge sessions are
 * still measured accurately because capacity comes from the battery's charge counter at
 * the start and end of each charge, not from continuous sampling.
 */
class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        BatteryMonitor.init(applicationContext)
        BatteryMonitor.sample()
        BatteryWidget.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "quiet-monitor"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
