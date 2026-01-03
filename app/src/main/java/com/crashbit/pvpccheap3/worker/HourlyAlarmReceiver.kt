package com.crashbit.pvpccheap3.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * BroadcastReceiver que es dispara cada hora en punt.
 * Llança el ScheduleExecutionWorker immediatament.
 */
class HourlyAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HourlyAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarma rebuda! Executant comprovació de schedule...")

        // Llançar el worker immediatament
        val workRequest = OneTimeWorkRequestBuilder<ScheduleExecutionWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        // Programar la següent alarma
        HourlyAlarmScheduler(context).scheduleNextHourlyAlarm()
    }
}
