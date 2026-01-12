package com.crashbit.pvpccheap3.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.crashbit.pvpccheap3.service.ActionAlarmScheduler
import com.crashbit.pvpccheap3.service.ScheduleExecutorService

/**
 * BroadcastReceiver que es dispara quan el dispositiu s'encén.
 * Inicia el servei foreground i programa les alarmes de sincronització.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d(TAG, "Boot completat, iniciant servei...")

            // Iniciar el Foreground Service
            ScheduleExecutorService.startService(context)

            // Programar sincronització de preus (20:35)
            ActionAlarmScheduler.schedulePriceSync(context)

            // Programar sincronització a mitjanit
            ActionAlarmScheduler.scheduleMidnightSync(context)

            // Sincronitzar schedules immediatament
            ScheduleExecutorService.syncPrices(context)

            Log.d(TAG, "Servei iniciat i alarmes programades")
        }
    }
}
