package com.crashbit.pvpccheap3.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/**
 * Programador d'alarmes per accions específiques.
 *
 * A diferència del HourlyAlarmScheduler anterior (que programava "cada hora"),
 * aquest programador crea alarmes EXACTES per cada acció:
 * - Una alarma a l'hora d'inici (per encendre)
 * - Una alarma a l'hora de fi (per apagar o marcar com missed)
 *
 * Utilitza setAlarmClock() que és la forma més fiable d'alarmes exactes
 * ja que el sistema SEMPRE les executa (com les alarmes del rellotge).
 */
object ActionAlarmScheduler {

    private const val TAG = "ActionAlarmScheduler"

    // Request codes base - s'afegeix hash de l'actionId per fer-los únics
    private const val REQUEST_CODE_START_BASE = 10000
    private const val REQUEST_CODE_END_BASE = 20000

    /**
     * Programa una alarma per INICIAR una acció (encendre dispositiu).
     */
    fun scheduleStartAction(
        context: Context,
        actionId: String,
        deviceId: String,
        startTime: LocalTime
    ) {
        val triggerTime = calculateTriggerTime(startTime)
        val requestCode = REQUEST_CODE_START_BASE + actionId.hashCode().and(0xFFFF)

        val intent = Intent(context, ActionAlarmReceiver::class.java).apply {
            action = ActionAlarmReceiver.ACTION_START
            putExtra(ActionAlarmReceiver.EXTRA_ACTION_ID, actionId)
            putExtra(ActionAlarmReceiver.EXTRA_DEVICE_ID, deviceId)
        }

        scheduleExactAlarm(context, triggerTime, requestCode, intent)
        Log.d(TAG, "Alarma START programada: $actionId a les $startTime (trigger=$triggerTime)")
    }

    /**
     * Programa una alarma per FINALITZAR una acció (apagar dispositiu o marcar missed).
     *
     * @param tomorrowIfNeeded Si és true i l'hora de fi és <= ara, programa per demà.
     *                         Útil per accions que creuen mitjanit (ex: 23:00-01:00).
     */
    fun scheduleEndAction(
        context: Context,
        actionId: String,
        deviceId: String,
        endTime: LocalTime,
        tomorrowIfNeeded: Boolean = false
    ) {
        val now = LocalTime.now()
        val daysToAdd = if (tomorrowIfNeeded && !endTime.isAfter(now)) 1 else 0
        val triggerTime = calculateTriggerTime(endTime, daysToAdd = daysToAdd)
        val requestCode = REQUEST_CODE_END_BASE + actionId.hashCode().and(0xFFFF)

        val intent = Intent(context, ActionAlarmReceiver::class.java).apply {
            action = ActionAlarmReceiver.ACTION_END
            putExtra(ActionAlarmReceiver.EXTRA_ACTION_ID, actionId)
            putExtra(ActionAlarmReceiver.EXTRA_DEVICE_ID, deviceId)
        }

        scheduleExactAlarm(context, triggerTime, requestCode, intent)
        val dayInfo = if (daysToAdd > 0) " (demà)" else ""
        Log.d(TAG, "Alarma END programada: $actionId a les $endTime$dayInfo (trigger=$triggerTime)")
    }

    /**
     * Programa una alarma per sincronitzar preus.
     * S'executa a les 20:35 per obtenir els preus de demà (disponibles a les 20:30).
     */
    fun schedulePriceSync(context: Context) {
        val now = LocalTime.now()
        val syncTime = LocalTime.of(20, 35) // 5 minuts després que estiguin disponibles

        val triggerTime = if (now.isBefore(syncTime)) {
            // Avui a les 20:35
            calculateTriggerTime(syncTime)
        } else {
            // Demà a les 20:35
            calculateTriggerTime(syncTime, daysToAdd = 1)
        }

        val requestCode = 99999 // Fix per sync de preus

        val intent = Intent(context, ActionAlarmReceiver::class.java).apply {
            action = ActionAlarmReceiver.ACTION_SYNC_PRICES
        }

        scheduleExactAlarm(context, triggerTime, requestCode, intent)
        Log.d(TAG, "Alarma SYNC programada per les 20:35 (trigger=$triggerTime)")
    }

    /**
     * Programa una alarma per sincronitzar a mitjanit (nou dia = noves accions).
     */
    fun scheduleMidnightSync(context: Context) {
        val triggerTime = calculateTriggerTime(LocalTime.of(0, 1), daysToAdd = 1)
        val requestCode = 99998

        val intent = Intent(context, ActionAlarmReceiver::class.java).apply {
            action = ActionAlarmReceiver.ACTION_MIDNIGHT_SYNC
        }

        scheduleExactAlarm(context, triggerTime, requestCode, intent)
        Log.d(TAG, "Alarma MIDNIGHT SYNC programada (trigger=$triggerTime)")
    }

    /**
     * Programa una alarma de REINTENT per una acció fallida.
     * Utilitza alarmes exactes per assegurar execució encara que l'app estigui en segon pla.
     *
     * @param delayMinutes Minuts fins al reintent (per defecte 2 minuts)
     * @param retryCount Nombre de reintent actual (per al requestCode únic)
     */
    fun scheduleRetryAction(
        context: Context,
        actionId: String,
        deviceId: String,
        shouldBeOn: Boolean,
        delayMinutes: Int = 2,
        retryCount: Int = 1
    ) {
        val now = System.currentTimeMillis()
        val triggerTime = now + (delayMinutes * 60 * 1000L)

        // RequestCode únic per cada reintent (base 30000 + hash + retry)
        val requestCode = 30000 + actionId.hashCode().and(0xFFF) + (retryCount * 0x1000)

        val intent = Intent(context, ActionAlarmReceiver::class.java).apply {
            action = ActionAlarmReceiver.ACTION_RETRY
            putExtra(ActionAlarmReceiver.EXTRA_ACTION_ID, actionId)
            putExtra(ActionAlarmReceiver.EXTRA_DEVICE_ID, deviceId)
            putExtra(ActionAlarmReceiver.EXTRA_SHOULD_BE_ON, shouldBeOn)
            putExtra(ActionAlarmReceiver.EXTRA_RETRY_COUNT, retryCount)
        }

        scheduleExactAlarm(context, triggerTime, requestCode, intent)
        Log.d(TAG, "Alarma RETRY #$retryCount programada: $actionId en $delayMinutes minuts")
    }

    /**
     * Cancel.la totes les alarmes d'una acció.
     */
    fun cancelActionAlarms(context: Context, actionId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel.lar alarma START
        val startRequestCode = REQUEST_CODE_START_BASE + actionId.hashCode().and(0xFFFF)
        val startIntent = Intent(context, ActionAlarmReceiver::class.java)
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            startRequestCode,
            startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        startPendingIntent?.let {
            alarmManager.cancel(it)
            Log.d(TAG, "Alarma START cancel.lada: $actionId")
        }

        // Cancel.lar alarma END
        val endRequestCode = REQUEST_CODE_END_BASE + actionId.hashCode().and(0xFFFF)
        val endIntent = Intent(context, ActionAlarmReceiver::class.java)
        val endPendingIntent = PendingIntent.getBroadcast(
            context,
            endRequestCode,
            endIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        endPendingIntent?.let {
            alarmManager.cancel(it)
            Log.d(TAG, "Alarma END cancel.lada: $actionId")
        }
    }

    /**
     * Programa una alarma exacta utilitzant setAlarmClock (la més fiable).
     */
    private fun scheduleExactAlarm(
        context: Context,
        triggerTimeMs: Long,
        requestCode: Int,
        intent: Intent
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent per mostrar quan l'usuari toca la notificació d'alarma
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className?.let {
                Class.forName(it)
            }),
            PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // setAlarmClock és la forma MÉS fiable - el sistema SEMPRE l'executa
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTimeMs, showIntent),
                        pendingIntent
                    )
                    Log.d(TAG, "Alarma exacta (AlarmClock) programada: requestCode=$requestCode")
                } else {
                    // Fallback si no tenim permís d'alarmes exactes
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMs,
                        pendingIntent
                    )
                    Log.w(TAG, "Usant setExactAndAllowWhileIdle (menys fiable): requestCode=$requestCode")
                }
            } else {
                // Android < 12: sempre podem usar setAlarmClock
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTimeMs, showIntent),
                    pendingIntent
                )
                Log.d(TAG, "Alarma exacta (AlarmClock) programada: requestCode=$requestCode")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos programant alarma: ${e.message}")
            // Últim recurs
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        }
    }

    /**
     * Calcula el timestamp per una hora específica d'avui o demà.
     */
    private fun calculateTriggerTime(time: LocalTime, daysToAdd: Int = 0): Long {
        val today = LocalDate.now()
        val targetDate = today.plusDays(daysToAdd.toLong())
        val targetDateTime = targetDate.atTime(time)

        return targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
