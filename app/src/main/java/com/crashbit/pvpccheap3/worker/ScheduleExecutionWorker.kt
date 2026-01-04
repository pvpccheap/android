package com.crashbit.pvpccheap3.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crashbit.pvpccheap3.data.model.CommandResult
import com.crashbit.pvpccheap3.data.model.ScheduledAction
import com.crashbit.pvpccheap3.data.repository.GoogleHomeRepository
import com.crashbit.pvpccheap3.data.repository.ScheduleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Worker que s'executa periòdicament per comprovar i executar
 * les accions programades segons l'horari.
 *
 * MILLORES DE RESILIÈNCIA:
 * - Utilitza cache local si el backend falla
 * - Lògica de catch-up: processa accions pendents i fallides
 * - Refresh d'estat real dels dispositius abans d'actuar
 * - Retry automàtic per accions que fallen
 */
@HiltWorker
class ScheduleExecutionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleRepository: ScheduleRepository,
    private val googleHomeRepository: GoogleHomeRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScheduleExecutionWorker"
        const val WORK_NAME = "schedule_execution_work"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== INICI EXECUCIÓ WORKER ===")
        Log.d(TAG, "Hora actual: ${LocalTime.now()}")

        return try {
            // 1. Inicialitzar Google Home
            Log.d(TAG, "Inicialitzant Google Home SDK...")
            googleHomeRepository.initialize()

            // 2. IMPORTANT: Refrescar estats reals dels dispositius
            Log.d(TAG, "Refrescant estats reals dels dispositius...")
            googleHomeRepository.refreshDeviceStates()

            // 3. Obtenir schedules (amb fallback a cache local)
            val scheduleResult = scheduleRepository.getTodaySchedule()

            scheduleResult.fold(
                onSuccess = { actions ->
                    Log.d(TAG, "Processant ${actions.size} accions...")
                    processActionsWithCatchUp(actions)
                    Log.d(TAG, "=== FI EXECUCIÓ WORKER (SUCCESS) ===")
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error obtenint schedule: ${e.message}")
                    Log.d(TAG, "=== FI EXECUCIÓ WORKER (RETRY) ===")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en worker: ${e.message}", e)
            Log.d(TAG, "=== FI EXECUCIÓ WORKER (RETRY) ===")
            Result.retry()
        }
    }

    /**
     * Processa les accions amb lògica de catch-up.
     * Això significa que:
     * - Marca com "failed" les accions passades que no es van executar
     * - Processa accions "pending" que haurien d'estar actives ARA
     * - Reintenta accions "failed" que encara són rellevants (hora actual)
     */
    private suspend fun processActionsWithCatchUp(actions: List<ScheduledAction>) {
        val currentTime = LocalTime.now()
        val currentHour = currentTime.hour
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        Log.d(TAG, "Hora actual: $currentHour:${currentTime.minute}")

        // PRIMER: Marcar com "failed" les accions passades que no es van executar
        markMissedActionsAsFailed(actions, currentHour, timeFormatter)

        // Filtrar accions processables (pending, executed_on, executed_off, failed)
        val processableActions = actions.filter { action ->
            action.status in listOf("pending", "executed_on", "executed_off", "failed")
        }

        Log.d(TAG, "Accions processables: ${processableActions.size}")

        // Agrupar per dispositiu
        val deviceActions = processableActions.groupBy { it.googleDeviceId }

        for ((googleDeviceId, deviceSchedules) in deviceActions) {
            try {
                processDeviceSchedules(
                    googleDeviceId,
                    deviceSchedules,
                    currentHour,
                    timeFormatter
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error processant dispositiu $googleDeviceId: ${e.message}")
            }
        }
    }

    /**
     * Marca com "failed" les accions que ja han passat però encara estan "pending".
     * Una acció ha passat si l'hora actual és >= endHour.
     */
    private suspend fun markMissedActionsAsFailed(
        actions: List<ScheduledAction>,
        currentHour: Int,
        formatter: DateTimeFormatter
    ) {
        val pendingActions = actions.filter { it.status == "pending" }

        for (action in pendingActions) {
            try {
                val endTime = LocalTime.parse(action.endTime, formatter)
                val endHour = endTime.hour

                // L'acció ha passat si l'hora actual és >= endHour
                // (per accions que no creuen mitjanit)
                val startTime = LocalTime.parse(action.startTime, formatter)
                val startHour = startTime.hour

                val hasPassed = if (startHour <= endHour) {
                    // Rang normal (ex: 10:00-14:00)
                    currentHour >= endHour
                } else {
                    // Rang que creua mitjanit (ex: 22:00-02:00)
                    // Ha passat si estem entre endHour i startHour
                    currentHour >= endHour && currentHour < startHour
                }

                if (hasPassed) {
                    Log.d(TAG, "[${action.deviceName}] Acció ${action.startTime}-${action.endTime} ha passat sense executar-se, marcant com failed")
                    scheduleRepository.updateActionStatus(action.id, "failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error comprovant si acció ha passat: ${e.message}")
            }
        }
    }

    /**
     * Processa les programacions d'un dispositiu específic.
     */
    private suspend fun processDeviceSchedules(
        googleDeviceId: String,
        deviceSchedules: List<ScheduledAction>,
        currentHour: Int,
        timeFormatter: DateTimeFormatter
    ) {
        val deviceName = deviceSchedules.firstOrNull()?.deviceName ?: googleDeviceId

        // Determinar si el dispositiu hauria d'estar ON basant-se en TOTES les programacions
        val shouldBeOn = deviceSchedules.any { action ->
            isCurrentHourInSchedule(currentHour, action.startTime, action.endTime, timeFormatter)
        }

        Log.d(TAG, "[$deviceName] shouldBeOn=$shouldBeOn (hora=$currentHour)")

        // Obtenir estat REAL del dispositiu (no cache)
        val currentState = googleHomeRepository.getDeviceState(googleDeviceId).getOrNull()
        Log.d(TAG, "[$deviceName] estat actual=$currentState")

        // Trobar l'acció activa actual (si n'hi ha)
        val activeAction = deviceSchedules.find { action ->
            isCurrentHourInSchedule(currentHour, action.startTime, action.endTime, timeFormatter)
        }

        // Trobar accions fallides que encara són rellevants
        val failedActions = deviceSchedules.filter { action ->
            action.status == "failed" &&
            isCurrentHourInSchedule(currentHour, action.startTime, action.endTime, timeFormatter)
        }

        // Decidir si cal actuar
        when {
            // Cas 1: L'estat actual no coincideix amb el desitjat
            currentState != null && currentState != shouldBeOn -> {
                Log.d(TAG, "[$deviceName] Canviant estat: $currentState -> $shouldBeOn")
                val actionToUse = activeAction ?: failedActions.firstOrNull() ?: deviceSchedules.first()
                executeAction(actionToUse, shouldBeOn)
            }

            // Cas 2: No sabem l'estat actual - forçar el canvi
            currentState == null -> {
                Log.d(TAG, "[$deviceName] Estat desconegut, forçant a $shouldBeOn")
                val actionToUse = activeAction ?: failedActions.firstOrNull() ?: deviceSchedules.first()
                executeAction(actionToUse, shouldBeOn)
            }

            // Cas 3: Hi ha accions fallides que reintentar
            failedActions.isNotEmpty() && currentState == shouldBeOn -> {
                Log.d(TAG, "[$deviceName] Estat correcte però hi ha accions fallides, actualitzant estat...")
                failedActions.forEach { action ->
                    val newStatus = if (shouldBeOn) "executed_on" else "executed_off"
                    scheduleRepository.updateActionStatus(action.id, newStatus)
                }
            }

            // Cas 4: Tot correcte
            else -> {
                Log.d(TAG, "[$deviceName] Estat correcte ($shouldBeOn), no cal actuar")

                // Actualitzar accions pending a executed si l'estat és correcte
                activeAction?.let { action ->
                    if (action.status == "pending") {
                        val newStatus = if (shouldBeOn) "executed_on" else "executed_off"
                        scheduleRepository.updateActionStatus(action.id, newStatus)
                    }
                }
            }
        }
    }

    /**
     * Comprova si l'hora actual (0-23) està dins del període programat.
     * Ex: startTime="10:00:00", endTime="14:00:00" -> hores 10, 11, 12, 13 retornen true
     */
    private fun isCurrentHourInSchedule(
        currentHour: Int,
        startTimeStr: String,
        endTimeStr: String,
        formatter: DateTimeFormatter
    ): Boolean {
        return try {
            val startTime = LocalTime.parse(startTimeStr, formatter)
            val endTime = LocalTime.parse(endTimeStr, formatter)

            val startHour = startTime.hour
            val endHour = endTime.hour

            val result = if (startHour <= endHour) {
                // Rang normal (ex: 10:00 - 14:00 -> hores 10, 11, 12, 13)
                currentHour >= startHour && currentHour < endHour
            } else {
                // Rang que creua mitjanit (ex: 22:00 - 02:00 -> hores 22, 23, 0, 1)
                currentHour >= startHour || currentHour < endHour
            }

            Log.d(TAG, "isCurrentHourInSchedule: hora=$currentHour, rang=$startHour-$endHour, result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsejant hores: ${e.message}")
            false
        }
    }

    /**
     * Executa una acció (encendre o apagar dispositiu).
     */
    private suspend fun executeAction(action: ScheduledAction, turnOn: Boolean) {
        Log.d(TAG, "[${action.deviceName}] Executant: ${if (turnOn) "ON" else "OFF"}")

        val result = googleHomeRepository.setDeviceOnOff(action.googleDeviceId, turnOn)

        when (result) {
            is CommandResult.Success -> {
                Log.d(TAG, "[${action.deviceName}] Acció executada correctament")
                val newStatus = if (turnOn) "executed_on" else "executed_off"
                scheduleRepository.updateActionStatus(action.id, newStatus)
            }
            is CommandResult.Error -> {
                Log.e(TAG, "[${action.deviceName}] Error: ${result.message}")
                scheduleRepository.updateActionStatus(action.id, "failed")
            }
        }
    }
}
