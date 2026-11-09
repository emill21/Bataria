package com.batterypulse.app.wear

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.batterypulse.app.data.BatteryReader
import com.batterypulse.app.data.Prefs
import java.util.concurrent.TimeUnit

/** Quiet 15-minute check: records history and refreshes the tile and complication. No notification, no service. */
class SampleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Sampler.sample(applicationContext)
        return Result.success()
    }
}

object Sampler {
    fun sample(context: Context) {
        val snap = BatteryReader.read(context, Prefs(context))
        WearStore(context).record(snap)
        refreshSurfaces(context)
    }

    fun refreshSurfaces(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(BatteryTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, BatteryComplicationService::class.java))
                .requestUpdateAll()
        }
    }

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<SampleWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("bataria-sample", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Sampler.schedule(context)
    }
}
