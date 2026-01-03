package com.crashbit.pvpccheap3.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crashbit.pvpccheap3.data.model.ScheduledAction
import com.crashbit.pvpccheap3.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val scheduledActions: List<ScheduledAction> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        loadSchedule()
    }

    fun loadSchedule(showLoading: Boolean = true) {
        viewModelScope.launch {
            // Només mostrar loading a la càrrega inicial (quan no hi ha dades)
            if (showLoading && _uiState.value.scheduledActions.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            val date = _uiState.value.selectedDate
            val result = if (date == LocalDate.now()) {
                scheduleRepository.getTodaySchedule()
            } else {
                scheduleRepository.getScheduleByDate(date.format(dateFormatter))
            }

            result
                .onSuccess { actions ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        scheduledActions = actions.sortedBy { it.startTime }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error carregant horari"
                    )
                }
        }
    }

    /**
     * Refresca l'horari sense mostrar l'indicador de càrrega.
     */
    fun refreshSchedule() {
        loadSchedule(showLoading = false)
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadSchedule()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
