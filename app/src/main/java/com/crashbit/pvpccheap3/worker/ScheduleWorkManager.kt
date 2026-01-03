package com.crashbit.pvpccheap3.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona la programació del worker d'execució de schedules.
 */
@Singleton
class ScheduleWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScheduleWorkManager"
        private const val REPEAT_INTERVAL_MINUTES = 15L // WorkManager mínim és 15 minuts
    }

    /**
     * Inicia el worker periòdic per executar schedules.
     */
    fun startScheduleExecution() {
        Log.d(TAG, "Programant worker d'execució de schedules...")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ScheduleExecutionWorker>(
            REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ScheduleExecutionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "Worker programat cada $REPEAT_INTERVAL_MINUTES minuts")
    }

    /**
     * Atura el worker periòdic.
     */
    fun stopScheduleExecution() {
        Log.d(TAG, "Aturant worker d'execució de schedules...")
        WorkManager.getInstance(context).cancelUniqueWork(ScheduleExecutionWorker.WORK_NAME)
    }

    /**
     * Força una execució immediata del worker.
     */
    fun executeNow() {
        Log.d(TAG, "Forçant execució immediata...")
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<ScheduleExecutionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
