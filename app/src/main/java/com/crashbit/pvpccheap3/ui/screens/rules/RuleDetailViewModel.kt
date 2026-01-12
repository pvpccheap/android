package com.crashbit.pvpccheap3.ui.screens.rules

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crashbit.pvpccheap3.data.model.*
import com.crashbit.pvpccheap3.data.repository.DeviceRepository
import com.crashbit.pvpccheap3.data.repository.RuleRepository
import com.crashbit.pvpccheap3.service.ScheduleExecutorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuleDetailUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val devices: List<Device> = emptyList(),
    val selectedDeviceId: String? = null,
    val name: String = "",
    val maxHours: Int = 1,
    val minContinuousHours: Int = 1,
    val timeWindowStart: String? = null,
    val timeWindowEnd: String? = null,
    val daysOfWeek: Int = DaysOfWeek.ALL_DAYS,
    val isEnabled: Boolean = true,
    val error: String? = null,
    val isEditMode: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class RuleDetailViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val deviceRepository: DeviceRepository,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: String? = savedStateHandle.get<String>("ruleId")?.takeIf { it != "new" }

    private val _uiState = MutableStateFlow(RuleDetailUiState(isEditMode = ruleId != null))
    val uiState: StateFlow<RuleDetailUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
        ruleId?.let { loadRule(it) }
    }

    private fun loadDevices() {
        viewModelScope.launch {
            deviceRepository.getDevices()
                .onSuccess { devices ->
                    _uiState.value = _uiState.value.copy(
                        devices = devices.filter { it.isActive }
                    )
                }
        }
    }

    private fun loadRule(ruleId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            ruleRepository.getRule(ruleId)
                .onSuccess { rule ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedDeviceId = rule.deviceId,
                        name = rule.name,
                        maxHours = rule.maxHours,
                        minContinuousHours = rule.minContinuousHours,
                        timeWindowStart = rule.timeWindowStart,
                        timeWindowEnd = rule.timeWindowEnd,
                        daysOfWeek = rule.daysOfWeek,
                        isEnabled = rule.isEnabled
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error carregant regla"
                    )
                }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateSelectedDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedDeviceId = deviceId)
    }

    fun updateMaxHours(hours: Int) {
        _uiState.value = _uiState.value.copy(
            maxHours = hours.coerceIn(1, 24),
            minContinuousHours = _uiState.value.minContinuousHours.coerceAtMost(hours)
        )
    }

    fun updateMinContinuousHours(hours: Int) {
        _uiState.value = _uiState.value.copy(
            minContinuousHours = hours.coerceIn(1, _uiState.value.maxHours)
        )
    }

    fun updateTimeWindow(start: String?, end: String?) {
        _uiState.value = _uiState.value.copy(
            timeWindowStart = start,
            timeWindowEnd = end
        )
    }

    fun toggleDayOfWeek(day: Int) {
        _uiState.value = _uiState.value.copy(
            daysOfWeek = DaysOfWeek.toggle(_uiState.value.daysOfWeek, day)
        )
    }

    fun save() {
        val state = _uiState.value

        if (state.selectedDeviceId == null) {
            _uiState.value = state.copy(error = "Selecciona un dispositiu")
            return
        }

        if (state.name.isBlank()) {
            _uiState.value = state.copy(error = "Introdueix un nom per la regla")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)

            val result = if (ruleId != null) {
                ruleRepository.updateRule(
                    ruleId,
                    UpdateRuleRequest(
                        name = state.name,
                        maxHours = state.maxHours,
                        minContinuousHours = state.minContinuousHours,
                        timeWindowStart = state.timeWindowStart,
                        timeWindowEnd = state.timeWindowEnd,
                        daysOfWeek = state.daysOfWeek,
                        isEnabled = state.isEnabled
                    )
                )
            } else {
                ruleRepository.createRule(
                    CreateRuleRequest(
                        deviceId = state.selectedDeviceId,
                        name = state.name,
                        maxHours = state.maxHours,
                        minContinuousHours = state.minContinuousHours,
                        timeWindowStart = state.timeWindowStart,
                        timeWindowEnd = state.timeWindowEnd,
                        daysOfWeek = state.daysOfWeek
                    )
                )
            }

            result
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveSuccess = true
                    )
                    // Forçar sincronització de schedules després de modificar una regla
                    ScheduleExecutorService.syncPrices(context)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = e.message ?: "Error guardant regla"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
