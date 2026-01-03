package com.crashbit.pvpccheap3.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Programa alarmes exactes per executar-se a cada hora en punt.
 */
class HourlyAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "HourlyAlarmScheduler"
        private const val ALARM_REQUEST_CODE = 1001
    }

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Programa l'alarma per la pròxima hora en punt.
     */
    fun scheduleNextHourlyAlarm() {
        val pendingIntent = createPendingIntent()

        // Calcular la pròxima hora en punt
        val calendar = Calendar.getInstance().apply {
            // Afegir 1 hora i posar minuts/segons a 0
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerTime = calendar.timeInMillis

        Log.d(TAG, "Programant alarma per: ${calendar.time}")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ necessita comprovar permisos d'alarmes exactes
                if (alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarma exacta programada (Android 12+)")
                } else {
                    // Fallback a alarma no exacta
                    alarmManager?.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarma no exacta programada (sense permís SCHEDULE_EXACT_ALARM)")
                }
            } else {
                // Android < 12
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "Alarma exacta programada")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error programant alarma: ${e.message}")
            // Fallback a alarma inexacta
            alarmManager?.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Cancel·la l'alarma programada.
     */
    fun cancelAlarm() {
        val pendingIntent = createPendingIntent()
        alarmManager?.cancel(pendingIntent)
        Log.d(TAG, "Alarma cancel·lada")
    }

    /**
     * Programa una alarma immediata per executar ara.
     */
    fun executeNow() {
        val pendingIntent = createPendingIntent()
        val triggerTime = System.currentTimeMillis() + 1000 // 1 segon

        try {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "Alarma immediata programada")
        } catch (e: Exception) {
            Log.e(TAG, "Error programant alarma immediata: ${e.message}")
        }
    }

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, HourlyAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
