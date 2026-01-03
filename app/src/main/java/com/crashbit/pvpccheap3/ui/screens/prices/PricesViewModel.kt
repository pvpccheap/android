package com.crashbit.pvpccheap3.ui.screens.prices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crashbit.pvpccheap3.data.model.DailyPrices
import com.crashbit.pvpccheap3.data.model.HourlyPrice
import com.crashbit.pvpccheap3.data.repository.PriceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PricesUiState(
    val isLoading: Boolean = false,
    val selectedTab: Int = 0, // 0 = today, 1 = tomorrow
    val todayPrices: DailyPrices? = null,
    val tomorrowPrices: DailyPrices? = null,
    val error: String? = null
)

@HiltViewModel
class PricesViewModel @Inject constructor(
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PricesUiState())
    val uiState: StateFlow<PricesUiState> = _uiState.asStateFlow()

    init {
        loadPrices()
    }

    fun loadPrices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load today's prices
            priceRepository.getTodayPrices()
                .onSuccess { prices ->
                    _uiState.value = _uiState.value.copy(todayPrices = prices)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Error carregant preus d'avui"
                    )
                }

            // Load tomorrow's prices
            priceRepository.getTomorrowPrices()
                .onSuccess { prices ->
                    _uiState.value = _uiState.value.copy(tomorrowPrices = prices)
                }
                .onFailure {
                    // Tomorrow's prices might not be available yet, that's ok
                }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun selectTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getCurrentPrices(): List<HourlyPrice> {
        return when (_uiState.value.selectedTab) {
            0 -> _uiState.value.todayPrices?.prices ?: emptyList()
            1 -> _uiState.value.tomorrowPrices?.prices ?: emptyList()
            else -> emptyList()
        }
    }

    fun getCheapestHours(count: Int): List<Int> {
        return getCurrentPrices()
            .sortedBy { it.price }
            .take(count)
            .map { it.hour }
            .sorted()
    }
}
