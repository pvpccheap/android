package com.crashbit.pvpccheap3.data.repository

import android.util.Log
import com.crashbit.pvpccheap3.data.api.PvpcApi
import com.crashbit.pvpccheap3.data.local.ScheduleCache
import com.crashbit.pvpccheap3.data.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per gestionar les programacions.
 * Utilitza cache local per funcionar encara que el backend estigui caigut.
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val api: PvpcApi,
    private val scheduleCache: ScheduleCache
) {
    companion object {
        private const val TAG = "ScheduleRepository"
    }

    /**
     * Obté les programacions d'avui.
     * Estratègia: Intent del backend -> Si falla, utilitza cache local.
     */
    suspend fun getTodaySchedule(): Result<List<ScheduledAction>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        return try {
            // Intentar obtenir del backend
            Log.d(TAG, "Obtenint schedule del backend...")
            val actions = api.getTodaySchedule()

            // Guardar al cache local
            scheduleCache.saveSchedule(today, actions)
            Log.d(TAG, "Schedule obtingut del backend: ${actions.size} accions")

            Result.success(actions)
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint schedule del backend: ${e.message}")

            // Fallback: utilitzar cache local
            val cachedActions = scheduleCache.getTodaySchedule()
            if (cachedActions != null) {
                Log.d(TAG, "Utilitzant cache local: ${cachedActions.size} accions")
                Result.success(cachedActions)
            } else {
                Log.e(TAG, "No hi ha cache disponible")
                Result.failure(e)
            }
        }
    }

    /**
     * Obté les programacions per una data específica.
     */
    suspend fun getScheduleByDate(date: String): Result<List<ScheduledAction>> {
        return try {
            val actions = api.getScheduleByDate(date)
            // Guardar al cache si és avui
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (date == today) {
                scheduleCache.saveSchedule(date, actions)
            }
            Result.success(actions)
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint schedule per $date: ${e.message}")

            // Fallback per la data d'avui
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (date == today) {
                val cachedActions = scheduleCache.getTodaySchedule()
                if (cachedActions != null) {
                    Log.d(TAG, "Utilitzant cache local per $date")
                    return Result.success(cachedActions)
                }
            }
            Result.failure(e)
        }
    }

    /**
     * Calcula les hores òptimes per una regla.
     */
    suspend fun calculateOptimalHours(ruleId: String, date: String? = null): Result<OptimalHoursResponse> {
        return try {
            Result.success(api.calculateOptimalHours(CalculateScheduleRequest(ruleId, date)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Actualitza l'estat d'una acció.
     * Sempre actualitza el cache local, i intenta sincronitzar amb el backend amb retry.
     */
    suspend fun updateActionStatus(actionId: String, status: String): Result<Unit> {
        // Primer actualitzem el cache local (sempre funciona)
        scheduleCache.updateActionStatus(actionId, status)

        // Intentar sincronitzar amb el backend amb retry automàtic
        var lastException: Exception? = null
        val maxRetries = 3

        repeat(maxRetries) { attempt ->
            try {
                api.updateActionStatus(actionId, UpdateActionStatusRequest(status))
                Log.d(TAG, "Estat sincronitzat amb backend: $actionId -> $status")
                return Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Intent ${attempt + 1}/$maxRetries fallit per $actionId: ${e.message}")
                if (attempt < maxRetries - 1) {
                    // Esperar abans del següent intent (backoff exponencial)
                    kotlinx.coroutines.delay(500L * (attempt + 1))
                }
            }
        }

        // Si tots els intents fallen, loguejar l'error però retornar success
        // perquè el cache local està actualitzat
        Log.e(TAG, "No s'ha pogut sincronitzar $actionId amb backend després de $maxRetries intents: ${lastException?.message}")
        // Guardar per sincronització posterior (quan hi hagi connexió)
        scheduleCache.markPendingSync(actionId, status)
        return Result.success(Unit)
    }

    /**
     * Actualitza l'estat d'una acció amb retry.
     * Útil per tasques crítiques que necessiten sincronitzar amb el backend.
     */
    suspend fun updateActionStatusWithRetry(
        actionId: String,
        status: String,
        maxRetries: Int = 3
    ): Result<Unit> {
        // Primer actualitzem el cache local
        scheduleCache.updateActionStatus(actionId, status)

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                api.updateActionStatus(actionId, UpdateActionStatusRequest(status))
                Log.d(TAG, "Estat sincronitzat amb backend (intent ${attempt + 1}): $actionId -> $status")
                return Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Retry ${attempt + 1}/$maxRetries fallit: ${e.message}")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }

        Log.e(TAG, "No s'ha pogut sincronitzar després de $maxRetries intents")
        // Retornem success perquè el cache local està actualitzat
        return Result.success(Unit)
    }

    /**
     * Força una actualització del cache des del backend.
     */
    suspend fun refreshCache(): Result<Unit> {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val actions = api.getTodaySchedule()
            scheduleCache.saveSchedule(today, actions)
            Log.d(TAG, "Cache refrescat: ${actions.size} accions")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescant cache: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Comprova si hi ha cache disponible.
     */
    suspend fun hasCachedSchedule(): Boolean {
        return scheduleCache.hasTodayCache()
    }
}
