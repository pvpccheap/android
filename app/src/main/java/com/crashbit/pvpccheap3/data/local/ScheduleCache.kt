package com.crashbit.pvpccheap3.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crashbit.pvpccheap3.data.model.ScheduledAction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_cache")

/**
 * Cache local per guardar les programacions.
 * Permet executar les accions encara que el backend estigui caigut.
 */
@Singleton
class ScheduleCache @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScheduleCache"
        private val SCHEDULE_DATA_KEY = stringPreferencesKey("schedule_data")
        private val SCHEDULE_DATE_KEY = stringPreferencesKey("schedule_date")
        private val LAST_UPDATE_KEY = longPreferencesKey("last_update")
        private val PENDING_SYNC_KEY = stringPreferencesKey("pending_sync")

        // Màxim temps que la cache és vàlida (24 hores)
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L
    }

    private val gson = Gson()

    /**
     * Guarda les programacions al cache local.
     */
    suspend fun saveSchedule(date: String, actions: List<ScheduledAction>) {
        try {
            val json = gson.toJson(actions)
            context.scheduleDataStore.edit { prefs ->
                prefs[SCHEDULE_DATA_KEY] = json
                prefs[SCHEDULE_DATE_KEY] = date
                prefs[LAST_UPDATE_KEY] = System.currentTimeMillis()
            }
            Log.d(TAG, "Schedule guardat al cache: ${actions.size} accions per $date")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardant schedule al cache: ${e.message}")
        }
    }

    /**
     * Obté les programacions del cache local.
     * Retorna null si no hi ha cache o si és massa antiga.
     */
    suspend fun getSchedule(date: String): List<ScheduledAction>? {
        return try {
            val prefs = context.scheduleDataStore.data.first()
            val cachedDate = prefs[SCHEDULE_DATE_KEY]
            val lastUpdate = prefs[LAST_UPDATE_KEY] ?: 0L
            val json = prefs[SCHEDULE_DATA_KEY]

            // Comprovar si la cache és vàlida
            if (cachedDate != date) {
                Log.d(TAG, "Cache per data diferent: cached=$cachedDate, requested=$date")
                return null
            }

            if (System.currentTimeMillis() - lastUpdate > CACHE_VALIDITY_MS) {
                Log.d(TAG, "Cache expirada")
                return null
            }

            if (json.isNullOrEmpty()) {
                Log.d(TAG, "Cache buida")
                return null
            }

            val type = object : TypeToken<List<ScheduledAction>>() {}.type
            val actions: List<ScheduledAction> = gson.fromJson(json, type)
            Log.d(TAG, "Schedule recuperat del cache: ${actions.size} accions")
            actions
        } catch (e: Exception) {
            Log.e(TAG, "Error llegint cache: ${e.message}")
            null
        }
    }

    /**
     * Obté les programacions d'avui del cache.
     */
    suspend fun getTodaySchedule(): List<ScheduledAction>? {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return getSchedule(today)
    }

    /**
     * Actualitza l'estat d'una acció al cache local.
     */
    suspend fun updateActionStatus(actionId: String, newStatus: String) {
        try {
            val prefs = context.scheduleDataStore.data.first()
            val json = prefs[SCHEDULE_DATA_KEY] ?: return
            val cachedDate = prefs[SCHEDULE_DATE_KEY] ?: return

            val type = object : TypeToken<List<ScheduledAction>>() {}.type
            val actions: MutableList<ScheduledAction> = gson.fromJson(json, type)

            // Actualitzar l'acció
            val updatedActions = actions.map { action ->
                if (action.id == actionId) {
                    action.copy(status = newStatus)
                } else {
                    action
                }
            }

            // Guardar de nou
            val updatedJson = gson.toJson(updatedActions)
            context.scheduleDataStore.edit { prefs ->
                prefs[SCHEDULE_DATA_KEY] = updatedJson
            }
            Log.d(TAG, "Estat actualitzat al cache: $actionId -> $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "Error actualitzant estat al cache: ${e.message}")
        }
    }

    /**
     * Comprova si hi ha cache vàlida per avui.
     */
    suspend fun hasTodayCache(): Boolean {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return getSchedule(today) != null
    }

    /**
     * Neteja el cache.
     */
    suspend fun clearCache() {
        context.scheduleDataStore.edit { prefs ->
            prefs.clear()
        }
        Log.d(TAG, "Cache netejat")
    }

    /**
     * Obté l'última vegada que es va actualitzar el cache.
     */
    suspend fun getLastUpdateTime(): Long {
        return try {
            context.scheduleDataStore.data.first()[LAST_UPDATE_KEY] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Marca una acció com pendent de sincronització amb el backend.
     * Útil per reintentar la sincronització quan hi hagi connexió.
     */
    suspend fun markPendingSync(actionId: String, status: String) {
        try {
            val prefs = context.scheduleDataStore.data.first()
            val existingJson = prefs[PENDING_SYNC_KEY] ?: "{}"

            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val pendingMap: MutableMap<String, String> = try {
                gson.fromJson(existingJson, type) ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }

            pendingMap[actionId] = status

            context.scheduleDataStore.edit { prefs ->
                prefs[PENDING_SYNC_KEY] = gson.toJson(pendingMap)
            }
            Log.d(TAG, "Acció marcada per sincronització pendent: $actionId -> $status")
        } catch (e: Exception) {
            Log.e(TAG, "Error marcant sincronització pendent: ${e.message}")
        }
    }

    /**
     * Obté les accions pendents de sincronització.
     */
    suspend fun getPendingSyncs(): Map<String, String> {
        return try {
            val prefs = context.scheduleDataStore.data.first()
            val json = prefs[PENDING_SYNC_KEY] ?: return emptyMap()

            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint sincronitzacions pendents: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Neteja una acció de la llista de pendents de sincronització.
     */
    suspend fun clearPendingSync(actionId: String) {
        try {
            val prefs = context.scheduleDataStore.data.first()
            val existingJson = prefs[PENDING_SYNC_KEY] ?: return

            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            val pendingMap: MutableMap<String, String> = gson.fromJson(existingJson, type) ?: return

            pendingMap.remove(actionId)

            context.scheduleDataStore.edit { prefs ->
                prefs[PENDING_SYNC_KEY] = gson.toJson(pendingMap)
            }
            Log.d(TAG, "Acció eliminada de pendents: $actionId")
        } catch (e: Exception) {
            Log.e(TAG, "Error netejant sincronització pendent: ${e.message}")
        }
    }
}
