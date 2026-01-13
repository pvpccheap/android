package com.crashbit.pvpccheap3.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.crashbit.pvpccheap3.MainActivity
import com.crashbit.pvpccheap3.R
import com.crashbit.pvpccheap3.data.model.CommandResult
import com.crashbit.pvpccheap3.data.model.ScheduledAction
import com.crashbit.pvpccheap3.data.repository.GoogleHomeRepository
import com.crashbit.pvpccheap3.data.repository.ScheduleRepository
import com.crashbit.pvpccheap3.util.FileLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Foreground Service que gestiona l'execució de les accions programades.
 *
 * Aquest servei:
 * - Es manté actiu constantment amb una notificació persistent
 * - Rep alarmes exactes per cada acció programada
 * - Executa les accions de Google Home
 * - Gestiona els estats (executed_on, executed_off, failed, missed)
 */
@AndroidEntryPoint
class ScheduleExecutorService : Service() {

    companion object {
        private const val TAG = "ScheduleExecutorService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "schedule_executor_channel"
        private const val PREFS_NAME = "schedule_executor_prefs"
        private const val KEY_RESTART_COUNT = "restart_count"
        private const val KEY_LAST_RESTART_TIME = "last_restart_time"
        private const val MAX_RESTART_ATTEMPTS = 3
        private const val RESTART_WINDOW_MS = 60000L // 1 minut
        private const val MAX_INIT_RETRIES = 5

        // Actions per controlar el servei
        const val ACTION_START = "com.crashbit.pvpccheap3.action.START"
        const val ACTION_EXECUTE = "com.crashbit.pvpccheap3.action.EXECUTE"
        const val ACTION_SYNC_PRICES = "com.crashbit.pvpccheap3.action.SYNC_PRICES"
        const val ACTION_MARK_MISSED = "com.crashbit.pvpccheap3.action.MARK_MISSED"
        const val ACTION_MARK_EXECUTED_OFF = "com.crashbit.pvpccheap3.action.MARK_EXECUTED_OFF"
        const val ACTION_RETRY = "com.crashbit.pvpccheap3.action.RETRY"

        // Extras
        const val EXTRA_ACTION_ID = "action_id"
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_SHOULD_BE_ON = "should_be_on"
        const val EXTRA_RETRY_COUNT = "retry_count"

        const val MAX_RETRIES = 5

        fun startService(context: Context) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciant servei: ${e.message}")
            }
        }

        fun executeAction(context: Context, actionId: String, deviceId: String, shouldBeOn: Boolean) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_EXECUTE
                putExtra(EXTRA_ACTION_ID, actionId)
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_SHOULD_BE_ON, shouldBeOn)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executant acció: ${e.message}")
            }
        }

        fun syncPrices(context: Context) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_SYNC_PRICES
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sincronitzant: ${e.message}")
            }
        }

        fun markMissedAction(context: Context, actionId: String) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_MARK_MISSED
                putExtra(EXTRA_ACTION_ID, actionId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marcant missed: ${e.message}")
            }
        }

        fun markActionExecutedOff(context: Context, actionId: String) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_MARK_EXECUTED_OFF
                putExtra(EXTRA_ACTION_ID, actionId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marcant executed_off: ${e.message}")
            }
        }

        fun retryAction(context: Context, actionId: String, deviceId: String, shouldBeOn: Boolean, retryCount: Int) {
            val intent = Intent(context, ScheduleExecutorService::class.java).apply {
                action = ACTION_RETRY
                putExtra(EXTRA_ACTION_ID, actionId)
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_SHOULD_BE_ON, shouldBeOn)
                putExtra(EXTRA_RETRY_COUNT, retryCount)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reintentant acció: ${e.message}")
            }
        }
    }

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var googleHomeRepository: GoogleHomeRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Thread-safe initialization flag
    private val isGoogleHomeInitialized = AtomicBoolean(false)
    private val initRetryCount = AtomicInteger(0)

    // Set per evitar alarmes duplicades
    private val scheduledAlarms = mutableSetOf<String>()

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service creat")
        FileLogger.i(TAG, "=== SERVICE CREAT ===")
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        resetRestartCountIfNeeded()

        // Iniciar comprovació periòdica de seguretat
        startSafetyCheck()
    }

    /**
     * Inicia una comprovació periòdica que assegura que els dispositius
     * estan apagats quan no hi ha cap acció activa.
     */
    private fun startSafetyCheck() {
        serviceScope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // Cada 5 minuts
                performSafetyCheck()
            }
        }
    }

    /**
     * Comprova si hi ha dispositius que haurien d'estar apagats però estan encesos.
     */
    private suspend fun performSafetyCheck() {
        try {
            val now = LocalTime.now()
            FileLogger.d(TAG, "SAFETY CHECK a les $now")

            // Obtenir accions d'avui
            val result = scheduleRepository.getTodaySchedule()
            result.fold(
                onSuccess = { actions ->
                    // Per cada dispositiu únic, comprovar si hauria d'estar encès
                    val deviceIds = actions.map { it.googleDeviceId }.distinct()

                    for (deviceId in deviceIds) {
                        val deviceActions = actions.filter { it.googleDeviceId == deviceId }
                        val shouldBeOn = isDeviceSupposedToBeOn(deviceActions, now)

                        FileLogger.d(TAG, "SAFETY: Dispositiu $deviceId shouldBeOn=$shouldBeOn")

                        if (!shouldBeOn) {
                            // Comprovar estat real del dispositiu
                            if (isGoogleHomeInitialized.get()) {
                                googleHomeRepository.refreshDeviceStates()
                                val stateResult = googleHomeRepository.getDeviceState(deviceId)
                                stateResult.fold(
                                    onSuccess = { isOn ->
                                        if (isOn == true) {
                                            FileLogger.w(TAG, "SAFETY: Dispositiu $deviceId està ENCÈS però NO hauria d'estar-ho! APAGANT...")
                                            val cmdResult = googleHomeRepository.setDeviceOnOff(deviceId, false)
                                            if (cmdResult is CommandResult.Success) {
                                                FileLogger.i(TAG, "SAFETY: Dispositiu $deviceId APAGAT correctament")
                                            } else {
                                                FileLogger.e(TAG, "SAFETY: Error apagant dispositiu $deviceId")
                                            }
                                        }
                                    },
                                    onFailure = { e ->
                                        FileLogger.e(TAG, "SAFETY: Error obtenint estat de $deviceId: ${e.message}")
                                    }
                                )
                            }
                        }
                    }
                },
                onFailure = { e ->
                    FileLogger.e(TAG, "SAFETY: Error obtenint schedule: ${e.message}")
                }
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "SAFETY: Excepció: ${e.message}", e)
        }
    }

    /**
     * Determina si un dispositiu hauria d'estar encès basant-se en les seves accions.
     */
    private fun isDeviceSupposedToBeOn(actions: List<ScheduledAction>, now: LocalTime): Boolean {
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        for (action in actions) {
            // Només considerar accions executades o en curs
            if (action.status != "executed_on" && action.status != "pending") continue

            val startTime = try {
                LocalTime.parse(action.startTime, formatter)
            } catch (e: Exception) { continue }

            val endTime = try {
                LocalTime.parse(action.endTime, formatter)
            } catch (e: Exception) { continue }

            // Comprovar si estem dins del rang
            val crossesMidnight = endTime.isBefore(startTime) || endTime == startTime

            val isWithinRange = if (crossesMidnight) {
                now >= startTime || now < endTime
            } else {
                now >= startTime && now < endTime
            }

            if (isWithinRange && action.status == "executed_on") {
                return true
            }
        }

        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        FileLogger.i(TAG, "onStartCommand: action=${intent?.action}")

        // Assegurar-se que estem en foreground
        startForeground(NOTIFICATION_ID, createNotification("Servei actiu"))

        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Servei iniciat")
                initializeGoogleHome()
            }
            ACTION_EXECUTE -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return START_STICKY
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return START_STICKY
                val shouldBeOn = intent.getBooleanExtra(EXTRA_SHOULD_BE_ON, false)
                handleExecuteAction(actionId, deviceId, shouldBeOn)
            }
            ACTION_SYNC_PRICES -> {
                handleSyncPrices()
            }
            ACTION_MARK_MISSED -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return START_STICKY
                handleMarkMissed(actionId)
            }
            ACTION_MARK_EXECUTED_OFF -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return START_STICKY
                handleMarkExecutedOff(actionId)
            }
            ACTION_RETRY -> {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID) ?: return START_STICKY
                val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return START_STICKY
                val shouldBeOn = intent.getBooleanExtra(EXTRA_SHOULD_BE_ON, true)
                val retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 1)
                handleRetryAction(actionId, deviceId, shouldBeOn, retryCount)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destruït")

        // Només reiniciar si no hem superat el límit de restarts
        if (shouldAttemptRestart()) {
            Log.d(TAG, "Intentant reiniciar servei...")
            incrementRestartCount()

            // Usar Handler per reiniciar amb delay (serviceScope ja no és vàlid)
            val restartDelay = getRestartDelay()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    startService(this@ScheduleExecutorService)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reiniciant servei: ${e.message}")
                }
            }, restartDelay)
        } else {
            Log.w(TAG, "Massa intents de restart, no es reinicia automàticament")
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun shouldAttemptRestart(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastRestartTime = prefs.getLong(KEY_LAST_RESTART_TIME, 0)
        val restartCount = prefs.getInt(KEY_RESTART_COUNT, 0)

        // Si ha passat més d'1 minut des de l'últim restart, reiniciem el comptador
        if (currentTime - lastRestartTime > RESTART_WINDOW_MS) {
            return true
        }

        return restartCount < MAX_RESTART_ATTEMPTS
    }

    private fun incrementRestartCount() {
        val currentTime = System.currentTimeMillis()
        val lastRestartTime = prefs.getLong(KEY_LAST_RESTART_TIME, 0)

        val newCount = if (currentTime - lastRestartTime > RESTART_WINDOW_MS) {
            1 // Reiniciem comptador
        } else {
            prefs.getInt(KEY_RESTART_COUNT, 0) + 1
        }

        prefs.edit()
            .putInt(KEY_RESTART_COUNT, newCount)
            .putLong(KEY_LAST_RESTART_TIME, currentTime)
            .apply()
    }

    private fun resetRestartCountIfNeeded() {
        val currentTime = System.currentTimeMillis()
        val lastRestartTime = prefs.getLong(KEY_LAST_RESTART_TIME, 0)

        if (currentTime - lastRestartTime > RESTART_WINDOW_MS) {
            prefs.edit()
                .putInt(KEY_RESTART_COUNT, 0)
                .apply()
        }
    }

    private fun getRestartDelay(): Long {
        val restartCount = prefs.getInt(KEY_RESTART_COUNT, 0)
        // Exponential backoff: 1s, 2s, 4s
        return (1000L * (1 shl restartCount.coerceAtMost(3)))
    }

    private fun initializeGoogleHome() {
        // Thread-safe check
        if (isGoogleHomeInitialized.get()) return
        if (initRetryCount.get() >= MAX_INIT_RETRIES) {
            Log.e(TAG, "Massa intents d'inicialització de Google Home")
            updateNotification("Error: No es pot connectar a Google Home")
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Inicialitzant Google Home SDK (intent ${initRetryCount.get() + 1}/$MAX_INIT_RETRIES)...")
                googleHomeRepository.initialize()

                // Verificar que realment s'ha inicialitzat
                if (googleHomeRepository.isInitialized()) {
                    isGoogleHomeInitialized.set(true)
                    initRetryCount.set(0) // Reset on success
                    Log.d(TAG, "Google Home SDK inicialitzat correctament")
                    updateNotification("Connectat a Google Home")
                } else {
                    throw Exception("SDK no inicialitzat correctament")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inicialitzant Google Home: ${e.message}")
                updateNotification("Error connexió Google Home")

                val currentRetry = initRetryCount.incrementAndGet()
                if (currentRetry < MAX_INIT_RETRIES) {
                    // Exponential backoff: 5s, 10s, 20s, 40s, 80s
                    val delayMs = 5000L * (1 shl (currentRetry - 1))
                    Log.d(TAG, "Reintentant en ${delayMs / 1000}s...")
                    delay(delayMs)
                    initializeGoogleHome()
                } else {
                    Log.e(TAG, "S'han esgotat els intents d'inicialització")
                }
            }
        }
    }

    private fun handleExecuteAction(actionId: String, deviceId: String, shouldBeOn: Boolean) {
        serviceScope.launch {
            // Adquirir wake lock temporal per assegurar execució
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PvpcCheap:ActionExecution"
            )
            wl.acquire(60000) // Màxim 1 minut

            try {
                Log.d(TAG, "Executant acció $actionId: shouldBeOn=$shouldBeOn")
                FileLogger.i(TAG, ">>> EXECUTANT ACCIÓ $actionId: shouldBeOn=$shouldBeOn")
                updateNotification(if (shouldBeOn) "Encenent dispositiu..." else "Apagant dispositiu...")

                // Assegurar que Google Home està inicialitzat
                if (!isGoogleHomeInitialized.get()) {
                    Log.d(TAG, "Google Home no inicialitzat, inicialitzant...")
                    googleHomeRepository.initialize()
                    if (googleHomeRepository.isInitialized()) {
                        isGoogleHomeInitialized.set(true)
                    } else {
                        throw Exception("No s'ha pogut inicialitzar Google Home SDK")
                    }
                }

                // Refrescar estat del dispositiu
                googleHomeRepository.refreshDeviceStates()

                // Executar l'acció
                val result = googleHomeRepository.setDeviceOnOff(deviceId, shouldBeOn)

                when (result) {
                    is CommandResult.Success -> {
                        Log.d(TAG, "Acció $actionId executada correctament")
                        FileLogger.i(TAG, "<<< ACCIÓ $actionId COMPLETADA: ${if (shouldBeOn) "ON" else "OFF"}")
                        val newStatus = if (shouldBeOn) "executed_on" else "executed_off"
                        scheduleRepository.updateActionStatus(actionId, newStatus)
                        updateNotification("Última acció: ${if (shouldBeOn) "ON" else "OFF"} OK")
                    }
                    is CommandResult.Error -> {
                        Log.e(TAG, "Error executant acció $actionId: ${result.message}")
                        FileLogger.e(TAG, "!!! ERROR ACCIÓ $actionId: ${result.message}")
                        scheduleRepository.updateActionStatus(actionId, "failed")
                        updateNotification("Error: ${result.message}")

                        // Programar reintent amb alarma exacta en 2 minuts
                        Log.d(TAG, "Programant reintent #1 en 2 minuts")
                        ActionAlarmScheduler.scheduleRetryAction(
                            this@ScheduleExecutorService,
                            actionId,
                            deviceId,
                            shouldBeOn,
                            delayMinutes = 2,
                            retryCount = 1
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepció executant acció $actionId: ${e.message}", e)
                scheduleRepository.updateActionStatus(actionId, "failed")
                updateNotification("Error: ${e.message}")

                // Programar reintent amb alarma exacta en 2 minuts
                Log.d(TAG, "Programant reintent #1 en 2 minuts")
                ActionAlarmScheduler.scheduleRetryAction(
                    this@ScheduleExecutorService,
                    actionId,
                    deviceId,
                    shouldBeOn,
                    delayMinutes = 2,
                    retryCount = 1
                )
            } finally {
                try {
                    if (wl.isHeld) wl.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error alliberant wake lock: ${e.message}")
                }
            }
        }
    }

    private fun handleSyncPrices() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Sincronitzant preus i schedules...")
                updateNotification("Sincronitzant...")

                // Netejar alarmes programades anteriorment
                scheduledAlarms.clear()

                // Obtenir schedules del backend (amb fallback a cache)
                val result = scheduleRepository.getTodaySchedule()

                result.fold(
                    onSuccess = { actions ->
                        Log.d(TAG, "Sincronitzat: ${actions.size} accions")
                        updateNotification("${actions.size} accions programades")

                        // Programar alarmes per les accions pendents
                        scheduleAlarmsForActions(actions)
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error sincronitzant: ${e.message}")
                        updateNotification("Error sync: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepció sincronitzant: ${e.message}", e)
            }
        }
    }

    private fun handleMarkMissed(actionId: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Marcant acció $actionId com a missed")
                scheduleRepository.updateActionStatus(actionId, "missed")
            } catch (e: Exception) {
                Log.e(TAG, "Error marcant missed: ${e.message}")
            }
        }
    }

    /**
     * Marca una acció com executed_off sense enviar comanda al dispositiu.
     * S'usa quan hi ha una acció consecutiva i no cal apagar/encendre innecessàriament.
     */
    private fun handleMarkExecutedOff(actionId: String) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Marcant acció $actionId com a executed_off (sense comanda - acció consecutiva)")
                scheduleRepository.updateActionStatus(actionId, "executed_off")
            } catch (e: Exception) {
                Log.e(TAG, "Error marcant executed_off: ${e.message}")
            }
        }
    }

    /**
     * Gestiona un reintent d'una acció fallida.
     * Si té èxit, actualitza l'estat a executed_on/executed_off.
     * Si falla i no s'han esgotat els reintents, programa un altre reintent.
     */
    private fun handleRetryAction(actionId: String, deviceId: String, shouldBeOn: Boolean, retryCount: Int) {
        serviceScope.launch {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PvpcCheap:RetryExecution"
            )
            wl.acquire(60000)

            try {
                Log.d(TAG, "RETRY #$retryCount: Executant acció $actionId (shouldBeOn=$shouldBeOn)")
                updateNotification("Reintent #$retryCount: ${if (shouldBeOn) "Encenent" else "Apagant"}...")

                // Assegurar que Google Home està inicialitzat
                if (!isGoogleHomeInitialized.get()) {
                    Log.d(TAG, "Google Home no inicialitzat, inicialitzant...")
                    googleHomeRepository.initialize()
                    if (googleHomeRepository.isInitialized()) {
                        isGoogleHomeInitialized.set(true)
                    } else {
                        throw Exception("No s'ha pogut inicialitzar Google Home SDK")
                    }
                }

                // Refrescar estat del dispositiu
                googleHomeRepository.refreshDeviceStates()

                // Executar l'acció
                val result = googleHomeRepository.setDeviceOnOff(deviceId, shouldBeOn)

                when (result) {
                    is CommandResult.Success -> {
                        Log.d(TAG, "RETRY #$retryCount: Acció $actionId executada correctament!")
                        val newStatus = if (shouldBeOn) "executed_on" else "executed_off"
                        scheduleRepository.updateActionStatus(actionId, newStatus)
                        updateNotification("Reintent #$retryCount: ${if (shouldBeOn) "ON" else "OFF"} OK")
                    }
                    is CommandResult.Error -> {
                        Log.e(TAG, "RETRY #$retryCount: Error executant acció $actionId: ${result.message}")

                        if (retryCount < MAX_RETRIES) {
                            // Programar següent reintent amb alarma exacta (2 minuts)
                            Log.d(TAG, "Programant reintent #${retryCount + 1} en 2 minuts")
                            ActionAlarmScheduler.scheduleRetryAction(
                                this@ScheduleExecutorService,
                                actionId,
                                deviceId,
                                shouldBeOn,
                                delayMinutes = 2,
                                retryCount = retryCount + 1
                            )
                            updateNotification("Reintent #$retryCount fallit, proper en 2 min")
                        } else {
                            // S'han esgotat els reintents
                            Log.e(TAG, "RETRY: S'han esgotat els $MAX_RETRIES reintents per $actionId")
                            scheduleRepository.updateActionStatus(actionId, "failed")
                            updateNotification("Acció fallida després de $MAX_RETRIES reintents")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RETRY #$retryCount: Excepció: ${e.message}", e)

                if (retryCount < MAX_RETRIES) {
                    Log.d(TAG, "Programant reintent #${retryCount + 1} en 2 minuts")
                    ActionAlarmScheduler.scheduleRetryAction(
                        this@ScheduleExecutorService,
                        actionId,
                        deviceId,
                        shouldBeOn,
                        delayMinutes = 2,
                        retryCount = retryCount + 1
                    )
                    updateNotification("Error, proper reintent en 2 min")
                } else {
                    scheduleRepository.updateActionStatus(actionId, "failed")
                    updateNotification("Acció fallida després de $MAX_RETRIES reintents")
                }
            } finally {
                try {
                    if (wl.isHeld) wl.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error alliberant wake lock: ${e.message}")
                }
            }
        }
    }

    private fun scheduleAlarmsForActions(actions: List<ScheduledAction>) {
        val pendingActions = actions.filter { it.status == "pending" }
        val now = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        for (action in pendingActions) {
            // Evitar duplicats
            if (scheduledAlarms.contains(action.id)) {
                Log.d(TAG, "Alarma ja programada per ${action.id}, saltant")
                continue
            }

            try {
                // Validar i parsejar temps amb try-catch
                val startTime = parseTimeSafely(action.startTime, formatter)
                val endTime = parseTimeSafely(action.endTime, formatter)

                if (startTime == null || endTime == null) {
                    Log.e(TAG, "Temps invàlids per acció ${action.id}: start=${action.startTime}, end=${action.endTime}")
                    continue
                }

                // Detectar si creua mitjanit
                val crossesMidnight = endTime.isBefore(startTime) || endTime == startTime

                // Comprovar si estem DINS del rang d'aquesta acció (catch-up)
                val isWithinRange = if (crossesMidnight) {
                    // Rang que creua mitjanit (ex: 23:00-01:00)
                    // Estem dins si now >= startTime O now < endTime
                    now >= startTime || now < endTime
                } else {
                    // Rang normal (ex: 02:00-03:00)
                    now >= startTime && now < endTime
                }

                if (isWithinRange) {
                    // CATCH-UP: L'acció hauria d'estar activa PERÒ està pending
                    // Executar immediatament!
                    Log.i(TAG, "CATCH-UP: Acció ${action.id} hauria d'estar activa (${action.startTime}-${action.endTime}), executant ara!")
                    handleExecuteAction(action.id, action.googleDeviceId, shouldBeOn = true)

                    // Programar alarma de fi
                    if (crossesMidnight) {
                        ActionAlarmScheduler.scheduleEndAction(
                            this,
                            action.id,
                            action.googleDeviceId,
                            endTime,
                            tomorrowIfNeeded = true
                        )
                    } else {
                        ActionAlarmScheduler.scheduleEndAction(
                            this,
                            action.id,
                            action.googleDeviceId,
                            endTime,
                            tomorrowIfNeeded = false
                        )
                    }
                    Log.d(TAG, "Alarma END programada per ${action.id} a les $endTime")

                } else if (startTime.isAfter(now)) {
                    // L'acció encara no ha començat - programar alarmes normals
                    ActionAlarmScheduler.scheduleStartAction(
                        this,
                        action.id,
                        action.googleDeviceId,
                        startTime
                    )
                    Log.d(TAG, "Alarma START programada per ${action.id} a les $startTime")

                    // Programar alarma de fi
                    if (crossesMidnight) {
                        ActionAlarmScheduler.scheduleEndAction(
                            this,
                            action.id,
                            action.googleDeviceId,
                            endTime,
                            tomorrowIfNeeded = true
                        )
                        Log.d(TAG, "Alarma END programada per ${action.id} a les $endTime (DEMÀ)")
                    } else {
                        ActionAlarmScheduler.scheduleEndAction(
                            this,
                            action.id,
                            action.googleDeviceId,
                            endTime,
                            tomorrowIfNeeded = false
                        )
                        Log.d(TAG, "Alarma END programada per ${action.id} a les $endTime")
                    }

                } else if (endTime.isBefore(now) || endTime == now) {
                    // L'acció ja ha passat completament - marcar com missed
                    if (!crossesMidnight) {
                        Log.w(TAG, "Acció ${action.id} ja ha passat (${action.startTime}-${action.endTime}), marcant com missed")
                        handleMarkMissed(action.id)
                    }
                }

                scheduledAlarms.add(action.id)

            } catch (e: Exception) {
                Log.e(TAG, "Error programant alarma per ${action.id}: ${e.message}")
            }
        }
    }

    private fun parseTimeSafely(timeStr: String?, formatter: DateTimeFormatter): LocalTime? {
        if (timeStr.isNullOrBlank()) return null
        return try {
            LocalTime.parse(timeStr, formatter)
        } catch (e: DateTimeParseException) {
            Log.e(TAG, "Error parsejant temps '$timeStr': ${e.message}")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Control de dispositius",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificació del servei de control automàtic de dispositius"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PVPC Cheap")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Error actualitzant notificació: ${e.message}")
        }
    }
}
