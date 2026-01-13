package com.crashbit.pvpccheap3.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.crashbit.pvpccheap3.data.local.ScheduleCache
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver que rep les alarmes d'accions programades.
 *
 * Quan una alarma es dispara:
 * - ACTION_START: Encén el dispositiu
 * - ACTION_END: Apaga el dispositiu o marca com missed si no s'ha executat
 * - ACTION_SYNC_PRICES: Sincronitza preus del backend
 * - ACTION_MIDNIGHT_SYNC: Sincronitza schedules del nou dia
 *
 * Utilitza wake lock per assegurar que el codi s'executa fins i tot
 * si el dispositiu està en Doze mode.
 */
@AndroidEntryPoint
class ActionAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActionAlarmReceiver"

        const val ACTION_START = "com.crashbit.pvpccheap3.alarm.START"
        const val ACTION_END = "com.crashbit.pvpccheap3.alarm.END"
        const val ACTION_SYNC_PRICES = "com.crashbit.pvpccheap3.alarm.SYNC_PRICES"
        const val ACTION_MIDNIGHT_SYNC = "com.crashbit.pvpccheap3.alarm.MIDNIGHT_SYNC"
        const val ACTION_RETRY = "com.crashbit.pvpccheap3.alarm.RETRY"

        const val EXTRA_ACTION_ID = "action_id"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_SHOULD_BE_ON = "should_be_on"
        const val EXTRA_RETRY_COUNT = "retry_count"

        const val MAX_RETRIES = 5
    }

    @Inject
    lateinit var scheduleCache: ScheduleCache

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarma rebuda: action=${intent.action}")

        // goAsync() ens permet executar operacions asíncrones sense que Android
        // mati el receiver als 10 segons (tenim fins a 30 segons)
        val pendingResult = goAsync()

        // Adquirir wake lock per assegurar execució
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PvpcCheap:AlarmReceiver"
        )
        wakeLock.acquire(60_000) // Màxim 60 segons

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_START -> handleStartActionAsync(context, intent)
                    ACTION_END -> handleEndActionAsync(context, intent)
                    ACTION_SYNC_PRICES -> handleSyncPrices(context)
                    ACTION_MIDNIGHT_SYNC -> handleMidnightSync(context)
                    ACTION_RETRY -> handleRetryAction(context, intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processant alarma: ${e.message}", e)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                // IMPORTANT: finish() ha de ser cridat quan acabem
                pendingResult.finish()
            }
        }
    }

    /**
     * Gestiona l'alarma d'inici d'una acció (encendre dispositiu).
     * Versió suspend per usar amb goAsync().
     */
    private suspend fun handleStartActionAsync(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)

        if (actionId == null || deviceId == null) {
            Log.e(TAG, "START: Falten paràmetres")
            return
        }

        Log.d(TAG, "START: actionId=$actionId, deviceId=$deviceId")

        // Verificar que l'acció encara està pending
        val actions = scheduleCache.getTodaySchedule() ?: run {
            Log.w(TAG, "START: No hi ha schedule al cache")
            return
        }
        val action = actions.find { it.id == actionId }

        if (action == null) {
            Log.w(TAG, "START: Acció $actionId no trobada al cache")
            return
        }

        if (action.status != "pending") {
            Log.d(TAG, "START: Acció $actionId ja processada (status=${action.status})")
            return
        }

        // Delegar al servei per encendre el dispositiu
        ScheduleExecutorService.executeAction(context, actionId, deviceId, shouldBeOn = true)
    }

    /**
     * Gestiona l'alarma de fi d'una acció.
     * Si l'acció estava activa, apaga el dispositiu (excepte si hi ha una acció consecutiva).
     * Si l'acció estava pending, la marca com missed.
     * Versió suspend per usar amb goAsync().
     */
    private suspend fun handleEndActionAsync(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)

        if (actionId == null || deviceId == null) {
            Log.e(TAG, "END: Falten paràmetres")
            return
        }

        Log.d(TAG, "END: actionId=$actionId, deviceId=$deviceId")

        val actions = scheduleCache.getTodaySchedule() ?: run {
            Log.w(TAG, "END: No hi ha schedule al cache")
            return
        }
        val action = actions.find { it.id == actionId }

        if (action == null) {
            Log.w(TAG, "END: Acció $actionId no trobada al cache")
            return
        }

        when (action.status) {
            "pending" -> {
                // Mai es va executar - marcar com missed
                Log.d(TAG, "END: Acció $actionId mai executada, marcant com missed")
                ScheduleExecutorService.markMissedAction(context, actionId)
            }
            "executed_on" -> {
                // Comprovar si hi ha una acció consecutiva pel mateix dispositiu
                val hasConsecutiveAction = hasConsecutiveActionForDevice(
                    actions = actions,
                    currentEndTime = action.endTime,
                    deviceId = deviceId
                )

                if (hasConsecutiveAction) {
                    // Hi ha una acció consecutiva - NO apagar, només marcar com executed_off
                    Log.d(TAG, "END: Acció $actionId té acció consecutiva, NO apaguem (estalvi de comanda)")
                    ScheduleExecutorService.markActionExecutedOff(context, actionId)
                } else {
                    // No hi ha acció consecutiva - apagar normalment
                    Log.d(TAG, "END: Acció $actionId acabada, apagant dispositiu")
                    ScheduleExecutorService.executeAction(context, actionId, deviceId, shouldBeOn = false)
                }
            }
            "failed" -> {
                // Va fallar però l'hora ha acabat - marcar com missed
                Log.d(TAG, "END: Acció $actionId va fallar i l'hora ha acabat, marcant com missed")
                ScheduleExecutorService.markMissedAction(context, actionId)
            }
            else -> {
                Log.d(TAG, "END: Acció $actionId amb status=${action.status}, no cal fer res")
            }
        }
    }

    /**
     * Comprova si hi ha una altra acció que comença exactament quan aquesta acaba,
     * pel mateix dispositiu i amb status pending (és a dir, s'executarà).
     */
    private fun hasConsecutiveActionForDevice(
        actions: List<com.crashbit.pvpccheap3.data.model.ScheduledAction>,
        currentEndTime: String,
        deviceId: String
    ): Boolean {
        return actions.any { otherAction ->
            otherAction.googleDeviceId == deviceId &&
            otherAction.startTime == currentEndTime &&
            otherAction.status == "pending"
        }
    }

    /**
     * Gestiona un reintent d'una acció fallida.
     * Comprova primer si l'acció encara necessita ser executada.
     */
    private suspend fun handleRetryAction(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val shouldBeOn = intent.getBooleanExtra(EXTRA_SHOULD_BE_ON, true)
        val retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 1)

        if (actionId == null || deviceId == null) {
            Log.e(TAG, "RETRY: Falten paràmetres")
            return
        }

        Log.d(TAG, "RETRY #$retryCount: actionId=$actionId, deviceId=$deviceId, shouldBeOn=$shouldBeOn")

        // Verificar l'estat actual de l'acció al cache
        val actions = scheduleCache.getTodaySchedule()
        val action = actions?.find { it.id == actionId }

        if (action != null) {
            // Si ja s'ha executat correctament, no reintentar
            if (action.status == "executed_on" || action.status == "executed_off") {
                Log.d(TAG, "RETRY: Acció $actionId ja executada (status=${action.status}), no reintentem")
                return
            }

            // Si està marcada com missed, no reintentar
            if (action.status == "missed") {
                Log.d(TAG, "RETRY: Acció $actionId marcada com missed, no reintentem")
                return
            }
        }

        // Delegar al servei amb informació de reintent
        ScheduleExecutorService.retryAction(context, actionId, deviceId, shouldBeOn, retryCount)
    }

    /**
     * Sincronitza preus del backend (s'executa a les 20:35).
     */
    private fun handleSyncPrices(context: Context) {
        Log.d(TAG, "SYNC_PRICES: Iniciant sincronització de preus")

        // Assegurar que el servei està actiu
        ScheduleExecutorService.startService(context)

        // Delegar la sincronització al servei
        ScheduleExecutorService.syncPrices(context)

        // Programar la propera sincronització per demà
        ActionAlarmScheduler.schedulePriceSync(context)
    }

    /**
     * Sincronització a mitjanit per obtenir les accions del nou dia.
     */
    private fun handleMidnightSync(context: Context) {
        Log.d(TAG, "MIDNIGHT_SYNC: Iniciant sincronització de mitjanit")

        // Assegurar que el servei està actiu
        ScheduleExecutorService.startService(context)

        // Sincronitzar schedules del nou dia
        ScheduleExecutorService.syncPrices(context)

        // Programar la propera sincronització a mitjanit
        ActionAlarmScheduler.scheduleMidnightSync(context)
    }
}
