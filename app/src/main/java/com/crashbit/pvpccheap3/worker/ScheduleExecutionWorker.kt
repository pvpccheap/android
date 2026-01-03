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
 * Lògica: Comprova l'hora actual i assegura que el dispositiu
 * està en l'estat correcte (ON si estem en hora barata, OFF si no).
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

    // Guardem l'últim estat enviat per no repetir comandes innecessàriament
    private data class DeviceLastState(val deviceId: String, val isOn: Boolean, val hour: Int)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executant comprovació de schedule...")

        return try {
            // Inicialitzar Google Home si cal
            googleHomeRepository.initialize()

            // Obtenir schedules d'avui
            val scheduleResult = scheduleRepository.getTodaySchedule()

            scheduleResult.fold(
                onSuccess = { actions ->
                    processActions(actions)
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "Error obtenint schedule: ${e.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en worker: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun processActions(actions: List<ScheduledAction>) {
        val currentTime = LocalTime.now()
        val currentHour = currentTime.hour
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        Log.d(TAG, "Processant ${actions.size} accions, hora actual: $currentHour:XX")

        // Agrupar accions per dispositiu per determinar l'estat desitjat
        val deviceActions = actions
            .filter { it.status == "pending" || it.status == "executed_on" || it.status == "executed_off" }
            .groupBy { it.googleDeviceId }

        for ((googleDeviceId, deviceSchedules) in deviceActions) {
            try {
                // Comprovar si l'hora actual està dins d'algun període programat
                val shouldBeOn = deviceSchedules.any { action ->
                    isCurrentHourInSchedule(currentHour, action.startTime, action.endTime, timeFormatter)
                }

                val deviceName = deviceSchedules.firstOrNull()?.deviceName ?: googleDeviceId

                Log.d(TAG, "Dispositiu $deviceName: hora=$currentHour, shouldBeOn=$shouldBeOn")

                // Obtenir estat actual del dispositiu
                val currentState = googleHomeRepository.getDeviceState(googleDeviceId).getOrNull()

                // Només actuar si l'estat és diferent del desitjat
                if (currentState != null && currentState != shouldBeOn) {
                    Log.d(TAG, "Canviant estat de $deviceName: $currentState -> $shouldBeOn")
                    executeAction(deviceSchedules.first(), shouldBeOn)
                } else if (currentState == null) {
                    // Si no sabem l'estat, forçar el canvi
                    Log.d(TAG, "Estat desconegut de $deviceName, forçant a $shouldBeOn")
                    executeAction(deviceSchedules.first(), shouldBeOn)
                } else {
                    Log.d(TAG, "Dispositiu $deviceName ja està en l'estat correcte ($shouldBeOn)")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processant dispositiu $googleDeviceId: ${e.message}")
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

            if (startHour <= endHour) {
                // Rang normal (ex: 10:00 - 14:00 -> hores 10, 11, 12, 13)
                currentHour >= startHour && currentHour < endHour
            } else {
                // Rang que creua mitjanit (ex: 22:00 - 02:00 -> hores 22, 23, 0, 1)
                currentHour >= startHour || currentHour < endHour
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsejant hores: ${e.message}")
            false
        }
    }

    private suspend fun executeAction(action: ScheduledAction, turnOn: Boolean) {
        val result = googleHomeRepository.setDeviceOnOff(action.googleDeviceId, turnOn)

        when (result) {
            is CommandResult.Success -> {
                Log.d(TAG, "Acció executada correctament: ${action.deviceName} -> ${if (turnOn) "ON" else "OFF"}")
                // Actualitzar estat al backend
                val newStatus = if (turnOn) "executed_on" else "executed_off"
                scheduleRepository.updateActionStatus(action.id, newStatus)
            }
            is CommandResult.Error -> {
                Log.e(TAG, "Error executant acció: ${result.message}")
                scheduleRepository.updateActionStatus(action.id, "failed")
            }
        }
    }
}
