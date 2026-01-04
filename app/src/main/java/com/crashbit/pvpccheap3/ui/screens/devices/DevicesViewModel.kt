package com.crashbit.pvpccheap3.ui.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crashbit.pvpccheap3.data.model.*
import com.crashbit.pvpccheap3.data.repository.DeviceRepository
import com.crashbit.pvpccheap3.data.repository.GoogleHomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<DeviceWithState> = emptyList(),
    val googleHomeDevices: List<GoogleHomeDevice> = emptyList(),
    val error: String? = null,
    val isSyncing: Boolean = false,
    val googleHomeAuthState: GoogleHomeAuthState = GoogleHomeAuthState.NOT_INITIALIZED,
    val showDeviceSelector: Boolean = false
)

sealed class DevicesEvent {
    data class ShowError(val message: String) : DevicesEvent()
    data class DeviceControlled(val deviceName: String, val isOn: Boolean) : DevicesEvent()
    data object SyncCompleted : DevicesEvent()
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val googleHomeRepository: GoogleHomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<DevicesEvent?>(null)
    val events: StateFlow<DevicesEvent?> = _events.asStateFlow()

    init {
        // Sempre carregar dispositius del backend primer (independentment de Google Home auth)
        loadDevicesFromBackend()
        initializeGoogleHome()
        observeGoogleHomeAuth()
        observeDeviceStateChanges()
    }

    private fun initializeGoogleHome() {
        viewModelScope.launch {
            googleHomeRepository.initialize()
        }
    }

    private fun observeGoogleHomeAuth() {
        viewModelScope.launch {
            googleHomeRepository.authState.collect { authState ->
                _uiState.value = _uiState.value.copy(googleHomeAuthState = authState)
                if (authState == GoogleHomeAuthState.AUTHORIZED) {
                    // Quan s'autoritza, refrescar estats dels dispositius i iniciar observació
                    refreshDeviceStates()
                    startDeviceStateObservation()
                }
            }
        }
    }

    /**
     * Carrega dispositius del backend (sense dependre de Google Home auth).
     * Els dispositius sincronitzats es guarden al backend i sempre estan disponibles.
     */
    private fun loadDevicesFromBackend() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            deviceRepository.getDevices()
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        devices = devices.map { device ->
                            DeviceWithState(
                                device = device,
                                isOn = null,  // Estat desconegut fins que Google Home estigui autoritzat
                                isOnline = false
                            )
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error carregant dispositius"
                    )
                }
        }
    }

    /**
     * Refresca els estats dels dispositius des de Google Home.
     */
    private fun refreshDeviceStates() {
        viewModelScope.launch {
            deviceRepository.getDevicesWithState()
                .onSuccess { devicesWithState ->
                    _uiState.value = _uiState.value.copy(devices = devicesWithState)
                }
        }
    }

    /**
     * Inicia l'observació de canvis d'estat dels dispositius.
     */
    private fun startDeviceStateObservation() {
        viewModelScope.launch {
            googleHomeRepository.startObservingDeviceStates()
        }
    }

    /**
     * Observa els canvis d'estat dels dispositius i actualitza la UI.
     */
    private fun observeDeviceStateChanges() {
        viewModelScope.launch {
            googleHomeRepository.deviceStateChanges.collect { stateChange ->
                // Actualitzar l'estat del dispositiu a la UI
                _uiState.value = _uiState.value.copy(
                    devices = _uiState.value.devices.map { deviceWithState ->
                        if (deviceWithState.device.googleDeviceId == stateChange.deviceId) {
                            deviceWithState.copy(isOn = stateChange.isOn)
                        } else {
                            deviceWithState
                        }
                    }
                )
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Si tenim autorització de Google Home, carregar amb estats
            if (_uiState.value.googleHomeAuthState == GoogleHomeAuthState.AUTHORIZED) {
                deviceRepository.getDevicesWithState()
                    .onSuccess { devices ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            devices = devices
                        )
                    }
                    .onFailure {
                        // Fallback a carregar només del backend
                        loadDevicesFromBackendInternal()
                    }
            } else {
                // Sense autorització, carregar només del backend
                loadDevicesFromBackendInternal()
            }
        }
    }

    private suspend fun loadDevicesFromBackendInternal() {
        deviceRepository.getDevices()
            .onSuccess { devices ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    devices = devices.map { device ->
                        DeviceWithState(
                            device = device,
                            isOn = null,
                            isOnline = false
                        )
                    }
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error carregant dispositius"
                )
            }
    }

    /**
     * Inicia el flux d'autorització de Google Home.
     */
    fun requestGoogleHomeAuthorization() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)

            when (val result = googleHomeRepository.requestPermissionsAsync(forceLaunch = true)) {
                is HomeAuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        googleHomeAuthState = GoogleHomeAuthState.AUTHORIZED
                    )
                    // Donar temps al SDK per sincronitzar després de l'autorització
                    kotlinx.coroutines.delay(1000)
                    startGoogleHomeSync()
                }
                is HomeAuthResult.Error -> {
                    // Si falla, prova amb simulació per a proves
                    simulateGoogleHomeAuth()
                }
                is HomeAuthResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                }
            }
        }
    }

    /**
     * Simula autorització per a proves (quan no hi ha SDK).
     */
    private fun simulateGoogleHomeAuth() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            when (val result = googleHomeRepository.simulateAuthorization()) {
                is HomeAuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        googleHomeAuthState = GoogleHomeAuthState.AUTHORIZED
                    )
                    startGoogleHomeSync()
                }
                is HomeAuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        error = result.message
                    )
                }
                is HomeAuthResult.Cancelled -> {
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                }
            }
        }
    }

    /**
     * Inicia la sincronització amb Google Home.
     */
    fun startGoogleHomeSync() {
        viewModelScope.launch {
            if (_uiState.value.googleHomeAuthState != GoogleHomeAuthState.AUTHORIZED) {
                requestGoogleHomeAuthorization()
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSyncing = true)

            // Obtenir dispositius de Google Home
            googleHomeRepository.getControllableDevices()
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(
                        googleHomeDevices = devices,
                        showDeviceSelector = true,
                        isSyncing = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        error = "Error obtenint dispositius: ${e.message}"
                    )
                }
        }
    }

    /**
     * Sincronitza els dispositius seleccionats amb el backend.
     */
    fun syncSelectedDevices(selectedDevices: List<GoogleHomeDevice>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                showDeviceSelector = false
            )

            val syncItems = selectedDevices.map { it.toSyncItem() }

            deviceRepository.syncDevices(syncItems)
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                    _events.value = DevicesEvent.SyncCompleted
                    loadDevices() // Recarregar amb nous dispositius
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        error = "Error sincronitzant: ${e.message}"
                    )
                }
        }
    }

    /**
     * Cancel·la la selecció de dispositius.
     */
    fun cancelDeviceSelection() {
        _uiState.value = _uiState.value.copy(
            showDeviceSelector = false,
            googleHomeDevices = emptyList()
        )
    }

    /**
     * Controla un dispositiu (ON/OFF).
     */
    fun controlDevice(deviceWithState: DeviceWithState, turnOn: Boolean) {
        viewModelScope.launch {
            // Actualitzar UI optimisticament
            _uiState.value = _uiState.value.copy(
                devices = _uiState.value.devices.map {
                    if (it.device.id == deviceWithState.device.id) {
                        it.copy(isExecutingCommand = true)
                    } else it
                }
            )

            when (val result = deviceRepository.controlDevice(deviceWithState.device.googleDeviceId, turnOn)) {
                is CommandResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        devices = _uiState.value.devices.map {
                            if (it.device.id == deviceWithState.device.id) {
                                it.copy(isOn = turnOn, isExecutingCommand = false)
                            } else it
                        }
                    )
                    _events.value = DevicesEvent.DeviceControlled(deviceWithState.device.name, turnOn)
                }
                is CommandResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        devices = _uiState.value.devices.map {
                            if (it.device.id == deviceWithState.device.id) {
                                it.copy(isExecutingCommand = false)
                            } else it
                        },
                        error = result.message
                    )
                }
            }
        }
    }

    fun toggleDeviceActive(device: Device) {
        viewModelScope.launch {
            deviceRepository.updateDevice(device.id, null, !device.isActive)
                .onSuccess { updatedDevice ->
                    _uiState.value = _uiState.value.copy(
                        devices = _uiState.value.devices.map {
                            if (it.device.id == updatedDevice.id) {
                                it.copy(device = updatedDevice)
                            } else it
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Error actualitzant dispositiu"
                    )
                }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.deleteDevice(deviceId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        devices = _uiState.value.devices.filter { it.device.id != deviceId }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Error eliminant dispositiu"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearEvent() {
        _events.value = null
    }
}
