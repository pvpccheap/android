package com.crashbit.pvpccheap3

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.crashbit.pvpccheap3.worker.HourlyAlarmScheduler
import com.crashbit.pvpccheap3.worker.ScheduleWorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PvpcApp : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "PvpcApp"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var scheduleWorkManager: ScheduleWorkManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Inicialitzant aplicació...")

        // Iniciar el worker periòdic (backup cada 15 min)
        scheduleWorkManager.startScheduleExecution()
        Log.d(TAG, "Worker de schedules iniciat")

        // Iniciar alarmes horàries exactes
        HourlyAlarmScheduler(this).scheduleNextHourlyAlarm()
        Log.d(TAG, "Alarma horària programada")
    }
}
