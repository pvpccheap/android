package com.crashbit.pvpccheap3.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crashbit.pvpccheap3.data.model.RuleWithDevice
import com.crashbit.pvpccheap3.data.model.UpdateRuleRequest
import com.crashbit.pvpccheap3.data.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUiState(
    val isLoading: Boolean = false,
    val rules: List<RuleWithDevice> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    fun loadRules(showLoading: Boolean = true) {
        viewModelScope.launch {
            // Només mostrar loading a la càrrega inicial (quan no hi ha dades)
            if (showLoading && _uiState.value.rules.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }

            ruleRepository.getRules()
                .onSuccess { rules ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        rules = rules
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error carregant regles"
                    )
                }
        }
    }

    /**
     * Refresca les regles sense mostrar l'indicador de càrrega.
     * Útil quan es torna a la pantalla des d'una altra.
     */
    fun refreshRules() {
        loadRules(showLoading = false)
    }

    fun toggleRuleEnabled(rule: RuleWithDevice) {
        viewModelScope.launch {
            ruleRepository.updateRule(rule.id, UpdateRuleRequest(isEnabled = !rule.isEnabled))
                .onSuccess { updatedRule ->
                    _uiState.value = _uiState.value.copy(
                        rules = _uiState.value.rules.map {
                            if (it.id == updatedRule.id) updatedRule else it
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Error actualitzant regla"
                    )
                }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            ruleRepository.deleteRule(ruleId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        rules = _uiState.value.rules.filter { it.id != ruleId }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Error eliminant regla"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
