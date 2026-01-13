package com.crashbit.pvpccheap3.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import com.crashbit.pvpccheap3.data.model.*
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeException
import com.google.home.FactoryRegistry
import com.google.home.PermissionsState
import com.google.home.Structure
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.LevelControl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per gestionar la comunicació amb Google Home APIs SDK.
 */
@Singleton
class GoogleHomeRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleHomeRepository"
    }

    private var homeClient: HomeClient? = null

    private val _authState = MutableStateFlow(GoogleHomeAuthState.NOT_INITIALIZED)
    val authState: Flow<GoogleHomeAuthState> = _authState.asStateFlow()

    private val _isAuthorized = MutableStateFlow(false)

    /**
     * Retorna si el repositori està inicialitzat i autoritzat.
     */
    fun isInitialized(): Boolean = homeClient != null && _isAuthorized.value

    // Cache simple per dispositius
    private data class CachedDevice(
        val id: String,
        var isOn: Boolean
    )
    private val deviceCache = mutableMapOf<String, CachedDevice>()

    // Flow per emetre canvis d'estat dels dispositius
    private val _deviceStateChanges = MutableSharedFlow<DeviceStateChange>(replay = 0)
    val deviceStateChanges: Flow<DeviceStateChange> = _deviceStateChanges.asSharedFlow()

    // Job per gestionar l'observació
    private var observationJob: Job? = null

    // Referència a l'Activity per permisos
    private var activityRef: ComponentActivity? = null

    /**
     * Inicialitza el client de Google Home.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _authState.value = GoogleHomeAuthState.CHECKING
            Log.d(TAG, "Inicialitzant Google Home SDK...")

            // Crear FactoryRegistry amb els tipus suportats
            val registry = FactoryRegistry(
                types = listOf(
                    OnOffLightDevice,
                    OnOffPluginUnitDevice,
                    DimmableLightDevice
                ),
                traits = listOf(
                    OnOff,
                    LevelControl
                )
            )

            // Crear configuració
            val config = HomeConfig(
                coroutineContext = Dispatchers.IO,
                factoryRegistry = registry
            )

            // Obtenir client
            homeClient = Home.getClient(context, config)
            Log.d(TAG, "HomeClient creat correctament")

            // Comprovar si ja tenim permisos vàlids
            checkExistingPermissions()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error inicialitzant SDK: ${e.message}", e)
            _authState.value = GoogleHomeAuthState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Comprova si ja tenim permisos vàlids de sessions anteriors.
     * Utilitza hasPermissions() que és l'API correcta segons la documentació.
     */
    private suspend fun checkExistingPermissions() {
        val client = homeClient ?: return

        Log.d(TAG, "Comprovant permisos existents amb hasPermissions()...")

        try {
            // Utilitzar hasPermissions() que és l'API correcta
            val permissionsState = withTimeoutOrNull(5000L) {
                client.hasPermissions().first()
            }

            Log.d(TAG, "PermissionsState: $permissionsState")

            when (permissionsState) {
                PermissionsState.GRANTED -> {
                    Log.d(TAG, "Permisos concedits!")
                    _isAuthorized.value = true
                    _authState.value = GoogleHomeAuthState.AUTHORIZED
                }
                PermissionsState.NOT_GRANTED -> {
                    Log.d(TAG, "Permisos no concedits, cal sol·licitar-los")
                    _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
                }
                PermissionsState.PERMISSIONS_STATE_UNAVAILABLE -> {
                    Log.d(TAG, "Estat de permisos no disponible, cal sol·licitar-los")
                    _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
                }
                PermissionsState.PERMISSIONS_STATE_UNINITIALIZED -> {
                    Log.d(TAG, "Permisos encara inicialitzant-se, reintentant...")
                    // Esperar i reintentar
                    delay(2000)
                    checkExistingPermissionsWithRetry(retries = 2)
                }
                null -> {
                    Log.d(TAG, "Timeout comprovant permisos")
                    // Intentar amb estructures com a fallback
                    checkPermissionsViaStructures()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comprovant permisos: ${e.message}", e)
            // Fallback: intentar amb estructures
            checkPermissionsViaStructures()
        }
    }

    /**
     * Reintenta comprovar permisos amb un nombre limitat de reintents.
     */
    private suspend fun checkExistingPermissionsWithRetry(retries: Int) {
        if (retries <= 0) {
            Log.d(TAG, "Màxim de reintents assolit")
            _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
            return
        }

        val client = homeClient ?: return

        try {
            val permissionsState = withTimeoutOrNull(5000L) {
                client.hasPermissions().first()
            }

            when (permissionsState) {
                PermissionsState.GRANTED -> {
                    Log.d(TAG, "Permisos concedits després de retry!")
                    _isAuthorized.value = true
                    _authState.value = GoogleHomeAuthState.AUTHORIZED
                }
                PermissionsState.PERMISSIONS_STATE_UNINITIALIZED -> {
                    Log.d(TAG, "Encara inicialitzant, reintentant... (${retries - 1} restants)")
                    delay(2000)
                    checkExistingPermissionsWithRetry(retries - 1)
                }
                else -> {
                    Log.d(TAG, "Permisos no disponibles: $permissionsState")
                    _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en retry: ${e.message}")
            _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
        }
    }

    /**
     * Fallback: comprova permisos intentant accedir a estructures.
     */
    private suspend fun checkPermissionsViaStructures() {
        val client = homeClient ?: return

        Log.d(TAG, "Fallback: comprovant permisos via estructures...")

        try {
            // Timeout més llarg per donar temps al SDK
            val structures = withTimeoutOrNull(8000L) {
                client.structures().first { it.isNotEmpty() }
            }

            if (structures != null) {
                Log.d(TAG, "Estructures trobades via fallback: ${structures.size}")
                _isAuthorized.value = true
                _authState.value = GoogleHomeAuthState.AUTHORIZED
            } else {
                Log.d(TAG, "No s'han trobat estructures")
                _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fallback fallit: ${e.message}")
            _authState.value = GoogleHomeAuthState.NOT_AUTHORIZED
        }
    }

    /**
     * Registra l'Activity per gestionar permisos.
     */
    fun registerActivity(activity: ComponentActivity) {
        activityRef = activity
        try {
            homeClient?.registerActivityResultCallerForPermissions(activity)
            Log.d(TAG, "Activity registrada per permisos")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrant Activity: ${e.message}")
        }
    }

    /**
     * Sol·licita permisos utilitzant el SDK.
     */
    suspend fun requestPermissionsAsync(forceLaunch: Boolean = false): HomeAuthResult = withContext(Dispatchers.Main) {
        try {
            val client = homeClient ?: run {
                initialize()
                homeClient ?: return@withContext HomeAuthResult.Error("No s'ha pogut inicialitzar el client")
            }

            Log.d(TAG, "Sol·licitant permisos (forceLaunch=$forceLaunch)...")
            val result = client.requestPermissions(forceLaunch = forceLaunch)

            when (result.status) {
                com.google.home.PermissionsResultStatus.SUCCESS -> {
                    Log.d(TAG, "Permisos concedits!")
                    _isAuthorized.value = true
                    _authState.value = GoogleHomeAuthState.AUTHORIZED
                    HomeAuthResult.Success
                }
                com.google.home.PermissionsResultStatus.CANCELLED -> {
                    Log.d(TAG, "Permisos cancel·lats per l'usuari")
                    HomeAuthResult.Cancelled
                }
                else -> {
                    Log.e(TAG, "Error de permisos: ${result.errorMessage}")
                    HomeAuthResult.Error(result.errorMessage ?: "Error desconegut")
                }
            }
        } catch (e: HomeException) {
            Log.e(TAG, "HomeException: ${e.message}", e)
            HomeAuthResult.Error(e.message ?: "Error de Google Home")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            HomeAuthResult.Error(e.message ?: "Error desconegut")
        }
    }

    /**
     * Simula autorització per a proves.
     */
    suspend fun simulateAuthorization(): HomeAuthResult {
        return withContext(Dispatchers.IO) {
            try {
                if (homeClient == null) {
                    initialize()
                }
                delay(500)
                _isAuthorized.value = true
                _authState.value = GoogleHomeAuthState.AUTHORIZED
                HomeAuthResult.Success
            } catch (e: Exception) {
                HomeAuthResult.Error(e.message ?: "Error d'autorització")
            }
        }
    }

    /**
     * Obté tots els dispositius de Google Home.
     */
    suspend fun getDevices(): Result<List<GoogleHomeDevice>> = withContext(Dispatchers.IO) {
        try {
            val client = homeClient ?: return@withContext Result.failure(Exception("Client no inicialitzat"))

            if (!_isAuthorized.value) {
                return@withContext Result.failure(Exception("No autoritzat"))
            }

            Log.d(TAG, "Obtenint dispositius del SDK...")
            val devices = mutableListOf<GoogleHomeDevice>()

            // Esperar un moment inicial perquè el SDK sincronitzi
            Log.d(TAG, "Esperant sincronització inicial del SDK...")
            delay(2000)

            // Primer intentar amb subscripció Flow (més fiable)
            var structures: Set<Structure> = emptySet()

            try {
                Log.d(TAG, "Intent amb Flow subscription...")
                structures = withTimeoutOrNull(10000L) {
                    client.structures().first()
                } ?: emptySet()
                Log.d(TAG, "Flow subscription - Estructures: ${structures.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error amb Flow subscription: ${e.message}")
            }

            // Si Flow no funciona, provar amb list() i retry
            if (structures.isEmpty()) {
                val maxRetries = 10
                val retryDelay = 2000L // 2 segons
                var retries = 0

                while (retries < maxRetries && structures.isEmpty()) {
                    if (retries > 0) {
                        Log.d(TAG, "Esperant que les dades estiguin disponibles (intent ${retries + 1}/$maxRetries)...")
                        delay(retryDelay)
                    }

                    try {
                        structures = client.structures().list()
                        Log.d(TAG, "list() - Estructures obtingudes: ${structures.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error obtenint estructures (intent ${retries + 1}): ${e.message}")
                    }
                    retries++
                }

                if (structures.isEmpty()) {
                    Log.w(TAG, "No s'han trobat estructures després de $retries intents (${retries * retryDelay / 1000}s)")
                    return@withContext Result.success(emptyList())
                }
            }

            Log.d(TAG, "Trobades ${structures.size} estructures")

            for (structure in structures) {
                try {
                    Log.d(TAG, "Processant estructura...")

                    // Obtenir dispositius de l'estructura
                    val homeDevices = structure.devices().list()
                    Log.d(TAG, "Dispositius a l'estructura: ${homeDevices.size}")

                    for (homeDevice in homeDevices) {
                        try {
                            val deviceId = homeDevice.id.id

                            // Obtenir el nom real del dispositiu
                            val deviceName = try {
                                val name = homeDevice.name
                                if (name.isNotEmpty()) name else "Dispositiu ${devices.size + 1}"
                            } catch (e: Exception) {
                                Log.w(TAG, "No s'ha pogut obtenir el nom: ${e.message}")
                                "Dispositiu ${devices.size + 1}"
                            }

                            // Timeout per evitar bloqueig si el Flow no emet
                            val types = withTimeoutOrNull(5000L) {
                                homeDevice.types().first()
                            }

                            if (types == null) {
                                Log.w(TAG, "Timeout obtenint tipus per dispositiu: $deviceId")
                                continue
                            }

                            Log.d(TAG, "Processant dispositiu: $deviceName ($deviceId) amb ${types.size} tipus")

                            // Determinar tipus, estat i si té OnOff
                            var deviceType = GoogleDeviceType.OTHER
                            var hasOnOff = false
                            var currentOnOffState: Boolean? = null

                            for (type in types) {
                                when (type) {
                                    is OnOffLightDevice -> {
                                        deviceType = GoogleDeviceType.LIGHT
                                        val onOffTrait = type.standardTraits.onOff
                                        hasOnOff = onOffTrait != null
                                        // Llegir l'estat actual del dispositiu
                                        currentOnOffState = try {
                                            onOffTrait?.onOff
                                        } catch (e: Exception) {
                                            Log.w(TAG, "No s'ha pogut llegir estat OnOff: ${e.message}")
                                            null
                                        }
                                        Log.d(TAG, "Dispositiu és OnOffLightDevice, hasOnOff=$hasOnOff, isOn=$currentOnOffState")
                                    }
                                    is DimmableLightDevice -> {
                                        deviceType = GoogleDeviceType.LIGHT
                                        val onOffTrait = type.standardTraits.onOff
                                        hasOnOff = onOffTrait != null
                                        currentOnOffState = try {
                                            onOffTrait?.onOff
                                        } catch (e: Exception) {
                                            Log.w(TAG, "No s'ha pogut llegir estat OnOff: ${e.message}")
                                            null
                                        }
                                        Log.d(TAG, "Dispositiu és DimmableLightDevice, hasOnOff=$hasOnOff, isOn=$currentOnOffState")
                                    }
                                    is OnOffPluginUnitDevice -> {
                                        deviceType = GoogleDeviceType.OUTLET
                                        val onOffTrait = type.standardTraits.onOff
                                        hasOnOff = onOffTrait != null
                                        currentOnOffState = try {
                                            onOffTrait?.onOff
                                        } catch (e: Exception) {
                                            Log.w(TAG, "No s'ha pogut llegir estat OnOff: ${e.message}")
                                            null
                                        }
                                        Log.d(TAG, "Dispositiu és OnOffPluginUnitDevice, hasOnOff=$hasOnOff, isOn=$currentOnOffState")
                                    }
                                    else -> {
                                        Log.d(TAG, "Dispositiu tipus desconegut: ${type::class.simpleName}")
                                    }
                                }
                            }

                            // Actualitzar cache amb l'estat real
                            val isOn = currentOnOffState ?: false
                            deviceCache[deviceId] = CachedDevice(deviceId, isOn)

                            devices.add(
                                GoogleHomeDevice(
                                    id = deviceId,
                                    name = deviceName,
                                    deviceType = deviceType,
                                    roomName = null,
                                    traits = if (hasOnOff) listOf(DeviceTrait.OnOff(isOn)) else emptyList(),
                                    isOnline = true
                                )
                            )
                            Log.d(TAG, "Dispositiu afegit: $deviceName ($deviceId)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processant dispositiu: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processant estructura: ${e.message}")
                }
            }

            Log.d(TAG, "Total dispositius trobats: ${devices.size}")
            Result.success(devices)
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint dispositius: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obté dispositius que suporten control ON/OFF.
     */
    suspend fun getControllableDevices(): Result<List<GoogleHomeDevice>> = withContext(Dispatchers.IO) {
        getDevices().map { deviceList ->
            deviceList.filter { device ->
                device.traits.any { it is DeviceTrait.OnOff }
            }
        }
    }

    /**
     * Envia comanda ON/OFF a un dispositiu.
     * Comprova primer si el dispositiu ja està en l'estat desitjat per evitar comandes innecessàries.
     */
    suspend fun setDeviceOnOff(deviceId: String, on: Boolean): CommandResult = withContext(Dispatchers.IO) {
        try {
            val client = homeClient ?: return@withContext CommandResult.Error("Client no inicialitzat")

            Log.d(TAG, "Controlant dispositiu $deviceId: ${if (on) "ON" else "OFF"}")

            // Buscar dispositiu a les estructures
            val structures = client.structures().list()

            for (structure in structures) {
                val homeDevices = structure.devices().list()

                for (homeDevice in homeDevices) {
                    if (homeDevice.id.id == deviceId) {
                        val types = homeDevice.types().first()

                        for (type in types) {
                            val onOffTrait = when (type) {
                                is OnOffLightDevice -> type.standardTraits.onOff
                                is DimmableLightDevice -> type.standardTraits.onOff
                                is OnOffPluginUnitDevice -> type.standardTraits.onOff
                                else -> null
                            }

                            if (onOffTrait != null) {
                                // Comprovar estat actual abans d'enviar comanda
                                val currentState = try {
                                    onOffTrait.onOff ?: false
                                } catch (e: Exception) {
                                    Log.w(TAG, "No s'ha pogut llegir estat actual: ${e.message}")
                                    null // Si no podem llegir, enviem la comanda igualment
                                }

                                if (currentState != null && currentState == on) {
                                    Log.d(TAG, "Dispositiu $deviceId ja està ${if (on) "encès" else "apagat"}, saltant comanda")
                                    // Actualitzar cache per assegurar consistència
                                    deviceCache[deviceId]?.isOn = on
                                    return@withContext CommandResult.Success
                                }

                                // Enviar comanda
                                if (on) {
                                    onOffTrait.on()
                                } else {
                                    onOffTrait.off()
                                }

                                // Actualitzar cache
                                deviceCache[deviceId]?.isOn = on

                                Log.d(TAG, "Comanda enviada correctament")
                                return@withContext CommandResult.Success
                            }
                        }
                    }
                }
            }

            CommandResult.Error("Dispositiu no trobat")
        } catch (e: HomeException) {
            Log.e(TAG, "HomeException: ${e.message}", e)
            CommandResult.Error(e.message ?: "Error executant comanda")
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            CommandResult.Error(e.message ?: "Error desconegut")
        }
    }

    /**
     * Obté l'estat actual d'un dispositiu.
     * Intenta obtenir l'estat real del SDK, amb fallback al cache.
     */
    suspend fun getDeviceState(deviceId: String): Result<Boolean?> = withContext(Dispatchers.IO) {
        try {
            val client = homeClient ?: return@withContext Result.success(deviceCache[deviceId]?.isOn)

            if (!_isAuthorized.value) {
                Log.d(TAG, "No autoritzat, retornant cache per $deviceId")
                return@withContext Result.success(deviceCache[deviceId]?.isOn)
            }

            // Intentar obtenir l'estat real del dispositiu amb timeout
            val realState = withTimeoutOrNull(5000L) {
                getRealDeviceState(client, deviceId)
            }

            if (realState != null) {
                // Actualitzar cache amb l'estat real
                deviceCache[deviceId] = CachedDevice(deviceId, realState)
                Log.d(TAG, "Estat real obtingut per $deviceId: $realState")
                Result.success(realState)
            } else {
                // Fallback al cache si no podem obtenir l'estat real
                Log.w(TAG, "Timeout obtenint estat real, utilitzant cache per $deviceId")
                Result.success(deviceCache[deviceId]?.isOn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint estat de $deviceId, utilitzant cache: ${e.message}")
            Result.success(deviceCache[deviceId]?.isOn)
        }
    }

    /**
     * Obté l'estat real d'un dispositiu des del SDK.
     */
    private suspend fun getRealDeviceState(client: HomeClient, deviceId: String): Boolean? {
        try {
            val structures = client.structures().first()

            for (structure in structures) {
                val homeDevices = structure.devices().list()

                for (homeDevice in homeDevices) {
                    if (homeDevice.id.id == deviceId) {
                        val types = withTimeoutOrNull(3000L) {
                            homeDevice.types().first()
                        } ?: return null

                        for (type in types) {
                            val onOffTrait = when (type) {
                                is OnOffLightDevice -> type.standardTraits.onOff
                                is DimmableLightDevice -> type.standardTraits.onOff
                                is OnOffPluginUnitDevice -> type.standardTraits.onOff
                                else -> null
                            }

                            if (onOffTrait != null) {
                                return try {
                                    onOffTrait.onOff
                                } catch (e: Exception) {
                                    Log.w(TAG, "No s'ha pogut llegir estat OnOff: ${e.message}")
                                    null
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obtenint estat real: ${e.message}")
        }
        return null
    }

    /**
     * Refresca els estats de tots els dispositius des de Google Home SDK.
     * Actualitza el cache intern amb els estats actuals.
     */
    suspend fun refreshDeviceStates(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!_isAuthorized.value) {
                Log.d(TAG, "No autoritzat, no es pot refrescar estats")
                return@withContext Result.success(Unit)
            }

            val client = homeClient ?: return@withContext Result.success(Unit)

            Log.d(TAG, "Refrescant estats dels dispositius...")

            val structures = withTimeoutOrNull(5000L) {
                client.structures().first()
            } ?: return@withContext Result.success(Unit)

            for (structure in structures) {
                try {
                    val homeDevices = structure.devices().list()

                    for (homeDevice in homeDevices) {
                        try {
                            val deviceId = homeDevice.id.id
                            val types = withTimeoutOrNull(3000L) {
                                homeDevice.types().first()
                            } ?: continue

                            for (type in types) {
                                val onOffTrait = when (type) {
                                    is OnOffLightDevice -> type.standardTraits.onOff
                                    is DimmableLightDevice -> type.standardTraits.onOff
                                    is OnOffPluginUnitDevice -> type.standardTraits.onOff
                                    else -> null
                                }

                                if (onOffTrait != null) {
                                    val currentState = try {
                                        onOffTrait.onOff ?: false
                                    } catch (e: Exception) {
                                        false
                                    }
                                    deviceCache[deviceId] = CachedDevice(deviceId, currentState)
                                    Log.d(TAG, "Estat refrescat: $deviceId = $currentState")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error refrescant dispositiu: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processant estructura: ${e.message}")
                }
            }

            Log.d(TAG, "Estats refrescats: ${deviceCache.size} dispositius")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescant estats: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Inicia l'observació de canvis d'estat de tots els dispositius.
     * Subscriu-se als Flows del SDK i emet canvis via deviceStateChanges.
     */
    suspend fun startObservingDeviceStates() = coroutineScope {
        if (!_isAuthorized.value) {
            Log.d(TAG, "No autoritzat, no es pot iniciar observació")
            return@coroutineScope
        }

        // Cancel·lar observació anterior si existeix
        observationJob?.cancelAndJoin()

        val client = homeClient ?: return@coroutineScope

        Log.d(TAG, "Iniciant observació de canvis d'estat...")

        observationJob = launch(Dispatchers.IO) {
            try {
                // Subscriure's a les estructures
                client.structures().collect { structures ->
                    Log.d(TAG, "Estructures actualitzades: ${structures.size}")

                    for (structure in structures) {
                        // Subscriure's als dispositius de cada estructura
                        launch {
                            try {
                                structure.devices().collect { devices ->
                                    for (homeDevice in devices) {
                                        // Subscriure's als tipus/traits de cada dispositiu
                                        launch {
                                            observeDeviceTypes(homeDevice)
                                        }
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                Log.d(TAG, "Observació de dispositius cancel·lada")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error observant dispositius: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Observació d'estructures cancel·lada")
            } catch (e: Exception) {
                Log.e(TAG, "Error en observació: ${e.message}")
            }
        }
    }

    /**
     * Observa els canvis de tipus/traits d'un dispositiu específic.
     */
    private suspend fun observeDeviceTypes(homeDevice: com.google.home.HomeDevice) {
        val deviceId = homeDevice.id.id

        try {
            homeDevice.types().collect { types ->
                for (type in types) {
                    val onOffTrait = when (type) {
                        is OnOffLightDevice -> type.standardTraits.onOff
                        is DimmableLightDevice -> type.standardTraits.onOff
                        is OnOffPluginUnitDevice -> type.standardTraits.onOff
                        else -> null
                    }

                    if (onOffTrait != null) {
                        val currentState = try {
                            onOffTrait.onOff ?: false
                        } catch (e: Exception) {
                            false
                        }

                        // Comprovar si l'estat ha canviat
                        val previousState = deviceCache[deviceId]?.isOn
                        if (previousState != currentState) {
                            Log.d(TAG, "Canvi detectat: $deviceId -> $currentState (abans: $previousState)")

                            // Actualitzar cache
                            deviceCache[deviceId] = CachedDevice(deviceId, currentState)

                            // Emetre el canvi
                            _deviceStateChanges.emit(
                                DeviceStateChange(
                                    deviceId = deviceId,
                                    isOn = currentState
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Ignorar cancel·lacions - és normal quan es reinicia l'observació
            Log.d(TAG, "Observació cancel·lada per dispositiu $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error observant dispositiu $deviceId: ${e.message}")
        }
    }

    /**
     * Atura l'observació de canvis d'estat.
     */
    suspend fun stopObservingDeviceStates() {
        Log.d(TAG, "Aturant observació de canvis d'estat...")
        observationJob?.cancelAndJoin()
        observationJob = null
    }

    /**
     * Neteja recursos.
     */
    fun cleanup() {
        _isAuthorized.value = false
        _authState.value = GoogleHomeAuthState.NOT_INITIALIZED
        deviceCache.clear()
        activityRef = null
        observationJob?.cancel()
        observationJob = null
    }

    // Per compatibilitat
    suspend fun requestAuthorization(): Result<Intent> = withContext(Dispatchers.IO) {
        Result.failure(NotImplementedError("Utilitza requestPermissionsAsync()"))
    }

    suspend fun handleAuthorizationResult(resultCode: Int, data: Intent?): HomeAuthResult {
        return withContext(Dispatchers.IO) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                _isAuthorized.value = true
                _authState.value = GoogleHomeAuthState.AUTHORIZED
                HomeAuthResult.Success
            } else {
                HomeAuthResult.Cancelled
            }
        }
    }
}
