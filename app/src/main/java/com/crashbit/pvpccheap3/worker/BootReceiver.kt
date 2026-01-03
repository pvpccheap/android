package com.crashbit.pvpccheap3.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver que es dispara quan el dispositiu s'encén.
 * Reprograma les alarmes horàries.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completat, reprogramant alarmes...")

            // Programar la pròxima alarma horària
            HourlyAlarmScheduler(context).scheduleNextHourlyAlarm()

            // També executar immediatament per actualitzar estat
            HourlyAlarmScheduler(context).executeNow()
        }
    }
}
