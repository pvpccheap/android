package com.crashbit.pvpccheap3

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.crashbit.pvpccheap3.service.ActionAlarmScheduler
import com.crashbit.pvpccheap3.service.ScheduleExecutorService
import com.crashbit.pvpccheap3.util.BatteryOptimizationHelper
import com.crashbit.pvpccheap3.util.FileLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PvpcApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "PvpcApp"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Inicialitzar FileLogger PRIMER
        FileLogger.init(this)
        FileLogger.i(TAG, "=== APLICACIÓ INICIADA ===")

        Log.d(TAG, "Inicialitzant aplicació...")

        // Iniciar el Foreground Service
        ScheduleExecutorService.startService(this)
        FileLogger.i(TAG, "Foreground Service iniciat")

        // Programar sincronització de preus (20:35)
        ActionAlarmScheduler.schedulePriceSync(this)
        Log.d(TAG, "Alarma de sincronització de preus programada")

        // Programar sincronització a mitjanit
        ActionAlarmScheduler.scheduleMidnightSync(this)
        Log.d(TAG, "Alarma de mitjanit programada")

        // Sincronitzar schedules immediatament
        ScheduleExecutorService.syncPrices(this)
        Log.d(TAG, "Sincronització inicial iniciada")

        // Avisar si no tenim exempció de bateria
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            Log.w(TAG, "ATENCIÓ: L'app NO està exempta d'optimització de bateria!")
        } else {
            Log.d(TAG, "L'app està exempta d'optimització de bateria")
        }
    }
}
